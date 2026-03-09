package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Map;

public final class TitansWisdomAugment extends YamlAugment implements AugmentHooks.PassiveStatAugment {
    public static final String ID = "titans_wisdom";

    private final double healthToSorceryConversionPercent;

    public TitansWisdomAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> healthToSorcery = AugmentValueReader.getMap(passives, "health_to_sorcery");
        this.healthToSorceryConversionPercent = Math.max(0.0D,
                AugmentValueReader.getDouble(healthToSorcery, "conversion_percent", 0.0D));
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null || context.getStatMap() == null) {
            return;
        }

        double maxHealth = AugmentUtils.getMaxHealth(context.getStatMap());
        double sorceryBonus = maxHealth * healthToSorceryConversionPercent;
        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_sorc",
                SkillAttributeType.SORCERY,
                sorceryBonus,
                0L);
    }
}
