package com.airijko.endlessleveling.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a bonk sound when player attacks another entity with 10% chance.
 */
public final class PlayerAttackBonkSoundSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String BONK_SOUND_ID = "SFX_EL_Attack_Bonk";
    private static final double BONK_TRIGGER_CHANCE = 0.10; // 10% chance

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid() || attackerRef.getStore() == null) {
            return;
        }

        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }

        // 10% chance to play bonk sound
        if (ThreadLocalRandom.current().nextDouble() < BONK_TRIGGER_CHANCE) {
            playSoundToAttacker(attackerRef);
        }
    }

    private static void playSoundToAttacker(@Nonnull Ref<EntityStore> attackerRef) {
        int soundIndex = resolveSoundIndex(BONK_SOUND_ID);
        if (soundIndex == 0) {
            return;
        }

        try {
            SoundUtil.playSoundEvent2d(attackerRef, soundIndex, SoundCategory.SFX, attackerRef.getStore());
        } catch (Exception ex) {
            LOGGER.atWarning().log("[ELAttackBonk] Failed to play bonk sound: %s", ex.getMessage());
        }
    }

    private static int resolveSoundIndex(String soundEventId) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? 0 : index;
    }
}
