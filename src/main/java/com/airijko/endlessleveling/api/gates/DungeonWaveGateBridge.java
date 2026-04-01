package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Typed bridge exposed by external addons that implement the dungeon/wave gate
 * runtime. Core and third-party integrations can use this contract instead of
 * reflective manager lookups.
 */
public interface DungeonWaveGateBridge {

    CompletableFuture<Boolean> spawnRandomGateNearPlayer(@Nonnull Player player, boolean isTestSpawn);

    CompletableFuture<Boolean> spawnGateNearPlayerWithRank(@Nonnull Player player,
            @Nonnull String rankTierId,
            boolean isTestSpawn);

    boolean isGateEntryLocked(@Nullable String gateIdentity);
}