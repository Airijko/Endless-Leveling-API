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
    private static final String WAVE_GATE_SPAWN_SOUND_ID = "SFX_EL_S_Rank_Gate_Spawn";
    private static final String WAVE_10_SECOND_COUNTDOWN_SOUND_ID = "SFX_EL_DungeonBreak_10_Second_Countdown";
    private static final String S_WAVE_1_MINUTE_COUNTDOWN_SOUND_ID = "SFX_EL_S_Wave_1_Minute_Countdown";

    private static final Map<UUID, ActiveWavePreview> ACTIVE_PREVIEWS = new ConcurrentHashMap<>();

    private WavePortalPreviewManager() {
    }

    public static CompletableFuture<WavePreviewSnapshot> spawnPreviewNearPlayer(@Nonnull PlayerRef playerRef) {
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

                GateRank gateRank = NaturalGateSpawner.resolveGateRankForPlayer(playerRef);
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

    private static void openPreview(@Nonnull World world, @Nonnull ActiveWavePreview preview) {
        try {
            announceNaturalWaveOpen(preview.rankTier, preview.placement);
            clearPortalPlacement(world, preview.placement);
        } finally {
            cancelScheduledFutures(preview.countdownFutures);
            ACTIVE_PREVIEWS.remove(preview.playerUuid);
        }
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

            return new WavePortalPlacement(UUID.randomUUID(), worldUuid, baseX, y, baseZ, existing,
                    resolveWavePortalBlockIdFromIntId(existing, portalBlockId));
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
        chunk.markNeedsSaving();
        return new WavePortalPlacement(UUID.randomUUID(), worldUuid, baseX, placementY, baseZ, portalBlockIntId, portalBlockId);
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
            chunk.markNeedsSaving();
        }
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
                                       @Nonnull String blockId) {
    }
}