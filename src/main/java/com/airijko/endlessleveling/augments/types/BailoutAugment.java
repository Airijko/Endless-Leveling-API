package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import java.util.Map;

public final class BailoutAugment extends YamlAugment
        implements AugmentHooks.OnLowHpAugment, AugmentHooks.PassiveStatAugment, AugmentHooks.OnKillAugment {
    public static final String ID = "bailout";

    private final long cooldownMillis;
    private final double reviveHealthPercent;
    private final long decayDurationMillis;
    private final boolean drainsToZero;
    private final boolean cancelOnKill;
    private final double emergencyHealMissingPercent;

    public BailoutAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> deathPrevention = AugmentValueReader.getMap(passives, "death_prevention");
        Map<String, Object> healthDecay = AugmentValueReader.getMap(passives, "health_decay");
        Map<String, Object> emergencyHeal = AugmentValueReader.getMap(passives, "emergency_heal");

        this.cooldownMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(deathPrevention, "cooldown", 0.0D));
        this.reviveHealthPercent = Math.max(0.0D,
                Math.min(1.0D, AugmentValueReader.getDouble(deathPrevention, "revive_health_percent", 0.0D)));

        this.decayDurationMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(healthDecay, "duration", 0.0D));
        this.drainsToZero = AugmentValueReader.getBoolean(healthDecay, "drains_to_zero", true);
        this.cancelOnKill = AugmentValueReader.getBoolean(healthDecay, "cancel_on_kill", true);

        this.emergencyHealMissingPercent = Math.max(0.0D,
                Math.min(1.0D, AugmentValueReader.getDouble(emergencyHeal, "missing_health_percent", 0.0D)));
    }

    @Override
    public float onLowHp(AugmentHooks.DamageTakenContext context) {
        if (context == null || context.getIncomingDamage() <= 0f) {
            return context != null ? context.getIncomingDamage() : 0f;
        }
        EntityStatMap statMap = context.getStatMap();
        EntityStatValue hp = statMap == null ? null : statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.getMax() <= 0f || hp.get() <= 0f) {
            return context.getIncomingDamage();
        }

        // 1 HP or lower.
        float projectedHp = hp.get() - context.getIncomingDamage();
        if (projectedHp > 1.0f) {
            return context.getIncomingDamage();
        }

        if (!AugmentUtils.consumeCooldown(context.getRuntimeState(), ID, getName(), cooldownMillis)) {
            return context.getIncomingDamage();
        }

        AugmentUtils.applyUnkillableThreshold(statMap, context.getIncomingDamage(), 1.0f, 1.0f);
        float revivedHealth = (float) (hp.getMax() * reviveHealthPercent);
        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), Math.max(1.0f, revivedHealth));

        AugmentState state = context.getRuntimeState() != null ? context.getRuntimeState().getState(ID) : null;
        if (state != null) {
            if (drainsToZero && decayDurationMillis > 0L) {
                state.setStacks(1);
                state.setStoredValue(Math.max(1.0D, revivedHealth));
                state.setExpiresAt(System.currentTimeMillis() + decayDurationMillis);
            } else {
                state.clear();
            }
        }

        return 0f;
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null || context.getStatMap() == null) {
            return;
        }
        AugmentState state = context.getRuntimeState().getState(ID);
        if (state.getStacks() <= 0 || state.getStoredValue() <= 0.0D || state.getExpiresAt() <= 0L) {
            return;
        }

        EntityStatValue hp = context.getStatMap().get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.get() <= 0f) {
            state.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long expiresAt = state.getExpiresAt();
        if (now >= expiresAt) {
            float drained = Math.min(hp.get(), (float) state.getStoredValue());
            context.getStatMap().setStatValue(DefaultEntityStatTypes.getHealth(), Math.max(0f, hp.get() - drained));
            state.clear();
            return;
        }

        double deltaSeconds = Math.max(0.0D, context.getDeltaSeconds());
        if (deltaSeconds <= 0.0D) {
            return;
        }

        long remainingMillis = Math.max(1L, expiresAt - now);
        double tickMillis = deltaSeconds * 1000.0D;
        double plannedDrain = state.getStoredValue() * Math.min(1.0D, tickMillis / remainingMillis);
        float currentHp = hp.get();
        float appliedDrain = (float) Math.min(currentHp, plannedDrain);
        if (appliedDrain <= 0f) {
            return;
        }

        context.getStatMap().setStatValue(DefaultEntityStatTypes.getHealth(), Math.max(0f, currentHp - appliedDrain));
        state.setStoredValue(Math.max(0.0D, state.getStoredValue() - appliedDrain));
        if (state.getStoredValue() <= 0.0001D) {
            state.clear();
        }
    }

    @Override
    public void onKill(AugmentHooks.KillContext context) {
        if (!cancelOnKill || context == null || context.getRuntimeState() == null) {
            return;
        }
        AugmentState state = context.getRuntimeState().getState(ID);
        if (state.getStacks() <= 0) {
            return;
        }

        state.clear();

        if (emergencyHealMissingPercent <= 0.0D || context.getCommandBuffer() == null
                || context.getKillerRef() == null) {
            return;
        }

        EntityStatMap killerStats = context.getCommandBuffer().getComponent(context.getKillerRef(),
                EntityStatMap.getComponentType());
        EntityStatValue hp = killerStats == null ? null : killerStats.get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.getMax() <= 0f) {
            return;
        }

        double missing = Math.max(0.0D, hp.getMax() - hp.get());
        if (missing <= 0.0D) {
            return;
        }
        AugmentUtils.heal(killerStats, missing * emergencyHealMissingPercent);
    }
}
