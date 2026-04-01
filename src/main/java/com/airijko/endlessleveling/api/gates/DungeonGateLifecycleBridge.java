package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DungeonGateLifecycleBridge {

    CompletableFuture<Boolean> spawnRandomGateNearPlayer(@Nonnull Player player, boolean isTestSpawn);

    CompletableFuture<Boolean> spawnGateNearPlayerWithRank(@Nonnull Player player,
            @Nonnull String rankTierId,
            boolean isTestSpawn);

    CompletableFuture<Boolean> spawnGateNearPlayerWithRankAndForcedLinkedWave(@Nonnull Player player,
            @Nonnull String rankTierId,
            boolean isTestSpawn);

    @Nullable
    String rollGateRankTierForPlayer(@Nonnull PlayerRef playerRef);

    @Nonnull
    List<TrackedDungeonGateSnapshot> listTrackedGates();

    @Nullable
    String resolveGateIdAt(@Nonnull World world, int x, int y, int z);

    void forceRemoveGateAt(@Nonnull World world, int x, int y, int z, @Nonnull String blockId);
}
