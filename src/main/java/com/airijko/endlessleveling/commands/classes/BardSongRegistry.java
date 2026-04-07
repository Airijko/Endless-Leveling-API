package com.airijko.endlessleveling.commands.classes;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Tracks which Bard song each player is currently performing.
 *
 * Songs are intentionally lightweight here — the registry just stores the
 * canonical name a player has activated. Aura-side tick systems can read the
 * active song later to apply effects.
 */
public final class BardSongRegistry {

    /**
     * Canonical song catalogue. Keys are lowercase identifiers used by the
     * /bard play command; values are SongEntry holding display name and
     * optional sound-event id for audio playback.
     */
    private static final Map<String, SongEntry> SONGS;

    static {
        Map<String, SongEntry> songs = new LinkedHashMap<>();
        songs.put("bridal_chorus", new SongEntry("Bridal Chorus", "SFX_EL_Bard_BridalChorus"));
        SONGS = songs;
    }

    private static final Map<UUID, ActiveSong> ACTIVE = new ConcurrentHashMap<>();

    private BardSongRegistry() {
    }

    public static String resolveSongId(String input) {
        if (input == null) {
            return null;
        }
        String key = input.trim().toLowerCase(Locale.ROOT);
        return SONGS.containsKey(key) ? key : null;
    }

    public static String getDisplayName(String songId) {
        if (songId == null) {
            return null;
        }
        SongEntry entry = SONGS.get(songId);
        return entry != null ? entry.displayName : null;
    }

    @Nullable
    public static String getSoundEventId(String songId) {
        if (songId == null) {
            return null;
        }
        SongEntry entry = SONGS.get(songId);
        return entry != null ? entry.soundEventId : null;
    }

    public static String listSongNames() {
        return String.join(", ", SONGS.keySet());
    }

    /** Returns an unmodifiable view of all registered song ids. */
    public static Collection<String> allSongIds() {
        return Collections.unmodifiableSet(SONGS.keySet());
    }

    public static void setActiveSong(UUID playerUuid, String songId) {
        if (playerUuid == null || songId == null) {
            return;
        }
        ACTIVE.put(playerUuid, new ActiveSong(songId, System.currentTimeMillis()));
    }

    public static ActiveSong clearActiveSong(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return ACTIVE.remove(playerUuid);
    }

    public static ActiveSong getActiveSong(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return ACTIVE.get(playerUuid);
    }

    public static final class ActiveSong {
        private final String songId;
        private final long startedAtMillis;

        public ActiveSong(String songId, long startedAtMillis) {
            this.songId = songId;
            this.startedAtMillis = startedAtMillis;
        }

        public String getSongId() {
            return songId;
        }

        public long getStartedAtMillis() {
            return startedAtMillis;
        }
    }

    /** Immutable catalogue entry. */
    public static final class SongEntry {
        private final String displayName;
        @Nullable
        private final String soundEventId;

        public SongEntry(String displayName, @Nullable String soundEventId) {
            this.displayName = displayName;
            this.soundEventId = soundEventId;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Nullable
        public String getSoundEventId() {
            return soundEventId;
        }
    }

}
