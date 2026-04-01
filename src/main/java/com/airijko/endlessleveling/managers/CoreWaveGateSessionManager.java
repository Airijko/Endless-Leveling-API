package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.api.gates.TrackedWaveGateSnapshot;
import com.airijko.endlessleveling.api.gates.WaveGateSessionBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionExecutorBridge;
import com.airijko.endlessleveling.api.gates.WaveSessionResults;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core-owned public wave session service.
 *
 * The addon supplies a low-level executor bridge, while this manager becomes the
 * authoritative API/runtime surface exposed by the core mod.
 */
public final class CoreWaveGateSessionManager implements WaveGateSessionBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final AtomicLong naturalStartRequests = new AtomicLong();
    private final AtomicLong directStartRequests = new AtomicLong();

    public void start() {
        LOGGER.atInfo().log("[EL-CoreWaveGateSession] Started.");
    }

    public void shutdown() {
        LOGGER.atInfo().log(
                "[EL-CoreWaveGateSession] Stopped. naturalRequests=%d directRequests=%d",
                naturalStartRequests.get(),
                directStartRequests.get());
    }

    @Override
    public CompletableFuture<Boolean> startNaturalWaveForPlayer(@Nonnull Player player, @Nullable String preferredRankTierId) {
        naturalStartRequests.incrementAndGet();
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        if (executor == null) {
            return CompletableFuture.completedFuture(false);
        }
        return executor.startNaturalWaveForPlayer(player, preferredRankTierId);
    }

    @Override
    public WaveSessionResults.StartResult startWaveForPlayer(@Nonnull PlayerRef playerRef, @Nonnull String rankTierId) {
        directStartRequests.incrementAndGet();
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        if (executor == null) {
            return new WaveSessionResults.StartResult(false, "Wave session executor is unavailable.", null, null, 0, 0, 0, 0, 0);
        }
        return executor.startWaveForPlayer(playerRef, rankTierId);
    }

    @Override
    public WaveSessionResults.StopResult stopWaveForPlayer(@Nonnull PlayerRef playerRef) {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        if (executor == null) {
            return new WaveSessionResults.StopResult(false, null);
        }
        return executor.stopWaveForPlayer(playerRef);
    }

    @Override
    public WaveSessionResults.StatusResult getStatus(@Nonnull PlayerRef playerRef) {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        return executor == null ? null : executor.getStatus(playerRef);
    }

    @Override
    public WaveSessionResults.SkipResult skipWaveForPlayer(@Nonnull PlayerRef playerRef) {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        if (executor == null) {
            return new WaveSessionResults.SkipResult(false, false, 0, 0, 0);
        }
        return executor.skipWaveForPlayer(playerRef);
    }

    @Override
    public int clearLinkedGateCombosForPlayer(@Nonnull PlayerRef playerRef) {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        return executor == null ? 0 : executor.clearLinkedGateCombosForPlayer(playerRef);
    }

    @Override
    public int clearWavePortalVisualsInPlayerWorld(@Nonnull PlayerRef playerRef) {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        return executor == null ? 0 : executor.clearWavePortalVisualsInPlayerWorld(playerRef);
    }

    @Override
    public List<TrackedWaveGateSnapshot> listTrackedStandaloneWaves() {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        return executor == null ? List.of() : executor.listTrackedStandaloneWaves();
    }

    @Override
    public List<TrackedWaveGateSnapshot> listTrackedGateWaveCombos() {
        WaveGateSessionExecutorBridge executor = resolveExecutor();
        return executor == null ? List.of() : executor.listTrackedGateWaveCombos();
    }

    @Nullable
    private WaveGateSessionExecutorBridge resolveExecutor() {
        return EndlessLevelingAPI.get().getWaveGateSessionExecutorBridge();
    }
}
