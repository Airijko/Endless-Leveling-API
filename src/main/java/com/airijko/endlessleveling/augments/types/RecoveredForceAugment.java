package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Map;

/**
 * Recovered Force (Elite) — Gain bonus damage based on total stamina and innately gain flat stamina.
 */
public final class RecoveredForceAugment extends Augment
        implements AugmentHooks.PassiveStatAugment, AugmentHooks.OnHitAugment {
    public static final String ID = "recovered_force";

    private final double innateStamina;
    private final double staminaDamageRatio;

    public RecoveredForceAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");

        this.innateStamina = AugmentValueReader.getNestedDouble(buffs, 50.0D, "innate_stamina", "value");
        this.staminaDamageRatio = Math.max(0.0D,
                AugmentValueReader.getNestedDouble(buffs, 0.10D, "stamina_damage_ratio", "value"));
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }

        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_innate",
                SkillAttributeType.STAMINA,
                innateStamina,
                0L);
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }

        float damage = context.getDamage();
        if (damage <= 0f) {
            return damage;
        }

        double stamina = AugmentUtils.resolveStamina(context);
        if (stamina <= 0.0D) {
            return damage;
        }

        double bonusDamage = stamina * staminaDamageRatio;
        return damage + (float) bonusDamage;
    }
}
