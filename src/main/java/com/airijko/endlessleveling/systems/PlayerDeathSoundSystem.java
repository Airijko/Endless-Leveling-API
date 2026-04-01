package com.airijko.endlessleveling.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a local death stinger to the player when they die.
 */
public final class PlayerDeathSoundSystem extends DeathSystems.OnDeathSystem {

        private static final String[] PLAYER_DEATH_SOUND_IDS = {
            "SFX_EL_Player_Death_Fahhh",
            "SFX_EL_Player_Death_Dexter_Meme",
            "SFX_EL_Player_Death_Roblox"
        };

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (store.getComponent(ref, Player.getComponentType()) == null) {
            return;
        }

        int soundIndex = pickRandomSoundIndex();
        if (soundIndex == 0) {
            return;
        }

        SoundUtil.playSoundEvent2d(ref, soundIndex, SoundCategory.SFX, ref.getStore());
    }

    private static int resolveSoundIndex(String soundEventId) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    private static int pickRandomSoundIndex() {
        List<Integer> candidates = new ArrayList<>(PLAYER_DEATH_SOUND_IDS.length);
        for (String soundId : PLAYER_DEATH_SOUND_IDS) {
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