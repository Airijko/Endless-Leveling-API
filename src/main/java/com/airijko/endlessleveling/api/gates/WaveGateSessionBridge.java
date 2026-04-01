package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WaveGateSessionBridge {

    CompletableFuture<Boolean> startNaturalWaveForPlayer(Player player, String preferredRankTierId);

    WaveSessionResults.StartResult startWaveForPlayer(PlayerRef playerRef, String rankTierId);

    WaveSessionResults.StopResult stopWaveForPlayer(PlayerRef playerRef);

    WaveSessionResults.StatusResult getStatus(PlayerRef playerRef);

    WaveSessionResults.SkipResult skipWaveForPlayer(PlayerRef playerRef);

    int clearLinkedGateCombosForPlayer(PlayerRef playerRef);

    int clearWavePortalVisualsInPlayerWorld(PlayerRef playerRef);

    List<TrackedWaveGateSnapshot> listTrackedStandaloneWaves();

    List<TrackedWaveGateSnapshot> listTrackedGateWaveCombos();
}
