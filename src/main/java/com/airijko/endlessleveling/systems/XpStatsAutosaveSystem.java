package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.xpstats.XpStatsManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Periodically saves dirty XP stats entries to disk (every 5 minutes).
 * Only entries marked dirty since the last save are written.
 */
public class XpStatsAutosaveSystem extends TickingSystem<EntityStore> {

    private static final float AUTOSAVE_INTERVAL_SECONDS = 300.0f; // 5 minutes
    private float timeSinceLastSave = 0f;
    private final XpStatsManager xpStatsManager;

    public XpStatsAutosaveSystem(XpStatsManager xpStatsManager) {
        this.xpStatsManager = xpStatsManager;
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, @Nonnull Store<EntityStore> store) {
        if (xpStatsManager == null || store == null || store.isShutdown()) return;
        timeSinceLastSave += deltaSeconds;
        if (timeSinceLastSave < AUTOSAVE_INTERVAL_SECONDS) return;
        timeSinceLastSave = 0f;
        xpStatsManager.saveAll();
    }
}
