package com.airijko.endlessleveling.enums.themes;

import java.util.Locale;

/**
 * Maps weapon category keys to representative Hytale item icon IDs.
 */
public enum WeaponIconTheme {

    SWORD("sword", "Weapon_Sword_Onyxium"),
    LONGSWORD("longsword", "Weapon_Longsword_Adamantite_Saurian"),
    DAGGER("dagger", "Weapon_Daggers_Mithril"),
    DAGGERS("daggers", "Weapon_Daggers_Mithril"),
    BOW("bow", "Weapon_Shortbow_Combat"),
    SHORTBOW("shortbow", "Weapon_Shortbow_Combat"),
    STAFF("staff", "Weapon_Staff_Bronze"),
    SPEAR("spear", "Weapon_Spear_Iron"),
    MACE("mace", "Weapon_Mace_Prisma"),
    BATTLEAXE("battleaxe", "Weapon_Battleaxe_Mithril"),
    SPELLBOOK("spellbook", "Weapon_Spellbook_Grimoire_Brown"),
    GRIMOIRE("grimoire", "Weapon_Spellbook_Grimoire_Brown");

    private static final String FALLBACK_ICON = "Weapon_Sword_Onyxium";

    private final String key;
    private final String itemIconId;

    WeaponIconTheme(String key, String itemIconId) {
        this.key = key;
        this.itemIconId = itemIconId;
    }

    public String key() {
        return key;
    }

    public String itemIconId() {
        return itemIconId;
    }

    /**
     * Resolves a weapon category key to its representative item icon ID.
     */
    public static String resolveIcon(String weaponKey) {
        if (weaponKey == null || weaponKey.isBlank()) {
            return FALLBACK_ICON;
        }
        String normalized = weaponKey.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
        for (WeaponIconTheme theme : values()) {
            if (theme.key.equals(normalized)) {
                return theme.itemIconId;
            }
        }
        return FALLBACK_ICON;
    }
}
