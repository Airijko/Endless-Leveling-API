package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveTier;

import java.util.Map;
import java.util.Objects;

public class Augment {

    private final AugmentDefinition definition;

    protected Augment(AugmentDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public String getId() {
        return definition.getId();
    }

    public String getName() {
        return definition.getName();
    }

    public PassiveTier getTier() {
        return definition.getTier();
    }

    public PassiveCategory getCategory() {
        return definition.getCategory();
    }

    public String getDescription() {
        return definition.getDescription();
    }

    public Map<String, Object> getPassives() {
        return definition.getPassives();
    }

    public AugmentDefinition definition() {
        return definition;
    }
}
