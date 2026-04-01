package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WaveGateSessionBridge {

    CompletableFuture<Boolean> startNaturalWaveForPlayer(@Nonnull Player player, @Nullable String preferredRankTierId);

    @Nonnull
    WaveSessionResults.StartResult startWaveForPlayer(@Nonnull PlayerRef playerRef, @Nonnull String rankTierId);

    @Nonnull
    WaveSessionResults.StopResult stopWaveForPlayer(@Nonnull PlayerRef playerRef);

    @Nullable
    WaveSessionResults.StatusResult getStatus(@Nonnull PlayerRef playerRef);

    @Nonnull
    WaveSessionResults.SkipResult skipWaveForPlayer(@Nonnull PlayerRef playerRef);

    int clearLinkedGateCombosForPlayer(@Nonnull PlayerRef playerRef);

    int clearWavePortalVisualsInPlayerWorld(@Nonnull PlayerRef playerRef);

    @Nonnull
    List<TrackedWaveGateSnapshot> listTrackedStandaloneWaves();

    @Nonnull
    List<TrackedWaveGateSnapshot> listTrackedGateWaveCombos();
}
