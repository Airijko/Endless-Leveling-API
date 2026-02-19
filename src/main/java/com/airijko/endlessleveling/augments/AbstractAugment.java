package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveTier;

import java.util.Map;
import java.util.Objects;

public abstract class AbstractAugment implements Augment {

    private final AugmentDefinition definition;

    protected AbstractAugment(AugmentDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    @Override
    public String getId() {
        return definition.getId();
    }

    @Override
    public String getName() {
        return definition.getName();
    }

    @Override
    public PassiveTier getTier() {
        return definition.getTier();
    }

    @Override
    public PassiveCategory getCategory() {
        return definition.getCategory();
    }

    @Override
    public String getDescription() {
        return definition.getDescription();
    }

    @Override
    public Map<String, Object> getPassives() {
        return definition.getPassives();
    }

    @Override
    public AugmentDefinition definition() {
        return definition;
    }
}
