package com.airijko.endlessleveling.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Supported weapon categories for character class damage modifiers.
 */
public enum ClassWeaponType {

    SWORD("SWORD", "SWORD", "BLADE", "SABER"),
    AXE("AXE", "AXE", "HATCHET"),
    BOW("BOW", "BOW"),
    STAFF("STAFF", "STAFF"),
    DAGGER("DAGGER", "DAGGER", "KNIFE"),
    MACE("MACE", "MACE", "HAMMER", "CLUB"),
    SPEAR("SPEAR", "SPEAR", "HALBERD", "POLEARM"),
    CROSSBOW("CROSSBOW", "CROSSBOW"),
    WAND("WAND", "WAND", "ORB", "FOCUS"),
    UNARMED("UNARMED", "UNARMED", "FIST"),
    GENERIC("GENERIC", "GENERIC");

    private final String configKey;
    private final String[] matchTokens;

    ClassWeaponType(String configKey, String... tokens) {
        this.configKey = configKey;
        if (tokens == null || tokens.length == 0) {
            this.matchTokens = new String[] { configKey };
        } else {
            this.matchTokens = Arrays.stream(tokens)
                    .filter(Objects::nonNull)
                    .map(token -> token.isBlank() ? configKey : token)
                    .toArray(String[]::new);
        }
    }

    public String getConfigKey() {
        return configKey;
    }

    public static ClassWeaponType fromConfigKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        for (ClassWeaponType type : values()) {
            if (type.configKey.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static ClassWeaponType fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String normalized = itemId.trim().toUpperCase(Locale.ROOT);
        for (ClassWeaponType type : values()) {
            if (type.matches(normalized)) {
                return type;
            }
        }
        return null;
    }

    private boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : matchTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (text.contains(token.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
