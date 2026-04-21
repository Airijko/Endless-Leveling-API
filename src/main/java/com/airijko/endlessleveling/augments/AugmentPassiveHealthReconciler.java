package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentRuntimeState;
import com.airijko.endlessleveling.augments.types.GoliathAugment;
import com.airijko.endlessleveling.augments.types.GraspOfTheUndyingAugment;
import com.airijko.endlessleveling.augments.types.RaidBossAugment;
import com.airijko.endlessleveling.augments.types.TankEngineAugment;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;

import java.util.List;

/**
 * Reconciles passive max-health augments from a single baseline to prevent
 * multiplicative compounding across passive ticks.
 */
public final class AugmentPassiveHealthReconciler {
    private static final double EPSILON = 0.0001D;

    private AugmentPassiveHealthReconciler() {
    }

    public static void reconcile(EntityStatMap statMap,
            List<Augment> augments,
            AugmentRuntimeState runtimeState) {
        if (statMap == null || augments == null || augments.isEmpty()) {
            return;
        }

        GoliathAugment goliath = null;
        RaidBossAugment raidBoss = null;
        TankEngineAugment tankEngine = null;
        GraspOfTheUndyingAugment grasp = null;

        for (Augment augment : augments) {
            if (augment instanceof GoliathAugment value) {
                goliath = value;
            } else if (augment instanceof RaidBossAugment value) {
                raidBoss = value;
            } else if (augment instanceof TankEngineAugment value) {
                tankEngine = value;
            } else if (augment instanceof GraspOfTheUndyingAugment value) {
                grasp = value;
            }
        }

        if (goliath == null && raidBoss == null && tankEngine == null && grasp == null) {
            return;
        }

        EntityStatValue before = statMap.get(DefaultEntityStatTypes.getHealth());
        if (before == null || before.getMax() <= 0.0f) {
            return;
        }
        float previousMax = before.getMax();
        float previousCurrent = before.get();

        clearModifier(statMap, GoliathAugment.ID);
        clearModifier(statMap, RaidBossAugment.ID);
        clearModifier(statMap, TankEngineAugment.ID);
        clearModifier(statMap, GraspOfTheUndyingAugment.ID);
        statMap.update();

        EntityStatValue baselineStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (baselineStat == null || baselineStat.getMax() <= 0.0f) {
            return;
        }

        double baselineMax = Math.max(1.0D, baselineStat.getMax());
        int tankStacks = resolveStacks(runtimeState, TankEngineAugment.ID,
                tankEngine != null ? tankEngine.getConfiguredMaxStacks() : 0);
        int graspStacks = resolveStacks(runtimeState, GraspOfTheUndyingAugment.ID,
                grasp != null ? grasp.getConfiguredMaxStacks() : 0);

        double graspFlatBonus = grasp != null
                ? Math.max(0.0D, grasp.getFlatHealthPerStack() * graspStacks)
                : 0.0D;
        double tankFlatBonus = tankEngine != null
                ? Math.max(0.0D, tankEngine.getFlatHealthPerStack() * tankStacks)
                : 0.0D;

        double goliathPercent = goliath != null ? Math.max(0.0D, goliath.getMaxHealthPercentBonus()) : 0.0D;
        double raidPercent = raidBoss != null ? Math.max(0.0D, raidBoss.getMaxHealthPercentBonus()) : 0.0D;
        double tankPercent = tankEngine != null
                ? Math.max(0.0D, tankEngine.getPercentMaxHealthPerStack() * tankStacks)
                : 0.0D;

        boolean excludeTankFlat = tankEngine != null && tankEngine.isExcludeFlatFromPercentScaling();
        double percentBaseline = Math.max(1.0D,
                baselineMax + graspFlatBonus + (excludeTankFlat ? 0.0D : tankFlatBonus));

        double goliathBonus = percentBaseline * goliathPercent;
        double raidBonus = percentBaseline * raidPercent;
        double tankPercentBonus = percentBaseline * tankPercent;
        double tankTotalBonus = tankFlatBonus + tankPercentBonus;

        putModifier(statMap, GraspOfTheUndyingAugment.ID, graspFlatBonus);
        putModifier(statMap, TankEngineAugment.ID, tankTotalBonus);
        putModifier(statMap, GoliathAugment.ID, goliathBonus);
        putModifier(statMap, RaidBossAugment.ID, raidBonus);
        statMap.update();

        EntityStatValue updated = statMap.get(DefaultEntityStatTypes.getHealth());
        if (updated == null || updated.getMax() <= 0.0f) {
            return;
        }

        float newMax = updated.getMax();
        float ratio = 1.0f;
        if (Float.isFinite(previousMax) && previousMax > 0.01f && Float.isFinite(previousCurrent)) {
            ratio = previousCurrent / previousMax;
        }
        if (!Float.isFinite(ratio)) {
            ratio = 1.0f;
        }

        // [SUMMON-FIX-4] Preserve HP=0 so dead entities are not resurrected
        // by the Math.max(1.0f, ...) floor.
        float adjustedCurrent = (previousCurrent <= 0.0f)
                ? 0.0f
                : Math.max(1.0f, Math.min(newMax, ratio * newMax));
        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), adjustedCurrent);
    }

    private static int resolveStacks(AugmentRuntimeState runtimeState, String augmentId, int maxStacks) {
        if (runtimeState == null || augmentId == null) {
            return 0;
        }
        int stacks = Math.max(0, runtimeState.getState(augmentId).getStacks());
        if (maxStacks > 0) {
            return Math.min(maxStacks, stacks);
        }
        return stacks;
    }

    private static void putModifier(EntityStatMap statMap, String augmentId, double amount) {
        if (statMap == null || augmentId == null || Math.abs(amount) <= EPSILON) {
            return;
        }
        statMap.putModifier(DefaultEntityStatTypes.getHealth(),
                prefixedKey(augmentId),
                new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, (float) amount));
    }

    private static void clearModifier(EntityStatMap statMap, String augmentId) {
        if (statMap == null || augmentId == null) {
            return;
        }
        statMap.removeModifier(DefaultEntityStatTypes.getHealth(), prefixedKey(augmentId));
        statMap.removeModifier(DefaultEntityStatTypes.getHealth(), legacyKey(augmentId));
    }

    private static String prefixedKey(String augmentId) {
        return "EL_" + augmentId + "_max_hp_bonus";
    }

    private static String legacyKey(String augmentId) {
        return augmentId + "_max_hp_bonus";
    }
}
