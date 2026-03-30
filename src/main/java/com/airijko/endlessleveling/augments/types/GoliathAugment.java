package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Map;

public final class GoliathAugment extends Augment implements AugmentHooks.PassiveStatAugment {
    public static final String ID = "goliath";

    private final double maxHealthPercentBonus;
    private final double strengthPercentBonus;
    private final double sorceryPercentBonus;

    public GoliathAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");

        this.maxHealthPercentBonus = Math.max(0.0D,
                AugmentValueReader.getNestedDouble(buffs,
                        AugmentValueReader.getNestedDouble(buffs, 0.0D, "health_percent", "value"),
                        "max_health_percent",
                        "value"));
        this.strengthPercentBonus = Math.max(0.0D,
                AugmentValueReader.getNestedDouble(buffs, 0.0D, "strength", "value"));
        this.sorceryPercentBonus = Math.max(0.0D,
                AugmentValueReader.getNestedDouble(buffs, 0.0D, "sorcery", "value"));
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }

        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_str",
                SkillAttributeType.STRENGTH,
                strengthPercentBonus * 100.0D,
                0L);
        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_sorc",
                SkillAttributeType.SORCERY,
                sorceryPercentBonus * 100.0D,
                0L);
    }

    public double getMaxHealthPercentBonus() {
        return maxHealthPercentBonus;
    }
}
