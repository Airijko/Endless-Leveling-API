package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;

import java.util.Map;

public final class NestingDollAugment extends YamlAugment
        implements AugmentHooks.OnLowHpAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "nesting_doll";
    private static final String MAX_HP_PENALTY_KEY = "EL_AUG_NESTING_DOLL_MAX_HP";

    private final int maxDeaths;
    private final double healthPenaltyPerDeath;
    private final long restoreAfterMillis;
    private final double regenPercentPerSecond;

    public NestingDollAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> deathStacks = AugmentValueReader.getMap(passives, "death_stacks");
        Map<String, Object> regeneration = AugmentValueReader.getMap(passives, "regeneration");

        this.maxDeaths = Math.max(1, AugmentValueReader.getInt(deathStacks, "max_deaths", 1));
        this.healthPenaltyPerDeath = Math.max(0.0D,
                Math.min(1.0D, AugmentValueReader.getDouble(deathStacks, "health_penalty_per_death", 0.0D)));
        this.restoreAfterMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(deathStacks, "restore_after_seconds", 0.0D));
        this.regenPercentPerSecond = Math.max(0.0D,
                AugmentValueReader.getDouble(regeneration, "max_health_regen_percent_per_second", 0.0D));
    }

    @Override
    public float onLowHp(AugmentHooks.DamageTakenContext context) {
        if (context == null || context.getRuntimeState() == null || context.getStatMap() == null
                || context.getIncomingDamage() <= 0f) {
            return context != null ? context.getIncomingDamage() : 0f;
        }

        EntityStatValue hp = context.getStatMap().get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.getMax() <= 0f || hp.get() <= 0f) {
            return context.getIncomingDamage();
        }

        // would push the player to 1 HP or lower.
        float projected = hp.get() - context.getIncomingDamage();
        if (projected > 1.0f) {
            return context.getIncomingDamage();
        }

        AugmentState state = context.getRuntimeState().getState(ID);
        if (state.getStacks() >= maxDeaths) {
            // Final stack is consumed: let the lethal hit through and reset state so the
            // next life starts clean.
            state.clear();
            clearMaxHealthPenalty(context.getStatMap());
            return context.getIncomingDamage();
        }

        if (state.getStoredValue() <= 0.0D) {
            state.setStoredValue(Math.max(1.0D, hp.getMax()));
        }

        int newStacks = state.getStacks() + 1;
        state.setStacks(newStacks);
        state.setLastProc(System.currentTimeMillis());
        state.setExpiresAt(System.currentTimeMillis() + restoreAfterMillis);

        // First prevent death at 1 HP, then restore to full of the reduced health pool.
        AugmentUtils.applyUnkillableThreshold(context.getStatMap(), context.getIncomingDamage(), 1.0f, 1.0f);
        float reducedMaxPool = applyMaxHealthPenalty(context.getStatMap(), state);
        context.getStatMap().setStatValue(DefaultEntityStatTypes.getHealth(), Math.max(1.0f, reducedMaxPool));

        var playerRef = AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getDefenderRef());
        if (playerRef != null && playerRef.isValid()) {
            double totalReductionPercent = Math.min(100.0D, Math.max(0.0D, newStacks * healthPenaltyPerDeath * 100.0D));
            String stackTier = newStacks + "/" + maxDeaths;
            String msg = String.format("%s triggered! Max HP reduced by %.0f%% (%s).", getName(),
                    totalReductionPercent, stackTier);
            if (newStacks >= maxDeaths) {
                msg = msg + " Next lethal hit will kill you.";
            }
            AugmentUtils.sendAugmentMessage(playerRef, msg);
        }
        return 0f;
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null || context.getStatMap() == null) {
            return;
        }

        AugmentState state = context.getRuntimeState().getState(ID);
        if (state.getStacks() > 0 && isRestoreWindowCompleteOutOfCombat(context, state)) {
            state.clear();
            clearMaxHealthPenalty(context.getStatMap());
        }

        EntityStatValue hp = context.getStatMap().get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.getMax() <= 0f) {
            return;
        }

        if (state.getStacks() > 0) {
            applyMaxHealthPenalty(context.getStatMap(), state);
        }

        if (regenPercentPerSecond > 0.0D && context.getDeltaSeconds() > 0.0D) {
            double heal = hp.getMax() * regenPercentPerSecond * context.getDeltaSeconds();
            AugmentUtils.heal(context.getStatMap(), heal);
        }

        if (state.getStacks() <= 0) {
            clearMaxHealthPenalty(context.getStatMap());
            return;
        }
    }

    private boolean isRestoreWindowCompleteOutOfCombat(AugmentHooks.PassiveStatContext context, AugmentState state) {
        if (state == null || state.getStacks() <= 0) {
            return false;
        }
        if (restoreAfterMillis <= 0L) {
            return true;
        }
        var playerData = context != null ? context.getPlayerData() : null;
        if (playerData == null || playerData.getUuid() == null) {
            long now = System.currentTimeMillis();
            return state.getExpiresAt() > 0L && now >= state.getExpiresAt();
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        var passiveManager = plugin != null ? plugin.getPassiveManager() : null;
        if (passiveManager != null) {
            return passiveManager.isOutOfCombat(playerData.getUuid(), restoreAfterMillis);
        }

        long now = System.currentTimeMillis();
        return state.getExpiresAt() > 0L && now >= state.getExpiresAt();
    }

    private float applyMaxHealthPenalty(com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap statMap,
            AugmentState state) {
        if (statMap == null || state == null || state.getStacks() <= 0) {
            return 1.0f;
        }
        double baselineMax = state.getStoredValue() > 0.0D ? state.getStoredValue() : 1.0D;
        float targetMax = (float) Math.max(1.0D, baselineMax * effectiveHealthRatio(state.getStacks()));
        float delta = (float) (targetMax - baselineMax);

        statMap.removeModifier(DefaultEntityStatTypes.getHealth(), MAX_HP_PENALTY_KEY);
        if (Math.abs(delta) > 0.0001f) {
            statMap.putModifier(DefaultEntityStatTypes.getHealth(),
                    MAX_HP_PENALTY_KEY,
                    new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, delta));
        }

        EntityStatValue updatedHp = statMap.get(DefaultEntityStatTypes.getHealth());
        if (updatedHp != null && updatedHp.get() > updatedHp.getMax()) {
            statMap.setStatValue(DefaultEntityStatTypes.getHealth(), Math.max(1.0f, updatedHp.getMax()));
        }
        statMap.update();
        return targetMax;
    }

    private void clearMaxHealthPenalty(com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap statMap) {
        if (statMap == null) {
            return;
        }
        statMap.removeModifier(DefaultEntityStatTypes.getHealth(), MAX_HP_PENALTY_KEY);
        statMap.update();
    }

    private double effectiveHealthRatio(int stacks) {
        return Math.max(0.05D, 1.0D - (healthPenaltyPerDeath * Math.max(0, stacks)));
    }
}
