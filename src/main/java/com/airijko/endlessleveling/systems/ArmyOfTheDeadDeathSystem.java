package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.MobAugmentExecutor;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
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
        if (killerUuid == null || !executor.hasMobAugments(killerUuid)) {
            return;
        }

        EntityStatMap victimStats = EntityRefUtil.tryGetComponent(commandBuffer, victimRef,
                EntityStatMap.getComponentType());
        if (victimStats == null) {
            victimStats = EntityRefUtil.tryGetComponent(store, victimRef, EntityStatMap.getComponentType());
        }

        executor.handleKill(killerUuid, killerRef, victimRef, commandBuffer, victimStats);
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