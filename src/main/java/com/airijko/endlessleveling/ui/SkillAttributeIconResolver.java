package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.EnumMap;

/**
 * Shared icon mapping for skill attributes and COMMON stat augments.
 */
public final class SkillAttributeIconResolver {

    private static final EnumMap<SkillAttributeType, String> ICONS = new EnumMap<>(SkillAttributeType.class);

    static {
        ICONS.put(SkillAttributeType.LIFE_FORCE, "Potion_Health");
        ICONS.put(SkillAttributeType.STRENGTH, "Weapon_Longsword_Adamantite_Saurian");
        ICONS.put(SkillAttributeType.SORCERY, "Weapon_Staff_Mithril");
        ICONS.put(SkillAttributeType.DEFENSE, "Weapon_Shield_Orbis_Knight");
        ICONS.put(SkillAttributeType.HASTE, "Spawn_Temple_Helix");
        ICONS.put(SkillAttributeType.PRECISION, "Weapon_Shortbow_Combat");
        ICONS.put(SkillAttributeType.FEROCITY, "Weapon_Battleaxe_Mithril");
        ICONS.put(SkillAttributeType.STAMINA, "Potion_Stamina");
        ICONS.put(SkillAttributeType.FLOW, "Prototype_Tool_Book_Mana");
        ICONS.put(SkillAttributeType.DISCIPLINE, "Ingredient_Life_Essence");
    }

    private SkillAttributeIconResolver() {
    }

    public static String resolve(SkillAttributeType attributeType) {
        if (attributeType == null) {
            return null;
        }
        return ICONS.get(attributeType);
    }

    public static String resolveByConfigKey(String attributeKey, String fallback) {
        SkillAttributeType type = SkillAttributeType.fromConfigKey(attributeKey);
        String resolved = resolve(type);
        if (resolved == null || resolved.isBlank()) {
            return fallback;
        }
        return resolved;
    }
}