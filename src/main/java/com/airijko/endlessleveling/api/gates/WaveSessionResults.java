package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WaveSessionResults {

    private WaveSessionResults() {
    }

    public record StartResult(boolean started,
            @Nonnull String message,
            @Nullable String roleName,
            @Nullable String rankTierId,
            int waves,
            int mobsPerWave,
            int intervalSeconds,
            int levelMin,
            int levelMax) {
    }

    public record StopResult(boolean stopped, @Nullable String roleName) {
    }

    public record SkipResult(boolean skipped, boolean completed, int killed, int nextWave, int totalWaves) {
    }

    public record StatusResult(@Nonnull String roleName,
            @Nonnull String rankTierId,
            int waves,
            int mobsPerWave,
            int intervalSeconds,
            int levelMin,
            int levelMax) {
    }

    public record NaturalStartResult(boolean scheduled, @Nonnull String message, @Nullable String rankTierId,
            int delayMinutes) {
    }
}
