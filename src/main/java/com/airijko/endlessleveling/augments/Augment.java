package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveTier;

import java.util.Map;

public interface Augment {
    String getId();

    String getName();

    PassiveTier getTier();

    PassiveCategory getCategory();

    String getDescription();

    Map<String, Object> getPassives();

    AugmentDefinition definition();
}
