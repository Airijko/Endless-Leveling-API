package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.ui.PlayerHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

import java.util.Set;
import java.util.UUID;

/**
 * Periodically refreshes the Endless Leveling HUD so dynamic values stay in
 * sync without relying on manual triggers.
 *
 * Two cadences:
 *  - Dirty refresh every 0.1s drains any HUD explicitly marked dirty; pushes
 *    the full HUD state (level, xp, race, class, mob level, overlays).
 *  - Overlay refresh every 0.5s (10 ticks @ 20 TPS) pushes only the augment/
 *    passive overlay (duration bar, shield bar, stacking augment icons +
 *    progress + stack counts) so time-driven overlay state animates smoothly
 *    while the rest of the HUD stays event-driven. Diff guards in
 *    pushHudState drop unchanged properties, so steady-state cost is a
 *    no-op.
 */
public class HudRefreshSystem extends TickingSystem<EntityStore> {

    private static final float DIRTY_REFRESH_INTERVAL_SECONDS = 0.1f;
    private static final float OVERLAY_REFRESH_INTERVAL_SECONDS = 0.5f;
    private static final int MAX_DIRTY_REFRESHES_PER_PASS = 48;
    private static final int MAX_OVERLAY_REFRESHES_PER_PASS = 48;

    private float timeSinceDirtyRefresh = 0f;
    private float timeSinceOverlayRefresh = 0f;

    public HudRefreshSystem() {
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, @Nonnull Store<EntityStore> store) {
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

        timeSinceOverlayRefresh += deltaSeconds;
        if (timeSinceOverlayRefresh >= OVERLAY_REFRESH_INTERVAL_SECONDS) {
            timeSinceOverlayRefresh = 0f;
            refreshOverlaysForStore(store);
        }
    }

    private void refreshDirtyHudsForStore(Store<EntityStore> store) {
        Set<UUID> dirtyHuds = PlayerHud.snapshotDirtyHudUuids();
        if (dirtyHuds.isEmpty()) {
            return;
        }

        int refreshed = 0;

        for (UUID uuid : dirtyHuds) {
            if (uuid == null) {
                continue;
            }

            if (!PlayerHud.isActive(uuid)) {
                PlayerHud.clearDirty(uuid);
                continue;
            }

            if (!PlayerHud.isHudInStore(uuid, store)) {
                continue;
            }

            PlayerHud.refreshHudNow(uuid);
            PlayerHud.clearDirty(uuid);
            refreshed++;
            if (refreshed >= MAX_DIRTY_REFRESHES_PER_PASS) {
                break;
            }
        }
    }

    private void refreshOverlaysForStore(Store<EntityStore> store) {
        int refreshed = 0;
        Set<UUID> activeHudUuids = PlayerHud.getActiveHudUuids();

        for (UUID uuid : activeHudUuids) {
            if (uuid == null || !PlayerHud.isActive(uuid)) {
                continue;
            }

            if (!PlayerHud.isHudInStore(uuid, store)) {
                continue;
            }

            PlayerHud.refreshAugmentOverlayNow(uuid);
            refreshed++;
            if (refreshed >= MAX_OVERLAY_REFRESHES_PER_PASS) {
                break;
            }
        }
    }
}
