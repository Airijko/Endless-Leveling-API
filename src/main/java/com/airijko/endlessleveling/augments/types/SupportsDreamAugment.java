package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Map;

/**
 * Support's Dream (Legendary) — Triple total stamina and innately gain flat stamina.
 */
public final class SupportsDreamAugment extends Augment implements AugmentHooks.PassiveStatAugment {
    public static final String ID = "supports_dream";

    private final double innateStamina;
    private final double staminaMultiplier;

    public SupportsDreamAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");

        this.innateStamina = AugmentValueReader.getNestedDouble(buffs, 100.0D, "innate_stamina", "value");
        this.staminaMultiplier = AugmentValueReader.getNestedDouble(buffs, 3.0D, "stamina_multiplier", "value");
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }

        var runtime = context.getRuntimeState();

        // Clear our single bonus to resolve base stamina without self-influence.
        AugmentUtils.setAttributeBonus(runtime, ID, SkillAttributeType.STAMINA, 0.0D, 0L);

        double baseStamina = AugmentUtils.resolveStamina(context);

        // Target: (base + innate) * multiplier. Our bonus is the difference.
        double desiredTotal = (baseStamina + innateStamina) * staminaMultiplier;
        double bonus = desiredTotal - baseStamina;

        AugmentUtils.setAttributeBonus(runtime, ID, SkillAttributeType.STAMINA, bonus, 0L);
    }
}
