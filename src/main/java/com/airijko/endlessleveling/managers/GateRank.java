package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.enums.GateRankTier;

import javax.annotation.Nonnull;

/**
 * Represents a spawned gate rank with tier and level information.
 */
public class GateRank {
    public final GateRankTier tier;
    public final int normalLevelMin;
    public final int normalLevelMax;
    public final int bossLevel;
    public final double roll; // 0.0-1.0+ random roll used for tier selection

    public GateRank(@Nonnull GateRankTier tier, int normalLevelMin, int normalLevelMax, int bossLevel, double roll) {
        this.tier = tier;
        this.normalLevelMin = normalLevelMin;
        this.normalLevelMax = normalLevelMax;
        this.bossLevel = bossLevel;
        this.roll = roll;
    }
}
