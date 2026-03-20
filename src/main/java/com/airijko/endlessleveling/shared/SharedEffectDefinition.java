package com.airijko.endlessleveling.shared;

import com.airijko.endlessleveling.enums.DamageLayer;
import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveStackingStyle;
import com.airijko.endlessleveling.enums.PassiveTier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Unified representation for augment and passive effects.
 */
public final class SharedEffectDefinition {

    private final String id;
    private final String name;
    private final SharedEffectKind kind;
    private final PassiveTier tier;
    private final PassiveCategory category;
    private final PassiveStackingStyle stackingStyle;
    private final DamageLayer damageLayer;
    private final String tag;
    private final Double value;
    private final Map<String, Object> payload;

    public SharedEffectDefinition(String id,
            String name,
            SharedEffectKind kind,
            PassiveTier tier,
            PassiveCategory category,
            PassiveStackingStyle stackingStyle,
            DamageLayer damageLayer,
            String tag,
            Double value,
            Map<String, Object> payload) {
        this.id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        this.name = name == null || name.isBlank() ? this.id : name;
        this.kind = kind == null ? SharedEffectKind.AUGMENT : kind;
        this.tier = tier == null ? PassiveTier.COMMON : tier;
        this.category = category == null ? PassiveCategory.PASSIVE_STAT : category;
        this.stackingStyle = stackingStyle == null ? PassiveStackingStyle.ADDITIVE : stackingStyle;
        this.damageLayer = damageLayer == null ? DamageLayer.BONUS : damageLayer;
        this.tag = tag == null || tag.isBlank() ? this.id : tag.trim().toLowerCase(Locale.ROOT);
        this.value = value;
        Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : new LinkedHashMap<>(payload);
        this.payload = Collections.unmodifiableMap(safePayload);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SharedEffectKind getKind() {
        return kind;
    }

    public PassiveTier getTier() {
        return tier;
    }

    public PassiveCategory getCategory() {
        return category;
    }

    public PassiveStackingStyle getStackingStyle() {
        return stackingStyle;
    }

    public DamageLayer getDamageLayer() {
        return damageLayer;
    }

    public String getTag() {
        return tag;
    }

    public Double getValue() {
        return value;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
