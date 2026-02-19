package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveTier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable data parsed from an augment YAML.
 */
public final class AugmentDefinition {

    private final String id;
    private final String name;
    private final PassiveTier tier;
    private final PassiveCategory category;
    private final String description;
    private final Map<String, Object> passives;

    public AugmentDefinition(String id,
            String name,
            PassiveTier tier,
            PassiveCategory category,
            String description,
            Map<String, Object> passives) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? id : name;
        this.tier = tier == null ? PassiveTier.COMMON : tier;
        this.category = category == null ? PassiveCategory.PASSIVE_STAT : category;
        this.description = description == null ? "" : description;
        Map<String, Object> safe = passives == null ? Collections.emptyMap() : new LinkedHashMap<>(passives);
        this.passives = Collections.unmodifiableMap(safe);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PassiveTier getTier() {
        return tier;
    }

    public PassiveCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getPassives() {
        return passives;
    }
}
