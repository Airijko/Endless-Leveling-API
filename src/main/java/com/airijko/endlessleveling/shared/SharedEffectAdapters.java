package com.airijko.endlessleveling.shared;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.enums.DamageLayer;
import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveStackingStyle;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.enums.PassiveType;
import com.airijko.endlessleveling.races.RacePassiveDefinition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts existing augment and passive definitions into the shared effect model.
 */
public final class SharedEffectAdapters {

    private SharedEffectAdapters() {
    }

    public static SharedEffectDefinition fromAugment(AugmentDefinition definition) {
        String id = normalizeId(definition != null ? definition.getId() : "augment:unknown");
        String name = definition != null ? definition.getName() : id;
        PassiveTier tier = definition != null ? definition.getTier() : PassiveTier.COMMON;
        PassiveCategory category = definition != null ? definition.getCategory() : PassiveCategory.PASSIVE_STAT;
        PassiveStackingStyle stackingStyle = tier != null && tier.isUniqueTier()
                ? PassiveStackingStyle.UNIQUE
                : PassiveStackingStyle.ADDITIVE;
        Map<String, Object> payload = definition != null ? definition.getPassives() : Map.of();

        return new SharedEffectDefinition(
                id,
                name,
                SharedEffectKind.AUGMENT,
                tier,
                category,
                stackingStyle,
                DamageLayer.BONUS,
                id,
                null,
                payload);
    }

    public static SharedEffectDefinition fromArchetype(RacePassiveDefinition definition) {
        if (definition == null) {
            return new SharedEffectDefinition(
                    "archetype:unknown",
                    "Unknown Archetype Passive",
                    SharedEffectKind.ARCHETYPE_PASSIVE,
                    PassiveTier.COMMON,
                    PassiveCategory.PASSIVE_STAT,
                    PassiveStackingStyle.ADDITIVE,
                    DamageLayer.BONUS,
                    "archetype:unknown",
                    0.0D,
                    Map.of());
        }

        ArchetypePassiveType type = definition.type();
        String typeKey = type == null ? "unknown" : type.getConfigKey().toLowerCase(Locale.ROOT);
        String tag = normalizeTag(definition.tag(), typeKey);
        String id = "archetype:" + typeKey + ":" + tag;

        Map<String, Object> payload = new LinkedHashMap<>(definition.properties());
        if (!definition.classValues().isEmpty()) {
            payload.put("class_values", definition.classValues());
        }

        return new SharedEffectDefinition(
                id,
                prettyName(typeKey),
                SharedEffectKind.ARCHETYPE_PASSIVE,
                definition.tier(),
                definition.category(),
                definition.effectiveStackingStyle(),
                definition.damageLayer() == null ? DamageLayer.BONUS : definition.damageLayer(),
                tag,
                definition.value(),
                payload);
    }

    public static SharedEffectDefinition fromInnate(PassiveType type,
            int level,
            double value,
            boolean unlocked) {
        String typeKey = type == null ? "unknown" : type.getConfigKey().toLowerCase(Locale.ROOT);
        Map<String, Object> payload = Map.of(
                "level", Math.max(0, level),
                "unlocked", unlocked);

        return new SharedEffectDefinition(
                "innate:" + typeKey,
                type == null ? "Unknown Passive" : type.getDisplayName(),
                SharedEffectKind.INNATE_PASSIVE,
                PassiveTier.COMMON,
                PassiveCategory.PASSIVE_STAT,
                PassiveStackingStyle.ADDITIVE,
                DamageLayer.BONUS,
                typeKey,
                unlocked ? Math.max(0.0D, value) : 0.0D,
                payload);
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTag(String rawTag, String fallback) {
        if (rawTag == null || rawTag.isBlank()) {
            return fallback;
        }
        return rawTag.trim().toLowerCase(Locale.ROOT);
    }

    private static String prettyName(String snakeCase) {
        if (snakeCase == null || snakeCase.isBlank()) {
            return "Unknown";
        }
        String[] words = snakeCase.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.isEmpty() ? "Unknown" : sb.toString();
    }
}
