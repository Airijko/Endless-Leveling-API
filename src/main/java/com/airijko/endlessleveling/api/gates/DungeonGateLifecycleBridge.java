package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DungeonGateLifecycleBridge {

    CompletableFuture<Boolean> spawnRandomGateNearPlayer(Player player, boolean isTestSpawn);

    CompletableFuture<Boolean> spawnGateNearPlayerWithRank(Player player, String rankTierId, boolean isTestSpawn);

    CompletableFuture<Boolean> spawnGateNearPlayerWithRankAndForcedLinkedWave(Player player,
            String rankTierId,
            boolean isTestSpawn);

    String rollGateRankTierForPlayer(PlayerRef playerRef);

    List<TrackedDungeonGateSnapshot> listTrackedGates();
}
