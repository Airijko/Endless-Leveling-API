package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Typed bridge for runtime wave gate operations exposed by addon implementations.
 */
public interface WaveGateRuntimeBridge {

    /**
     * Request a natural wave gate session start for a player.
     *
     * @param player target player
     * @param preferredRankTierId optional preferred rank tier id (for example S/A/B/C/D/E)
     * @return true when a session was scheduled/started
     */
    CompletableFuture<Boolean> startNaturalWaveForPlayer(@Nonnull Player player, @Nullable String preferredRankTierId);
}
