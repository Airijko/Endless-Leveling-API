package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.ui.PlayerHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;

/**
 * Periodically refreshes the Endless Leveling HUD so dynamic values (e.g.,
 * local mob level)
 * stay in sync with player position without relying on manual triggers.
 */
public class HudRefreshSystem extends TickingSystem<EntityStore> {

    private static final float DIRTY_REFRESH_INTERVAL_SECONDS = 0.1f;
    private static final float FALLBACK_REFRESH_INTERVAL_SECONDS = 2.0f;
    private float timeSinceDirtyRefresh = 0f;
    private float timeSinceFallbackRefresh = 0f;

    public HudRefreshSystem() {
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return;
        }

        if (!PlayerHud.hasActiveHuds()) {
            return;
        }

        timeSinceDirtyRefresh += deltaSeconds;
        if (timeSinceDirtyRefresh >= DIRTY_REFRESH_INTERVAL_SECONDS) {
            timeSinceDirtyRefresh = 0f;
            refreshDirtyHudsForStore(store);
        }

        timeSinceFallbackRefresh += deltaSeconds;
        if (timeSinceFallbackRefresh >= FALLBACK_REFRESH_INTERVAL_SECONDS) {
            timeSinceFallbackRefresh = 0f;
            refreshAllHudsForStore(store);
        }
    }

    private void refreshDirtyHudsForStore(Store<EntityStore> store) {
        Set<UUID> dirtyHuds = PlayerHud.drainDirtyHudUuids();
        if (dirtyHuds.isEmpty()) {
            return;
        }

        for (UUID uuid : dirtyHuds) {
            if (uuid == null) {
                continue;
            }

            if (!PlayerHud.isActive(uuid)) {
                continue;
            }

            if (!PlayerHud.isHudInStore(uuid, store)) {
                continue;
            }

            PlayerHud.refreshHudNow(uuid);
        }
    }

    private void refreshAllHudsForStore(Store<EntityStore> store) {
        for (UUID uuid : PlayerHud.getActiveHudUuids()) {
            if (uuid == null || !PlayerHud.isActive(uuid)) {
                continue;
            }

            if (!PlayerHud.isHudInStore(uuid, store)) {
                continue;
            }

            PlayerHud.refreshHudNow(uuid);
        }
    }
}
