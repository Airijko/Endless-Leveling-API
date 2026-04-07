package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.commands.classes.BardSongRegistry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Bard music player GUI opened by /bard music or /bard song.
 * Lists all songs in the catalogue; clicking a song's PLAY button
 * triggers 3D positional audio audible within a 50-block radius.
 *
 * Note: Hytale has no server-side stop-sound API, so pause/stop
 * controls are not possible. Each play triggers a new sound event.
 */
public class BardMusicPage extends InteractiveCustomUIPage<BardMusicPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String SONG_ROW_TEMPLATE = "Pages/Bard/SongRow.ui";

    private final Ref<EntityStore> entityRef;
    private final Store<EntityStore> entityStore;

    public BardMusicPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> entityStore) {
        super(playerRef, lifetime, Data.CODEC);
        this.entityRef = entityRef;
        this.entityStore = entityStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/Bard/BardMusicPage.ui");

        UUID senderUuid = playerRef.getUuid();

        // ---- Now Playing card ----
        BardSongRegistry.ActiveSong active = BardSongRegistry.getActiveSong(senderUuid);
        if (active != null) {
            String display = BardSongRegistry.getDisplayName(active.getSongId());
            ui.set("#NowPlayingLabel.Text", display != null ? display : active.getSongId());
            ui.set("#NowPlayingStatusLabel.Text", "Playing");
            ui.set("#NowPlayingStatusLabel.Style.TextColor", "#66ff66");
        } else {
            ui.set("#NowPlayingLabel.Text", "Nothing playing");
            ui.set("#NowPlayingStatusLabel.Text", "Idle");
            ui.set("#NowPlayingStatusLabel.Style.TextColor", "#7a9abf");
        }

        // ---- Song Library ----
        buildSongList(ui, events, senderUuid);
    }

    private void buildSongList(@Nonnull SafeUICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull UUID senderUuid) {

        ui.clear("#SongRows");

        List<String> songIds = new ArrayList<>(BardSongRegistry.allSongIds());
        BardSongRegistry.ActiveSong active = BardSongRegistry.getActiveSong(senderUuid);

        for (int i = 0; i < songIds.size(); i++) {
            String songId = songIds.get(i);
            String displayName = BardSongRegistry.getDisplayName(songId);

            ui.append("#SongRows", SONG_ROW_TEMPLATE);
            String base = "#SongRows[" + i + "]";

            ui.set(base + " #SongName.Text", displayName != null ? displayName : songId);

            // Highlight the currently active song
            if (active != null && songId.equals(active.getSongId())) {
                ui.set(base + " #SongName.Style.TextColor", "#f0c040");
            }

            events.addEventBinding(Activating, base + " #SongPlayButton",
                    of("Action", "bard_music:select:" + songId), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        if (data.action.startsWith("bard_music:select:")) {
            String songId = data.action.substring("bard_music:select:".length());
            handleSelectSong(playerRef.getUuid(), songId);
        }

        rebuild();
    }

    private void handleSelectSong(@Nonnull UUID uuid, @Nonnull String songId) {
        String resolved = BardSongRegistry.resolveSongId(songId);
        if (resolved == null) {
            return;
        }
        BardSongRegistry.setActiveSong(uuid, resolved);
        playSoundForSong(resolved);
        playerRef.sendMessage(Message.join(
                Message.raw("[Bard] ").color("#4fd7f7"),
                Message.raw("Now playing ").color("#ffffff"),
                Message.raw(BardSongRegistry.getDisplayName(resolved)).color("#ffc300"),
                Message.raw(".").color("#ffffff")));
    }

    private void playSoundForSong(@Nonnull String songId) {
        String soundEventId = BardSongRegistry.getSoundEventId(songId);
        if (soundEventId == null) {
            return;
        }
        try {
            int soundIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
            if (soundIndex == Integer.MIN_VALUE || soundIndex == 0) {
                LOGGER.atFiner().log("Sound event '%s' not found in asset map.", soundEventId);
                return;
            }
            Vector3d position = positionOf(entityRef, entityStore);
            if (position == null) {
                SoundUtil.playSoundEvent2d(entityRef, soundIndex, SoundCategory.SFX, entityStore);
                return;
            }
            SoundUtil.playSoundEvent3d(entityRef, soundIndex, position, entityStore);
        } catch (Exception ex) {
            LOGGER.atFiner().withCause(ex).log("Failed to play bard sound event '%s'.", soundEventId);
        }
    }

    @Nullable
    private static Vector3d positionOf(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
        if (ref == null || store == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public static final class Data {
        public String action;

        public Data() {
            this.action = "";
        }

        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (d, v) -> d.action = v,
                        d -> d.action)
                .add()
                .build();
    }
}
