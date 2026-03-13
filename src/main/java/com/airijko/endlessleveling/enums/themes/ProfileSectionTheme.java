package com.airijko.endlessleveling.enums.themes;

/**
 * Shared row colors for Profile page sections that use
 * ProfileRacePassiveEntry.ui.
 */
public enum ProfileSectionTheme {
    PRIMARY_WEAPON("#d4e1f4", "#8ec5ff"),
    AUGMENT("#d7e3f5", "#7ad8c2"),
    PASSIVE("#d8e9ff", "#7fd7ff"),
    INNATE_PASSIVE("#d9efcc", "#9ee087"),
    INNATE_ATTRIBUTE("#eddcff", "#c79bff");

    private final String nameColor;
    private final String valueColor;

    ProfileSectionTheme(String nameColor, String valueColor) {
        this.nameColor = nameColor;
        this.valueColor = valueColor;
    }

    public String nameColor() {
        return nameColor;
    }

    public String valueColor() {
        return valueColor;
    }
}
