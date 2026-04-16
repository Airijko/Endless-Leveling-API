package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Zeros the player's pending bank XP on death inside an Outlander Bridge
 * session and queues a rewards-panel pending on respawn if saved XP > 0.
 * Player is then returned via the existing death-exit route (unchanged).
 */
public final class OutlanderBridgePlayerDeathSystem extends DeathSystems.OnDeathSystem {

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull DeathComponent component,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef playerRef = EntityRefUtil.tryGetComponent(store, ref, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) return;
        UUID uuid = playerRef.getUuid();
        if (uuid == null) return;

        World world = store.getExternalData() == null ? null : store.getExternalData().getWorld();
        if (world == null) return;

        OutlanderBridgeWaveManager.get().handlePlayerDeath(uuid, world);
    }
}
