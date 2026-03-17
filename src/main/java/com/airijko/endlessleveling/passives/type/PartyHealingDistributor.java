package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.types.OverhealAugment;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Shared party healing distribution logic used by healer passives.
 */
public final class PartyHealingDistributor {

    public static final double DEFAULT_BASE_RADIUS_BLOCKS = 5.0D;
    public static final double DEFAULT_MANA_PER_RADIUS_BLOCK = 75.0D;
    public static final double DEFAULT_SELF_HEAL_EFFECTIVENESS = 1.0D;

    private PartyHealingDistributor() {
    }

    public static void applySplitHealingToWoundedParty(PlayerData sourcePlayerData,
            Ref<EntityStore> sourceRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap sourceStats,
            double healPool,
            double baseRadius,
            double manaPerBlock,
            double selfHealEffectiveness) {
        if (sourcePlayerData == null || !EntityRefUtil.isUsable(sourceRef) || commandBuffer == null
                || sourceStats == null
                || healPool <= 0.0D) {
            return;
        }

        TransformComponent sourceTransform = EntityRefUtil.tryGetComponent(commandBuffer,
                sourceRef,
                TransformComponent.getComponentType());
        if (sourceTransform == null || sourceTransform.getPosition() == null) {
            return;
        }

        EntityStatValue sourceMana = sourceStats.get(DefaultEntityStatTypes.getMana());
        double totalMana = sourceMana != null ? Math.max(0.0D, sourceMana.getMax()) : 0.0D;

        double radius = Math.max(0.0D, baseRadius);
        if (manaPerBlock > 0.0D) {
            radius += Math.floor(totalMana / manaPerBlock);
        }
        if (radius <= 0.0D) {
            return;
        }

        double clampedSelfEffectiveness = Math.max(0.0D, Math.min(1.0D, selfHealEffectiveness));

        UUID sourceUuid = sourcePlayerData.getUuid();
        PartyManager partyManager = resolvePartyManager();
        UUID sourcePartyLeader = resolvePartyLeader(partyManager, sourceUuid);

        List<PartyHealTarget> woundedTargets = new ArrayList<>();
        HashSet<Integer> visitedEntityIds = new HashSet<>();
        for (Ref<EntityStore> targetRef : TargetUtil.getAllEntitiesInSphere(
                sourceTransform.getPosition(),
                radius,
                commandBuffer)) {
            if (!EntityRefUtil.isUsable(targetRef)) {
                continue;
            }
            if (!visitedEntityIds.add(targetRef.getIndex())) {
                continue;
            }

            PlayerRef targetPlayer = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    PlayerRef.getComponentType());
            if (targetPlayer == null || !targetPlayer.isValid() || targetPlayer.getUuid() == null) {
                continue;
            }

            UUID targetUuid = targetPlayer.getUuid();
            boolean selfTarget = sourceUuid != null && sourceUuid.equals(targetUuid);

            if (!isSamePartyTarget(sourceUuid, sourcePartyLeader, targetUuid, partyManager)) {
                continue;
            }

            EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    EntityStatMap.getComponentType());
            if (!isWounded(targetStats)) {
                continue;
            }

            woundedTargets.add(new PartyHealTarget(targetStats, selfTarget));
        }

        if (woundedTargets.isEmpty()) {
            return;
        }

        double splitHeal = healPool / woundedTargets.size();
        if (splitHeal <= 0.0D) {
            return;
        }

        for (PartyHealTarget target : woundedTargets) {
            if (target == null || target.statMap() == null) {
                continue;
            }

            double effectiveHeal = target.selfTarget()
                    ? splitHeal * clampedSelfEffectiveness
                    : splitHeal;
            if (effectiveHeal <= 0.0D) {
                continue;
            }
            applyHeal(target.statMap(), effectiveHeal);
        }
    }

    private static void applyHeal(EntityStatMap statMap, double healAmount) {
        if (statMap == null || healAmount <= 0.0D) {
            return;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null || healthStat.getMax() <= 0f || healthStat.get() <= 0f) {
            return;
        }

        float current = healthStat.get();
        float max = healthStat.getMax();
        if (current >= max) {
            OverhealAugment.recordOverhealOverflow(statMap, healAmount);
            return;
        }

        float applied = (float) Math.min(max - current, healAmount);
        double overflow = Math.max(0.0D, healAmount - applied);
        if (overflow > 0.0D) {
            OverhealAugment.recordOverhealOverflow(statMap, overflow);
        }
        if (applied <= 0f) {
            return;
        }

        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), current + applied);
    }

    private static boolean isWounded(EntityStatMap statMap) {
        if (statMap == null) {
            return false;
        }
        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null || healthStat.getMax() <= 0f || healthStat.get() <= 0f) {
            return false;
        }
        return healthStat.get() < healthStat.getMax();
    }

    private static boolean isSamePartyTarget(UUID sourceUuid,
            UUID sourcePartyLeader,
            UUID targetUuid,
            PartyManager partyManager) {
        if (targetUuid == null) {
            return false;
        }
        if (sourceUuid != null && sourceUuid.equals(targetUuid)) {
            return true;
        }
        if (sourceUuid == null || partyManager == null || !partyManager.isAvailable()) {
            return false;
        }

        UUID effectiveSourceLeader = sourcePartyLeader != null
                ? sourcePartyLeader
                : resolvePartyLeader(partyManager, sourceUuid);
        if (effectiveSourceLeader == null) {
            return false;
        }

        UUID targetLeader = resolvePartyLeader(partyManager, targetUuid);
        return targetLeader != null && targetLeader.equals(effectiveSourceLeader);
    }

    private static UUID resolvePartyLeader(PartyManager partyManager, UUID playerUuid) {
        if (partyManager == null || !partyManager.isAvailable() || playerUuid == null) {
            return null;
        }
        if (!partyManager.isInParty(playerUuid)) {
            return null;
        }
        return partyManager.getPartyLeader(playerUuid);
    }

    private static PartyManager resolvePartyManager() {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        return plugin != null ? plugin.getPartyManager() : null;
    }

    private record PartyHealTarget(EntityStatMap statMap, boolean selfTarget) {
    }
}
