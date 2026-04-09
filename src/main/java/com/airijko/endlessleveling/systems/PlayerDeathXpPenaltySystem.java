package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Applies an XP penalty when a player dies.
 * The penalty is (current_xp_percent% of current XP) + (max_xp_percent% of max XP for the level).
 * The player cannot lose a level or prestige — XP is clamped at 0.
 */
public class PlayerDeathXpPenaltySystem extends DeathSystems.OnDeathSystem {

    private final LevelingManager levelingManager;

    public PlayerDeathXpPenaltySystem(LevelingManager levelingManager) {
        this.levelingManager = levelingManager;
    }

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
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        levelingManager.applyDeathXpPenalty(playerRef.getUuid());
    }
}
