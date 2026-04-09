package com.airijko.endlessleveling.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.airijko.endlessleveling.player.PlayerData.PlayerProfile;
import com.airijko.endlessleveling.enums.PassiveType;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.hypixel.hytale.logger.HytaleLogger;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.managers.PluginFilesManager;
import com.airijko.endlessleveling.managers.VersionRegistry;

public class PlayerDataManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final HytaleLogger SHUTDOWN_LOGGER = HytaleLogger.get("EndlessLeveling");
    private static final Gson PLAYERDATA_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type STRING_OBJECT_MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final PluginFilesManager filesManager;
    private final ConfigManager configManager;
    private final SkillManager skillManager;
    private final RaceManager raceManager;
    private final ClassManager classManager;
    private final ConcurrentHashMap<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastAutoBackupMs = new ConcurrentHashMap<>();

    private static final int AUTO_BACKUP_RETENTION = 5; // keep latest N per player
    private static final long AUTO_BACKUP_MIN_INTERVAL_MS = 10 * 60 * 1000L; // 10 minutes between backups per player
    private static final DateTimeFormatter AUTO_BACKUP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        private static final DateTimeFormatter LEGACY_YML_BACKUP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_SSS");
        private static final Pattern UUID_IN_PLAYERDATA_FILE_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private record ParsedSwitchValue(int value, boolean legacyDerived) {
    }

    public PlayerDataManager(PluginFilesManager filesManager,
            ConfigManager configManager,
            SkillManager skillManager,
            RaceManager raceManager,
            ClassManager classManager) {
        this.filesManager = filesManager;
        this.configManager = configManager;
        this.skillManager = skillManager;
        this.raceManager = raceManager;
        this.classManager = classManager;

        migrateLegacyPlayerDataFiles();

        LOGGER.atInfo().log("PlayerDataManager initialized.");
    }

    public int getCurrentPlayerDataVersion() {
        return VersionRegistry.PLAYERDATA_SCHEMA_VERSION;
    }

    // --- Load or create a player ---
    public PlayerData loadOrCreate(UUID uuid, String playerName) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerData cached = playerCache.get(uuid);
            if (cached != null) {
                return cached;
            }

            File jsonFile = filesManager.getPlayerDataFile(uuid);
            File legacyYamlFile = filesManager.getLegacyPlayerDataFile(uuid);
            PlayerData data;
            boolean safeToSave = true; // avoid overwriting if load failed
            boolean createdNewPlayerData = false;
            boolean loadedFromLegacyYaml = false;

            if (jsonFile.exists()) {
                data = loadFromJsonFile(uuid, playerName, jsonFile);
                if (data == null) {
                    if (legacyYamlFile.exists()) {
                        data = loadFromLegacyYamlFile(uuid, playerName, legacyYamlFile);
                        loadedFromLegacyYaml = data != null;
                        if (data != null) {
                            LOGGER.atWarning().log(
                                    "PlayerData JSON for UUID %s was unreadable; recovered from legacy YAML and will rewrite JSON.",
                                    uuid);
                        } else {
                            safeToSave = false;
                            data = new PlayerData(uuid, playerName, getStartingSkillPoints());
                            applyConfigDefaults(data);
                            createdNewPlayerData = true;
                            LOGGER.atSevere().log(
                                    "PlayerData for UUID %s could not be parsed from JSON or legacy YAML; using in-memory fallback and will NOT overwrite %s.",
                                    uuid,
                                    jsonFile.getName());
                        }
                    } else {
                        safeToSave = false;
                        data = new PlayerData(uuid, playerName, getStartingSkillPoints());
                        applyConfigDefaults(data);
                        createdNewPlayerData = true;
                        LOGGER.atSevere().log(
                                "PlayerData for UUID %s could not be parsed; using in-memory fallback and will NOT overwrite %s. Please fix the JSON or restore a backup.",
                                uuid, jsonFile.getName());
                    }
                } else {
                    LOGGER.atInfo().log("PlayerData for UUID %s loaded from file.", uuid);
                }
            } else if (legacyYamlFile.exists()) {
                data = loadFromLegacyYamlFile(uuid, playerName, legacyYamlFile);
                loadedFromLegacyYaml = data != null;
                if (data == null) {
                    safeToSave = false;
                    data = new PlayerData(uuid, playerName, getStartingSkillPoints());
                    applyConfigDefaults(data);
                    createdNewPlayerData = true;
                    LOGGER.atSevere().log(
                            "Legacy PlayerData for UUID %s could not be parsed; using in-memory fallback and will NOT overwrite %s.",
                            uuid, legacyYamlFile.getName());
                } else {
                    LOGGER.atInfo().log("PlayerData for UUID %s loaded from legacy YAML and will be migrated to JSON.", uuid);
                }
            } else {
                data = new PlayerData(uuid, playerName, getStartingSkillPoints());
                applyConfigDefaults(data);
                createdNewPlayerData = true;
                LOGGER.atInfo().log("PlayerData for UUID %s created new.", uuid);
            }

            ensureValidRace(data);
            ensureValidClasses(data);
            initializeSwapDefaultsForAllProfiles(data, createdNewPlayerData);

            // Cache and save
            playerCache.put(uuid, data);
            if (safeToSave) {
                save(data);
                if (loadedFromLegacyYaml) {
                    archiveAndDeleteLegacyYamlIfJsonPresent(uuid, legacyYamlFile, jsonFile);
                }
                LOGGER.atInfo().log("PlayerData for UUID %s cached and saved.", uuid);
            } else {
                LOGGER.atWarning().log("PlayerData for UUID %s cached only; not saved to avoid overwriting original.",
                        uuid);
            }

            return data;
        } finally {
            lock.unlock();
        }
    }

    // --- Get player from cache ---
    public PlayerData get(UUID uuid) {
        return playerCache.get(uuid);
    }

    // --- Remove player from cache ---
    public void remove(UUID uuid) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            playerCache.remove(uuid);
            playerLocks.remove(uuid);
            LOGGER.atInfo().log("PlayerData for UUID %s removed from cache.", uuid);
        } finally {
            lock.unlock();
        }
    }

    // --- Save a player ---
    public void save(PlayerData data) {
        if (data == null) {
            return;
        }
        ReentrantLock lock = lockFor(data.getUuid());
        lock.lock();
        try {
            ensureValidRace(data);
            ensureValidClasses(data);
            initializeSwapDefaultsForAllProfiles(data, false);
            File file = filesManager.getPlayerDataFile(data.getUuid());

            Map<String, Object> map = buildPlayerDataMap(data);
            String jsonContent = PLAYERDATA_GSON.toJson(map);

            if (!isJsonRoundTripSafe(jsonContent)) {
                LOGGER.atSevere().log(
                        "Aborting save for %s: generated JSON failed validation; file left unchanged.",
                        data.getUuid());
                return;
            }

            if (!jsonContent.endsWith("\n")) {
                jsonContent = jsonContent + "\n";
            }

            try {
                // Create a rolling backup of the previous on-disk file before overwriting.
                createAutoBackupIfNeeded(file, data.getUuid());

                writeAtomically(file.toPath(), jsonContent);
                LOGGER.atFine().log("PlayerData for UUID %s saved to file.", data.getUuid());
            } catch (IOException e) {
                LOGGER.atSevere().log("Failed to save PlayerData for UUID %s: %s", data.getUuid(), e.getMessage());
                e.printStackTrace();
            }
        } finally {
            lock.unlock();
        }
    }

    private PlayerData loadFromJsonFile(UUID uuid, String fallbackPlayerName, File file) {
        Map<String, Object> map;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            map = PLAYERDATA_GSON.fromJson(reader, STRING_OBJECT_MAP_TYPE);
        } catch (IOException | JsonParseException e) {
            LOGGER.atSevere().log("Failed to load PlayerData JSON for UUID %s from %s: %s", uuid,
                    file.getName(), e.getMessage());
            e.printStackTrace();
            var backupPath = backupCorruptFile(file, "json-parse-error");
            if (backupPath != null) {
                LOGGER.atWarning().log("Backed up unreadable playerdata to %s; original left untouched.",
                        backupPath.toString());
            }
            return null;
        }

        if (map == null) {
            LOGGER.atWarning().log("PlayerData JSON file %s for UUID %s is empty; skipping load.", file.getName(),
                    uuid);
            var backupPath = backupCorruptFile(file, "empty-json-file");
            if (backupPath != null) {
                LOGGER.atWarning().log("Backed up empty playerdata to %s; original left untouched.",
                        backupPath.toString());
            }
            return null;
        }

        Map<String, Object> migratedMap = migrateMapInMemoryOrNull(uuid, file, map);
        if (migratedMap == null) {
            return null;
        }
        return hydratePlayerDataFromMap(uuid, fallbackPlayerName, migratedMap);
    }

    private PlayerData loadFromLegacyYamlFile(UUID uuid, String fallbackPlayerName, File file) {
        Map<String, Object> map;
        Yaml yaml = createYaml();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            map = yaml.load(reader);
        } catch (Exception e) {
            LOGGER.atSevere().log("Failed to load legacy PlayerData YAML for UUID %s from %s: %s", uuid,
                    file.getName(), e.getMessage());
            e.printStackTrace();
            var backupPath = backupCorruptFile(file, "yaml-parse-error");
            if (backupPath != null) {
                LOGGER.atWarning().log("Backed up unreadable legacy playerdata to %s; original left untouched.",
                        backupPath.toString());
            }
            return null;
        }

        if (map == null) {
            LOGGER.atWarning().log("Legacy PlayerData YAML file %s for UUID %s is empty; skipping load.",
                    file.getName(), uuid);
            var backupPath = backupCorruptFile(file, "empty-yaml-file");
            if (backupPath != null) {
                LOGGER.atWarning().log("Backed up empty legacy playerdata to %s; original left untouched.",
                        backupPath.toString());
            }
            return null;
        }

        Map<String, Object> migratedMap = migrateMapInMemoryOrNull(uuid, file, map);
        if (migratedMap == null) {
            return null;
        }
        return hydratePlayerDataFromMap(uuid, fallbackPlayerName, migratedMap);
    }

    private Map<String, Object> migrateMapInMemoryOrNull(UUID uuid, File sourceFile, Map<String, Object> sourceMap) {
        try {
            return PlayerDataMigration.migrateMapIfNeeded(
                    sourceMap,
                    VersionRegistry.PLAYERDATA_SCHEMA_VERSION,
                    sourceFile != null ? sourceFile.getName() : null);
        } catch (LinkageError e) {
            LOGGER.atSevere().log(
                    "PlayerData migration helper is unavailable for UUID %s while loading %s: %s",
                    uuid,
                    sourceFile != null ? sourceFile.getName() : "unknown",
                    e.getMessage());
            e.printStackTrace();
            LOGGER.atWarning().log(
                    "Continuing to load PlayerData for UUID %s without migration to avoid crashing the world thread.",
                    uuid);
            return sourceMap;
        } catch (Exception e) {
            LOGGER.atSevere().log("Failed to migrate PlayerData for UUID %s from %s: %s", uuid,
                    sourceFile != null ? sourceFile.getName() : "unknown", e.getMessage());
            e.printStackTrace();
            if (sourceFile != null) {
                var backupPath = backupCorruptFile(sourceFile, "migration-error");
                if (backupPath != null) {
                    LOGGER.atWarning().log(
                            "Backed up playerdata prior to migration failure at %s; original left untouched.",
                            backupPath.toString());
                }
            }
            return null;
        }
    }

    private PlayerData hydratePlayerDataFromMap(UUID uuid, String fallbackPlayerName, Map<String, Object> map) {
        String playerName = parseString(map.get("playerName"));
        if (playerName == null || playerName.isBlank()) {
            playerName = fallbackPlayerName;
        }
        if (playerName == null || playerName.isBlank()) {
            playerName = uuid.toString();
        }

        PlayerData data = new PlayerData(uuid, playerName, getStartingSkillPoints());
        applyConfigDefaults(data);

        Map<Integer, PlayerProfile> profiles = parseProfiles(map.get("profiles"), data.getBaseSkillPoints());
        if (profiles.isEmpty()) {
            profiles = buildLegacyProfiles(map, data.getBaseSkillPoints());
        }
        int activeProfileIndex = parseActiveProfileIndex(map.get("activeProfile"));
        data.loadProfilesFromStorage(profiles, activeProfileIndex);
        applyOptions(map, data);
        ensureValidRace(data);
        ensureValidClasses(data);
        LOGGER.atInfo().log("PlayerData for UUID %s loaded from disk.", uuid);
        return data;
    }

    public PlayerData getByName(String playerName) {
        if (playerName == null)
            return null;

        String normalizedTarget = playerName.trim();
        if (normalizedTarget.isEmpty()) {
            return null;
        }

        for (PlayerData data : playerCache.values()) {
            if (data.getPlayerName().equalsIgnoreCase(normalizedTarget)) {
                LOGGER.atFine().log("PlayerData for %s retrieved from cache by name.", normalizedTarget);
                return data;
            }
        }

        File folder = filesManager.getPlayerDataFolder();
        if (folder != null && folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml");
            });
            if (files != null) {
                for (File file : files) {
                    UUID uuid = parseUuidFromPlayerDataFileName(file.getName());
                    if (uuid == null) {
                        continue;
                    }

                    PlayerData loaded = playerCache.get(uuid);
                    if (loaded == null) {
                        loaded = loadFromFileMinimal(uuid, file);
                        if (loaded != null) {
                            playerCache.putIfAbsent(uuid, loaded);
                        }
                    }

                    PlayerData candidate = playerCache.get(uuid);
                    if (candidate != null && candidate.getPlayerName().equalsIgnoreCase(normalizedTarget)) {
                        LOGGER.atInfo().log("PlayerData for %s loaded from disk lookup by name.", normalizedTarget);
                        return candidate;
                    }
                }
            }
        }

        LOGGER.atWarning().log("PlayerData for player name %s not found in cache or on disk.", normalizedTarget);
        return null;
    }

    public void saveAll() {
        SHUTDOWN_LOGGER.atInfo().log("[PlayerDataManager] Saving all cached PlayerData (%d entries)...", playerCache.size());
        for (Map.Entry<UUID, PlayerData> entry : playerCache.entrySet()) {
            save(entry.getValue());
        }
        SHUTDOWN_LOGGER.atInfo().log("[PlayerDataManager] All cached PlayerData saved successfully.");
    }

    public Collection<PlayerData> getAllCached() {
        return Collections.unmodifiableCollection(playerCache.values());
    }

    /**
     * Load all player data files from disk (if not already cached) and
     * return a list of PlayerData sorted by prestige descending, then level
     * descending, then XP descending.
     */
    public List<PlayerData> getAllPlayersSortedByLevel() {
        File folder = filesManager.getPlayerDataFolder();
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml");
        });
        if (files != null) {
            for (File file : files) {
                UUID uuid = parseUuidFromPlayerDataFileName(file.getName());
                if (uuid == null) {
                    continue;
                }

                // If already cached, use cached version; otherwise, load minimally from file
                if (!playerCache.containsKey(uuid)) {
                    PlayerData loaded = loadFromFileMinimal(uuid, file);
                    if (loaded != null) {
                        playerCache.put(uuid, loaded);
                    }
                }
            }
        }

        List<PlayerData> all = new ArrayList<>(playerCache.values());
        all.sort(Comparator
                .comparingInt(PlayerData::getPrestigeLevel).reversed()
                .thenComparing(Comparator.comparingInt(PlayerData::getLevel).reversed())
                .thenComparing(Comparator.comparingDouble(PlayerData::getXp).reversed()));
        return all;
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return defaultValue;
    }

    private boolean isRaceModelGloballyDisabled() {
        return raceManager != null && raceManager.isRaceModelGloballyDisabled();
    }

    private boolean defaultUseRaceModel() {
        if (raceManager == null) {
            return false;
        }
        return raceManager.isRaceModelDefaultEnabled();
    }

    private void applyConfigDefaults(PlayerData data) {
        if (data == null) {
            return;
        }
        boolean useRaceModelDefault = defaultUseRaceModel();
        if (isRaceModelGloballyDisabled()) {
            useRaceModelDefault = false;
        }
        data.setUseRaceModel(useRaceModelDefault);
        data.setLanguage(resolveConfiguredDefaultLanguage());
    }

    private String resolveConfiguredDefaultLanguage() {
        if (configManager == null) {
            return PlayerData.DEFAULT_LANGUAGE;
        }
        Object configured = configManager.get("language.locale", PlayerData.DEFAULT_LANGUAGE, false);
        if (configured instanceof String locale && !locale.isBlank()) {
            return locale;
        }
        return PlayerData.DEFAULT_LANGUAGE;
    }

    public void initializeSwapDefaultsForNewProfile(PlayerData data, int profileIndex) {
        if (data == null || !PlayerData.isValidProfileIndex(profileIndex)) {
            return;
        }

        PlayerProfile profile = data.getProfiles().get(profileIndex);
        if (profile == null) {
            return;
        }

        initializeProfileSwapDefaults(profile, true);
    }

    private void initializeSwapDefaultsForAllProfiles(PlayerData data, boolean seedFromConfigWhenEmpty) {
        if (data == null) {
            return;
        }

        data.getProfiles().values().forEach(profile -> initializeProfileSwapDefaults(profile, seedFromConfigWhenEmpty));
    }

    private void initializeProfileSwapDefaults(PlayerProfile profile, boolean seedFromConfigWhenEmpty) {
        if (profile == null) {
            return;
        }

        int configuredRaceMax = resolveConfiguredRaceMaxSwaps();
        if (seedFromConfigWhenEmpty && profile.getRemainingRaceSwitches() <= 0) {
            profile.setRemainingRaceSwitches(configuredRaceMax);
        }
        if (!hasAssignedSelection(profile.getRaceId()) && profile.getRemainingRaceSwitches() <= 0) {
            profile.setRemainingRaceSwitches(1);
        }

        int configuredClassMax = resolveConfiguredClassMaxSwaps();
        if (seedFromConfigWhenEmpty && profile.getRemainingPrimaryClassSwitches() <= 0) {
            profile.setRemainingPrimaryClassSwitches(configuredClassMax);
        }
        if (!hasAssignedSelection(profile.getPrimaryClassId()) && profile.getRemainingPrimaryClassSwitches() <= 0) {
            profile.setRemainingPrimaryClassSwitches(1);
        }

        if (seedFromConfigWhenEmpty && profile.getRemainingSecondaryClassSwitches() <= 0) {
            profile.setRemainingSecondaryClassSwitches(configuredClassMax);
        }
        if (!hasAssignedSelection(profile.getSecondaryClassId())
                && profile.getRemainingSecondaryClassSwitches() <= 0) {
            profile.setRemainingSecondaryClassSwitches(1);
        }
    }

    private int resolveConfiguredRaceMaxSwaps() {
        int configured = raceManager != null ? raceManager.getMaxRaceSwitches() : 1;
        return configured < 0 ? 1 : Math.max(0, configured);
    }

    private int resolveConfiguredClassMaxSwaps() {
        int configured = classManager != null ? classManager.getMaxClassSwitches() : 1;
        return configured < 0 ? 1 : Math.max(0, configured);
    }

    private Map<String, Object> castToStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private Map<Integer, PlayerProfile> parseProfiles(Object profilesNode, int baseSkillPoints) {
        Map<Integer, PlayerProfile> profiles = new LinkedHashMap<>();
        if (!(profilesNode instanceof Map<?, ?> rawProfiles)) {
            return profiles;
        }

        for (Map.Entry<?, ?> entry : rawProfiles.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            int index = parseProfileIndex(key);
            if (!PlayerData.isValidProfileIndex(index)) {
                continue;
            }
            Map<String, Object> profileMap = castToStringObjectMap(entry.getValue());
            if (profileMap == null) {
                continue;
            }
            PlayerProfile profile = PlayerProfile.fresh(baseSkillPoints, PlayerData.defaultProfileName(index));
            applyProfileMap(profile, profileMap, index);
            profiles.put(index, profile);
        }

        return profiles;
    }

    private Map<Integer, PlayerProfile> buildLegacyProfiles(Map<String, Object> source, int baseSkillPoints) {
        Map<Integer, PlayerProfile> profiles = new LinkedHashMap<>();
        if (source == null) {
            profiles.put(1, PlayerProfile.fresh(baseSkillPoints, PlayerData.defaultProfileName(1)));
            return profiles;
        }
        PlayerProfile profile = PlayerProfile.fresh(baseSkillPoints, PlayerData.defaultProfileName(1));
        applyProfileMap(profile, source, 1);
        profiles.put(1, profile);
        return profiles;
    }

    private void applyProfileMap(PlayerProfile profile, Map<String, Object> source, int slot) {
        if (profile == null || source == null) {
            return;
        }

        profile.setXp(parseDouble(source.get("xp"), 0.0));
        profile.setLevel(parseInt(source.get("level"), 1));
        profile.setPrestigeLevel(parseInt(source.get("prestige"), 0));
        profile.setSkillPoints(parseInt(source.get("skillPoints"), profile.getSkillPoints()));
        profile.setName(PlayerData.normalizeProfileName(parseString(source.get("name")), slot));

        Map<String, Object> attrs = castToStringObjectMap(source.get("attributes"));
        if (attrs != null) {
            for (SkillAttributeType type : SkillAttributeType.values()) {
                Object value = attrs.get(type.name());
                if (value == null) {
                    value = attrs.get(type.getConfigKey());
                }
                profile.getAttributes().put(type, parseInt(value, 0));
            }
        }

        Map<String, Object> autoAllocateNode = castToStringObjectMap(source.get("autoAllocate"));
        if (autoAllocateNode != null) {
            profile.setAutoAllocatePointsPerLevel(parseInt(autoAllocateNode.get("pointsPerLevel"), 0));
            String selectedAttribute = parseString(autoAllocateNode.get("selectedAttribute"));
            SkillAttributeType selectedType = null;
            if (selectedAttribute != null && !selectedAttribute.isBlank()) {
                try {
                    selectedType = SkillAttributeType.valueOf(selectedAttribute);
                } catch (IllegalArgumentException ignored) {
                    selectedType = null;
                }
            }
            for (SkillAttributeType type : SkillAttributeType.values()) {
                profile.setAutoAllocateEnabled(type, type == selectedType);
            }
        }

        Map<String, Object> passivesNode = castToStringObjectMap(source.get("passives"));
        boolean loadedPassives = false;
        if (passivesNode != null) {
            loadedPassives = loadPassiveLevels(profile, passivesNode);
        }

        Map<String, Object> augmentOffersNode = castToStringObjectMap(source.get("augmentOffers"));
        if (augmentOffersNode != null) {
            for (Map.Entry<String, Object> entry : augmentOffersNode.entrySet()) {
                List<String> offers = parseStringList(entry.getValue());
                profile.setAugmentOffers(entry.getKey(), offers);
            }
        }

        Map<String, Object> selectedAugmentsNode = castToStringObjectMap(source.get("selectedAugments"));
        if (selectedAugmentsNode != null) {
            for (Map.Entry<String, Object> entry : selectedAugmentsNode.entrySet()) {
                String augmentId = parseString(entry.getValue());
                profile.setSelectedAugment(entry.getKey(), augmentId);
            }
        }

        Map<String, Object> augmentValueRollsNode = castToStringObjectMap(source.get("augmentValueRolls"));
        if (augmentValueRollsNode != null) {
            for (Map.Entry<String, Object> selectionEntry : augmentValueRollsNode.entrySet()) {
                Map<String, Object> rollsNode = castToStringObjectMap(selectionEntry.getValue());
                if (rollsNode == null) {
                    continue;
                }
                for (Map.Entry<String, Object> rollEntry : rollsNode.entrySet()) {
                    double parsed = parseDouble(rollEntry.getValue(), Double.NaN);
                    if (Double.isFinite(parsed)) {
                        profile.setAugmentValueRoll(selectionEntry.getKey(), rollEntry.getKey(), parsed);
                    }
                }
            }
        }
        profile.pruneAugmentValueRollsToSelections();

        Map<String, Object> rerollsNode = castToStringObjectMap(source.get("augmentRerollsUsed"));
        if (rerollsNode != null) {
            for (Map.Entry<String, Object> entry : rerollsNode.entrySet()) {
                profile.setAugmentRerollsUsed(entry.getKey(), parseInt(entry.getValue(), 0));
            }
        }

        Map<String, Object> rerollBonusNode = castToStringObjectMap(source.get("augmentRerollsBonus"));
        if (rerollBonusNode != null) {
            for (Map.Entry<String, Object> entry : rerollBonusNode.entrySet()) {
                profile.setAugmentRerollBonus(entry.getKey(), parseInt(entry.getValue(), 0));
            }
        }

        Object raceNode = source.get("race");
        profile.setRaceId(parseRaceId(raceNode));
        Map<String, Object> raceMap = castToStringObjectMap(raceNode);
        List<String> completedRaceForms = parseStringList(raceMap != null
                ? raceMap.get("completedForms")
                : source.get("completedRaceForms"));
        profile.setCompletedRaceForms(completedRaceForms);
        String resolvedPathId = resolveRacePathId(profile.getRaceId());
        if (resolvedPathId != null) {
            profile.addCompletedRaceForm(resolvedPathId);
        }
        profile.setLastRaceChangeEpochSeconds(parseRaceLastChanged(raceNode));
        int maxRaceSwitches = raceManager != null ? raceManager.getMaxRaceSwitches() : -1;
        ParsedSwitchValue parsedRaceRemaining = parseRaceRemainingSwitches(raceNode, source, maxRaceSwitches);
        int raceRemaining = parsedRaceRemaining.value();
        if (parsedRaceRemaining.legacyDerived()) {
            raceRemaining = applyLegacyMigrationSwapConsumption(
                    raceRemaining,
                    maxRaceSwitches,
                    profile.getLevel(),
                    hasAssignedSelection(profile.getRaceId()),
                    raceManager != null && raceManager.isSwapAntiExploitConsumeEnabled(),
                    raceManager != null ? raceManager.getSwapAntiExploitConsumeLevelThreshold() : 0);
        }
        raceRemaining = grantEmergencySwapForNoneSelection(raceRemaining, maxRaceSwitches,
                hasAssignedSelection(profile.getRaceId()));
        profile.setRemainingRaceSwitches(raceRemaining);
        profile.setRaceSwapAntiExploitConsumedAtLevel(parseBoolean(
            raceMap != null ? raceMap.get("antiExploitConsumedAtLevel") : null,
            false));

        Map<String, Object> classesNode = castToStringObjectMap(source.get("classes"));
        String primaryClassId = parseClassId(classesNode != null ? classesNode.get("primary") : null);
        String secondaryClassId = parseClassId(classesNode != null ? classesNode.get("secondary") : null);
        profile.setPrimaryClassId(primaryClassId);
        profile.setSecondaryClassId(secondaryClassId);
        List<String> completedClassForms = parseStringList(classesNode != null
                ? classesNode.get("completedForms")
                : source.get("completedClassForms"));
        profile.setCompletedClassForms(completedClassForms);
        String primaryClassPathId = resolveClassPathId(primaryClassId);
        if (primaryClassPathId != null) {
            profile.addCompletedClassForm(primaryClassPathId);
        }
        String secondaryClassPathId = resolveClassPathId(secondaryClassId);
        if (secondaryClassPathId != null) {
            profile.addCompletedClassForm(secondaryClassPathId);
        }
        long legacyClassTimestamp = parseClassTimestamp(classesNode, "lastChangedEpochSeconds");
        long primaryClassTimestamp = parseClassTimestamp(classesNode, "primaryLastChangedEpochSeconds");
        long secondaryClassTimestamp = parseClassTimestamp(classesNode, "secondaryLastChangedEpochSeconds");
        if (primaryClassTimestamp <= 0L && legacyClassTimestamp > 0L) {
            primaryClassTimestamp = legacyClassTimestamp;
        }
        if (secondaryClassTimestamp <= 0L && legacyClassTimestamp > 0L) {
            secondaryClassTimestamp = legacyClassTimestamp;
        }
        int maxClassSwitches = classManager != null ? classManager.getMaxClassSwitches() : -1;
        ParsedSwitchValue parsedPrimaryClassRemaining = parseClassRemainingSwitches(classesNode,
                "primaryRemainingSwitchCount",
                "primarySwitchCount",
                maxClassSwitches);
        ParsedSwitchValue parsedSecondaryClassRemaining = parseClassRemainingSwitches(classesNode,
                "secondaryRemainingSwitchCount",
                "secondarySwitchCount",
                maxClassSwitches);
        int legacyClassSwitchCount = parseInt(classesNode != null ? classesNode.get("switchCount") : null, -1);
        if (parsedPrimaryClassRemaining.value() < 0
                && parsedSecondaryClassRemaining.value() < 0
                && legacyClassSwitchCount >= 0) {
            int convertedRemaining = convertLegacyConsumedToRemaining(legacyClassSwitchCount, maxClassSwitches);
            if (secondaryClassTimestamp > 0L && primaryClassTimestamp <= 0L) {
                parsedSecondaryClassRemaining = new ParsedSwitchValue(convertedRemaining, true);
            } else {
                parsedPrimaryClassRemaining = new ParsedSwitchValue(convertedRemaining, true);
            }
        }

        int primaryClassRemaining = normalizeParsedRemaining(parsedPrimaryClassRemaining.value(), maxClassSwitches);
        int secondaryClassRemaining = normalizeParsedRemaining(parsedSecondaryClassRemaining.value(), maxClassSwitches);

        boolean classAntiExploitEnabled = classManager != null && classManager.isSwapAntiExploitConsumeEnabled();
        int classConsumeLevelThreshold = classManager != null ? classManager.getSwapAntiExploitConsumeLevelThreshold()
                : 0;

        if (parsedPrimaryClassRemaining.legacyDerived()) {
            primaryClassRemaining = applyLegacyMigrationSwapConsumption(
                    primaryClassRemaining,
                    maxClassSwitches,
                    profile.getLevel(),
                    hasAssignedSelection(primaryClassId),
                    classAntiExploitEnabled,
                    classConsumeLevelThreshold);
        }

        if (parsedSecondaryClassRemaining.legacyDerived()) {
            secondaryClassRemaining = applyLegacyMigrationSwapConsumption(
                    secondaryClassRemaining,
                    maxClassSwitches,
                    profile.getLevel(),
                    hasAssignedSelection(secondaryClassId),
                    classAntiExploitEnabled,
                    classConsumeLevelThreshold);
        }

        primaryClassRemaining = grantEmergencySwapForNoneSelection(primaryClassRemaining, maxClassSwitches,
                hasAssignedSelection(primaryClassId));
        secondaryClassRemaining = grantEmergencySwapForNoneSelection(secondaryClassRemaining, maxClassSwitches,
                hasAssignedSelection(secondaryClassId));

        profile.setLastPrimaryClassChangeEpochSeconds(primaryClassTimestamp);
        profile.setLastSecondaryClassChangeEpochSeconds(secondaryClassTimestamp);
        profile.setRemainingPrimaryClassSwitches(primaryClassRemaining);
        profile.setRemainingSecondaryClassSwitches(secondaryClassRemaining);
        boolean classAntiExploitConsumed = parseBoolean(classesNode != null
            ? classesNode.get("antiExploitConsumedAtLevel")
            : null, false);
        profile.setPrimaryClassSwapAntiExploitConsumedAtLevel(parseBoolean(classesNode != null
            ? classesNode.get("primaryAntiExploitConsumedAtLevel")
            : null, classAntiExploitConsumed));
        profile.setSecondaryClassSwapAntiExploitConsumedAtLevel(parseBoolean(classesNode != null
            ? classesNode.get("secondaryAntiExploitConsumedAtLevel")
            : null, classAntiExploitConsumed));
    }

    private int parseProfileIndex(String key) {
        if (key == null) {
            return -1;
        }
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private int parseActiveProfileIndex(Object node) {
        int parsed = parseInt(node, 1);
        if (!PlayerData.isValidProfileIndex(parsed)) {
            return 1;
        }
        return parsed;
    }

    private boolean loadPassiveLevels(PlayerProfile profile, Map<String, Object> node) {
        boolean loaded = false;
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            PassiveType type = resolvePassiveType(entry.getKey());
            if (type == null) {
                continue;
            }
            int level = parseInt(entry.getValue(), profile.getPassiveLevel(type));
            profile.setPassiveLevel(type, level);
            loaded = true;
        }
        return loaded;
    }

    private PassiveType resolvePassiveType(Object rawKey) {
        if (!(rawKey instanceof String key)) {
            return null;
        }
        String normalized = key.trim();
        for (PassiveType type : PassiveType.values()) {
            if (type.getConfigKey().equalsIgnoreCase(normalized)) {
                return type;
            }
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }

    private String parseString(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof String str && !str.isBlank()) {
                    result.add(str.trim());
                }
            }
            return result;
        }
        if (value instanceof String single && !single.isBlank()) {
            return List.of(single.trim());
        }
        return Collections.emptyList();
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private String parseRaceId(Object raceNode) {
        String directValue = coerceRaceString(raceNode);
        if (directValue != null) {
            return directValue;
        }

        Map<String, Object> raceMap = castToStringObjectMap(raceNode);
        if (raceMap == null) {
            return null;
        }

        String idValue = coerceRaceString(raceMap.get("id"));
        if (idValue != null) {
            return idValue;
        }

        String nameValue = coerceRaceString(raceMap.get("name"));
        if (nameValue != null) {
            return nameValue;
        }

        String legacyValue = coerceRaceString(raceMap.get("race"));
        if (legacyValue != null) {
            return legacyValue;
        }

        return null;
    }

    private long parseRaceLastChanged(Object raceNode) {
        Map<String, Object> raceMap = castToStringObjectMap(raceNode);
        if (raceMap == null) {
            return 0L;
        }

        Object raw = raceMap.get("lastChangedEpochSeconds");
        if (raw instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (raw instanceof String stringValue) {
            try {
                long parsed = Long.parseLong(stringValue.trim());
                return Math.max(0L, parsed);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private ParsedSwitchValue parseRaceRemainingSwitches(Object raceNode,
            Map<String, Object> profileSource,
            int maxSwitches) {
        Map<String, Object> raceMap = castToStringObjectMap(raceNode);
        if (raceMap != null) {
            int explicitRemaining = parseInt(raceMap.get("remainingSwitchCount"), -1);
            if (explicitRemaining >= 0) {
                return new ParsedSwitchValue(explicitRemaining, false);
            }

            int direct = parseInt(raceMap.get("switchCount"), -1);
            if (direct >= 0) {
                return new ParsedSwitchValue(convertLegacyConsumedToRemaining(direct, maxSwitches), true);
            }

            int alternate = parseInt(raceMap.get("raceSwitchCount"), -1);
            if (alternate >= 0) {
                return new ParsedSwitchValue(convertLegacyConsumedToRemaining(alternate, maxSwitches), true);
            }
        }

        if (profileSource != null) {
            int legacy = parseInt(profileSource.get("raceSwitchCount"), -1);
            if (legacy >= 0) {
                return new ParsedSwitchValue(convertLegacyConsumedToRemaining(legacy, maxSwitches), true);
            }
        }

        return new ParsedSwitchValue(defaultRemainingSwitches(maxSwitches), true);
    }

    private ParsedSwitchValue parseClassRemainingSwitches(Map<String, Object> classesNode,
            String remainingKey,
            String legacyConsumedKey,
            int maxSwitches) {
        if (classesNode != null) {
            int explicitRemaining = parseInt(classesNode.get(remainingKey), -1);
            if (explicitRemaining >= 0) {
                return new ParsedSwitchValue(explicitRemaining, false);
            }

            int legacyConsumed = parseInt(classesNode.get(legacyConsumedKey), -1);
            if (legacyConsumed >= 0) {
                return new ParsedSwitchValue(convertLegacyConsumedToRemaining(legacyConsumed, maxSwitches), true);
            }
        }

        return new ParsedSwitchValue(defaultRemainingSwitches(maxSwitches), true);
    }

    private int normalizeParsedRemaining(int parsedValue, int maxSwitches) {
        if (parsedValue >= 0) {
            return Math.max(0, parsedValue);
        }
        return defaultRemainingSwitches(maxSwitches);
    }

    private int convertLegacyConsumedToRemaining(int consumedCount, int maxSwitches) {
        int normalizedConsumed = Math.max(0, consumedCount);
        if (maxSwitches < 0) {
            return 0;
        }
        return Math.max(0, maxSwitches - normalizedConsumed);
    }

    private int defaultRemainingSwitches(int maxSwitches) {
        if (maxSwitches < 0) {
            return 0;
        }
        return Math.max(0, maxSwitches);
    }

    private int applyLegacyMigrationSwapConsumption(int remainingCount,
            int maxSwitches,
            int level,
            boolean hasAssignedSelection,
            boolean antiExploitEnabled,
            int consumeLevelThreshold) {
        if (maxSwitches < 0 || remainingCount <= 0) {
            return Math.max(0, remainingCount);
        }
        if (!hasAssignedSelection || !antiExploitEnabled) {
            return Math.max(0, remainingCount);
        }
        if (level < Math.max(1, consumeLevelThreshold)) {
            return Math.max(0, remainingCount);
        }
        return Math.max(0, remainingCount - 1);
    }

    private int grantEmergencySwapForNoneSelection(int remainingCount, int maxSwitches, boolean hasAssignedSelection) {
        int normalized = Math.max(0, remainingCount);
        if (hasAssignedSelection || maxSwitches <= 0 || normalized > 0) {
            return normalized;
        }
        return 1;
    }

    private boolean hasAssignedSelection(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return !"none".equalsIgnoreCase(id.trim());
    }

    private String parseClassId(Object classNode) {
        if (classNode instanceof String stringValue) {
            String trimmed = stringValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }

    private long parseClassTimestamp(Map<String, Object> classesNode, String key) {
        if (classesNode == null || key == null) {
            return 0L;
        }
        Object raw = classesNode.get(key);
        if (raw instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (raw instanceof String stringValue) {
            try {
                long parsed = Long.parseLong(stringValue.trim());
                return Math.max(0L, parsed);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private String coerceRaceString(Object value) {
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private void ensureValidRace(PlayerData data) {
        if (data == null) {
            return;
        }
        if (raceManager == null) {
            data.getProfiles().values().forEach(profile -> profile.setRaceId(null));
            data.setRaceId(null);
            return;
        }

        data.getProfiles().values().forEach(profile -> {
            String resolved = raceManager.resolveRaceIdentifier(profile.getRaceId());
            // Nullify stale race IDs that no longer exist in the server's race registry.
            if (resolved != null && raceManager.getRace(resolved) == null) {
                resolved = null;
            }
            profile.setRaceId(resolved);
        });

        raceManager.setPlayerRaceSilently(data, data.getRaceId());
    }

    private void ensureValidClasses(PlayerData data) {
        if (data == null) {
            return;
        }
        if (classManager == null || !classManager.isEnabled()) {
            data.getProfiles().values().forEach(profile -> {
                profile.setPrimaryClassId(null);
                profile.setSecondaryClassId(null);
            });
            data.setPrimaryClassId(null);
            data.setSecondaryClassId(null);
            return;
        }

        data.getProfiles().values().forEach(profile -> {
            String resolvedPrimary = classManager.resolvePrimaryClassIdentifier(profile.getPrimaryClassId());
            // Nullify stale class IDs that no longer exist in the server's class registry
            // so the emergency-swap mechanism can grant a swap for re-selection.
            if (resolvedPrimary != null && classManager.getClass(resolvedPrimary) == null) {
                resolvedPrimary = null;
            }
            profile.setPrimaryClassId(resolvedPrimary);
            String resolvedSecondary = classManager.resolveSecondaryClassIdentifier(profile.getSecondaryClassId());
            if (resolvedSecondary != null && classManager.getClass(resolvedSecondary) == null) {
                resolvedSecondary = null;
            }
            if (resolvedSecondary != null && resolvedSecondary.equalsIgnoreCase(resolvedPrimary)) {
                resolvedSecondary = null;
            }
            profile.setSecondaryClassId(resolvedSecondary);
        });

        classManager.setPlayerPrimaryClass(data, data.getPrimaryClassId());
        classManager.setPlayerSecondaryClass(data, data.getSecondaryClassId());
    }

    private String resolveRaceDisplayName(String raceId) {
        if (raceManager == null) {
            return raceId;
        }
        RaceDefinition definition = raceManager.getRace(raceId);
        if (definition == null) {
            definition = raceManager.getDefaultRace();
        }
        return definition != null ? definition.getDisplayName() : raceId;
    }

    private String resolveRacePathId(String raceId) {
        if (raceId == null || raceId.isBlank()) {
            return null;
        }
        if (raceManager == null) {
            return raceId.trim().toLowerCase();
        }
        return raceManager.resolveAscensionPathId(raceId);
    }

    private String resolveClassPathId(String classId) {
        if (classId == null || classId.isBlank()) {
            return null;
        }
        if (classManager == null) {
            return classId.trim().toLowerCase();
        }
        return classManager.resolveAscensionPathId(classId);
    }

    /**
     * Minimal loader used for leaderboards when a player has never joined
     * this server run.
     */
    private PlayerData loadFromFileMinimal(UUID uuid, File file) {
        try {
            String lower = file.getName().toLowerCase();
            Map<String, Object> map;
            if (lower.endsWith(".json")) {
                map = readPlayerDataJson(file);
            } else {
                map = readPlayerDataYaml(file);
            }

            if (map == null) {
                LOGGER.atWarning().log("Minimal load: empty playerdata for UUID %s in file %s", uuid,
                        file.getName());
                return null;
            }

            // Do NOT auto-migrate on minimal loads (leaderboards) to avoid touching files
            // during read-only operations. Full load will perform migration and persistence.
            String playerName = parseString(map.get("playerName"));
            if (playerName == null || playerName.isBlank()) {
                playerName = uuid.toString();
            }
            PlayerData data = new PlayerData(uuid, playerName, getStartingSkillPoints());
            applyConfigDefaults(data);

            Map<Integer, PlayerProfile> profiles = parseProfiles(map.get("profiles"), data.getBaseSkillPoints());
            if (profiles.isEmpty()) {
                profiles = buildLegacyProfiles(map, data.getBaseSkillPoints());
            }
            int activeProfileIndex = parseActiveProfileIndex(map.get("activeProfile"));
            data.loadProfilesFromStorage(profiles, activeProfileIndex);

            applyOptions(map, data);
            ensureValidRace(data);
            ensureValidClasses(data);

            return data;
        } catch (Exception e) {
            LOGGER.atSevere().log("Failed to minimally load PlayerData for UUID %s from %s: %s", uuid,
                    file.getName(), e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private int getStartingSkillPoints() {
        return skillManager != null ? skillManager.getBaseSkillPoints() : 0;
    }

    private Map<String, Object> buildPlayerDataMap(PlayerData data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(VersionRegistry.PLAYERDATA_VERSION_KEY, VersionRegistry.PLAYERDATA_SCHEMA_VERSION);
        map.put("playerName", data.getPlayerName());
        map.put("activeProfile", data.getActiveProfileIndex());

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("playerHud", data.isPlayerHudEnabled());
        options.put("criticalNotif", data.isCriticalNotifEnabled());
        options.put("xpNotif", data.isXpNotifEnabled());
        options.put("passiveLevelUpNotif", data.isPassiveLevelUpNotifEnabled());
        options.put("luckDoubleDropsNotif", data.isLuckDoubleDropsNotifEnabled());
        options.put("healthRegenNotif", data.isHealthRegenNotifEnabled());
        options.put("augmentNotif", data.isAugmentNotifEnabled());
        options.put("useRaceModel", data.isUseRaceModel());
        options.put("supportPveMode", data.isSupportPveMode());
        options.put("necromancerPveMode", data.isNecromancerPveMode());
        options.put("language", data.getLanguage());
        map.put("options", options);

        map.put("profiles", buildProfilesSection(data));
        return map;
    }

    private Map<String, Object> buildProfilesSection(PlayerData data) {
        Map<String, Object> profilesSection = new LinkedHashMap<>();
        data.getProfiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int index = entry.getKey();
                    PlayerProfile profile = entry.getValue();
                    if (profile == null) {
                        return;
                    }

                    Map<String, Object> profileMap = new LinkedHashMap<>();
                    profileMap.put("xp", profile.getXp());
                    profileMap.put("level", profile.getLevel());
                    profileMap.put("prestige", profile.getPrestigeLevel());
                    profileMap.put("skillPoints", profile.getSkillPoints());
                    profileMap.put("name", profile.getName());

                    Map<String, Integer> profileAttrs = new LinkedHashMap<>();
                    for (SkillAttributeType type : SkillAttributeType.values()) {
                        profileAttrs.put(type.name(),
                                profile.getAttributes().getOrDefault(type, 0));
                    }
                    profileMap.put("attributes", profileAttrs);

                    Map<String, Object> autoAllocateSection = new LinkedHashMap<>();
                    autoAllocateSection.put("pointsPerLevel", profile.getAutoAllocatePointsPerLevel());
                    for (SkillAttributeType type : SkillAttributeType.values()) {
                        if (profile.isAutoAllocateEnabled(type)) {
                            autoAllocateSection.put("selectedAttribute", type.name());
                            break;
                        }
                    }
                    profileMap.put("autoAllocate", autoAllocateSection);

                    Map<String, Object> raceSection = new LinkedHashMap<>();
                    String raceId = profile.getRaceId();
                    raceSection.put("id", raceId);
                    String raceDisplay = resolveRaceDisplayName(raceId);
                    if (raceDisplay != null && !raceDisplay.equalsIgnoreCase(raceId)) {
                        raceSection.put("name", raceDisplay);
                    }
                    raceSection.put("lastChangedEpochSeconds", profile.getLastRaceChangeEpochSeconds());
                    raceSection.put("remainingSwitchCount", profile.getRemainingRaceSwitches());
                    raceSection.put("antiExploitConsumedAtLevel", profile.isRaceSwapAntiExploitConsumedAtLevel());
                    if (!profile.getCompletedRaceFormsSnapshot().isEmpty()) {
                        raceSection.put("completedForms", profile.getCompletedRaceFormsSnapshot());
                    }
                    profileMap.put("race", raceSection);

                    Map<String, Object> classesSection = new LinkedHashMap<>();
                    classesSection.put("primary", profile.getPrimaryClassId());
                    if (profile.getSecondaryClassId() != null) {
                        classesSection.put("secondary", profile.getSecondaryClassId());
                    }
                    if (!profile.getCompletedClassFormsSnapshot().isEmpty()) {
                        classesSection.put("completedForms", profile.getCompletedClassFormsSnapshot());
                    }
                    long primaryChanged = profile.getLastPrimaryClassChangeEpochSeconds();
                    long secondaryChanged = profile.getLastSecondaryClassChangeEpochSeconds();
                    int primarySwitchCount = profile.getRemainingPrimaryClassSwitches();
                    int secondarySwitchCount = profile.getRemainingSecondaryClassSwitches();
                    classesSection.put("primaryLastChangedEpochSeconds", primaryChanged);
                    classesSection.put("secondaryLastChangedEpochSeconds", secondaryChanged);
                    classesSection.put("primaryRemainingSwitchCount", primarySwitchCount);
                    classesSection.put("secondaryRemainingSwitchCount", secondarySwitchCount);
                        classesSection.put("primaryAntiExploitConsumedAtLevel",
                            profile.isPrimaryClassSwapAntiExploitConsumedAtLevel());
                        classesSection.put("secondaryAntiExploitConsumedAtLevel",
                            profile.isSecondaryClassSwapAntiExploitConsumedAtLevel());
                    classesSection.put("lastChangedEpochSeconds", Math.max(primaryChanged, secondaryChanged));
                    profileMap.put("classes", classesSection);

                    Map<String, Object> profileAugmentOffers = new LinkedHashMap<>();
                    profile.getAugmentOffers().forEach((tier, offers) -> {
                        if (offers != null && !offers.isEmpty()) {
                            profileAugmentOffers.put(tier, new ArrayList<>(offers));
                        }
                    });
                    if (!profileAugmentOffers.isEmpty()) {
                        profileMap.put("augmentOffers", profileAugmentOffers);
                    }

                    Map<String, Object> profileSelectedAugments = new LinkedHashMap<>();
                    profile.getSelectedAugments().forEach((tier, augmentId) -> {
                        if (augmentId != null && !augmentId.isBlank()) {
                            profileSelectedAugments.put(tier, augmentId);
                        }
                    });
                    if (!profileSelectedAugments.isEmpty()) {
                        profileMap.put("selectedAugments", profileSelectedAugments);
                    }

                    Map<String, Object> profileAugmentValueRolls = new LinkedHashMap<>();
                    profile.getAugmentValueRolls().forEach((selectionKey, rolls) -> {
                        if (selectionKey == null || selectionKey.isBlank() || rolls == null || rolls.isEmpty()) {
                            return;
                        }

                        Map<String, Double> serializedRolls = new LinkedHashMap<>();
                        rolls.forEach((rollKey, value) -> {
                            if (rollKey == null || rollKey.isBlank() || value == null || !Double.isFinite(value)) {
                                return;
                            }
                            serializedRolls.put(rollKey, value);
                        });

                        if (!serializedRolls.isEmpty()) {
                            profileAugmentValueRolls.put(selectionKey, serializedRolls);
                        }
                    });
                    if (!profileAugmentValueRolls.isEmpty()) {
                        profileMap.put("augmentValueRolls", profileAugmentValueRolls);
                    }

                    Map<String, Integer> profileRerollsUsed = new LinkedHashMap<>();
                    profile.getAugmentRerollsUsed().forEach((tier, used) -> {
                        int normalized = Math.max(0, used == null ? 0 : used);
                        if (normalized > 0) {
                            profileRerollsUsed.put(tier, normalized);
                        }
                    });
                    if (!profileRerollsUsed.isEmpty()) {
                        profileMap.put("augmentRerollsUsed", profileRerollsUsed);
                    }

                    Map<String, Integer> profileRerollsBonus = new LinkedHashMap<>();
                    profile.getAugmentRerollsBonus().forEach((tier, bonus) -> {
                        int normalized = Math.max(0, bonus == null ? 0 : bonus);
                        if (normalized > 0) {
                            profileRerollsBonus.put(tier, normalized);
                        }
                    });
                    if (!profileRerollsBonus.isEmpty()) {
                        profileMap.put("augmentRerollsBonus", profileRerollsBonus);
                    }

                    Map<String, Integer> profilePassives = new LinkedHashMap<>();
                    profile.getPassiveLevels().forEach((type, level) -> {
                        int normalized = Math.max(0, level);
                        if (normalized > 0) {
                            profilePassives.put(type.getConfigKey(), normalized);
                        }
                    });
                    if (!profilePassives.isEmpty()) {
                        profileMap.put("passives", profilePassives);
                    }

                    profilesSection.put(String.valueOf(index), profileMap);
                });

        return profilesSection;
    }

    private ReentrantLock lockFor(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, key -> new ReentrantLock());
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

    private Map<String, Object> readPlayerDataJson(File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return PLAYERDATA_GSON.fromJson(reader, STRING_OBJECT_MAP_TYPE);
        }
    }

    private Map<String, Object> readPlayerDataYaml(File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return createYaml().load(reader);
        }
    }

    private UUID parseUuidFromPlayerDataFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".json") && !lower.endsWith(".yml") && !lower.endsWith(".yaml")) {
            return null;
        }

        Matcher matcher = UUID_IN_PLAYERDATA_FILE_PATTERN.matcher(fileName);
        if (!matcher.find()) {
            return null;
        }
        String uuidPart = matcher.group(1);

        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void migrateLegacyPlayerDataFiles() {
        File folder = filesManager != null ? filesManager.getPlayerDataFolder() : null;
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return;
        }

        File[] legacyFiles = folder.listFiles((dir, name) -> {
            if (name == null) {
                return false;
            }
            String lower = name.toLowerCase();
            return lower.endsWith(".yml") || lower.endsWith(".yaml");
        });
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }

        int migrated = 0;
        int archivedOnly = 0;
        for (File legacyFile : legacyFiles) {
            UUID uuid = parseUuidFromPlayerDataFileName(legacyFile.getName());
            if (uuid == null) {
                continue;
            }

            ReentrantLock lock = lockFor(uuid);
            lock.lock();
            try {
                File jsonFile = filesManager.getPlayerDataFile(uuid);
                if (jsonFile.exists()) {
                    if (archiveAndDeleteLegacyYamlIfJsonPresent(uuid, legacyFile, jsonFile)) {
                        archivedOnly++;
                    }
                    continue;
                }

                PlayerData data = loadFromLegacyYamlFile(uuid, uuid.toString(), legacyFile);
                if (data == null) {
                    continue;
                }

                save(data);
                if (archiveAndDeleteLegacyYamlIfJsonPresent(uuid, legacyFile, jsonFile)) {
                    migrated++;
                }
            } finally {
                lock.unlock();
            }
        }

        if (migrated > 0) {
            LOGGER.atInfo().log("Migrated %d legacy playerdata YAML file(s) to JSON.", migrated);
        }
        if (archivedOnly > 0) {
            LOGGER.atInfo().log("Archived and removed %d legacy playerdata YAML file(s) that already had JSON.",
                    archivedOnly);
        }
    }

    private boolean archiveAndDeleteLegacyYamlIfJsonPresent(UUID uuid, File legacyYamlFile, File jsonFile) {
        if (legacyYamlFile == null || !legacyYamlFile.exists() || jsonFile == null || !jsonFile.exists()) {
            return false;
        }

        try {
            Path centralizedArchivePath = filesManager.archiveFileIfExists(
                    legacyYamlFile,
                    "playerdata/old-playerdata-yml/" + legacyYamlFile.getName(),
                    "playerdata.format:yml");
            Path localArchivePath = copyLegacyYamlToLocalFailsafeFolder(legacyYamlFile);

            // Never remove the original YAML unless at least one backup copy exists.
            if (centralizedArchivePath == null && localArchivePath == null) {
                LOGGER.atWarning().log(
                        "Skipping removal of legacy YAML playerdata %s because no backup could be created.",
                        legacyYamlFile.getName());
                return false;
            }

            Files.deleteIfExists(legacyYamlFile.toPath());
            LOGGER.atInfo().log("Archived and removed legacy YAML playerdata for UUID %s (central=%s, local=%s)",
                    uuid,
                    centralizedArchivePath == null ? "none" : centralizedArchivePath,
                    localArchivePath == null ? "none" : localArchivePath);
            return true;
        } catch (Exception ex) {
            LOGGER.atWarning().log("Failed to remove legacy YAML playerdata %s after JSON migration: %s",
                    legacyYamlFile.getName(), ex.getMessage());
            return false;
        }
    }

    private Path copyLegacyYamlToLocalFailsafeFolder(File legacyYamlFile) {
        if (legacyYamlFile == null || !legacyYamlFile.exists() || filesManager == null) {
            return null;
        }

        File archiveFolder = filesManager.getLegacyPlayerDataArchiveFolder();
        if (archiveFolder == null) {
            return null;
        }

        try {
            Files.createDirectories(archiveFolder.toPath());

            String originalName = legacyYamlFile.getName();
            String baseName = originalName.toLowerCase().endsWith(".yml")
                    ? originalName.substring(0, originalName.length() - 4)
                    : originalName;
            String stamp = LocalDateTime.now().format(LEGACY_YML_BACKUP_FORMATTER);
            Path archivePath = archiveFolder.toPath().resolve(baseName + "-" + stamp + ".yml");

            Files.copy(legacyYamlFile.toPath(), archivePath, StandardCopyOption.REPLACE_EXISTING);
            return archivePath;
        } catch (Exception ex) {
            LOGGER.atWarning().log("Failed to copy legacy YAML %s into local fail-safe folder: %s",
                    legacyYamlFile.getName(), ex.getMessage());
            return null;
        }
    }

    private Path backupCorruptFile(File file, String reason) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            Path source = file.toPath();
            String suffix = ".corrupt-" + System.currentTimeMillis();
            Path backup = source.resolveSibling(source.getFileName().toString() + suffix);
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.atWarning().log("Backed up potentially corrupt playerdata %s to %s (%s)", source, backup, reason);
            return backup;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to back up corrupt playerdata %s (%s): %s", file.getName(), reason,
                    e.getMessage());
            return null;
        }
    }

    private void applyOptions(Map<String, Object> map, PlayerData data) {
        Map<String, Object> options = castToStringObjectMap(map.get("options"));
        Object playerHud = options != null ? options.get("playerHud") : map.get("playerHud");
        Object criticalNotif = options != null ? options.get("criticalNotif") : map.get("criticalNotif");
        Object xpNotif = options != null ? options.get("xpNotif") : map.get("xpNotif");
        Object passiveLevelUpNotif = options != null ? options.get("passiveLevelUpNotif")
                : map.get("passiveLevelUpNotif");
        Object luckDoubleDropsNotif = options != null ? options.get("luckDoubleDropsNotif")
                : map.get("luckDoubleDropsNotif");
        Object healthRegenNotif = options != null ? options.get("healthRegenNotif")
                : map.get("healthRegenNotif");
        Object augmentNotif = options != null ? options.get("augmentNotif") : map.get("augmentNotif");
        Object useRaceModel = options != null ? options.get("useRaceModel") : map.get("useRaceModel");
        Object supportPveMode = options != null ? options.get("supportPveMode") : map.get("supportPveMode");
        Object necromancerPveMode = options != null ? options.get("necromancerPveMode") : map.get("necromancerPveMode");
        Object language = options != null ? options.get("language") : map.get("language");
        data.setPlayerHudEnabled(parseBoolean(playerHud, true));
        data.setCriticalNotifEnabled(parseBoolean(criticalNotif, true));
        data.setXpNotifEnabled(parseBoolean(xpNotif, true));
        data.setPassiveLevelUpNotifEnabled(parseBoolean(passiveLevelUpNotif, true));
        data.setLuckDoubleDropsNotifEnabled(parseBoolean(luckDoubleDropsNotif, true));
        data.setHealthRegenNotifEnabled(parseBoolean(healthRegenNotif, true));
        data.setAugmentNotifEnabled(parseBoolean(augmentNotif, true));
        data.setSupportPveMode(parseBoolean(supportPveMode, true));
        data.setNecromancerPveMode(parseBoolean(necromancerPveMode, true));
        String configuredLanguage = parseString(language);
        if (configuredLanguage == null || configuredLanguage.isBlank()) {
            data.setLanguage(resolveConfiguredDefaultLanguage());
        } else {
            data.setLanguage(configuredLanguage);
        }
        boolean useRaceModelValue = parseBoolean(useRaceModel, defaultUseRaceModel());
        if (isRaceModelGloballyDisabled()) {
            useRaceModelValue = false;
        }
        data.setUseRaceModel(useRaceModelValue);
    }

    /**
     * Quick safety check: ensure emitted JSON can be parsed back.
     * If this fails we skip writing to avoid corrupting the on-disk file.
     */
    private boolean isJsonRoundTripSafe(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return false;
        }
        try (StringReader reader = new StringReader(jsonContent)) {
            Object loaded = PLAYERDATA_GSON.fromJson(reader, STRING_OBJECT_MAP_TYPE);
            return loaded instanceof Map<?, ?>;
        } catch (Exception ex) {
            LOGGER.atSevere().log("Round-trip JSON validation failed: %s", ex.getMessage());
            return false;
        }
    }

    private Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options);
    }

    /**
     * Rolling backup policy: keep the latest few copies per player in
     * plugins/EndlessLeveling/playerdata/backups/auto/<uuid>/.
     * - Throttled to avoid copying on every save (10m default).
     * - Retention limited to AUTO_BACKUP_RETENTION newest files.
     */
    private void createAutoBackupIfNeeded(File sourceFile, UUID uuid) {
        if (sourceFile == null || uuid == null) {
            return;
        }
        if (!sourceFile.exists()) {
            return; // nothing on disk yet to back up
        }

        long now = System.currentTimeMillis();
        long last = lastAutoBackupMs.getOrDefault(uuid, 0L);
        if (now - last < AUTO_BACKUP_MIN_INTERVAL_MS) {
            return; // throttle
        }

        Path backupDir = filesManager.getPlayerDataFolder().toPath()
                .resolve("backups")
                .resolve("auto")
                .resolve(uuid.toString());
        try {
            Files.createDirectories(backupDir);
            String timestamp = LocalDateTime.now().format(AUTO_BACKUP_FORMATTER);
            Path target = backupDir.resolve(sourceFile.getName() + "." + timestamp + ".bak");
            Files.copy(sourceFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            lastAutoBackupMs.put(uuid, now);
            pruneAutoBackups(backupDir);
            LOGGER.atFine().log("Auto-backed up playerdata %s to %s", sourceFile.getName(), target);
        } catch (Exception ex) {
            LOGGER.atWarning().log("Auto-backup failed for %s: %s", sourceFile.getName(), ex.getMessage());
        }
    }

    private void pruneAutoBackups(Path backupDir) {
        try (Stream<Path> stream = Files.list(backupDir)) {
            var backups = stream
                    .filter(Files::isRegularFile)
                    .sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()))
                    .collect(Collectors.toList());

            for (int i = AUTO_BACKUP_RETENTION; i < backups.size(); i++) {
                try {
                    Files.deleteIfExists(backups.get(i));
                } catch (Exception ignored) {
                    // Best-effort cleanup
                }
            }
        } catch (Exception ex) {
            LOGGER.atWarning().log("Failed to prune auto-backups in %s: %s", backupDir, ex.getMessage());
        }
    }

}
