package com.airijko.endlessleveling.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared mob rank tier used across all EL-ecosystem mods.
 * Gates, elite mobs, and any future system that needs an E→S ranking
 * should reference this enum rather than defining its own.
 */
public enum MobRankTier {
    E("E", "#adb5bd"),
    D("D", "#4cc9f0"),
    C("C", "#8ac926"),
    B("B", "#f78c6b"),
    A("A", "#ef476f"),
    S("S", "#ffbf00");

    private final String letter;
    private final String defaultColor;

    MobRankTier(String letter, String defaultColor) {
        this.letter = letter;
        this.defaultColor = defaultColor;
    }

    /** Single-letter rank identifier (e.g. "D"). */
    @Nonnull
    public String letter() {
        return letter;
    }

    /** Default hex color for display. Mods may override with their own palette. */
    @Nonnull
    public String defaultColor() {
        return defaultColor;
    }

    /** Ordinal position where E=0 and S=5. Higher = stronger. */
    public int strength() {
        return ordinal();
    }

    /**
     * Parse a rank letter string (e.g. "D", "S") into a {@link MobRankTier}.
     * Returns {@code null} for unrecognized input.
     */
    @Nullable
    public static MobRankTier fromLetter(@Nullable String letter) {
        if (letter == null || letter.isEmpty()) {
            return null;
        }
        for (MobRankTier tier : values()) {
            if (tier.letter.equalsIgnoreCase(letter)) {
                return tier;
            }
        }
        return null;
    }
}
