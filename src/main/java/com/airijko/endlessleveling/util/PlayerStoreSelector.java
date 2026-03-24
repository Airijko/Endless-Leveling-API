package com.airijko.endlessleveling.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility helpers for building a player-only view for the currently ticking store.
 */
public final class PlayerStoreSelector {

    private PlayerStoreSelector() {
    }

    public static Map<Integer, PlayerRef> snapshotPlayersByEntityIndex(Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return Map.of();
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return Map.of();
        }

        Map<Integer, PlayerRef> playersByEntityIndex = new HashMap<>();
        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                continue;
            }

            if (playerEntityRef.getStore() != store) {
                continue;
            }

            playersByEntityIndex.put(playerEntityRef.getIndex(), playerRef);
        }

        return playersByEntityIndex;
    }
}
