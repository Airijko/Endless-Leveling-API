package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.enums.GateRankTier;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Snapshot of an active gate position and metadata.
 */
public class GateSnapshot {
    public final UUID gateId;
    public final int x;
    public final int y;
    public final int z;
    public final GateRankTier tier;
    public final long spawnTimeMillis;

    public GateSnapshot(@Nonnull UUID gateId, int x, int y, int z, @Nonnull GateRankTier tier, long spawnTimeMillis) {
        this.gateId = gateId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
        this.spawnTimeMillis = spawnTimeMillis;
    }

    @Nonnull
    public String getCoordinates() {
        return String.format("(%d, %d, %d)", x, y, z);
    }
}
