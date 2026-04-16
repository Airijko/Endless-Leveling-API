package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class OutlanderBridgeBlockDamageGuardSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    public OutlanderBridgeBlockDamageGuardSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull DamageBlockEvent event) {
        EntityStore ext = store.getExternalData();
        if (OutlanderBridgeWaveManager.get().isOutlanderBridgeWorld(ext.getWorld())) {
            event.setCancelled(true);
        }
    }
}
