package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.managers.PluginFilesManager;
import com.airijko.endlessleveling.managers.VersionRegistry;
import com.airijko.endlessleveling.augments.types.CommonAugment;
import com.hypixel.hytale.logger.HytaleLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AugmentManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final Yaml yaml;
    private final Path root;
    private final PluginFilesManager filesManager;
    private final boolean forceBuiltinAugments;
    private final Map<String, AugmentDefinition> externalDefinitions;
    private volatile Map<String, AugmentDefinition> fileDefinitions;
    private volatile Map<String, AugmentDefinition> cache;

    public AugmentManager(Path root, PluginFilesManager filesManager, ConfigManager configManager) {
        this.yaml = new Yaml();
        this.root = Objects.requireNonNull(root, "root");
        this.filesManager = Objects.requireNonNull(filesManager, "filesManager");
        Object forceFlag = configManager != null
                ? configManager.get("force_builtin_augments", Boolean.TRUE, false)
                : Boolean.TRUE;
        this.forceBuiltinAugments = parseBoolean(forceFlag, true);
        this.externalDefinitions = new LinkedHashMap<>();
        this.fileDefinitions = Collections.emptyMap();
        this.cache = Collections.emptyMap();
    }

    public synchronized void load() {
        syncBuiltinAugmentsIfNeeded();
        if (!Files.isDirectory(root)) {
            LOGGER.atWarning().log("Augment directory %s does not exist or is not a directory", root);
            this.fileDefinitions = Collections.emptyMap();
            rebuildCache();
            return;
        }
        Map<String, AugmentDefinition> loaded = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(root)) {
            List<Path> yamlFiles = paths
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".yml") || name.endsWith(".yaml") || !name.contains(".");
                    })
                    .collect(Collectors.toList());
            for (Path file : yamlFiles) {
                try {
                    AugmentDefinition def = AugmentParser.parse(file, yaml);
                    String augmentId = normalizeRegisteredId(def.getId());
                    if (augmentId == null) {
                        LOGGER.atWarning().log("Skipping augment %s because its id is blank", file.getFileName());
                        continue;
                    }
                    loaded.put(augmentId, def);
                } catch (Exception ex) {
                    LOGGER.atWarning().withCause(ex).log("Failed to parse augment %s", file.getFileName());
                }
            }
        } catch (IOException ex) {
            LOGGER.atSevere().withCause(ex).log("Error reading augment directory %s", root);
        }
        this.fileDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        rebuildCache();
        LOGGER.atInfo().log("Loaded %d augments from %s", cache.size(), root);
    }

    public Map<String, AugmentDefinition> getAugments() {
        return cache;
    }

    public AugmentDefinition getAugment(String id) {
        return cache.get(resolveLookupId(id));
    }

    public Augment createAugment(String id) {
        AugmentDefinition definition = cache.get(resolveLookupId(id));
        if (definition == null) {
            return null;
        }
        return AugmentRegistry.create(definition);
    }

    public synchronized boolean canRegisterExternalAugment(String id, boolean replaceExisting) {
        String augmentId = normalizeRegisteredId(id);
        if (augmentId == null) {
            return false;
        }
        return replaceExisting || !cache.containsKey(augmentId);
    }

    public synchronized void registerExternalAugment(AugmentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String augmentId = requireRegisteredId(definition.getId());
        boolean overridingFileDefinition = fileDefinitions.containsKey(augmentId);
        externalDefinitions.put(augmentId, definition);
        rebuildCache();
        if (overridingFileDefinition) {
            LOGGER.atInfo().log("Registered API augment '%s' and overrode the file-backed definition", augmentId);
        } else {
            LOGGER.atInfo().log("Registered API augment '%s'", augmentId);
        }
    }

    public synchronized boolean unregisterExternalAugment(String id) {
        String augmentId = normalizeRegisteredId(id);
        if (augmentId == null) {
            return false;
        }
        AugmentDefinition removed = externalDefinitions.remove(augmentId);
        if (removed == null) {
            return false;
        }
        rebuildCache();
        LOGGER.atInfo().log("Unregistered API augment '%s'", augmentId);
        return true;
    }

    private String resolveLookupId(String id) {
        if (id == null || id.isBlank()) {
            return id;
        }
        String candidate = id.trim();
        String resolved = CommonAugment.resolveBaseAugmentId(candidate);
        return resolved == null ? candidate : resolved;
    }

    private void syncBuiltinAugmentsIfNeeded() {
        if (!forceBuiltinAugments) {
            return;
        }

        File augmentsFolder = filesManager.getAugmentsFolder();
        if (augmentsFolder == null) {
            LOGGER.atWarning().log("Augments folder is null; cannot sync built-in augments.");
            return;
        }

        int storedVersion = readAugmentsVersion(augmentsFolder);
        if (storedVersion == VersionRegistry.BUILTIN_AUGMENTS_VERSION) {
            return;
        }

        filesManager.archivePathIfExists(augmentsFolder.toPath(), "augments", "augments.version:" + storedVersion);
        clearDirectory(augmentsFolder.toPath());
        filesManager.exportResourceDirectory("augments", augmentsFolder, true);
        writeAugmentsVersion(augmentsFolder, VersionRegistry.BUILTIN_AUGMENTS_VERSION);
        LOGGER.atInfo().log("Synced built-in augments to version %d (force_builtin_augments=true)",
                VersionRegistry.BUILTIN_AUGMENTS_VERSION);
    }

    private int readAugmentsVersion(File augmentsFolder) {
        Path versionPath = augmentsFolder.toPath().resolve(VersionRegistry.AUGMENTS_VERSION_FILE);
        if (!Files.exists(versionPath)) {
            return -1;
        }
        try {
            String text = Files.readString(versionPath).trim();
            return Integer.parseInt(text);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to read augments version file: %s", e.getMessage());
            return -1;
        }
    }

    private void writeAugmentsVersion(File augmentsFolder, int version) {
        Path versionPath = augmentsFolder.toPath().resolve(VersionRegistry.AUGMENTS_VERSION_FILE);
        try {
            Files.writeString(versionPath, Integer.toString(version));
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to write augments version file: %s", e.getMessage());
        }
    }

    private void clearDirectory(Path folder) {
        if (folder == null || !Files.exists(folder)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(folder))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            LOGGER.atWarning().log("Failed to delete %s: %s", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to clear augments directory: %s", e.getMessage());
        }
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str.trim());
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return defaultValue;
    }

    private void rebuildCache() {
        Map<String, AugmentDefinition> merged = new LinkedHashMap<>(fileDefinitions);
        merged.putAll(externalDefinitions);
        this.cache = Collections.unmodifiableMap(merged);
    }

    private String requireRegisteredId(String id) {
        String augmentId = normalizeRegisteredId(id);
        if (augmentId == null) {
            throw new IllegalArgumentException("augment id cannot be null or blank");
        }
        return augmentId;
    }

    private String normalizeRegisteredId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
