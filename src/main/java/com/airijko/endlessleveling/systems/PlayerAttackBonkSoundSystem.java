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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a meme attack sound when player attacks another entity with 20% chance.
 */
public final class PlayerAttackBonkSoundSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final List<String> ATTACK_SOUND_IDS = List.of(
            "SFX_EL_Attack_Bonk",
            "SFX_EL_Attack_TacoBell_Bong");
    private static final double BONK_TRIGGER_CHANCE = 0.20; // 20% chance

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

        // 20% chance to play an attack meme sound
        if (ThreadLocalRandom.current().nextDouble() < BONK_TRIGGER_CHANCE) {
            playSoundToAttacker(attackerRef);
        }
    }

    private static void playSoundToAttacker(@Nonnull Ref<EntityStore> attackerRef) {
        int randomIndex = ThreadLocalRandom.current().nextInt(ATTACK_SOUND_IDS.size());
        String soundId = ATTACK_SOUND_IDS.get(randomIndex);
        int soundIndex = resolveSoundIndex(soundId);
        if (soundIndex == 0) {
            return;
        }

        try {
            SoundUtil.playSoundEvent2d(attackerRef, soundIndex, SoundCategory.SFX, attackerRef.getStore());
        } catch (Exception ex) {
            LOGGER.atWarning().log("[ELAttackBonk] Failed to play attack sound '%s': %s", soundId, ex.getMessage());
        }
    }

    private static int resolveSoundIndex(String soundEventId) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? 0 : index;
    }
}
