package com.airijko.endlessleveling.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Plays the UI integrity alarm for the notified player and any online admins.
 */
public final class UiIntegrityAlertSound {

    public static final String TEMP_SMOKE_ALARM_OGG_PATH = "Common/Audio/smokealarm.ogg";

    private static final String[] ALERT_SOUND_IDS = {
            "EndlessLeveling_SmokeAlarm",
            "SFX_Attn_VeryLoud",
            "SFX_Attn_Loud"
    };

    private UiIntegrityAlertSound() {
    }

    public static void playForRecipientAndAdministrators(PlayerRef recipient) {
        if (recipient == null || !recipient.isValid()) {
            return;
        }

        int soundIndex = resolveAlertSoundIndex();
        if (soundIndex == 0) {
            return;
        }

        Set<UUID> played = new HashSet<>();
        playToPlayer(recipient, soundIndex, played);

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        for (PlayerRef onlinePlayer : universe.getPlayers()) {
            if (onlinePlayer == null || !onlinePlayer.isValid()) {
                continue;
            }
            if (!OperatorHelper.hasAdministrativeAccess(onlinePlayer)) {
                continue;
            }
            playToPlayer(onlinePlayer, soundIndex, played);
        }
    }

    private static void playToPlayer(PlayerRef playerRef, int soundIndex, Set<UUID> played) {
        if (playerRef == null || !playerRef.isValid() || soundIndex == 0) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        if (uuid != null && !played.add(uuid)) {
            return;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid() || playerEntityRef.getStore() == null) {
            return;
        }

        SoundUtil.playSoundEvent2d(playerEntityRef, soundIndex, SoundCategory.SFX, playerEntityRef.getStore());
    }

    private static int resolveAlertSoundIndex() {
        for (String soundId : ALERT_SOUND_IDS) {
            if (soundId == null || soundId.isBlank()) {
                continue;
            }

            int index = SoundEvent.getAssetMap().getIndex(soundId);
            if (index != Integer.MIN_VALUE && index != 0) {
                return index;
            }
        }
        return 0;
    }
}