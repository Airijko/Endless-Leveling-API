package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.enums.GateRankTier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Visual-only wave portal preview manager ported from the addon without actual mob spawning.
 */
public final class WavePortalPreviewManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final int AIR_BLOCK_ID = 0;
    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y = 319;
    private static final int WAVE_PORTAL_SEARCH_HEIGHT = 24;
    private static final int WAVE_PORTAL_BREAK_SETTINGS = 256 | 4;
    private static final String WAVE_PORTAL_BLOCK_BASE_ID = "EL_WavePortal";
    private static final String WAVE_GATE_GROUND_SIGN_BLOCK_ID = "Furniture_Construction_Sign";
    private static final String WAVE_GATE_SPAWN_SOUND_ID = "SFX_EL_S_Rank_Gate_Spawn";
    private static final String WAVE_10_SECOND_COUNTDOWN_SOUND_ID = "SFX_EL_DungeonBreak_10_Second_Countdown";
    private static final String S_WAVE_1_MINUTE_COUNTDOWN_SOUND_ID = "SFX_EL_S_Wave_1_Minute_Countdown";
    private static final double DIALOGUE_NEARBY_RADIUS_BLOCKS = 80.0;
    private static final long DIALOGUE_RETRY_INTERVAL_SECONDS = 3L;
    private static final double WAVE_PREVIEW_S_RANK_CHANCE = 0.50;
    private static final long S_RANK_PREVIEW_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(12);
    private static final long POST_OPEN_PORTAL_LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static final Map<UUID, ActiveWavePreview> ACTIVE_PREVIEWS = new ConcurrentHashMap<>();
    private static volatile long lastSRankPreviewSpawnMillis;

    private WavePortalPreviewManager() {
    }

    public static CompletableFuture<WavePreviewSnapshot> spawnPreviewNearPlayer(@Nonnull PlayerRef playerRef) {
        return spawnPreviewNearPlayer(playerRef, null);
    }

    public static CompletableFuture<WavePreviewSnapshot> spawnPreviewNearPlayer(@Nonnull PlayerRef playerRef,
                                                                                @Nullable GateRankTier forcedRankTier) {
        CompletableFuture<WavePreviewSnapshot> future = new CompletableFuture<>();
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            future.complete(null);
            return future;
        }
        if (ACTIVE_PREVIEWS.containsKey(playerUuid)) {
            future.complete(null);
            return future;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            future.complete(null);
            return future;
        }

        UUID worldUuid = playerRef.getWorldUuid();
        World world = worldUuid != null ? universe.getWorld(worldUuid) : null;
        if (world == null) {
            future.complete(null);
            return future;
        }

        world.execute(() -> {
            try {
                Vector3d center = playerRef.getTransform() == null ? null : playerRef.getTransform().getPosition();
                if (center == null) {
                    future.complete(null);
                    return;
                }

                GateRank gateRank = resolveWavePreviewRankForPlayer(playerRef, forcedRankTier);
                GateRankTier rankTier = gateRank.tier;
                WavePortalPlacement placement = createWavePortalPlacement(world, center, rankTier);
                if (placement == null) {
                    future.complete(null);
                    return;
                }

                int delayMinutes = resolveNaturalWaveOpenDelay(rankTier);
                long delaySeconds = Math.max(1L, TimeUnit.MINUTES.toSeconds(delayMinutes));
                long now = System.currentTimeMillis();
                long openAtMillis = now + TimeUnit.SECONDS.toMillis(delaySeconds);

                ActiveWavePreview preview = new ActiveWavePreview(playerUuid, rankTier, placement, now, openAtMillis);
                ACTIVE_PREVIEWS.put(playerUuid, preview);

                announceNaturalWaveBreak(rankTier, delayMinutes, placement);
                preview.countdownFutures = schedulePrestartCountdown(rankTier, delaySeconds, false);
                preview.openFuture = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> openPreview(world, preview),
                        delaySeconds,
                        TimeUnit.SECONDS);

                future.complete(toSnapshot(preview));
            } catch (Exception ex) {
                LOGGER.atWarning().log("[ELWavePreview] Failed to spawn preview portal for %s: %s",
                        playerRef.getUsername(),
                        ex.getMessage());
                future.complete(null);
            }
        });

        return future;
    }

    @Nonnull
    public static List<WavePreviewSnapshot> listActivePreviews() {
        long now = System.currentTimeMillis();
        return ACTIVE_PREVIEWS.values().stream()
                .sorted(Comparator.comparingLong(preview -> preview.openAtMillis))
                .map(preview -> toSnapshot(preview, now))
                .toList();
    }

    public static boolean removePreviewForPlayer(@Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return false;
        }
        ActiveWavePreview preview = ACTIVE_PREVIEWS.remove(playerUuid);
        if (preview == null) {
            return false;
        }
        removePreviewNow(preview);
        return true;
    }

    public static int removeAllPreviews() {
        List<ActiveWavePreview> previews = new ArrayList<>(ACTIVE_PREVIEWS.values());
        ACTIVE_PREVIEWS.clear();
        for (ActiveWavePreview preview : previews) {
            removePreviewNow(preview);
        }
        return previews.size();
    }

    public static int removePreviewsByRank(@Nonnull GateRankTier rankTier) {
        List<ActiveWavePreview> toRemove = new ArrayList<>();
        for (ActiveWavePreview preview : ACTIVE_PREVIEWS.values()) {
            if (preview.rankTier == rankTier) {
                toRemove.add(preview);
            }
        }

        int removed = 0;
        for (ActiveWavePreview preview : toRemove) {
            if (ACTIVE_PREVIEWS.remove(preview.playerUuid, preview)) {
                removePreviewNow(preview);
                removed++;
            }
        }
        return removed;
    }

    private static void openPreview(@Nonnull World world, @Nonnull ActiveWavePreview preview) {
        cancelScheduledFutures(preview.countdownFutures);

        announceNaturalWaveOpen(preview.rankTier, preview.placement);
        if (preview.rankTier == GateRankTier.S) {
            schedulePostOpenMemeDialogue(preview.rankTier, preview.placement);
        } else {
            schedulePostOpenMiniDialogue(preview.rankTier, preview.placement);
        }

        // Keep the visual gate and construction sign for a short post-open window.
        preview.openFuture = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                clearPortalPlacement(world, preview.placement);
            } finally {
                ACTIVE_PREVIEWS.remove(preview.playerUuid, preview);
            }
        }, POST_OPEN_PORTAL_LIFETIME_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Nonnull
    private static GateRank resolveWavePreviewRankForPlayer(@Nonnull PlayerRef playerRef,
                                                            @Nullable GateRankTier forcedRankTier) {
        GateRank baseRank = NaturalGateSpawner.resolveGateRankForPlayer(playerRef);
        if (forcedRankTier != null) {
            return new GateRank(
                    forcedRankTier,
                    baseRank.normalLevelMin,
                    baseRank.normalLevelMax,
                    baseRank.bossLevel,
                    baseRank.roll);
        }

        long now = System.currentTimeMillis();

        if (isSRankPreviewAvailable(now) && ThreadLocalRandom.current().nextDouble() < WAVE_PREVIEW_S_RANK_CHANCE) {
            if (tryReserveSRankPreview(now)) {
                return new GateRank(
                        GateRankTier.S,
                        baseRank.normalLevelMin,
                        baseRank.normalLevelMax,
                        baseRank.bossLevel,
                        baseRank.roll);
            }
        }

        if (baseRank.tier == GateRankTier.S) {
            return new GateRank(
                    GateRankTier.A,
                    baseRank.normalLevelMin,
                    baseRank.normalLevelMax,
                    baseRank.bossLevel,
                    baseRank.roll);
        }
        return baseRank;
    }

    private static boolean isSRankPreviewAvailable(long nowMillis) {
        long elapsed = nowMillis - lastSRankPreviewSpawnMillis;
        return elapsed >= S_RANK_PREVIEW_COOLDOWN_MILLIS;
    }

    private static synchronized boolean tryReserveSRankPreview(long nowMillis) {
        if (!isSRankPreviewAvailable(nowMillis)) {
            return false;
        }
        lastSRankPreviewSpawnMillis = nowMillis;
        return true;
    }

    private static void removePreviewNow(@Nonnull ActiveWavePreview preview) {
        cancelScheduledFutures(preview.countdownFutures);
        ScheduledFuture<?> openFuture = preview.openFuture;
        if (openFuture != null) {
            openFuture.cancel(false);
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        UUID worldUuid = preview.placement.worldUuid();
        World world = worldUuid != null ? universe.getWorld(worldUuid) : null;
        if (world == null) {
            return;
        }

        world.execute(() -> clearPortalPlacement(world, preview.placement));
    }

    @Nullable
    private static WavePortalPlacement createWavePortalPlacement(@Nonnull World world,
                                                                 @Nullable Vector3d center,
                                                                 @Nonnull GateRankTier rankTier) {
        if (center == null) {
            return null;
        }

        String portalBlockId = resolveWavePortalBlockId(rankTier);
        int portalBlockIntId = BlockType.getAssetMap().getIndex(portalBlockId);
        if (portalBlockIntId == Integer.MIN_VALUE) {
            return null;
        }

        int baseX = (int) Math.floor(center.x);
        int baseZ = (int) Math.floor(center.z);
        int preferredY = Math.max(
                WORLD_MIN_Y + 1,
                Math.min(WORLD_MAX_Y - 1,
                        (int) Math.floor(center.y) + resolveWavePortalVerticalOffset(rankTier)));
        UUID worldUuid = world.getWorldConfig() == null ? null : world.getWorldConfig().getUuid();

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(baseX, baseZ));
        if (chunk == null) {
            return null;
        }

        int maxY = Math.min(WORLD_MAX_Y - 1, preferredY + WAVE_PORTAL_SEARCH_HEIGHT);
        for (int y = preferredY; y <= maxY; y++) {
            int existing = chunk.getBlock(baseX, y, baseZ);
            if (!isAnyWavePortalBlockIntId(existing)) {
                continue;
            }

                return new WavePortalPlacement(
                    UUID.randomUUID(),
                    worldUuid,
                    baseX,
                    y,
                    baseZ,
                    existing,
                    resolveWavePortalBlockIdFromIntId(existing, portalBlockId),
                    baseX,
                    -1,
                    baseZ);
        }

        int placementY = resolveWavePortalPlacementY(chunk, baseX, preferredY, baseZ, maxY);
        if (placementY < 0) {
            return null;
        }

        int existing = chunk.getBlock(baseX, placementY, baseZ);
        if (existing != AIR_BLOCK_ID && !isAnyWavePortalBlockIntId(existing)) {
            return null;
        }

        chunk.setBlock(baseX, placementY, baseZ, portalBlockIntId);
        int signY = resolveWaveGroundSignY(chunk, baseX, baseZ, placementY - 1);
        if (signY >= WORLD_MIN_Y) {
            placeWaveGroundSign(chunk, baseX, signY, baseZ);
        }
        chunk.markNeedsSaving();
        return new WavePortalPlacement(
                UUID.randomUUID(),
                worldUuid,
                baseX,
                placementY,
                baseZ,
                portalBlockIntId,
                portalBlockId,
                baseX,
                signY,
                baseZ);
    }

    private static int resolveWaveGroundSignY(@Nonnull WorldChunk chunk, int x, int z, int startY) {
        int searchY = Math.max(WORLD_MIN_Y + 1, Math.min(WORLD_MAX_Y - 1, startY));
        for (int y = searchY; y >= WORLD_MIN_Y + 1; y--) {
            if (chunk.getBlock(x, y, z) == AIR_BLOCK_ID && chunk.getBlock(x, y - 1, z) != AIR_BLOCK_ID) {
                return y;
            }
        }
        return -1;
    }

    private static void placeWaveGroundSign(@Nonnull WorldChunk chunk, int x, int y, int z) {
        int signBlockIntId = BlockType.getAssetMap().getIndex(WAVE_GATE_GROUND_SIGN_BLOCK_ID);
        if (signBlockIntId == Integer.MIN_VALUE) {
            return;
        }
        if (chunk.getBlock(x, y, z) != AIR_BLOCK_ID) {
            return;
        }
        chunk.setBlock(x, y, z, signBlockIntId);
    }

    private static int resolveWavePortalPlacementY(@Nonnull WorldChunk chunk,
                                                   int x,
                                                   int preferredY,
                                                   int z,
                                                   int maxY) {
        int startY = Math.max(WORLD_MIN_Y + 1, Math.min(WORLD_MAX_Y - 1, preferredY));
        for (int y = startY; y <= maxY; y++) {
            int placeBlock = chunk.getBlock(x, y, z);
            if (placeBlock != AIR_BLOCK_ID && !isAnyWavePortalBlockIntId(placeBlock)) {
                continue;
            }
            return y;
        }
        return -1;
    }

    private static int resolveWavePortalVerticalOffset(@Nonnull GateRankTier rankTier) {
        return switch (rankTier) {
            case E -> 12;
            case D -> 13;
            case C -> 14;
            case B -> 15;
            case A -> 16;
            case S -> 20;
        };
    }

    @Nonnull
    private static String resolveWavePortalBlockId(@Nonnull GateRankTier rankTier) {
        String candidate = WAVE_PORTAL_BLOCK_BASE_ID + rankTier.blockIdSuffix();
        return BlockType.getAssetMap().getIndex(candidate) == Integer.MIN_VALUE
                ? WAVE_PORTAL_BLOCK_BASE_ID
                : candidate;
    }

    private static boolean isAnyWavePortalBlockIntId(int blockIntId) {
        if (blockIntId == Integer.MIN_VALUE || blockIntId == AIR_BLOCK_ID) {
            return false;
        }
        if (blockIntId == BlockType.getAssetMap().getIndex(WAVE_PORTAL_BLOCK_BASE_ID)) {
            return true;
        }
        for (GateRankTier tier : GateRankTier.values()) {
            if (blockIntId == BlockType.getAssetMap().getIndex(WAVE_PORTAL_BLOCK_BASE_ID + tier.blockIdSuffix())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String resolveWavePortalBlockIdFromIntId(int blockIntId, @Nonnull String fallback) {
        if (blockIntId == BlockType.getAssetMap().getIndex(WAVE_PORTAL_BLOCK_BASE_ID)) {
            return WAVE_PORTAL_BLOCK_BASE_ID;
        }
        for (GateRankTier tier : GateRankTier.values()) {
            String candidate = WAVE_PORTAL_BLOCK_BASE_ID + tier.blockIdSuffix();
            if (blockIntId == BlockType.getAssetMap().getIndex(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static void clearPortalPlacement(@Nonnull World world, @Nonnull WavePortalPlacement placement) {
        boolean removed = clearPortalPlacementFromLoadedChunk(world, placement);
        if (!removed) {
            requestPortalPlacementClearWithChunkLoad(world, placement);
        }
    }

    private static boolean clearPortalPlacementFromLoadedChunk(@Nonnull World world,
                                                               @Nonnull WavePortalPlacement placement) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(placement.x, placement.z));
        if (chunk == null) {
            return false;
        }

        int currentBlock = chunk.getBlock(placement.x, placement.y, placement.z);
        int persistedBlockId = BlockType.getAssetMap().getIndex(placement.blockId);
        if (currentBlock == placement.blockIntId
                || (persistedBlockId != Integer.MIN_VALUE && currentBlock == persistedBlockId)) {
            try {
                chunk.breakBlock(placement.x, placement.y, placement.z, WAVE_PORTAL_BREAK_SETTINGS);
            } catch (Exception ignored) {
                chunk.setBlock(placement.x, placement.y, placement.z, AIR_BLOCK_ID);
            }
        }

        int signBlockIntId = BlockType.getAssetMap().getIndex(WAVE_GATE_GROUND_SIGN_BLOCK_ID);
        if (signBlockIntId != Integer.MIN_VALUE && placement.signY >= WORLD_MIN_Y + 1) {
            int existingSignBlock = chunk.getBlock(placement.signX, placement.signY, placement.signZ);
            if (existingSignBlock == signBlockIntId) {
                chunk.setBlock(placement.signX, placement.signY, placement.signZ, AIR_BLOCK_ID);
            }
        }
        chunk.markNeedsSaving();
        return true;
    }

    private static void requestPortalPlacementClearWithChunkLoad(@Nonnull World world,
                                                                 @Nonnull WavePortalPlacement placement) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(placement.x, placement.z);
        world.getChunkAsync(chunkIndex).whenComplete((loadedChunk, throwable) ->
                world.execute(() -> clearPortalPlacementFromLoadedChunk(world, placement)));
    }

    private static int resolveNaturalWaveOpenDelay(@Nonnull GateRankTier rankTier) {
        return switch (rankTier) {
            case S -> 10;
            case A -> 5;
            default -> 1;
        };
    }

    private static void announceNaturalWaveBreak(@Nonnull GateRankTier rankTier,
                                                 int delayMinutes,
                                                 @Nullable WavePortalPlacement previewPortal) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        String timeLabel = delayMinutes == 1 ? "1 minute" : delayMinutes + " minutes";
        String coordinateLabel = previewPortal == null
                ? ""
                : String.format(Locale.ROOT, " at (%d, %d, %d)", previewPortal.x, previewPortal.y, previewPortal.z);
        showTitleToAllPlayers(
                Message.raw("DUNGEON BREAK").color(rankTier.color().hex()),
                Message.raw(String.format(Locale.ROOT, "%s-Rank breach in %s%s", rankTier.letter(), timeLabel, coordinateLabel))
                        .color("#d0e8ff"),
                true
        );

        Message message = previewPortal == null
                ? Message.join(
                        Message.raw("[DUNGEON BREAK] ").color(rankTier.color().hex()),
                        Message.raw(String.format(Locale.ROOT,
                                "%s-Rank breach detected. Wave opens in %s.",
                                rankTier.letter(),
                                timeLabel)).color("#ffffff"))
                : Message.join(
                        Message.raw("[DUNGEON BREAK] ").color(rankTier.color().hex()),
                        Message.raw(String.format(Locale.ROOT,
                                "%s-Rank breach detected. Wave opens in %s.",
                                rankTier.letter(),
                                timeLabel)).color("#ffffff"),
                        Message.raw("\n"),
                        Message.raw("[WAYPOINT] ").color(rankTier.color().hex()),
                        Message.raw(String.format(Locale.ROOT,
                                "Coordinates (%d, %d, %d)",
                                previewPortal.x,
                                previewPortal.y,
                                previewPortal.z)).color("#d0e8ff"));
        universe.sendMessage(message);

        if (rankTier == GateRankTier.S) {
            playSoundToAllPlayers(WAVE_GATE_SPAWN_SOUND_ID);
        }

        if (rankTier == GateRankTier.S) {
            universe.sendMessage(Message.join(
                    Message.raw("[Scout] ").color("#ff9de1"),
                    Message.raw("Do I hear boss music?").color("#ffe8fb")
            ));
        }
    }

    private static void announceNaturalWaveOpen(@Nonnull GateRankTier rankTier,
                                                @Nullable WavePortalPlacement placement) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        playSoundToAllPlayers(WAVE_GATE_SPAWN_SOUND_ID);
        String coordinateLabel = placement == null
                ? ""
                : String.format(Locale.ROOT, " at (%d, %d, %d)", placement.x, placement.y, placement.z);
        if (rankTier == GateRankTier.S) {
            universe.sendMessage(Message.join(
                    Message.raw("[WORLD DISASTER] ").color("#ff5a36"),
                    Message.raw("The Sovereign Rift has opened" + coordinateLabel + ". Survive the storm.").color("#ffd7cf")
            ));
            return;
        }
        universe.sendMessage(Message.join(
                Message.raw("[DUNGEON BREAK] ").color(rankTier.color().hex()),
                Message.raw(String.format(Locale.ROOT,
                        "%s-Rank breach opened%s.",
                        rankTier.letter(),
                        coordinateLabel)).color("#ffffff")
        ));
    }

    private static void schedulePostOpenMemeDialogue(@Nonnull GateRankTier rankTier,
                                                     @Nullable WavePortalPlacement placement) {
        if (placement == null) {
            return;
        }
        String coordinateLabel = placement == null
                ? ""
                : String.format(Locale.ROOT, " (%d, %d, %d)", placement.x, placement.y, placement.z);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Scout] ").color("#8fe3ff"),
            Message.raw("Where are the mobs" + coordinateLabel + "?").color("#ffffff")
        ), 1);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Veteran] ").color(rankTier.color().hex()),
            Message.raw("I brought potions for nothing...").color("#ffe8b5")
        ), 5);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Merchant] ").color("#ffd27a"),
            Message.raw("bro, report this to the dev. I think it's broken.").color("#fff4d6")
        ), 10);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Admin] ").color("#ff8f8f"),
            Message.raw("How do I remove this portal? It's at spawn. wtf...").color("#ffe1e1")
        ), 14);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Resident] ").color("#9dff8f"),
            Message.raw("Bro, this thing spawned in my house.").color("#e8ffe3")
        ), 18);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Moderator] ").color("#c7a6ff"),
            Message.raw("Who gave /spawnportal permissions to the intern?").color("#f1e6ff")
        ), 22);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Raid Leader] ").color("#ffb36b"),
            Message.raw("Tank check, healer check... wave check? Hello?").color("#ffe6cf")
        ), 26);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Cleric] ").color("#7de0ff"),
            Message.raw("I pre-buffed the whole party for absolutely nothing.").color("#dff7ff")
        ), 30);

        scheduleNearbyDialogueLine(placement, Message.join(
            Message.raw("[Server Console] ").color("#ff6b6b"),
            Message.raw("Notice: Portal event spawned with 0 mobs. Working as intended? probably not.").color("#ffd9d9")
        ), 34);
    }

    private static void schedulePostOpenMiniDialogue(@Nonnull GateRankTier rankTier,
                                                     @Nullable WavePortalPlacement placement) {
        if (placement == null) {
            return;
        }

        scheduleNearbyDialogueLine(placement, Message.join(
                Message.raw("[Scout] ").color("#8fe3ff"),
                Message.raw("Uh... did the wave forget to spawn?").color("#ffffff")
        ), 2);

        scheduleNearbyDialogueLine(placement, Message.join(
                Message.raw("[Party Chat] ").color(rankTier.color().hex()),
                Message.raw("Free loot window, no mobs attached.").color("#ffe8b5")
        ), 6);
    }

    private static void scheduleNearbyDialogueLine(@Nonnull WavePortalPlacement placement,
                                                   @Nonnull Message message,
                                                   long initialDelaySeconds) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> attemptNearbyDialogueLineDelivery(placement, message),
                Math.max(0L, initialDelaySeconds),
                TimeUnit.SECONDS);
    }

    private static void attemptNearbyDialogueLineDelivery(@Nonnull WavePortalPlacement placement,
                                                          @Nonnull Message message) {
        boolean delivered = sendDialogueToNearbyPlayers(placement, message);
        if (delivered) {
            return;
        }
        if (!isPlacementStillActive(placement)) {
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> attemptNearbyDialogueLineDelivery(placement, message),
                DIALOGUE_RETRY_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private static boolean isPlacementStillActive(@Nonnull WavePortalPlacement placement) {
        for (ActiveWavePreview preview : ACTIVE_PREVIEWS.values()) {
            if (preview != null && preview.placement != null
                    && preview.placement.placementUuid().equals(placement.placementUuid())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sendDialogueToNearbyPlayers(@Nonnull WavePortalPlacement placement, @Nonnull Message message) {
        Universe universe = Universe.get();
        if (universe == null) {
            return false;
        }

        boolean delivered = false;
        double radiusSquared = DIALOGUE_NEARBY_RADIUS_BLOCKS * DIALOGUE_NEARBY_RADIUS_BLOCKS;
        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            UUID playerWorldUuid = playerRef.getWorldUuid();
            if (playerWorldUuid == null || !playerWorldUuid.equals(placement.worldUuid)) {
                continue;
            }

            Vector3d playerPosition = playerRef.getTransform() == null ? null : playerRef.getTransform().getPosition();
            if (playerPosition == null) {
                continue;
            }

            double dx = playerPosition.x - placement.x;
            double dy = playerPosition.y - placement.y;
            double dz = playerPosition.z - placement.z;
            double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
            if (distanceSquared > radiusSquared) {
                continue;
            }

            playerRef.sendMessage(message);
            delivered = true;
        }
        return delivered;
    }

    @Nonnull
    private static List<ScheduledFuture<?>> schedulePrestartCountdown(@Nonnull GateRankTier rankTier,
                                                                       long delaySeconds,
                                                                       boolean linkedGateMode) {
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        List<Integer> markers = resolveCountdownMarkers(rankTier, delaySeconds);
        for (int secondsRemaining : markers) {
            long stepDelay = Math.max(0L, delaySeconds - secondsRemaining);
            ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                Universe universe = Universe.get();
                if (universe != null) {
                    String channel = linkedGateMode ? "RIFT LOCK" : "DUNGEON BREAK";
                    universe.sendMessage(Message.join(
                            Message.raw("[" + channel + "] ").color(rankTier.color().hex()),
                            Message.raw(String.format(Locale.ROOT,
                                    "%s-Rank wave begins in %s",
                                    rankTier.letter(),
                                    formatSeconds(secondsRemaining))).color("#ffe8b5")
                    ));
                }

                if (rankTier == GateRankTier.S && secondsRemaining == 60) {
                    playSoundToAllPlayers(S_WAVE_1_MINUTE_COUNTDOWN_SOUND_ID);
                    return;
                }

                if (rankTier != GateRankTier.S && secondsRemaining == 10) {
                    playSoundToAllPlayers(WAVE_10_SECOND_COUNTDOWN_SOUND_ID);
                }
            }, stepDelay, TimeUnit.SECONDS);
            futures.add(future);
        }
        return futures;
    }

    @Nonnull
    private static List<Integer> resolveCountdownMarkers(@Nonnull GateRankTier rankTier, long delaySeconds) {
        List<Integer> markers = new ArrayList<>();
        if (rankTier == GateRankTier.S) {
            addCountdownMarker(markers, delaySeconds, 300);
            addCountdownMarker(markers, delaySeconds, 60);
        } else if (rankTier == GateRankTier.A) {
            addCountdownMarker(markers, delaySeconds, 60);
        }
        addCountdownMarker(markers, delaySeconds, 30);
        addCountdownMarker(markers, delaySeconds, 10);
        addCountdownMarker(markers, delaySeconds, 5);
        addCountdownMarker(markers, delaySeconds, 4);
        addCountdownMarker(markers, delaySeconds, 3);
        addCountdownMarker(markers, delaySeconds, 2);
        addCountdownMarker(markers, delaySeconds, 1);
        return markers;
    }

    private static void addCountdownMarker(@Nonnull List<Integer> markers, long delaySeconds, int markerSeconds) {
        if (markerSeconds <= 0 || markerSeconds > delaySeconds || markers.contains(markerSeconds)) {
            return;
        }
        markers.add(markerSeconds);
    }

    @Nonnull
    private static String formatSeconds(int seconds) {
        return String.format(Locale.ROOT, "00:%02d", Math.max(0, seconds));
    }

    private static void cancelScheduledFutures(@Nullable List<ScheduledFuture<?>> futures) {
        if (futures == null) {
            return;
        }
        for (ScheduledFuture<?> future : futures) {
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private static void showTitleToAllPlayers(@Nonnull Message titlePrimary,
                                              @Nonnull Message titleSecondary,
                                              boolean playSound) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            EventTitleUtil.showEventTitleToPlayer(playerRef, titlePrimary, titleSecondary, playSound);
        }
    }

    private static void playSoundToAllPlayers(@Nonnull String soundEventId) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        int soundIndex = resolveSoundIndex(soundEventId);
        if (soundIndex == 0) {
            LOGGER.atWarning().log("[ELWavePreview] Missing sound event id: %s", soundEventId);
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
                Ref<EntityStore> playerEntityRef = playerRef.getReference();
                if (playerEntityRef == null || !playerEntityRef.isValid()) {
                    return;
                }

                try {
                    SoundUtil.playSoundEvent2d(playerEntityRef, soundIndex, SoundCategory.SFX, playerEntityRef.getStore());
                } catch (Exception ex) {
                    LOGGER.atWarning().log("[ELWavePreview] Failed preview SFX for player=%s: %s",
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

    @Nonnull
    private static WavePreviewSnapshot toSnapshot(@Nonnull ActiveWavePreview preview) {
        return toSnapshot(preview, System.currentTimeMillis());
    }

    @Nonnull
    private static WavePreviewSnapshot toSnapshot(@Nonnull ActiveWavePreview preview, long now) {
        long remainingMillis = Math.max(0L, preview.openAtMillis - now);
        int remainingSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(remainingMillis);
        return new WavePreviewSnapshot(preview.playerUuid,
                preview.rankTier,
                preview.placement.x,
                preview.placement.y,
                preview.placement.z,
                remainingSeconds,
                preview.openAtMillis);
    }

    public record WavePreviewSnapshot(@Nonnull UUID playerUuid,
                                      @Nonnull GateRankTier rankTier,
                                      int x,
                                      int y,
                                      int z,
                                      int secondsRemaining,
                                      long openAtMillis) {
    }

    private static final class ActiveWavePreview {
        private final UUID playerUuid;
        private final GateRankTier rankTier;
        private final WavePortalPlacement placement;
        private final long createdAtMillis;
        private final long openAtMillis;
        private volatile List<ScheduledFuture<?>> countdownFutures;
        private volatile ScheduledFuture<?> openFuture;

        private ActiveWavePreview(@Nonnull UUID playerUuid,
                                  @Nonnull GateRankTier rankTier,
                                  @Nonnull WavePortalPlacement placement,
                                  long createdAtMillis,
                                  long openAtMillis) {
            this.playerUuid = playerUuid;
            this.rankTier = rankTier;
            this.placement = placement;
            this.createdAtMillis = createdAtMillis;
            this.openAtMillis = openAtMillis;
        }
    }

    private record WavePortalPlacement(@Nonnull UUID placementUuid,
                                       @Nullable UUID worldUuid,
                                       int x,
                                       int y,
                                       int z,
                                       int blockIntId,
                                       @Nonnull String blockId,
                                       int signX,
                                       int signY,
                                       int signZ) {
    }
}