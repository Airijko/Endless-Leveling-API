package com.airijko.endlessleveling.mob.outlander;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent per-player cooldowns on claiming Outlander Bridge rewards.
 * <p>
 * Claim sets a 1-hour cooldown. File is rewritten atomically on every
 * change so a server crash cannot wipe claim history.
 */
public final class OutlanderBridgeRewardCooldowns {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String FILE_NAME = "outlander-bridge-reward-cooldowns.json";
    public static final long CLAIM_COOLDOWN_MS = 60L * 60L * 1000L; // 1 hour

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile OutlanderBridgeRewardCooldowns INSTANCE;

    private final Path file;
    private final ConcurrentHashMap<UUID, Long> expiresAtByPlayer = new ConcurrentHashMap<>();

    public static synchronized OutlanderBridgeRewardCooldowns init(@Nonnull Path pluginDataFolder) {
        if (INSTANCE == null) {
            INSTANCE = new OutlanderBridgeRewardCooldowns(pluginDataFolder);
        }
        return INSTANCE;
    }

    @Nullable
    public static OutlanderBridgeRewardCooldowns get() { return INSTANCE; }

    private OutlanderBridgeRewardCooldowns(@Nonnull Path pluginDataFolder) {
        this.file = pluginDataFolder.resolve(FILE_NAME);
        load();
    }

    // ========================================================================
    // Query / mutate
    // ========================================================================

    public boolean isOnCooldown(@Nonnull UUID playerUuid) {
        return remainingMs(playerUuid) > 0L;
    }

    public long remainingMs(@Nonnull UUID playerUuid) {
        Long expires = expiresAtByPlayer.get(playerUuid);
        if (expires == null) return 0L;
        long now = System.currentTimeMillis();
        long diff = expires - now;
        if (diff <= 0L) {
            expiresAtByPlayer.remove(playerUuid);
            return 0L;
        }
        return diff;
    }

    public void setClaimedNow(@Nonnull UUID playerUuid) {
        expiresAtByPlayer.put(playerUuid, System.currentTimeMillis() + CLAIM_COOLDOWN_MS);
        save();
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    private void load() {
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            Map<String, Long> raw = GSON.fromJson(json,
                    new TypeToken<Map<String, Long>>(){}.getType());
            if (raw == null) return;
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> e : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(e.getKey());
                    long expires = e.getValue() == null ? 0L : e.getValue();
                    if (expires > now) expiresAtByPlayer.put(uuid, expires);
                } catch (IllegalArgumentException ignored) {}
            }
            LOGGER.atInfo().log("Outlander Bridge: loaded %d reward cooldowns", expiresAtByPlayer.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load Outlander Bridge reward cooldowns");
        }
    }

    private void save() {
        Map<String, Long> raw = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : expiresAtByPlayer.entrySet()) {
            if (e.getValue() > now) raw.put(e.getKey().toString(), e.getValue());
        }
        try {
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(raw);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception atomicFail) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save Outlander Bridge reward cooldowns");
        }
    }
}
