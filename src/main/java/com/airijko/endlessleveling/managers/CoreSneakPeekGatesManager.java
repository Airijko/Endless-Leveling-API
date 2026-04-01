package com.airijko.endlessleveling.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Core fallback gates manager used when no addon manager is registered.
 * This implementation is intentionally visual-only and supports sneak-peek spawning.
 */
public final class CoreSneakPeekGatesManager {

    public static final CoreSneakPeekGatesManager INSTANCE = new CoreSneakPeekGatesManager();

    private CoreSneakPeekGatesManager() {
    }

    public boolean spawnSneakPeekNearPlayer(@Nonnull PlayerRef playerRef) {
        World world = resolvePlayerWorld(playerRef);
        Ref<EntityStore> sourceRef = playerRef.getReference();
        if (world == null || sourceRef == null || !sourceRef.isValid() || sourceRef.getStore() == null) {
            return false;
        }
        return PortalVisualManager.spawnSneakPeekNearPlayer(world, playerRef, sourceRef);
    }

    public boolean spawnVisualNearPlayer(@Nonnull PlayerRef playerRef, boolean sneakPeek) {
        World world = resolvePlayerWorld(playerRef);
        Ref<EntityStore> sourceRef = playerRef.getReference();
        if (world == null || sourceRef == null || !sourceRef.isValid() || sourceRef.getStore() == null) {
            return false;
        }
        return PortalVisualManager.spawnVisualNearPlayer(world, playerRef, sourceRef, sneakPeek);
    }

    private World resolvePlayerWorld(@Nonnull PlayerRef playerRef) {
        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            return null;
        }
        return universe.getWorld(worldUuid);
    }
}
