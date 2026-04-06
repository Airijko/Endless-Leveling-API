package com.airijko.endlessleveling.api;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Callback invoked after MobLevelingSystem assigns a level to a mob for the
 * first time. External mods can register implementations via
 * {@link EndlessLevelingAPI#registerMobPostProcessListener} to apply additional
 * modifiers, effects, or tracking immediately after EL finishes processing.
 */
@FunctionalInterface
public interface MobPostProcessListener {

    /**
     * Called once per mob, immediately after EL assigns its level.
     *
     * @param ref           the entity reference
     * @param store         the entity store
     * @param commandBuffer the command buffer for component access
     * @param assignedLevel the level EL assigned to this mob
     */
    void onMobProcessed(Ref<EntityStore> ref, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, int assignedLevel);
}
