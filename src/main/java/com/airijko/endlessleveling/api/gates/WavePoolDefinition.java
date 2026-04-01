package com.airijko.endlessleveling.api.gates;

import java.util.List;

public record WavePoolDefinition(List<Pool> pools, List<String> bossPool) {

    public record Pool(String id, List<String> mobs) {
    }
}
