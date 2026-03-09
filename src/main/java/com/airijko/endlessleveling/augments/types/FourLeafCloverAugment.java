package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Map;

public final class FourLeafCloverAugment extends YamlAugment implements AugmentHooks.PassiveStatAugment {
    public static final String ID = "four_leaf_clover";

    private final double luckBonus;

    public FourLeafCloverAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");
        this.luckBonus = AugmentValueReader.getNestedDouble(buffs, 0.0D, "luck", "value");
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }
        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_discipline",
                SkillAttributeType.DISCIPLINE,
                luckBonus * 100.0D,
                0L);
    }
}
