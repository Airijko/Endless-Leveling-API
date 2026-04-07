package com.airijko.endlessleveling.commands.classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.math.util.MathUtil;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages church prefab placement and undo for the Priest class.
 * <p>
 * Each priest can have at most one church placed at a time. The manager
 * persists church metadata (owner UUID, world name, position, bounding box)
 * to a JSON file so that churches survive server restarts and can be
 * properly undone even across sessions.
 * <p>
 * The undo mechanism captures the block IDs that occupied the bounding box
 * before the prefab was pasted, then restores them on undo.
 */
public final class ChurchManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String PERSISTENCE_FILE = "church-placements.json";
    private static final String SNAPSHOTS_DIR = "church-snapshots";
    private static final String PREFAB_FILE_NAME = "Church.prefab.json";
    private static final long PLACEMENT_COOLDOWN_MS = 30_000L;
    private static final int MAX_PREFAB_DIMENSION = 256;

    private static ChurchManager INSTANCE;

    private final Path dataFolder;
    private final Path prefabPath;
    private final ConcurrentHashMap<UUID, ChurchPlacement> placements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, long[]> confirmTimers = new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static synchronized ChurchManager init(Path pluginDataFolder) {
        if (INSTANCE == null) {
            INSTANCE = new ChurchManager(pluginDataFolder);
        }
        return INSTANCE;
    }

    public static ChurchManager get() {
        return INSTANCE;
    }

    private ChurchManager(Path pluginDataFolder) {
        this.dataFolder = pluginDataFolder;
        this.prefabPath = resolvePrefabPath();
        load();
    }

    private Path resolvePrefabPath() {
        // 1) Use PrefabStore to check the server's prefabs/ directory
        PrefabStore prefabStore = PrefabStore.get();
        if (prefabStore != null) {
            Path serverPrefabs = prefabStore.getServerPrefabsPath();
            if (serverPrefabs != null) {
                Path candidate = serverPrefabs.resolve(PREFAB_FILE_NAME);
                if (Files.exists(candidate)) {
                    LOGGER.atInfo().log("Found church prefab via PrefabStore: %s", candidate);
                    return candidate;
                }
            }
            // 2) Search asset packs for the prefab
            Path assetPath = prefabStore.findAssetPrefabPath(PREFAB_FILE_NAME);
            if (assetPath != null && Files.exists(assetPath)) {
                LOGGER.atInfo().log("Found church prefab in asset pack: %s", assetPath);
                return assetPath;
            }
        }

        // 3) Fallback: mod-specific prefabs dir
        Path modsPath = PluginManager.MODS_PATH;
        if (modsPath != null) {
            Path modPrefabs = modsPath.resolve("EndlessLeveling").resolve("prefabs");
            Path candidate = modPrefabs.resolve(PREFAB_FILE_NAME);
            if (Files.exists(candidate)) {
                return candidate;
            }
            // 4) Server directory sibling
            Path serverRoot = modsPath.getParent();
            if (serverRoot != null) {
                Path serverPrefabs = serverRoot.resolve("prefabs");
                candidate = serverPrefabs.resolve(PREFAB_FILE_NAME);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        // 5) Plugin data folder
        Path fallback = dataFolder.resolve("prefabs").resolve(PREFAB_FILE_NAME);
        return fallback;
    }

    public boolean isPrefabAvailable() {
        return prefabPath != null && Files.exists(prefabPath);
    }

    public Path getPrefabPath() {
        return prefabPath;
    }

    public boolean hasChurch(UUID playerUuid) {
        return placements.containsKey(playerUuid);
    }

    public ChurchPlacement getChurch(UUID playerUuid) {
        return placements.get(playerUuid);
    }

    public boolean isOnCooldown(UUID playerUuid) {
        Long last = cooldowns.get(playerUuid);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < PLACEMENT_COOLDOWN_MS;
    }

    public long getRemainingCooldownSeconds(UUID playerUuid) {
        Long last = cooldowns.get(playerUuid);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        long remaining = PLACEMENT_COOLDOWN_MS - elapsed;
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    /**
     * Request confirmation for placement. Returns true if a fresh confirmation
     * window was opened. Returns false if the player already has a pending
     * confirmation (within 15 seconds).
     */
    public boolean requestPlacementConfirmation(UUID playerUuid) {
        long now = System.currentTimeMillis();
        long[] timer = confirmTimers.get(playerUuid);
        if (timer != null && (now - timer[0]) < 15_000L) {
            // Confirmation is still pending — this is the second call = confirmed
            confirmTimers.remove(playerUuid);
            return false; // false = already confirmed, proceed
        }
        confirmTimers.put(playerUuid, new long[]{now});
        return true; // true = confirmation requested, wait for second call
    }

    public boolean requestUndoConfirmation(UUID playerUuid) {
        long now = System.currentTimeMillis();
        long[] timer = confirmTimers.get(playerUuid);
        if (timer != null && (now - timer[0]) < 15_000L) {
            confirmTimers.remove(playerUuid);
            return false;
        }
        confirmTimers.put(playerUuid, new long[]{now});
        return true;
    }

    /**
     * Place the church prefab at the given position in the given world.
     * Captures a block snapshot first for undo.
     *
     * @return null on success, or an error message on failure
     */
    public String placeChurch(UUID playerUuid, World world, Vector3i position,
                              Store<EntityStore> store) {
        if (!isPrefabAvailable()) {
            return "Church prefab file not found on server. Contact an administrator.";
        }

        if (hasChurch(playerUuid)) {
            return "You already have a church placed. Use /priest undo church to remove it first.";
        }

        if (isOnCooldown(playerUuid)) {
            return "Please wait " + getRemainingCooldownSeconds(playerUuid) + "s before placing another church.";
        }

        PrefabBuffer prefabBuffer;
        try {
            prefabBuffer = PrefabBufferUtil.loadBuffer(prefabPath);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to load church prefab: %s", e.getMessage());
            return "Failed to load church prefab. Contact an administrator.";
        }

        PrefabBuffer.PrefabBufferAccessor accessor = prefabBuffer.newAccess();
        try {
            int minX = accessor.getMinX(PrefabRotation.ROTATION_0);
            int minY = accessor.getMinY();
            int minZ = accessor.getMinZ(PrefabRotation.ROTATION_0);
            int maxX = accessor.getMaxX(PrefabRotation.ROTATION_0);
            int maxY = accessor.getMaxY();
            int maxZ = accessor.getMaxZ(PrefabRotation.ROTATION_0);

            int sizeX = maxX - minX;
            int sizeY = maxY - minY;
            int sizeZ = maxZ - minZ;

            if (sizeX > MAX_PREFAB_DIMENSION || sizeY > MAX_PREFAB_DIMENSION || sizeZ > MAX_PREFAB_DIMENSION) {
                return "Church prefab exceeds maximum allowed dimensions.";
            }

            // Compute adjusted paste position so the prefab is centered
            // horizontally on the player and the floor sits at the player's feet.
            int centerOffsetX = (minX + maxX) / 2;
            int centerOffsetZ = (minZ + maxZ) / 2;
            Vector3i adjustedPosition = new Vector3i(
                    position.x - centerOffsetX,   // center X on player
                    position.y - minY,             // floor at player feet
                    position.z - centerOffsetZ     // center Z on player
            );

            // Capture the block snapshot for undo using adjusted position
            // Expand by 1 block in every direction to catch edge blocks the prefab paste may affect
            int worldMinX = adjustedPosition.x + minX - 1;
            int worldMinY = adjustedPosition.y + minY - 1;
            int worldMinZ = adjustedPosition.z + minZ - 1;
            int worldMaxX = adjustedPosition.x + maxX + 1;
            int worldMaxY = adjustedPosition.y + maxY + 1;
            int worldMaxZ = adjustedPosition.z + maxZ + 1;

            // Check for protection zone overlap before placing
            String protectionConflict = checkProtectionOverlap(
                    world.getName(), playerUuid,
                    worldMinX, worldMinY, worldMinZ,
                    worldMaxX, worldMaxY, worldMaxZ);
            if (protectionConflict != null) {
                return protectionConflict;
            }

            int[] blockSnapshot = captureBlockSnapshot(world, worldMinX, worldMinY, worldMinZ,
                    worldMaxX, worldMaxY, worldMaxZ);

            if (blockSnapshot == null) {
                return "Failed to capture block snapshot for undo. Some chunks may not be loaded.";
            }

            // Paste the prefab at adjusted position (centered on player)
            world.execute(() -> {
                Store<EntityStore> worldStore = world.getEntityStore().getStore();
                PrefabBuffer.PrefabBufferAccessor pasteAccessor = prefabBuffer.newAccess();
                try {
                    PrefabUtil.paste(pasteAccessor, world, adjustedPosition, Rotation.None, true,
                            new Random(), worldStore);
                } finally {
                    pasteAccessor.release();
                }
            });

            // Record the placement
            ChurchPlacement placement = new ChurchPlacement(
                    playerUuid,
                    world.getName(),
                    adjustedPosition.x, adjustedPosition.y, adjustedPosition.z,
                    worldMinX, worldMinY, worldMinZ,
                    worldMaxX, worldMaxY, worldMaxZ,
                    null, // snapshot stored separately on disk
                    System.currentTimeMillis()
            );
            placements.put(playerUuid, placement);
            cooldowns.put(playerUuid, System.currentTimeMillis());
            saveSnapshot(playerUuid, blockSnapshot);
            save();

            return null; // success
        } finally {
            accessor.release();
        }
    }

    /**
     * Undo (remove) the priest's church, restoring blocks to their prior state.
     *
     * @return null on success, or an error message on failure
     */
    public String undoChurch(UUID playerUuid, World world) {
        ChurchPlacement placement = placements.get(playerUuid);
        if (placement == null) {
            return "You don't have a church placed.";
        }

        if (!placement.worldName.equals(world.getName())) {
            return "Your church is in world '" + placement.worldName + "'. You must be in that world to undo it.";
        }

        if (isOnCooldown(playerUuid)) {
            return "Please wait " + getRemainingCooldownSeconds(playerUuid) + "s before modifying your church.";
        }

        // Load snapshot eagerly before scheduling async world task
        int[] snapshot = loadSnapshot(playerUuid);
        if (snapshot == null) {
            snapshot = placement.blockSnapshot;
        }
        if (snapshot == null) {
            placements.remove(playerUuid);
            deleteSnapshot(playerUuid);
            save();
            return "No block snapshot found — church record removed.";
        }

        final int[] snapshotData = snapshot;
        world.execute(() -> {
            restoreBlockSnapshotDirect(world, placement, snapshotData);
            deleteSnapshot(playerUuid);
        });

        placements.remove(playerUuid);
        cooldowns.put(playerUuid, System.currentTimeMillis());
        save();

        return null;
    }

    // ── Protection zone checks (soft dependencies) ─────────────────────

    /**
     * Checks if the bounding box overlaps any OrbisGuard region or SimpleClaims chunk.
     * Returns an error message if blocked, or null if placement is allowed.
     */
    private String checkProtectionOverlap(String worldName, UUID playerUuid,
                                          int minX, int minY, int minZ,
                                          int maxX, int maxY, int maxZ) {
        String orbis = checkOrbisGuard(worldName, playerUuid, minX, minY, minZ, maxX, maxY, maxZ);
        if (orbis != null) return orbis;

        String claims = checkSimpleClaims(worldName, playerUuid, minX, minZ, maxX, maxZ);
        if (claims != null) return claims;

        return null;
    }

    private String checkOrbisGuard(String worldName, UUID playerUuid,
                                   int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ) {
        try {
            com.orbisguard.api.OrbisGuardAPI api = com.orbisguard.api.OrbisGuardAPI.getInstance();
            if (api == null) return null;
            com.orbisguard.api.region.IRegionContainer container = api.getRegionContainer();
            if (container == null) return null;

            // Sample corners and center of the bounding box at each Y layer
            int[][] xzSamples = {
                    {minX, minZ}, {maxX - 1, minZ}, {minX, maxZ - 1}, {maxX - 1, maxZ - 1},
                    {(minX + maxX) / 2, (minZ + maxZ) / 2}
            };

            for (int y = minY; y < maxY; y += Math.max(1, (maxY - minY) / 4)) {
                for (int[] xz : xzSamples) {
                    java.util.Set<?> regions = container.getRegionsAt(worldName, xz[0], y, xz[1]);
                    if (regions != null && !regions.isEmpty()) {
                        return "Cannot place church here — it overlaps with a protected region (OrbisGuard).";
                    }
                }
            }
            // Also check the very top Y
            for (int[] xz : xzSamples) {
                java.util.Set<?> regions = container.getRegionsAt(worldName, xz[0], maxY - 1, xz[1]);
                if (regions != null && !regions.isEmpty()) {
                    return "Cannot place church here — it overlaps with a protected region (OrbisGuard).";
                }
            }
        } catch (NoClassDefFoundError | Exception ignored) {
            // OrbisGuard not installed — skip check
        }
        return null;
    }

    private String checkSimpleClaims(String worldName, UUID playerUuid,
                                     int minX, int minZ, int maxX, int maxZ) {
        try {
            com.buuz135.simpleclaims.claim.ClaimManager cm =
                    com.buuz135.simpleclaims.claim.ClaimManager.getInstance();
            if (cm == null) return null;

            // Check every unique chunk that the bounding box touches
            int chunkMinX = minX >> 5;  // Hytale chunks are 32 blocks wide
            int chunkMaxX = (maxX - 1) >> 5;
            int chunkMinZ = minZ >> 5;
            int chunkMaxZ = (maxZ - 1) >> 5;

            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    Object chunk = cm.getChunk(worldName, cx, cz);
                    if (chunk != null) {
                        return "Cannot place church here — it overlaps with a claimed chunk (SimpleClaims).";
                    }
                }
            }
        } catch (NoClassDefFoundError | Exception ignored) {
            // SimpleClaims not installed — skip check
        }
        return null;
    }

    private int[] captureBlockSnapshot(World world, int minX, int minY, int minZ,
                                       int maxX, int maxY, int maxZ) {
        try {
            int sizeX = maxX - minX;
            int sizeY = maxY - minY;
            int sizeZ = maxZ - minZ;
            int blockCount = sizeX * sizeY * sizeZ;
            // Store 3 ints per block: blockId, rotation, filler
            int[] snapshot = new int[blockCount * 3];

            double halfExtent = 0.5 * Math.sqrt((double) sizeX * sizeX + (double) sizeZ * sizeZ);
            int prefabRadius = (int) MathUtil.fastFloor(halfExtent);
            int centerX = (minX + maxX) / 2;
            int centerZ = (minZ + maxZ) / 2;
            LocalCachedChunkAccessor chunkAccessor = LocalCachedChunkAccessor.atWorldCoords(
                    world, centerX, centerZ, prefabRadius + 16);

            int idx = 0;
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                        WorldChunk chunk = chunkAccessor.getNonTickingChunk(chunkIndex);
                        if (chunk == null) {
                            return null; // chunk not loaded
                        }
                        snapshot[idx++] = chunk.getBlock(x, y, z);
                        snapshot[idx++] = chunk.getRotationIndex(x, y, z);
                        snapshot[idx++] = chunk.getFiller(x, y, z);
                    }
                }
            }
            return snapshot;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to capture block snapshot: %s", e.getMessage());
            return null;
        }
    }

    private void restoreBlockSnapshotDirect(World world, ChurchPlacement placement, int[] snapshot) {
        try {
            int sizeX = placement.maxX - placement.minX;
            int sizeY = placement.maxY - placement.minY;
            int sizeZ = placement.maxZ - placement.minZ;
            int blockCount = sizeX * sizeY * sizeZ;

            boolean hasExtraData = (snapshot.length == blockCount * 3);
            if (!hasExtraData && snapshot.length != blockCount) {
                LOGGER.atWarning().log("Block snapshot size mismatch for church undo (player=%s, expected=%d or %d, got=%d)",
                        placement.ownerUuid, blockCount, blockCount * 3, snapshot.length);
                return;
            }

            double halfExtent = 0.5 * Math.sqrt((double) sizeX * sizeX + (double) sizeZ * sizeZ);
            int prefabRadius = (int) MathUtil.fastFloor(halfExtent);
            int centerX = (placement.minX + placement.maxX) / 2;
            int centerZ = (placement.minZ + placement.maxZ) / 2;
            LocalCachedChunkAccessor chunkAccessor = LocalCachedChunkAccessor.atWorldCoords(
                    world, centerX, centerZ, prefabRadius + 16);

            BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();

            int idx = 0;
            for (int y = placement.minY; y < placement.maxY; y++) {
                for (int z = placement.minZ; z < placement.maxZ; z++) {
                    for (int x = placement.minX; x < placement.maxX; x++) {
                        int blockId;
                        int rotation;
                        int filler;
                        if (hasExtraData) {
                            blockId = snapshot[idx++];
                            rotation = snapshot[idx++];
                            filler = snapshot[idx++];
                        } else {
                            blockId = snapshot[idx++];
                            rotation = 0;
                            filler = 0;
                        }
                        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                        WorldChunk chunk = chunkAccessor.getNonTickingChunk(chunkIndex);
                        if (chunk != null) {
                            BlockType type = blockTypeMap.getAsset(blockId);
                            chunk.setBlock(x, y, z, blockId, type, rotation, filler, 0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to restore block snapshot for church undo: %s", e.getMessage());
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private void saveSnapshot(UUID playerUuid, int[] snapshot) {
        try {
            Path dir = dataFolder.resolve(SNAPSHOTS_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(playerUuid.toString() + ".bin");
            ByteBuffer buf = ByteBuffer.allocate(snapshot.length * 4);
            for (int val : snapshot) {
                buf.putInt(val);
            }
            Files.write(file, buf.array());
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to save block snapshot for %s: %s", playerUuid, e.getMessage());
        }
    }

    private int[] loadSnapshot(UUID playerUuid) {
        Path file = dataFolder.resolve(SNAPSHOTS_DIR).resolve(playerUuid.toString() + ".bin");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length % 4 != 0) {
                LOGGER.atWarning().log("Corrupted snapshot file for %s (size=%d)", playerUuid, bytes.length);
                return null;
            }
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            int[] snapshot = new int[bytes.length / 4];
            for (int i = 0; i < snapshot.length; i++) {
                snapshot[i] = buf.getInt();
            }
            return snapshot;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to load block snapshot for %s: %s", playerUuid, e.getMessage());
            return null;
        }
    }

    private void deleteSnapshot(UUID playerUuid) {
        try {
            Path file = dataFolder.resolve(SNAPSHOTS_DIR).resolve(playerUuid.toString() + ".bin");
            Files.deleteIfExists(file);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to delete snapshot file for %s: %s", playerUuid, e.getMessage());
        }
    }

    private void load() {
        Path file = dataFolder.resolve(PERSISTENCE_FILE);
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<Map<String, ChurchPlacementDTO>>() {}.getType();
            Map<String, ChurchPlacementDTO> map = GSON.fromJson(reader, type);
            if (map != null) {
                for (var entry : map.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        ChurchPlacement placement = entry.getValue().toPlacement(uuid);
                        if (placement != null) {
                            placements.put(uuid, placement);
                        }
                    } catch (IllegalArgumentException ignored) {
                        LOGGER.atWarning().log("Skipping invalid UUID in church placements: %s", entry.getKey());
                    }
                }
            }
            LOGGER.atInfo().log("Loaded %d church placement(s) from disk.", placements.size());
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to load church placements: %s", e.getMessage());
        }
    }

    private void save() {
        Path file = dataFolder.resolve(PERSISTENCE_FILE);
        try {
            Files.createDirectories(file.getParent());
            Map<String, ChurchPlacementDTO> map = new LinkedHashMap<>();
            for (var entry : placements.entrySet()) {
                map.put(entry.getKey().toString(), ChurchPlacementDTO.fromPlacement(entry.getValue()));
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(map, writer);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to save church placements: %s", e.getMessage());
        }
    }

    // ── Admin ────────────────────────────────────────────────────────────

    public String adminRemoveChurch(UUID targetUuid, World world) {
        ChurchPlacement placement = placements.get(targetUuid);
        if (placement == null) {
            return "That player has no church placed.";
        }
        if (world != null && placement.worldName.equals(world.getName())) {
            int[] snapshot = loadSnapshot(targetUuid);
            if (snapshot == null) {
                snapshot = placement.blockSnapshot;
            }
            if (snapshot != null) {
                final int[] snapshotData = snapshot;
                world.execute(() -> {
                    restoreBlockSnapshotDirect(world, placement, snapshotData);
                    deleteSnapshot(targetUuid);
                });
            } else {
                deleteSnapshot(targetUuid);
            }
        } else {
            deleteSnapshot(targetUuid);
        }
        placements.remove(targetUuid);
        save();
        return null;
    }

    public int getPlacementCount() {
        return placements.size();
    }

    // ── Inner Classes ────────────────────────────────────────────────────

    public static final class ChurchPlacement {
        public final UUID ownerUuid;
        public final String worldName;
        public final int posX, posY, posZ;
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;
        public final int[] blockSnapshot;
        public final long placedAtMs;

        ChurchPlacement(UUID ownerUuid, String worldName,
                        int posX, int posY, int posZ,
                        int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ,
                        int[] blockSnapshot, long placedAtMs) {
            this.ownerUuid = ownerUuid;
            this.worldName = worldName;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.blockSnapshot = blockSnapshot;
            this.placedAtMs = placedAtMs;
        }
    }

    private static final class ChurchPlacementDTO {
        String worldName;
        int posX, posY, posZ;
        int minX, minY, minZ;
        int maxX, maxY, maxZ;
        int[] blockSnapshot;
        long placedAtMs;

        static ChurchPlacementDTO fromPlacement(ChurchPlacement p) {
            ChurchPlacementDTO dto = new ChurchPlacementDTO();
            dto.worldName = p.worldName;
            dto.posX = p.posX;
            dto.posY = p.posY;
            dto.posZ = p.posZ;
            dto.minX = p.minX;
            dto.minY = p.minY;
            dto.minZ = p.minZ;
            dto.maxX = p.maxX;
            dto.maxY = p.maxY;
            dto.maxZ = p.maxZ;
            dto.blockSnapshot = p.blockSnapshot;
            dto.placedAtMs = p.placedAtMs;
            return dto;
        }

        ChurchPlacement toPlacement(UUID uuid) {
            if (worldName == null) return null;
            return new ChurchPlacement(uuid, worldName, posX, posY, posZ,
                    minX, minY, minZ, maxX, maxY, maxZ, blockSnapshot, placedAtMs);
        }
    }
}
