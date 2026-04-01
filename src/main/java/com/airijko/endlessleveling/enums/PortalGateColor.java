package com.airijko.endlessleveling.enums;

import javax.annotation.Nonnull;

/**
 * Color palette for portal gate messaging and displays.
 */
public enum PortalGateColor {
    PREFIX("#ff3b30"),          // Red-orange
    HEADLINE("#ffc300"),        // Yellow
    WORLD("#66d9ff"),           // Cyan
    POSITION("#ffd166"),        // Orange-yellow
    LEVEL("#6cff78"),           // Green

    RANK_S("#ffbf00"),          // Gold
    RANK_A("#ef476f"),          // Red-pink
    RANK_B("#f78c6b"),          // Orange
    RANK_C("#8ac926"),          // Yellow-green
    RANK_D("#4cc9f0"),          // Bright cyan
    RANK_E("#adb5bd");          // Gray

    private final String hex;

    PortalGateColor(String hex) {
        this.hex = hex;
    }

    @Nonnull
    public String hex() {
        return hex;
    }
}
