package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.airijko.endlessleveling.ui.OutlanderBridgeWaveHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticks every ~250ms to push wave tracker state changes to the
 * OutlanderBridgeWaveHud on each player's client. Also sweeps stale HUDs
 * that belong to players not in an active outlander-bridge session world
 * (post-restart ghosts, wrong-world spawns).
 */
public final class OutlanderBridgeWaveHudRefreshSystem extends TickingSystem<EntityStore> {

    private static final long REFRESH_INTERVAL_NANOS = 250_000_000L;
    private static final ConcurrentHashMap<UUID, Long> LAST_REFRESH_NANOS = new ConcurrentHashMap<>();

    @Override
    public void tick(float deltaSeconds, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (store == null || store.isShutdown()) return;

        long now = System.nanoTime();
        OutlanderBridgeWaveManager mgr = OutlanderBridgeWaveManager.get();
        EntityStore ext = store.getExternalData();
        World storeWorld = ext != null ? ext.getWorld() : null;

        // Sweep: close HUDs belonging to players whose current world has
        // no active session. Covers post-restart ghosts + cross-world bleed.
        if (storeWorld != null && !mgr.hasActiveSession(storeWorld)) {
            for (PlayerRef pr : storeWorld.getPlayerRefs()) {
                if (pr == null || !pr.isValid()) continue;
                Ref<EntityStore> ref = pr.getReference();
                if (ref == null || !ref.isValid()) continue;
                if (ref.getStore() != store) continue;
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) continue;
                OutlanderBridgeWaveHud.close(player, pr);
            }
        }

        if (!OutlanderBridgeWaveHud.hasActiveHuds()) return;

        for (UUID uuid : OutlanderBridgeWaveHud.getActiveHudUuids()) {
            if (uuid == null) continue;
            if (!OutlanderBridgeWaveHud.isHudInStore(uuid, store)) continue;

            Long last = LAST_REFRESH_NANOS.get(uuid);
            if (last != null && now - last < REFRESH_INTERVAL_NANOS) continue;

            LAST_REFRESH_NANOS.put(uuid, now);
            OutlanderBridgeWaveHud.refreshHudNow(uuid, store);
        }
    }
}
