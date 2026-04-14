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
    MAGIC_ON_HIT("Weapon_Spellbook_Fire"),
    LIFESTEAL("Weapon_Sword_Adamantite"),
    NECROMANCY("Weapon_Spellbook_Demon"),
    STAMINA("Potion_Stamina");

    private final String iconItemId;

    PassiveCategory(String iconItemId) {
        this.iconItemId = iconItemId;
    }

    public String getIconItemId() {
        return iconItemId;
    }

    public static PassiveCategory fromConfigOrNull(Object raw) {
        if (raw instanceof PassiveCategory category) {
            return category;
        }
        if (raw instanceof String str && !str.isBlank()) {
            String normalized = str.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("NECOMANCY".equals(normalized)) {
                return NECROMANCY;
            }
            String collapsed = normalized.replace("_", "");
            for (PassiveCategory category : values()) {
                String enumName = category.name();
                if (enumName.equals(normalized) || enumName.replace("_", "").equals(collapsed)) {
                    return category;
                }
            }
        }
        return null;
    }

    public static PassiveCategory fromConfig(Object raw, PassiveCategory fallback) {
        PassiveCategory resolved = fromConfigOrNull(raw);
        if (resolved != null) {
            return resolved;
        }
        return fallback == null ? PASSIVE_STAT : fallback;
    }
}