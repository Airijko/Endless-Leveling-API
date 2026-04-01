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
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a local meme hit sound to players when they take non-lethal damage.
 */
public final class PlayerIncomingHitSoundSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String[] PLAYER_HIT_SOUND_IDS = {
        "SFX_EL_Player_Hit_Minecraft",
        "SFX_EL_Player_Hit_Steve_Old_Hurt"
    };

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (damage.getAmount() <= 0.0f) {
            return;
        }

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid() || targetRef.getStore() == null) {
            return;
        }

        if (store.getComponent(targetRef, Player.getComponentType()) == null) {
            return;
        }

        if (store.getComponent(targetRef, DeathComponent.getComponentType()) != null) {
            return;
        }

        int soundIndex = pickRandomSoundIndex();
        if (soundIndex == 0) {
            return;
        }

        try {
            SoundUtil.playSoundEvent2d(targetRef, soundIndex, SoundCategory.SFX, targetRef.getStore());
        } catch (Exception ex) {
            LOGGER.atWarning().log("[ELPlayerHitSfx] Failed to play incoming hit sound: %s", ex.getMessage());
        }
    }

    private static int resolveSoundIndex(String soundEventId) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    private static int pickRandomSoundIndex() {
        List<Integer> candidates = new ArrayList<>(PLAYER_HIT_SOUND_IDS.length);
        for (String soundId : PLAYER_HIT_SOUND_IDS) {
            int soundIndex = resolveSoundIndex(soundId);
            if (soundIndex != 0) {
                candidates.add(soundIndex);
            }
        }

        if (candidates.isEmpty()) {
            return 0;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}