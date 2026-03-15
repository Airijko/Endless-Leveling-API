package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.types.OverhealAugment;
import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.managers.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.managers.PartyManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
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

import java.util.HashSet;
import java.util.UUID;

/**
 * Periodically pulses party healing around the source player.
 */
public final class PartyMendingAuraPassive {

    private static final long PULSE_INTERVAL_MILLIS = 1000L;
    private static final double BASE_RANGE_BLOCKS = 5.0D;
    private static final double MANA_PER_RANGE_BLOCK = 75.0D;
    private static final double HEAL_FROM_TOTAL_MANA = 0.10D;
    private static final double HEAL_FROM_TOTAL_STAMINA = 0.20D;

    private PartyMendingAuraPassive() {
    }

    public static void pulse(PlayerData playerData,
            Ref<EntityStore> sourceRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap sourceStats,
            ArchetypePassiveSnapshot archetypeSnapshot,
            PassiveRuntimeState runtimeState) {
        if (playerData == null || sourceRef == null || commandBuffer == null
                || sourceStats == null || archetypeSnapshot == null || runtimeState == null) {
            return;
        }

        double passiveValue = archetypeSnapshot.getValue(ArchetypePassiveType.HEALING_AURA);
        if (passiveValue <= 0.0D) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastPulse = runtimeState.getPartyMendingLastPulseMillis();
        if (lastPulse > 0L && now - lastPulse < PULSE_INTERVAL_MILLIS) {
            return;
        }

        EntityStatValue sourceHealth = sourceStats.get(DefaultEntityStatTypes.getHealth());
        if (sourceHealth == null || sourceHealth.getMax() <= 0f || sourceHealth.get() <= 0f) {
            return;
        }

        EntityStatValue sourceMana = sourceStats.get(DefaultEntityStatTypes.getMana());
        EntityStatValue sourceStamina = sourceStats.get(DefaultEntityStatTypes.getStamina());
        double totalMana = sourceMana != null ? Math.max(0.0D, sourceMana.getMax()) : 0.0D;
        double totalStamina = sourceStamina != null ? Math.max(0.0D, sourceStamina.getMax()) : 0.0D;

        double healPerPulse = (totalMana * HEAL_FROM_TOTAL_MANA) + (totalStamina * HEAL_FROM_TOTAL_STAMINA);
        if (healPerPulse <= 0.0D) {
            return;
        }

        double radius = BASE_RANGE_BLOCKS;
        if (MANA_PER_RANGE_BLOCK > 0.0D) {
            radius += Math.floor(totalMana / MANA_PER_RANGE_BLOCK);
        }
        if (radius <= 0.0D) {
            return;
        }

        TransformComponent sourceTransform = EntityRefUtil.tryGetComponent(commandBuffer,
                sourceRef,
                TransformComponent.getComponentType());
        if (sourceTransform == null || sourceTransform.getPosition() == null) {
            return;
        }

        UUID sourceUuid = playerData.getUuid();
        PartyManager partyManager = resolvePartyManager();
        UUID sourcePartyLeader = resolvePartyLeader(partyManager, sourceUuid);

        runtimeState.setPartyMendingLastPulseMillis(now);

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

            if (!isSamePartyTarget(sourceUuid, sourcePartyLeader, targetPlayer.getUuid(), partyManager)) {
                continue;
            }

            EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    EntityStatMap.getComponentType());
            applyHeal(targetStats, healPerPulse);
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
}
