package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.enums.themes.AttributeTheme;

/**
 * Shared icon mapping for skill attributes and COMMON stat augments.
 */
public final class SkillAttributeIconResolver {
    private SkillAttributeIconResolver() {
    }

    public static String resolve(SkillAttributeType attributeType) {
        AttributeTheme theme = AttributeTheme.fromType(attributeType);
        if (theme == null) {
            return null;
        }
        String iconItemId = theme.iconItemId();
        return iconItemId == null || iconItemId.isBlank() ? null : iconItemId;
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