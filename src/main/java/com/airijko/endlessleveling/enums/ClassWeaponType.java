package com.airijko.endlessleveling.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Supported weapon categories for character class damage modifiers.
 */
public enum ClassWeaponType {

    SWORD("SWORD", "SWORD", "BLADE", "SABER", "LONGSWORD", "RAPIER"),
    AXE("AXE", "AXE", "HATCHET", "BATTLEAXE", "TOMAHAWK"),
    BOW("BOW", "BOW", "SHORTBOW", "LONGBOW"),
    STAFF("STAFF", "STAFF", "ROD"),
    DAGGER("DAGGER", "DAGGER", "KNIFE", "DIRK"),
    MACE("MACE", "MACE", "HAMMER", "CLUB", "WARHAMMER"),
    SPEAR("SPEAR", "SPEAR", "HALBERD", "POLEARM", "TRIDENT", "GLAIVE"),
    CROSSBOW("CROSSBOW", "CROSSBOW"),
    WAND("WAND", "WAND", "ORB", "FOCUS"),
    UNARMED("UNARMED", "UNARMED", "FIST"),
    GENERIC("GENERIC", "GENERIC");

    private static final Set<String> IGNORED_TOKENS = Set.of(
            "WEAPON",
            "ITEM",
            "CUSTOM",
            "MOD",
            "HAND",
            "MAINHAND",
            "OFFHAND",
            "TOOLS",
            "TOOL",
            "TEMPLATE");

    private final String configKey;
    private final String[] matchTokens;

    ClassWeaponType(String configKey, String... tokens) {
        this.configKey = configKey;
        String[] source = tokens == null || tokens.length == 0 ? new String[] { configKey } : tokens;
        this.matchTokens = Arrays.stream(source)
                .filter(Objects::nonNull)
                .map(token -> token.isBlank() ? configKey : token)
                .map(token -> token.trim().toUpperCase(Locale.ROOT))
                .toArray(String[]::new);
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
        String normalized = normalizeIdentifier(itemId);
        String[] tokens = normalized.split("[^A-Z0-9]+");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            ClassWeaponType byToken = fromToken(token);
            if (byToken != null) {
                return byToken;
            }
        }
        for (ClassWeaponType type : values()) {
            if (type.matches(normalized)) {
                return type;
            }
        }
        return null;
    }

    private static ClassWeaponType fromToken(String token) {
        if (token == null) {
            return null;
        }
        String upper = token.toUpperCase(Locale.ROOT);
        if (upper.isBlank() || IGNORED_TOKENS.contains(upper)) {
            return null;
        }
        for (ClassWeaponType type : values()) {
            if (type.matchesTokenExact(upper)) {
                return type;
            }
        }
        return null;
    }

    private static String normalizeIdentifier(String raw) {
        String trimmed = raw.trim();
        int namespaceIndex = trimmed.lastIndexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < trimmed.length() - 1) {
            trimmed = trimmed.substring(namespaceIndex + 1);
        }
        return trimmed.replace('-', '_')
                .replace('.', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : matchTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTokenExact(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        for (String alias : matchTokens) {
            if (alias.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
