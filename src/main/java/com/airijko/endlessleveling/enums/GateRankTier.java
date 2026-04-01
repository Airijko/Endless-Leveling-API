package com.airijko.endlessleveling.enums;

import javax.annotation.Nonnull;

/**
 * Gate rank tier system with colors and portal scale properties.
 */
public enum GateRankTier {
    S("S", PortalGateColor.RANK_S),
    A("A", PortalGateColor.RANK_A),
    B("B", PortalGateColor.RANK_B),
    C("C", PortalGateColor.RANK_C),
    D("D", PortalGateColor.RANK_D),
    E("E", PortalGateColor.RANK_E);

    private final String letter;
    private final PortalGateColor color;

    GateRankTier(String letter, PortalGateColor color) {
        this.letter = letter;
        this.color = color;
    }

    @Nonnull
    public String letter() {
        return letter;
    }

    @Nonnull
    public PortalGateColor color() {
        return color;
    }

    /**
     * The MagicPortal particle scale for this rank.
     * E is the baseline (1.5). Each rank up dramatically enlarges the portal.
     */
    public double portalScale() {
        return switch (this) {
            case S -> 10.0;
            case A -> 7.0;
            case B -> 5.0;
            case C -> 3.5;
            case D -> 2.25;
            case E -> 1.5;
        };
    }

    /**
     * Suffix appended to base block IDs to select the rank-sized portal variant.
     * E rank uses the base block (no suffix).
     */
    @Nonnull
    public String blockIdSuffix() {
        return this == E ? "" : "_Rank" + letter;
    }

    /**
     * Resolve rank tier from a ratio (0.0 to 1.0+).
     */
    @Nonnull
    public static GateRankTier fromRatio(double ratio) {
        if (ratio > 1.00D) {
            return S;
        }
        if (ratio >= 0.80D) {
            return A;
        }
        if (ratio >= 0.60D) {
            return B;
        }
        if (ratio >= 0.40D) {
            return C;
        }
        if (ratio >= 0.20D) {
            return D;
        }
        return E;
    }
}
