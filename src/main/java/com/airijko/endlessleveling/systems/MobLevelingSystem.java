package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.EndlessLeveling;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.logger.HytaleLogger;
import com.airijko.endlessleveling.managers.MobLevelingManager;

/**
 * Applies mob-level stat scaling. For now mobs are hard-coded to level 10.
 * Scales Health and Damage (if a "Damage" stat exists) using the multipliers
 * provided by `LevelingManager`.
 */
public class MobLevelingSystem extends TickingSystem<EntityStore> {

    private static final Query<EntityStore> ENTITY_QUERY = Query.any();
    private final MobLevelingManager mobLevelingManager = EndlessLeveling.getInstance().getMobLevelingManager();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    public MobLevelingSystem() {
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown())
            return;

        if (mobLevelingManager == null || !mobLevelingManager.isMobLevelingEnabled())
            return;

        store.forEachChunk(ENTITY_QUERY,
                (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        mobLevelingManager.applyLeveling(ref, commandBuffer);
                    }
                });
    }
}
