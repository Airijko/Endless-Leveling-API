package com.airijko.endlessleveling.api.gates;

import java.util.List;

public interface DungeonGateContentProvider {

    String getProviderId();

    boolean isEnabled();

    int getSpawnIntervalMinutesMin();

    int getSpawnIntervalMinutesMax();

    int getGateDurationMinutes();

    int getMaxConcurrentSpawns();

    List<String> getPortalWorldWhitelist();
}
