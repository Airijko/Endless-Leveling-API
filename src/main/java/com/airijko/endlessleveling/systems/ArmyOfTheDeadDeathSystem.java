package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.MobAugmentExecutor;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Tracks deaths of Army of the Dead summons to apply per-summon cooldowns,
 * and dispatches OnKill augment hooks when a managed summon secures a kill.
 */
public class ArmyOfTheDeadDeathSystem extends DeathSystems.OnDeathSystem {

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Fire OnKill augments mirrored from the owner when a summon scores the kill.
        dispatchSummonKillAugments(ref, component, store, commandBuffer);

        // Then handle the dying entity itself (if it is a managed summon).
        ArmyOfTheDeadPassive.handleDeath(ref, store, commandBuffer);

        // If a player owner just died, despawn their army. The periodic 3s
        // cleanup sweep treats only offline/invalid PlayerRefs as "disconnected",
        // so a dead-but-still-valid player would keep summons alive otherwise.
        PlayerRef victimPlayer = EntityRefUtil.tryGetComponent(commandBuffer, ref, PlayerRef.getComponentType());
        if (victimPlayer == null) {
            victimPlayer = EntityRefUtil.tryGetComponent(store, ref, PlayerRef.getComponentType());
        }
        if (victimPlayer != null && victimPlayer.isValid()) {
            ArmyOfTheDeadPassive.cleanupOwnerSummonsOnDeath(victimPlayer.getUuid());
        }
    }

    private void dispatchSummonKillAugments(@Nonnull Ref<EntityStore> victimRef,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        var deathInfo = component.getDeathInfo();
        if (deathInfo == null || !(deathInfo.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> killerRef = entitySource.getRef();
        if (!EntityRefUtil.isUsable(killerRef)) {
            return;
        }

        if (!ArmyOfTheDeadPassive.isManagedSummon(killerRef, store, commandBuffer)) {
            return;
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return;
        }
        MobAugmentExecutor executor = plugin.getMobAugmentExecutor();
        if (executor == null) {
            return;
        }

        UUID killerUuid = resolveUuid(killerRef, store, commandBuffer);
        if (killerUuid == null) {
            return;
        }

        // Ensure the summon's mirrored augment set is current before dispatching
        // the kill hook. A summon that spawned before the owner selected augments
        // would otherwise miss OnKill effects until its next combat hit.
        ArmyOfTheDeadPassive.ensureSummonAugmentsInSync(killerUuid, store);

        if (!executor.hasMobAugments(killerUuid)) {
            return;
        }

        EntityStatMap victimStats = EntityRefUtil.tryGetComponent(commandBuffer, victimRef,
                EntityStatMap.getComponentType());
        if (victimStats == null) {
            victimStats = EntityRefUtil.tryGetComponent(store, victimRef, EntityStatMap.getComponentType());
        }

        // Resolve the summoner's player data and skill manager so OnKill augments
        // (e.g. SoulReaver, BloodFrenzy) can scale from the owner's stats rather
        // than their default null fallbacks.
        PlayerData ownerPlayerData = null;
        SkillManager ownerSkillManager = plugin.getSkillManager();
        UUID ownerUuid = ArmyOfTheDeadPassive.getManagedSummonOwnerUuid(killerRef, store, commandBuffer);
        if (ownerUuid != null) {
            PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
            if (playerDataManager != null) {
                ownerPlayerData = playerDataManager.get(ownerUuid);
            }
        }

        executor.handleKill(killerUuid, killerRef, victimRef, commandBuffer, victimStats,
                ownerPlayerData, ownerSkillManager);
    }

    private UUID resolveUuid(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent uuidComponent = EntityRefUtil.tryGetComponent(commandBuffer, ref,
                UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            uuidComponent = EntityRefUtil.tryGetComponent(store, ref, UUIDComponent.getComponentType());
        }
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }
}