package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.api.gates.DungeonGateContentProvider;
import com.airijko.endlessleveling.api.gates.DungeonWaveGateBridge;
import com.airijko.endlessleveling.api.gates.WaveGateRuntimeBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionBridge;
import com.airijko.endlessleveling.api.gates.WaveGateContentProvider;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core-owned gate type runtime coordinator.
 *
 * Phase 2: provider-driven dungeon gate cadence and bridge spawn execution from core.
 */
public final class CoreGateTypeRuntimeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final List<String> WAVE_RANK_TIER_IDS = List.of("S", "A", "B", "C", "D", "E");

    private volatile boolean running;
    private volatile long nextDungeonGateSpawnAtMillis;
    private volatile long nextWaveGateStartAtMillis;

    public void start() {
        if (running) {
            return;
        }
        running = true;

        EndlessLevelingAPI api = EndlessLevelingAPI.get();
        scheduleNextDungeonGateSpawn(selectPrimaryDungeonGateProvider(api));
        scheduleNextWaveGateStart();

        LOGGER.atInfo().log(
            "[EL-CoreGateTypeRuntime] Started. dungeonGateProviders=%d waveGateProviders=%d bridgePresent=%s waveBridgePresent=%s nextDungeonGateSpawnAt=%d nextWaveGateStartAt=%d",
                api.getDungeonGateContentProviders().size(),
                api.getWaveGateContentProviders().size(),
                api.getDungeonWaveGateBridge() != null ? "yes" : "no",
            api.getWaveGateRuntimeBridge() != null ? "yes" : "no",
            nextDungeonGateSpawnAtMillis,
            nextWaveGateStartAtMillis);
    }

    public void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        LOGGER.atInfo().log("[EL-CoreGateTypeRuntime] Stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (!running || event.getPlayer() == null) {
            return;
        }

        EndlessLevelingAPI api = EndlessLevelingAPI.get();
        DungeonWaveGateBridge bridge = api.getDungeonWaveGateBridge();
        WaveGateSessionBridge waveSessionBridge = api.getWaveGateSessionBridge();
        WaveGateRuntimeBridge waveBridge = api.getWaveGateRuntimeBridge();
        DungeonGateContentProvider provider = selectPrimaryDungeonGateProvider(api);
        WaveGateContentProvider waveProvider = selectPrimaryWaveGateProvider(api);

        long now = System.currentTimeMillis();

        if (bridge != null && provider != null && provider.isEnabled() && now >= nextDungeonGateSpawnAtMillis) {
            // Schedule next window first so a failed spawn still backs off and avoids tight retry loops.
            scheduleNextDungeonGateSpawn(provider);

            bridge.spawnRandomGateNearPlayer(event.getPlayer(), false)
                    .whenComplete((spawned, throwable) -> {
                        if (throwable != null) {
                            LOGGER.atWarning().log("[EL-CoreDungeonGate] Spawn attempt failed: %s", throwable.toString());
                            return;
                        }

                        LOGGER.atInfo().log(
                                "[EL-CoreDungeonGate] Spawn attempt result=%s provider=%s nextWindowAt=%d",
                                Boolean.TRUE.equals(spawned) ? "spawned" : "not-spawned",
                                provider.getProviderId(),
                                nextDungeonGateSpawnAtMillis);
                    });
        }

        if ((waveSessionBridge != null || waveBridge != null) && waveProvider != null && now >= nextWaveGateStartAtMillis) {
            scheduleNextWaveGateStart();
            String rankTierId = pickWaveRankTierId(waveProvider);

            CompletableFuture<Boolean> waveStartFuture = waveSessionBridge != null
                    ? waveSessionBridge.startNaturalWaveForPlayer(event.getPlayer(), rankTierId)
                    : waveBridge.startNaturalWaveForPlayer(event.getPlayer(), rankTierId);

            waveStartFuture
                    .whenComplete((started, throwable) -> {
                        if (throwable != null) {
                            LOGGER.atWarning().log("[EL-CoreWaveGate] Natural start attempt failed: %s", throwable.toString());
                            return;
                        }

                        LOGGER.atInfo().log(
                                "[EL-CoreWaveGate] Natural start result=%s provider=%s rank=%s nextWindowAt=%d",
                                Boolean.TRUE.equals(started) ? "started" : "not-started",
                                waveProvider.getProviderId(),
                                rankTierId == null ? "auto" : rankTierId,
                                nextWaveGateStartAtMillis);
                    });
        }
    }

    @Nonnull
    public RuntimeSummary summarize() {
        EndlessLevelingAPI api = EndlessLevelingAPI.get();
        return new RuntimeSummary(
                running,
                api.getDungeonGateContentProviders().size(),
                api.getWaveGateContentProviders().size(),
                api.getDungeonWaveGateBridge() != null,
                nextDungeonGateSpawnAtMillis,
                api.getWaveGateRuntimeBridge() != null,
                nextWaveGateStartAtMillis);
    }

    private void scheduleNextDungeonGateSpawn(DungeonGateContentProvider provider) {
        int minMinutes = provider != null ? Math.max(1, provider.getSpawnIntervalMinutesMin()) : 15;
        int maxMinutes = provider != null ? Math.max(minMinutes, provider.getSpawnIntervalMinutesMax()) : minMinutes;
        int rolledMinutes = ThreadLocalRandom.current().nextInt(minMinutes, maxMinutes + 1);
        nextDungeonGateSpawnAtMillis = System.currentTimeMillis() + rolledMinutes * 60_000L;
    }

    private void scheduleNextWaveGateStart() {
        int minMinutes = 8;
        int maxMinutes = 14;
        int rolledMinutes = ThreadLocalRandom.current().nextInt(minMinutes, maxMinutes + 1);
        nextWaveGateStartAtMillis = System.currentTimeMillis() + rolledMinutes * 60_000L;
    }

    private DungeonGateContentProvider selectPrimaryDungeonGateProvider(EndlessLevelingAPI api) {
        List<DungeonGateContentProvider> providers = api.getDungeonGateContentProviders();
        if (providers.isEmpty()) {
            return null;
        }

        return providers.stream()
                .filter(DungeonGateContentProvider::isEnabled)
                .min(Comparator.comparing(DungeonGateContentProvider::getProviderId, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    private WaveGateContentProvider selectPrimaryWaveGateProvider(EndlessLevelingAPI api) {
        List<WaveGateContentProvider> providers = api.getWaveGateContentProviders();
        if (providers.isEmpty()) {
            return null;
        }

        return providers.stream()
                .min(Comparator.comparing(WaveGateContentProvider::getProviderId, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    private String pickWaveRankTierId(WaveGateContentProvider provider) {
        if (provider == null) {
            return null;
        }

        List<String> candidates = WAVE_RANK_TIER_IDS.stream()
                .filter(rankTierId -> {
                    var pool = provider.loadWavePool(rankTierId);
                    return pool != null && !pool.isEmpty();
                })
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        int idx = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(idx);
    }

    public record RuntimeSummary(boolean running,
            int dungeonGateProviderCount,
            int waveGateProviderCount,
            boolean bridgeRegistered,
            long nextDungeonGateSpawnAtMillis,
            boolean waveBridgeRegistered,
            long nextWaveGateStartAtMillis) {
    }
}
