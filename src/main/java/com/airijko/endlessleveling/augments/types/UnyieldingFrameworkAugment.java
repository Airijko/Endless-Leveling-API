package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Map;

/**
 * Unyielding Framework (Elite) — Gain +50% stamina.
 */
public final class UnyieldingFrameworkAugment extends Augment implements AugmentHooks.PassiveStatAugment {
    public static final String ID = "unyielding_framework";

    private final double staminaPercentBonus;

    public UnyieldingFrameworkAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");

        this.staminaPercentBonus = Math.max(0.0D,
                AugmentValueReader.getNestedDouble(buffs, 0.5D, "stamina_percent", "value"));
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }

        var runtime = context.getRuntimeState();

        // Clear first to avoid recursive self-bonus.
        AugmentUtils.setAttributeBonus(runtime,
                ID + "_stam",
                SkillAttributeType.STAMINA,
                0.0D,
                0L);

        double currentStamina = AugmentUtils.resolveStamina(context);
        double bonus = currentStamina * staminaPercentBonus;

        AugmentUtils.setAttributeBonus(runtime,
                ID + "_stam",
                SkillAttributeType.STAMINA,
                bonus,
                0L);
    }
}
