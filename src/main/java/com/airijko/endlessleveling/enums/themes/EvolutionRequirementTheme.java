package com.airijko.endlessleveling.enums.themes;

/**
 * Shared colors for race evolution requirement readability.
 */
public enum EvolutionRequirementTheme {
    GENERAL("#d7e3f2"),
    TRAINABLE_SKILLS("#9be3ff"),
    READY("#8be0b2"),
    MISSING("#ffb347"),
    NEUTRAL("#9fb6d3");

    private final String color;

    EvolutionRequirementTheme(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }
}