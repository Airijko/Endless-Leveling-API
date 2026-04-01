package com.airijko.endlessleveling.api.gates;

public final class WaveSessionResults {

    private WaveSessionResults() {
    }

    public record NaturalStartResult(boolean scheduled, String message, String rankTierId, int delaySeconds) {
    }

    public record StartResult(
            boolean started,
            String message,
            String roleName,
            String rankTierId,
            int waves,
            int mobsPerWave,
            int intervalSeconds,
            int levelMin,
            int levelMax) {
    }

    public record StopResult(boolean stopped, String roleName) {
    }

    public record StatusResult(
            String roleName,
            String rankTierId,
            int waves,
            int mobsPerWave,
            int intervalSeconds,
            int levelMin,
            int levelMax) {
    }

    public record SkipResult(boolean skipped, boolean completed, int killed, int nextWave, int totalWaves) {
    }
}
