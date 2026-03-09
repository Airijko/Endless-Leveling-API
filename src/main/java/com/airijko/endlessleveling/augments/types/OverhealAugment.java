package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;

import java.util.Map;

public final class OverhealAugment extends YamlAugment
        implements AugmentHooks.OnHitAugment, AugmentHooks.OnDamageTakenAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "overheal";

    private final double maxBonusHealthPercent;
    private final long durationMillis;
    private final double lifeStealPercent;

    public OverhealAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> shield = AugmentValueReader.getMap(passives, "overheal_shield");
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");

        this.maxBonusHealthPercent = Math.max(0.0D,
                AugmentValueReader.getDouble(shield, "max_bonus_health_percent", 0.0D));
        this.durationMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(shield, "duration", 0.0D));
        this.lifeStealPercent = normalizePercentPoints(
                AugmentValueReader.getNestedDouble(buffs, 0.0D, "lifesteal", "value"));
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null || context.getAttackerStats() == null || context.getRuntimeState() == null) {
            return context != null ? context.getDamage() : 0f;
        }

        double healAttempt = context.getDamage() * (lifeStealPercent / 100.0D);
        if (healAttempt <= 0.0D) {
            return context.getDamage();
        }

        float applied = AugmentUtils.heal(context.getAttackerStats(), healAttempt);
        double excess = Math.max(0.0D, healAttempt - applied);
        if (excess <= 0.0D || maxBonusHealthPercent <= 0.0D) {
            return context.getDamage();
        }

        float maxHealth = AugmentUtils.getMaxHealth(context.getAttackerStats());
        if (maxHealth <= 0f) {
            return context.getDamage();
        }

        AugmentState state = context.getRuntimeState().getState(ID);
        double maxShield = maxHealth * maxBonusHealthPercent;
        state.setStoredValue(Math.min(maxShield, state.getStoredValue() + excess));
        state.setExpiresAt(System.currentTimeMillis() + durationMillis);
        return context.getDamage();
    }

    @Override
    public float onDamageTaken(AugmentHooks.DamageTakenContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return context != null ? context.getIncomingDamage() : 0f;
        }
        AugmentState state = context.getRuntimeState().getState(ID);
        if (state.getStoredValue() <= 0.0D) {
            return context.getIncomingDamage();
        }

        float incoming = Math.max(0f, context.getIncomingDamage());
        double absorbed = Math.min(incoming, state.getStoredValue());
        state.setStoredValue(Math.max(0.0D, state.getStoredValue() - absorbed));
        if (state.getStoredValue() <= 0.0001D) {
            state.clear();
        }
        return (float) Math.max(0.0D, incoming - absorbed);
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }
        AugmentState state = context.getRuntimeState().getState(ID);
        if (state.getStoredValue() <= 0.0D) {
            return;
        }

        long now = System.currentTimeMillis();
        if (state.getExpiresAt() <= 0L || now >= state.getExpiresAt()) {
            state.clear();
            return;
        }

        double deltaSeconds = Math.max(0.0D, context.getDeltaSeconds());
        if (deltaSeconds <= 0.0D || durationMillis <= 0L) {
            return;
        }

        double decay = state.getStoredValue() * Math.min(1.0D, (deltaSeconds * 1000.0D) / durationMillis);
        state.setStoredValue(Math.max(0.0D, state.getStoredValue() - decay));
        if (state.getStoredValue() <= 0.0001D) {
            state.clear();
        }
    }

    private double normalizePercentPoints(double configuredValue) {
        double abs = Math.abs(configuredValue);
        if (abs > 0.0D && abs <= 5.0D) {
            return configuredValue * 100.0D;
        }
        return configuredValue;
    }
}
