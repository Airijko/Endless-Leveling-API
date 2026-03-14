package com.airijko.endlessleveling.enums;

import java.util.Locale;

/**
 * High-level trigger categories for passives (helps group behaviors in UI and
 * logic).
 */
public enum PassiveCategory {
    COMMON_STAT("Ingredient_Crystal_White"),
    PASSIVE_STAT("Ingredient_Life_Essence"),
    MAGIC_STAT("Weapon_Staff_Bronze"),
    ON_HIT("Weapon_Daggers_Fang_Doomed"),
    ON_RANGED_HIT("Weapon_Shortbow_Crude"),
    ON_DAMAGE_TAKEN("Weapon_Shield_Orbis_Knight"),
    ON_LOW_HP("Potion_Health_Lesser"),
    ON_CRIT("Weapon_Battleaxe_Mithril"),
    ON_KILL("Ingredient_Fire_Essence"),
    LIFESTEAL("Weapon_Sword_Adamantite");

    private final String iconItemId;

    PassiveCategory(String iconItemId) {
        this.iconItemId = iconItemId;
    }

    public String getIconItemId() {
        return iconItemId;
    }

    public static PassiveCategory fromConfig(Object raw, PassiveCategory fallback) {
        if (raw instanceof PassiveCategory category) {
            return category;
        }
        if (raw instanceof String str && !str.isBlank()) {
            String normalized = str.trim().toUpperCase(Locale.ROOT);
            for (PassiveCategory category : values()) {
                if (category.name().equals(normalized)) {
                    return category;
                }
            }
        }
        return fallback == null ? PASSIVE_STAT : fallback;
    }
}