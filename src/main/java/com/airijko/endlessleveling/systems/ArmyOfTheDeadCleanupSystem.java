package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ArmyOfTheDeadCleanupSystem extends TickingSystem<EntityStore> {

    private static final float CLEANUP_INTERVAL_SECONDS = 3.0f;

    private float elapsed;

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return;
        }

        elapsed += deltaSeconds;
        if (elapsed < CLEANUP_INTERVAL_SECONDS) {
            return;
        }
        elapsed = 0f;

        ArmyOfTheDeadPassive.cleanupPersistentSummons(store);
    }
}