package com.airijko.endlessleveling.enums;

import java.util.Locale;

import com.airijko.endlessleveling.systems.PassiveRegenSystem;

/**
 * Enumerates the configurable EndlessLeveling passive systems defined in
 * config.yml.
 */
public enum PassiveType {
    REGENERATION("regeneration", "Regeneration"),
    SIGNATURE_GAIN("signature_gain", "Signature Gain"),
    LUCK("luck", "Luck"),
    MANA_REGENERATION("mana_regeneration", "Mana Regeneration"),
    STAMINA_GAIN_BONUS("stamina_gain_bonus", "Stamina Gain Bonus");

    private final String configKey;
    private final String displayName;

    PassiveType(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String formatValue(double value) {
        return switch (this) {
            case SIGNATURE_GAIN -> formatNumber(value) + "%";
            case REGENERATION -> formatNumber(value) + " Health/sec";
            case LUCK -> formatNumber(value) + "% Luck";
            case MANA_REGENERATION -> formatNumber(value * PassiveRegenSystem.RESOURCE_REGEN_DIVISOR) + " Mana/5s";
            case STAMINA_GAIN_BONUS -> formatNumber(value) + "% Stamina Gain";
        };
    }

    private String formatNumber(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }
}
