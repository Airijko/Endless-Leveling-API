package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Tracks deaths of Army of the Dead summons to apply per-summon cooldowns.
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
        ArmyOfTheDeadPassive.handleDeath(ref, store, commandBuffer);
    }
}