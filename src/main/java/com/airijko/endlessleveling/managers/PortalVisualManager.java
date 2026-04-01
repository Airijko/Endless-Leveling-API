package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Core-only visual portal spawner ported from addon behavior, intentionally excluding mob-wave logic.
 */
public final class PortalVisualManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final List<String> PORTAL_PARTICLE_IDS = List.of();
    private static final String WAVE_GATE_SPAWN_SOUND_ID = "SFX_EL_S_Rank_Gate_Spawn";
    private static final String WAVE_10_SECOND_COUNTDOWN_SOUND_ID = "SFX_EL_DungeonBreak_10_Second_Countdown";
    private static final String GATE_GROUND_SIGN_BLOCK_ID = "Furniture_Construction_Sign";

    private static final long DEFAULT_VISUAL_LIFETIME_SECONDS = 90L;
    private static final int DEFAULT_PLACEMENT_ATTEMPTS = 14;

    private static final ConcurrentMap<String, ActiveVisualPortal> ACTIVE_VISUAL_PORTALS = new ConcurrentHashMap<>();

    private static volatile EndlessLeveling plugin;
    private static volatile PortalVisualBackendConfig backendConfig;
    private static volatile ScheduledFuture<?> autoSpawnTask;
    private static volatile ScheduledFuture<?> pulseTask;

    private PortalVisualManager() {
    }

    public static void initialize(@Nonnull EndlessLeveling owner) {
        plugin = owner;
        backendConfig = PortalVisualBackendConfig.load();

        if (backendConfig == null || !backendConfig.enabled()) {
            LOGGER.atInfo().log("[ELPortalVisual] Visual portals disabled by config.");
            return;
        }

        long pulseIntervalMillis = Math.max(500L, backendConfig.pulseIntervalMillis());
        pulseTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                PortalVisualManager::pulseVisualPortals,
                pulseIntervalMillis,
                pulseIntervalMillis,
                TimeUnit.MILLISECONDS);

        scheduleNextAutoSpawnTick();
        LOGGER.atInfo().log("[ELPortalVisual] Backend visual config loaded: %s", backendConfig.toString());
    }

    public static void shutdown() {
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel(false);
            autoSpawnTask = null;
        }
        if (pulseTask != null) {
            pulseTask.cancel(false);
            pulseTask = null;
        }
        ACTIVE_VISUAL_PORTALS.clear();
        plugin = null;
        backendConfig = null;
    }

    public static boolean spawnSneakPeekNearPlayer(@Nonnull World world,
                                                    @Nonnull PlayerRef playerRef,
                                                    @Nonnull Ref<EntityStore> sourceRef) {
        return spawnSneakPeekNearPlayerDetailed(world, playerRef, sourceRef) != null;
    }

    @Nullable
    public static SpawnedPortalDetails spawnSneakPeekNearPlayerDetailed(@Nonnull World world,
                                                                        @Nonnull PlayerRef playerRef,
                                                                        @Nonnull Ref<EntityStore> sourceRef) {
        return spawnVisualNearPlayerDetailed(world, playerRef, sourceRef, true);
    }

    public static boolean spawnVisualNearPlayer(@Nonnull World world,
                                                 @Nonnull PlayerRef playerRef,
                                                 @Nonnull Ref<EntityStore> sourceRef,
                                                 boolean sneakPeek) {
        return spawnVisualNearPlayerDetailed(world, playerRef, sourceRef, sneakPeek) != null;
    }

    @Nullable
    public static SpawnedPortalDetails spawnVisualNearPlayerDetailed(@Nonnull World world,
                                                                     @Nonnull PlayerRef playerRef,
                                                                     @Nonnull Ref<EntityStore> sourceRef,
                                                                     boolean sneakPeek) {
        PortalVisualBackendConfig config = backendConfig;
        if (config == null || !config.enabled()) {
            return null;
        }

        String worldName = world.getName();
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        if (!config.isWorldWhitelisted(worldName)) {
            return null;
        }

        if (!isPlayerLevelEligible(playerRef, config.minLevelRequired())) {
            return null;
        }

        int maxConcurrent = config.maxConcurrentSpawns();
        if (maxConcurrent >= 0 && ACTIVE_VISUAL_PORTALS.size() >= maxConcurrent) {
            return null;
        }

        String effectId = resolveEffectId(sneakPeek);
        if (playerRef == null || playerRef.getTransform() == null || playerRef.getTransform().getPosition() == null) {
            return null;
        }
        if (sourceRef == null || !sourceRef.isValid() || sourceRef.getStore() == null) {
            return null;
        }

        List<Long> loadedChunkIndexes = resolveLoadedChunkIndexes(world);
        if (loadedChunkIndexes.isEmpty()) {
            return null;
        }

        int attempts = Math.max(1, config.placementAttempts());
        Vector3d fallbackPosition = playerRef.getTransform().getPosition();

        for (int i = 0; i < attempts; i++) {
            long chunkIndex = loadedChunkIndexes.get(randomInt(0, loadedChunkIndexes.size() - 1));
            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                continue;
            }

            int chunkX = ChunkUtil.xOfChunkIndex(chunkIndex);
            int chunkZ = ChunkUtil.zOfChunkIndex(chunkIndex);
            int x = (chunkX << 5) + randomInt(2, 29);
            int z = (chunkZ << 5) + randomInt(2, 29);
            int y = resolveGroundPlacementY(chunk, x, z);
            if (y < 0) {
                continue;
            }

            Vector3d portalPos = new Vector3d(x + 0.5D, y + 0.05D, z + 0.5D);
            registerVisualPortal(world, portalPos, effectId, x, y, z);
            world.execute(() -> {
                placeGateGroundSign(world, x, y, z);
                trySpawnParticle(effectId, portalPos, sourceRef);
            });
            if (isWavePortalEffect(effectId)) {
                playWaveSpawnSoundToAllPlayers();
            }
            if (sneakPeek) {
                playSneakPeekCountdownSoundToAllPlayers();
            }
            if (config.announceOnSpawn()) {
                playerRef.sendMessage(Message.raw("A visual dungeon portal crackles into view nearby.").color("#8be7ff"));
            }
            return new SpawnedPortalDetails(resolveWorldUuid(world), x, y, z, portalPos, effectId);
        }

        int fallbackX = MathUtil.floor(fallbackPosition.x);
        int fallbackY = Math.max(1, MathUtil.floor(fallbackPosition.y));
        int fallbackZ = MathUtil.floor(fallbackPosition.z);
        registerVisualPortal(world, fallbackPosition, effectId, fallbackX, fallbackY, fallbackZ);
        world.execute(() -> {
            placeGateGroundSign(world, fallbackX, fallbackY, fallbackZ);
            trySpawnParticle(effectId, fallbackPosition, sourceRef);
        });
        if (isWavePortalEffect(effectId)) {
            playWaveSpawnSoundToAllPlayers();
        }
        if (sneakPeek) {
            playSneakPeekCountdownSoundToAllPlayers();
        }
        if (config.announceOnSpawn()) {
            playerRef.sendMessage(Message.raw("A visual dungeon portal crackles into view nearby.").color("#8be7ff"));
        }
        return new SpawnedPortalDetails(resolveWorldUuid(world), fallbackX, fallbackY, fallbackZ, fallbackPosition, effectId);
    }

    private static void spawnRandomVisualNearOnlinePlayer() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        PortalVisualBackendConfig config = backendConfig;
        if (config == null || !config.enabled()) {
            return;
        }

        List<PlayerRef> players = universe.getPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }

        List<PlayerRef> eligiblePlayers = new ArrayList<>();
        for (PlayerRef playerRef : players) {
            if (playerRef == null) {
                continue;
            }
            UUID playerWorldUuid = playerRef.getWorldUuid();
            if (playerWorldUuid == null) {
                continue;
            }
            World world = universe.getWorld(playerWorldUuid);
            if (world == null || world.getName() == null || !config.isWorldWhitelisted(world.getName())) {
                continue;
            }
            if (!isPlayerLevelEligible(playerRef, config.minLevelRequired())) {
                continue;
            }
            eligiblePlayers.add(playerRef);
        }

        if (eligiblePlayers.isEmpty()) {
            return;
        }

        PlayerRef selected = eligiblePlayers.get(randomInt(0, eligiblePlayers.size() - 1));
        Ref<EntityStore> selectedRef = selected.getReference();
        if (selectedRef == null || !selectedRef.isValid() || selectedRef.getStore() == null) {
            return;
        }

        UUID worldUuid = selected.getWorldUuid();
        if (worldUuid == null) {
            return;
        }
        World world = universe.getWorld(worldUuid);
        if (world == null) {
            return;
        }

        boolean spawned = spawnVisualNearPlayer(world, selected, selectedRef, false);
        if (spawned) {
            selected.sendMessage(Message.raw("A shimmering portal distortion briefly appears nearby...").color("#8be7ff"));
        }
    }

    private static void scheduleNextAutoSpawnTick() {
        PortalVisualBackendConfig config = backendConfig;
        if (config == null || !config.enabled()) {
            return;
        }
        long delayMinutes = Math.max(1L, config.resolveRandomSpawnIntervalMinutes());
        autoSpawnTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                spawnRandomVisualNearOnlinePlayer();
            } finally {
                if (plugin != null && backendConfig != null && backendConfig.enabled()) {
                    scheduleNextAutoSpawnTick();
                }
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    private static void pulseVisualPortals() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (ActiveVisualPortal portal : new ArrayList<>(ACTIVE_VISUAL_PORTALS.values())) {
            if (portal == null) {
                continue;
            }
            World world = universe.getWorld(portal.worldUuid());
            if (portal.expiresAtMillis() <= now) {
                if (backendConfig != null && backendConfig.announceOnDespawn()) {
                    logDespawn(portal);
                }
                if (world != null) {
                    world.execute(() -> removeGateGroundSign(world, portal));
                }
                ACTIVE_VISUAL_PORTALS.remove(portal.key());
                continue;
            }

            if (world == null) {
                continue;
            }

            world.execute(() -> {
                Ref<EntityStore> visualSource = resolveAnyPlayerRefInWorld(universe, world);
                if (visualSource == null) {
                    return;
                }
                trySpawnParticle(portal.effectId(), portal.position(), visualSource);
            });
        }
    }

    @Nullable
    private static Ref<EntityStore> resolveAnyPlayerRefInWorld(@Nonnull Universe universe, @Nonnull World world) {
        UUID worldUuid = world.getWorldConfig() == null ? null : world.getWorldConfig().getUuid();
        if (worldUuid == null) {
            return null;
        }
        for (PlayerRef ref : universe.getPlayers()) {
            if (ref == null || !worldUuid.equals(ref.getWorldUuid())) {
                continue;
            }
            Ref<EntityStore> playerEntityRef = ref.getReference();
            if (playerEntityRef != null && playerEntityRef.isValid() && playerEntityRef.getStore() != null) {
                return playerEntityRef;
            }
        }
        return null;
    }

    private static void trySpawnParticle(@Nonnull String effectId,
                                         @Nonnull Vector3d position,
                                         @Nonnull Ref<EntityStore> sourceRef) {
        if (effectId.isBlank()) {
            return;
        }
        try {
            ParticleUtil.spawnParticleEffect(effectId, position, sourceRef.getStore());
        } catch (Exception ignored) {
            // Fallback to first known particle to keep the portal visible even if one effect id is unavailable.
            if (!PORTAL_PARTICLE_IDS.isEmpty()) {
                try {
                    ParticleUtil.spawnParticleEffect(PORTAL_PARTICLE_IDS.get(0), position, sourceRef.getStore());
                } catch (Exception ignoredAgain) {
                    LOGGER.atFine().log("[ELPortalVisual] Failed to spawn portal particle effect '%s'.", effectId);
                }
            } else {
                LOGGER.atFine().log("[ELPortalVisual] Failed to spawn portal particle effect '%s'.", effectId);
            }
        }
    }

    private static void registerVisualPortal(@Nonnull World world,
                                             @Nonnull Vector3d position,
                                             @Nonnull String effectId,
                                             int signX,
                                             int signY,
                                             int signZ) {
        UUID worldUuid = world.getWorldConfig() == null ? null : world.getWorldConfig().getUuid();
        if (worldUuid == null) {
            return;
        }

        long durationMinutes = backendConfig != null ? backendConfig.gateDurationMinutes() : -1L;
        long lifetimeMillis;
        if (durationMinutes < 0L) {
            lifetimeMillis = DEFAULT_VISUAL_LIFETIME_SECONDS * 1000L;
        } else {
            lifetimeMillis = Math.max(1L, durationMinutes) * 60_000L;
        }
        long expiresAt = System.currentTimeMillis() + lifetimeMillis;
        String key = String.format(Locale.ROOT, "%s:%d:%d:%d:%s",
                worldUuid,
                MathUtil.floor(position.x),
                MathUtil.floor(position.y),
                MathUtil.floor(position.z),
                effectId);

        ACTIVE_VISUAL_PORTALS.put(key, new ActiveVisualPortal(key, worldUuid, position, effectId, expiresAt, signX, signY, signZ));
    }

    @Nullable
    private static UUID resolveWorldUuid(@Nonnull World world) {
        return world.getWorldConfig() == null ? null : world.getWorldConfig().getUuid();
    }

    private static void placeGateGroundSign(@Nonnull World world, int x, int y, int z) {
        try {
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) {
                return;
            }
            if (chunk.getBlock(x, y, z) != 0) {
                return;
            }
            chunk.setBlock(x, y, z, GATE_GROUND_SIGN_BLOCK_ID);
        } catch (Exception ex) {
            LOGGER.atFine().log("[ELPortalVisual] Failed placing gate ground sign at (%d,%d,%d): %s", x, y, z, ex.getMessage());
        }
    }

    private static void removeGateGroundSign(@Nonnull World world, @Nonnull ActiveVisualPortal portal) {
        try {
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(portal.signX(), portal.signZ()));
            if (chunk == null) {
                return;
            }
            int existingBlock = chunk.getBlock(portal.signX(), portal.signY(), portal.signZ());
            int expectedBlock = BlockType.getAssetMap().getIndex(GATE_GROUND_SIGN_BLOCK_ID);
            if (expectedBlock != Integer.MIN_VALUE && existingBlock == expectedBlock) {
                chunk.setBlock(portal.signX(), portal.signY(), portal.signZ(), 0);
            }
        } catch (Exception ex) {
            LOGGER.atFine().log("[ELPortalVisual] Failed removing gate ground sign at (%d,%d,%d): %s",
                    portal.signX(),
                    portal.signY(),
                    portal.signZ(),
                    ex.getMessage());
        }
    }

    @Nonnull
    private static String resolveEffectId(boolean sneakPeek) {
        String configuredPeek = backendConfig != null ? backendConfig.sneakPeekEffectId() : "";
        String configuredDefault = backendConfig != null ? backendConfig.defaultEffectId() : "";

        String chosen = sneakPeek ? configuredPeek : configuredDefault;
        if (chosen == null || chosen.isBlank()) {
            return PORTAL_PARTICLE_IDS.isEmpty() ? "" : PORTAL_PARTICLE_IDS.get(0);
        }
        return chosen;
    }

    private static boolean isPlayerLevelEligible(@Nonnull PlayerRef playerRef, int minLevelRequired) {
        if (minLevelRequired <= 1) {
            return true;
        }

        EndlessLevelingAPI api = EndlessLevelingAPI.get();
        if (api == null) {
            return false;
        }
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return false;
        }
        int level = api.getPlayerLevel(playerUuid);
        return level >= minLevelRequired;
    }

    private static void logDespawn(@Nonnull ActiveVisualPortal portal) {
        LOGGER.atInfo().log("[ELPortalVisual] Visual portal expired world=%s pos=(%.2f,%.2f,%.2f)",
                portal.worldUuid(),
                portal.position().x,
                portal.position().y,
                portal.position().z);
    }

    private static int resolveGroundPlacementY(@Nonnull WorldChunk chunk, int x, int z) {
        for (int y = 317; y >= 1; y--) {
            int supportBlock = chunk.getBlock(x, y, z);
            if (supportBlock == 0) {
                continue;
            }
            int placeY = y + 1;
            if (placeY > 318) {
                continue;
            }
            if (chunk.getBlock(x, placeY, z) != 0) {
                continue;
            }
            if (chunk.getBlock(x, placeY + 1, z) != 0) {
                continue;
            }
            return placeY;
        }
        return -1;
    }

    @Nonnull
    private static List<Long> resolveLoadedChunkIndexes(@Nonnull World world) {
        List<Long> loaded = new ArrayList<>();
        for (Long chunkIndexObj : world.getChunkStore().getChunkIndexes()) {
            if (chunkIndexObj == null) {
                continue;
            }
            long chunkIndex = chunkIndexObj;
            if (world.getChunkIfLoaded(chunkIndex) != null) {
                loaded.add(chunkIndex);
            }
        }
        return loaded;
    }

    private static int randomInt(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        return minInclusive + (int) Math.floor(Math.random() * (maxInclusive - minInclusive + 1));
    }

    private static boolean isWavePortalEffect(@Nonnull String effectId) {
        return effectId.toLowerCase(Locale.ROOT).contains("wave");
    }

    private static void playWaveSpawnSoundToAllPlayers() {
        playSoundToAllPlayers(WAVE_GATE_SPAWN_SOUND_ID);
    }

    private static void playSneakPeekCountdownSoundToAllPlayers() {
        playSoundToAllPlayers(WAVE_10_SECOND_COUNTDOWN_SOUND_ID);
    }

    private static void playSoundToAllPlayers(@Nonnull String soundEventId) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        int soundIndex = resolveSoundIndex(soundEventId);
        if (soundIndex == 0) {
            LOGGER.atWarning().log("[ELPortalVisual] Missing sound event id: %s", soundEventId);
            return;
        }

        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null) {
                continue;
            }

            World playerWorld = universe.getWorld(worldUuid);
            if (playerWorld == null) {
                continue;
            }

            playerWorld.execute(() -> {
                if (!playerRef.isValid()) {
                    return;
                }

                Ref<EntityStore> playerEntityRef = playerRef.getReference();
                if (playerEntityRef == null || !playerEntityRef.isValid()) {
                    return;
                }

                try {
                    SoundUtil.playSoundEvent2d(playerEntityRef, soundIndex, SoundCategory.SFX, playerEntityRef.getStore());
                } catch (Exception ex) {
                    LOGGER.atWarning().log("[ELPortalVisual] Failed wave spawn SFX for player=%s: %s",
                            playerRef.getUsername(),
                            ex.getMessage());
                }
            });
        }
    }

    private static int resolveSoundIndex(@Nullable String soundEventId) {
        if (soundEventId == null || soundEventId.isBlank()) {
            return 0;
        }
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    public static int countActivePortals() {
        return ACTIVE_VISUAL_PORTALS.size();
    }

    public record SpawnedPortalDetails(@Nullable UUID worldUuid,
                                       int x,
                                       int y,
                                       int z,
                                       @Nonnull Vector3d position,
                                       @Nonnull String effectId) {
    }

    private record ActiveVisualPortal(String key,
                                      UUID worldUuid,
                                      Vector3d position,
                                      String effectId,
                                      long expiresAtMillis,
                                      int signX,
                                      int signY,
                                      int signZ) {
    }
}
