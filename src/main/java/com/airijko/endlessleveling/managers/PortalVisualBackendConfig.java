package com.airijko.endlessleveling.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backend-only bundled config for visual portal spawning.
 * Loaded from classpath resources and never initialized in the mods folder.
 */
public final class PortalVisualBackendConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final String DUNGEON_GATE_RESOURCE = "backend/dungeongate-visual.yml";
    private static final String WORLD_SETTINGS_RESOURCE = "backend/world-settings/el-gate-dungeons.json";

    private final boolean enabled;
    private final long spawnIntervalMinutesMin;
    private final long spawnIntervalMinutesMax;
    private final int maxConcurrentSpawns;
    private final List<String> portalWorldWhitelist;
    private final boolean announceOnSpawn;
    private final boolean announceOnDespawn;
    private final long gateDurationMinutes;
    private final int minLevelRequired;
    private final String defaultEffectId;
    private final String sneakPeekEffectId;
    private final long pulseIntervalMillis;
    private final int placementAttempts;

    private PortalVisualBackendConfig(boolean enabled,
                                      long spawnIntervalMinutesMin,
                                      long spawnIntervalMinutesMax,
                                      int maxConcurrentSpawns,
                                      List<String> portalWorldWhitelist,
                                      boolean announceOnSpawn,
                                      boolean announceOnDespawn,
                                      long gateDurationMinutes,
                                      int minLevelRequired,
                                      String defaultEffectId,
                                      String sneakPeekEffectId,
                                      long pulseIntervalMillis,
                                      int placementAttempts) {
        this.enabled = enabled;
        this.spawnIntervalMinutesMin = spawnIntervalMinutesMin;
        this.spawnIntervalMinutesMax = spawnIntervalMinutesMax;
        this.maxConcurrentSpawns = maxConcurrentSpawns;
        this.portalWorldWhitelist = portalWorldWhitelist;
        this.announceOnSpawn = announceOnSpawn;
        this.announceOnDespawn = announceOnDespawn;
        this.gateDurationMinutes = gateDurationMinutes;
        this.minLevelRequired = minLevelRequired;
        this.defaultEffectId = defaultEffectId;
        this.sneakPeekEffectId = sneakPeekEffectId;
        this.pulseIntervalMillis = pulseIntervalMillis;
        this.placementAttempts = placementAttempts;
    }

    @Nonnull
    public static PortalVisualBackendConfig load() {
        Map<String, Object> yamlMap = loadYamlConfig();
        warmWorldSettingsResource();

        boolean enabled = asBoolean(yamlMap.get("enabled"), true);
        long minInterval = Math.max(1L, asLong(yamlMap.get("spawn_interval_minutes_min"), 25L));
        long maxInterval = Math.max(minInterval, asLong(yamlMap.get("spawn_interval_minutes_max"), 40L));
        int maxConcurrent = asInt(yamlMap.get("max_concurrent_spawns"), 5);

        List<String> whitelist = asStringList(yamlMap.get("portal_world_whitelist"));
        if (whitelist.isEmpty()) {
            whitelist = List.of("world", "default", "endless");
        }

        boolean announceSpawn = asBoolean(yamlMap.get("announce_on_spawn"), true);
        boolean announceDespawn = asBoolean(yamlMap.get("announce_on_despawn"), true);
        long gateDuration = asLong(yamlMap.get("gate_duration_minutes"), 45L);
        int minLevelRequired = Math.max(1, asInt(yamlMap.get("min_level_required"), 1));

        Object visualsNode = yamlMap.get("visuals");
        Map<String, Object> visualsMap = visualsNode instanceof Map<?, ?> map
                ? castMap(map)
                : Map.of();

        String defaultEffectId = asString(visualsMap.get("default_effect"), "EL_MagicPortal_Circle");
        String sneakPeekEffectId = asString(visualsMap.get("sneak_peek_effect"), "EL_MagicPortal_Circle_Wave");
        long pulseIntervalMillis = Math.max(500L, asLong(visualsMap.get("pulse_interval_millis"), 1800L));
        int placementAttempts = Math.max(1, asInt(visualsMap.get("placement_attempts"), 14));

        return new PortalVisualBackendConfig(enabled,
                minInterval,
                maxInterval,
                maxConcurrent,
                whitelist,
                announceSpawn,
                announceDespawn,
                gateDuration,
                minLevelRequired,
                defaultEffectId,
                sneakPeekEffectId,
                pulseIntervalMillis,
                placementAttempts);
    }

    private static void warmWorldSettingsResource() {
        try (InputStream in = PortalVisualBackendConfig.class.getClassLoader().getResourceAsStream(WORLD_SETTINGS_RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("[ELPortalVisual] Missing backend world-settings resource: %s", WORLD_SETTINGS_RESOURCE);
                return;
            }
            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                LOGGER.atWarning().log("[ELPortalVisual] Invalid backend world-settings JSON payload.");
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("World_Overrides")) {
                LOGGER.atWarning().log("[ELPortalVisual] backend world-settings missing World_Overrides root.");
            }
        } catch (Exception ex) {
            LOGGER.atWarning().log("[ELPortalVisual] Failed loading backend world-settings: %s", ex.getMessage());
        }
    }

    @Nonnull
    private static Map<String, Object> loadYamlConfig() {
        try (InputStream in = PortalVisualBackendConfig.class.getClassLoader().getResourceAsStream(DUNGEON_GATE_RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("[ELPortalVisual] Missing backend config resource: %s", DUNGEON_GATE_RESOURCE);
                return Map.of();
            }
            Object loaded = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!(loaded instanceof Map<?, ?> map)) {
                return Map.of();
            }
            return castMap(map);
        } catch (Exception ex) {
            LOGGER.atWarning().log("[ELPortalVisual] Failed loading backend YAML config: %s", ex.getMessage());
            return Map.of();
        }
    }

    @Nonnull
    private static Map<String, Object> castMap(@Nonnull Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> casted = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            casted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return casted;
    }

    @Nonnull
    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            String normalized = String.valueOf(entry).trim();
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue.trim());
        }
        return defaultValue;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static long asLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Nonnull
    private static String asString(Object value, @Nonnull String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    public boolean enabled() {
        return enabled;
    }

    public long spawnIntervalMinutesMin() {
        return spawnIntervalMinutesMin;
    }

    public long spawnIntervalMinutesMax() {
        return spawnIntervalMinutesMax;
    }

    public int maxConcurrentSpawns() {
        return maxConcurrentSpawns;
    }

    @Nonnull
    public List<String> portalWorldWhitelist() {
        return portalWorldWhitelist;
    }

    public boolean announceOnSpawn() {
        return announceOnSpawn;
    }

    public boolean announceOnDespawn() {
        return announceOnDespawn;
    }

    public long gateDurationMinutes() {
        return gateDurationMinutes;
    }

    public int minLevelRequired() {
        return minLevelRequired;
    }

    @Nonnull
    public String defaultEffectId() {
        return defaultEffectId;
    }

    @Nonnull
    public String sneakPeekEffectId() {
        return sneakPeekEffectId;
    }

    public long pulseIntervalMillis() {
        return pulseIntervalMillis;
    }

    public int placementAttempts() {
        return placementAttempts;
    }

    public boolean isWorldWhitelisted(@Nonnull String worldName) {
        for (String allowed : portalWorldWhitelist) {
            if (worldName.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    public long resolveRandomSpawnIntervalMinutes() {
        long min = spawnIntervalMinutesMin;
        long max = spawnIntervalMinutesMax;
        if (max <= min) {
            return min;
        }
        long range = max - min + 1L;
        return min + (long) Math.floor(Math.random() * range);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "PortalVisualBackendConfig(enabled=%s interval=%d-%d maxConcurrent=%d worlds=%d minLevel=%d)",
                enabled,
                spawnIntervalMinutesMin,
                spawnIntervalMinutesMax,
                maxConcurrentSpawns,
                portalWorldWhitelist.size(),
                minLevelRequired);
    }
}
