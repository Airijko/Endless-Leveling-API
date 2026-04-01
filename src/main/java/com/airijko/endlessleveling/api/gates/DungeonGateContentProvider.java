package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Supplies dungeon gate configuration/content to the core runtime.
 */
public interface DungeonGateContentProvider {

    @Nonnull
    String getProviderId();

    boolean isEnabled();

    int getSpawnIntervalMinutesMin();

    int getSpawnIntervalMinutesMax();

    int getGateDurationMinutes();

    int getMaxConcurrentSpawns();

    @Nonnull
    List<String> getPortalWorldWhitelist();
}
