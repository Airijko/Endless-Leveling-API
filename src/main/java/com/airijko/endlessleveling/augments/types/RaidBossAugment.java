package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;

import java.util.Map;

public final class RaidBossAugment extends Augment
        implements AugmentHooks.PassiveStatAugment, AugmentHooks.OnHitAugment {
    public static final String ID = "raid_boss";

    private final double maxHealthPercentBonus;
    private final double bonusDamage;

    public RaidBossAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");

        this.maxHealthPercentBonus = Math.max(0.0D,
                AugmentValueReader.getNestedDouble(buffs,
                        AugmentValueReader.getNestedDouble(buffs, 0.0D, "health_percent", "value"),
                        "max_health_percent",
                        "value"));
        this.bonusDamage = AugmentUtils.normalizeConfiguredBonusMultiplier(
                AugmentValueReader.getNestedDouble(buffs, 0.0D, "bonus_damage", "value"));
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        // Max health application is centrally reconciled to keep all percent sources additive.
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }
        return AugmentUtils.applyAdditiveBonusFromBase(context.getDamage(), context.getBaseDamage(), bonusDamage);
    }

    public double getMaxHealthPercentBonus() {
        return maxHealthPercentBonus;
    }
}
