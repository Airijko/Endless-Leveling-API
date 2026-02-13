package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.ui.PlayerHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;
import java.util.UUID;

/**
 * Periodically refreshes the Endless Leveling HUD so dynamic values (e.g.,
 * local mob level)
 * stay in sync with player position without relying on manual triggers.
 */
public class HudRefreshSystem extends TickingSystem<EntityStore> {

    private static final float REFRESH_INTERVAL_SECONDS = 0.5f; // ~10 ticks at 20 TPS
    private float timeSinceLastRefresh = 0f;

    public HudRefreshSystem() {
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        timeSinceLastRefresh += deltaSeconds;
        if (timeSinceLastRefresh < REFRESH_INTERVAL_SECONDS) {
            return;
        }
        timeSinceLastRefresh = 0f;

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        Collection<PlayerRef> players = universe.getPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }

        for (PlayerRef playerRef : players) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            UUID uuid = playerRef.getUuid();
            if (uuid == null) {
                continue;
            }
            PlayerHud.refreshHud(uuid);
        }
    }
}
