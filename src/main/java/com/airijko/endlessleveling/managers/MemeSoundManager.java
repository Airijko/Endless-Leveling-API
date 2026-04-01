package com.airijko.endlessleveling.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Meme-only periodic global smoke-detector beep.
 */
public final class MemeSoundManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String MEME_BEEP_SOUND_ID = "SFX_EL_Meme_Smoke_Detector_Beep";
    private static final int MIN_BEEP_INTERVAL_SECONDS = 3 * 60;
    private static final int MAX_BEEP_INTERVAL_SECONDS = 5 * 60;

    private static volatile ScheduledFuture<?> nextBeepTask;

    private MemeSoundManager() {
    }

    public static void initialize() {
        scheduleNextBeep();
    }

    public static void shutdown() {
        ScheduledFuture<?> task = nextBeepTask;
        if (task != null) {
            task.cancel(false);
            nextBeepTask = null;
        }
    }

    private static void scheduleNextBeep() {
        int delaySeconds = ThreadLocalRandom.current().nextInt(MIN_BEEP_INTERVAL_SECONDS, MAX_BEEP_INTERVAL_SECONDS + 1);
        nextBeepTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                playBeepToAllPlayers();
            } catch (Exception ex) {
                LOGGER.atWarning().log("[ELMeme] Failed to play meme beep: %s", ex.getMessage());
            } finally {
                scheduleNextBeep();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private static void playBeepToAllPlayers() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        int soundIndex = resolveSoundIndex(MEME_BEEP_SOUND_ID);
        if (soundIndex == 0) {
            LOGGER.atWarning().log("[ELMeme] Missing sound event id: %s", MEME_BEEP_SOUND_ID);
            return;
        }

        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null) {
                continue;
            }

            World playerWorld = universe.getWorld(worldUuid);
            if (playerWorld == null) {
                continue;
            }

            playerWorld.execute(() -> {
                if (!playerRef.isValid()) {
                    return;
                }
                Ref<EntityStore> playerEntityRef = playerRef.getReference();
                if (playerEntityRef == null || !playerEntityRef.isValid()) {
                    return;
                }

                try {
                    SoundUtil.playSoundEvent2d(playerEntityRef, soundIndex, SoundCategory.SFX, playerEntityRef.getStore());
                } catch (Exception ex) {
                    LOGGER.atWarning().log("[ELMeme] Failed meme beep for player=%s: %s",
                            playerRef.getUsername(),
                            ex.getMessage());
                }
            });
        }
    }

    private static int resolveSoundIndex(@Nullable String soundEventId) {
        if (soundEventId == null || soundEventId.isBlank()) {
            return 0;
        }
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? 0 : index;
    }
}