package com.airijko.endlessleveling.xpstats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import com.airijko.endlessleveling.managers.PluginFilesManager;

/**
 * Central manager for per-profile XP stats data. Handles in-memory caching,
 * disk persistence, and XP/prestige recording. Thread-safe via per-UUID locks.
 */
public class XpStatsManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final File playerDataFolder;
    private final ConcurrentHashMap<XpStatsKey, XpStatsData> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public XpStatsManager(PluginFilesManager filesManager) {
        this.playerDataFolder = filesManager.getPlayerDataFolder();
        LOGGER.atInfo().log("XpStatsManager initialized. playerdata folder: %s", playerDataFolder.getAbsolutePath());
    }

    // ------------------------------------------------------------------
    // Recording hooks (called from LevelingManager)
    // ------------------------------------------------------------------

    /**
     * Records an XP gain for a specific player profile. Loads data on-demand if needed.
     */
    public void recordXpGain(UUID uuid, int profileIndex, double amount) {
        if (amount <= 0) return;
        XpStatsData data = getOrLoad(uuid, profileIndex);
        if (data != null) {
            data.recordXpGain(amount);
        }
    }

    /**
     * Records a prestige event for a specific player profile.
     */
    public void recordPrestige(UUID uuid, int profileIndex, int prestigeLevel) {
        XpStatsData data = getOrLoad(uuid, profileIndex);
        if (data != null) {
            data.recordPrestige(prestigeLevel);
        }
    }

    // ------------------------------------------------------------------
    // Cache management
    // ------------------------------------------------------------------

    /**
     * Gets cached data or loads from disk. Creates a fresh instance if no file exists.
     */
    public XpStatsData getOrLoad(UUID uuid, int profileIndex) {
        XpStatsKey key = new XpStatsKey(uuid, profileIndex);
        XpStatsData cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            // Double-check after acquiring lock
            cached = cache.get(key);
            if (cached != null) {
                return cached;
            }

            XpStatsData data = loadFromDisk(uuid, profileIndex);
            if (data == null) {
                data = new XpStatsData();
            } else {
                data.rotateBuckets();
                data.applyCatchUp();
            }
            cache.put(key, data);
            return data;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns cached data without loading from disk. May return null.
     */
    public XpStatsData get(UUID uuid, int profileIndex) {
        return cache.get(new XpStatsKey(uuid, profileIndex));
    }

    /**
     * Returns all cached entries. Used by the leaderboard service.
     */
    public ConcurrentHashMap<XpStatsKey, XpStatsData> getCache() {
        return cache;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Saves a single profile's XP stats to disk if dirty.
     */
    public void save(UUID uuid, int profileIndex) {
        XpStatsKey key = new XpStatsKey(uuid, profileIndex);
        XpStatsData data = cache.get(key);
        if (data == null || !data.isDirty()) return;

        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            data.setLastTotalXp(data.getTotalXp());
            Path file = resolveStatsFile(uuid, profileIndex);
            Files.createDirectories(file.getParent());

            String json = GSON.toJson(buildSerializableMap(data));
            if (!json.endsWith("\n")) {
                json = json + "\n";
            }
            writeAtomically(file, json);
            data.markClean();
            LOGGER.atFine().log("XpStats saved for %s profile %d", uuid, profileIndex);
        } catch (IOException e) {
            LOGGER.atSevere().log("Failed to save XpStats for %s profile %d: %s", uuid, profileIndex, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Saves all dirty entries in the cache.
     */
    public void saveAll() {
        for (Map.Entry<XpStatsKey, XpStatsData> entry : cache.entrySet()) {
            if (entry.getValue().isDirty()) {
                save(entry.getKey().uuid(), entry.getKey().profileIndex());
            }
        }
    }

    /**
     * Saves all cached profiles for a given player UUID.
     */
    public void saveAllForPlayer(UUID uuid) {
        for (Map.Entry<XpStatsKey, XpStatsData> entry : cache.entrySet()) {
            if (entry.getKey().uuid().equals(uuid)) {
                save(uuid, entry.getKey().profileIndex());
            }
        }
    }

    /**
     * Removes all cached profiles for a given player UUID.
     */
    public void evict(UUID uuid) {
        cache.entrySet().removeIf(entry -> entry.getKey().uuid().equals(uuid));
        locks.remove(uuid);
    }

    /**
     * Clears all cached data. Returns the number of entries cleared.
     */
    public int clearRuntimeState() {
        int size = cache.size();
        cache.clear();
        locks.clear();
        return size;
    }

    // ------------------------------------------------------------------
    // Leaderboard support: scan all on-disk stats files
    // ------------------------------------------------------------------

    /**
     * Loads all xpstats files from disk (for offline players not in cache).
     * Returns a combined list of all known entries (cached + on-disk).
     */
    public List<XpStatsEntry> loadAllEntries() {
        List<XpStatsEntry> entries = new ArrayList<>();

        // Add all cached entries first
        for (Map.Entry<XpStatsKey, XpStatsData> entry : cache.entrySet()) {
            entry.getValue().rotateBuckets();
            entries.add(new XpStatsEntry(entry.getKey().uuid(), entry.getKey().profileIndex(), entry.getValue()));
        }

        // Scan disk for entries not in cache
        if (!playerDataFolder.isDirectory()) return entries;
        File[] playerDirs = playerDataFolder.listFiles(File::isDirectory);
        if (playerDirs == null) return entries;

        for (File playerDir : playerDirs) {
            UUID uuid;
            try {
                uuid = UUID.fromString(playerDir.getName());
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            File xpstatsDir = new File(playerDir, "xpstats");
            if (!xpstatsDir.isDirectory()) continue;

            File[] statsFiles = xpstatsDir.listFiles((dir, name) -> name.endsWith("_stats.json"));
            if (statsFiles == null) continue;

            for (File statsFile : statsFiles) {
                String fileName = statsFile.getName();
                String indexStr = fileName.replace("_stats.json", "");
                int profileIndex;
                try {
                    profileIndex = Integer.parseInt(indexStr);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                XpStatsKey key = new XpStatsKey(uuid, profileIndex);
                if (cache.containsKey(key)) continue; // Already added from cache

                XpStatsData data = loadFromDisk(uuid, profileIndex);
                if (data != null) {
                    data.rotateBuckets();
                    entries.add(new XpStatsEntry(uuid, profileIndex, data));
                }
            }
        }

        return entries;
    }

    public record XpStatsEntry(UUID uuid, int profileIndex, XpStatsData data) {
    }

    // ------------------------------------------------------------------
    // Internal disk I/O
    // ------------------------------------------------------------------

    private XpStatsData loadFromDisk(UUID uuid, int profileIndex) {
        Path file = resolveStatsFile(uuid, profileIndex);
        if (!Files.exists(file)) return null;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> map = GSON.fromJson(reader, MAP_TYPE);
            if (map == null) return null;
            return hydrateFromMap(map);
        } catch (IOException | JsonParseException e) {
            LOGGER.atWarning().log("Failed to load XpStats for %s profile %d: %s", uuid, profileIndex, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private XpStatsData hydrateFromMap(Map<String, Object> map) {
        XpStatsData data = new XpStatsData();

        if (map.containsKey("totalXp")) {
            data.setTotalXp(toDouble(map.get("totalXp")));
        }
        if (map.containsKey("lastTotalXp")) {
            data.setLastTotalXp(toDouble(map.get("lastTotalXp")));
        }

        Map<String, Object> tracking = (Map<String, Object>) map.get("xpTracking");
        if (tracking != null) {
            if (tracking.containsKey("lastHour")) {
                data.setLastHour(toInt(tracking.get("lastHour")));
            }
            if (tracking.containsKey("lastDay")) {
                data.setLastDay(toInt(tracking.get("lastDay")));
            }
            hydrateArray(data.getHourly(), (List<Object>) tracking.get("hourly"));
            hydrateArray(data.getDaily(), (List<Object>) tracking.get("daily"));
        }

        List<Map<String, Object>> history = (List<Map<String, Object>>) map.get("prestigeHistory");
        if (history != null) {
            for (Map<String, Object> entry : history) {
                long ts = toLong(entry.get("timestamp"));
                int prestige = toInt(entry.get("prestige"));
                data.getPrestigeHistory().add(new XpStatsData.PrestigeEvent(ts, prestige));
            }
        }

        data.markClean();
        return data;
    }

    private Map<String, Object> buildSerializableMap(XpStatsData data) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("totalXp", data.getTotalXp());

        Map<String, Object> tracking = new java.util.LinkedHashMap<>();
        tracking.put("hourly", toList(data.getHourly()));
        tracking.put("daily", toList(data.getDaily()));
        tracking.put("lastHour", data.getLastHour());
        tracking.put("lastDay", data.getLastDay());
        map.put("xpTracking", tracking);

        map.put("lastTotalXp", data.getLastTotalXp());

        List<Map<String, Object>> history = new ArrayList<>();
        for (XpStatsData.PrestigeEvent event : data.getPrestigeHistory()) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("timestamp", event.timestamp());
            entry.put("prestige", event.prestige());
            history.add(entry);
        }
        map.put("prestigeHistory", history);

        return map;
    }

    private Path resolveStatsFile(UUID uuid, int profileIndex) {
        return playerDataFolder.toPath()
                .resolve(uuid.toString())
                .resolve("xpstats")
                .resolve(profileIndex + "_stats.json");
    }

    private void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ReentrantLock lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, key -> new ReentrantLock());
    }

    // ------------------------------------------------------------------
    // Type-safe number conversions (Gson deserializes numbers as Double)
    // ------------------------------------------------------------------

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private static int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        return 0;
    }

    private static long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }

    private static void hydrateArray(double[] target, List<Object> source) {
        if (source == null) return;
        for (int i = 0; i < Math.min(target.length, source.size()); i++) {
            target[i] = toDouble(source.get(i));
        }
    }

    private static List<Double> toList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(v);
        return list;
    }
}
