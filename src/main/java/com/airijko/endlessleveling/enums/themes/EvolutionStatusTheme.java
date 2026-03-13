package com.airijko.endlessleveling.enums.themes;

/**
 * Shared status and progress colors for race evolution rows.
 */
public enum EvolutionStatusTheme {
    ACTIVE("#6fe3ff", "#6fe3ff"),
    UNLOCKED("#8adf9e", "#8adf9e"),
    AVAILABLE("#9be3ff", "#8be0b2"),
    ELIGIBLE("#b6e39f", "#8be0b2"),
    LOCKED("#ffc300", "#ffb347"),
    FINAL("#d7baff", "#9fb6d3"),
    UNKNOWN("#9fb6d3", "#9fb6d3");

    private final String statusColor;
    private final String progressColor;

    EvolutionStatusTheme(String statusColor, String progressColor) {
        this.statusColor = statusColor;
        this.progressColor = progressColor;
    }

    public String statusColor() {
        return statusColor;
    }

    public String progressColor() {
        return progressColor;
    }
}