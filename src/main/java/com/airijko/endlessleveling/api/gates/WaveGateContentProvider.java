package com.airijko.endlessleveling.api.gates;

public interface WaveGateContentProvider {

    String getProviderId();

    WavePoolDefinition loadWavePool(String rankTierId);
}
