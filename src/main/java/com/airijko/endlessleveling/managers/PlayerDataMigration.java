package com.airijko.endlessleveling.managers;

import org.yaml.snakeyaml.Yaml;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerDataMigration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private PlayerDataMigration() {
    }

    /**
     * Migrate a playerdata YAML map in-place on disk if it's older than
     * {@code currentVersion}. Creates a timestamped backup of the original
     * file before writing. Returns the migrated map (or the original if no
     * migration was necessary).
     */
    public static Map<String, Object> migrateIfNeeded(File file, Map<String, Object> originalMap, Yaml yaml,
            int currentVersion) {
        int fileVersion = 1;
        Object versionObj = originalMap.get("version");
        boolean missingVersion = false;
        if (versionObj instanceof Number) {
            fileVersion = ((Number) versionObj).intValue();
        } else if (versionObj instanceof String) {
            try {
                fileVersion = Integer.parseInt((String) versionObj);
            } catch (NumberFormatException ignored) {
            }
        } else {
            // missing or null version -> mark as missing so we still back up
            missingVersion = true;
            fileVersion = 1; // assume initial schema
        }

        // If the file is already at or above current version and it had a
        // version tag, nothing to do. If it was missing a version tag we
        // still proceed so we can back it up and normalize it.
        if (fileVersion >= currentVersion && !missingVersion) {
            return originalMap;
        }

        // Backup original file into backups/<timestamp>/ so multiple files
        // can be grouped per migration run.
        try {
            // use date + hour + minute (no seconds) as requested
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
            Path backupsRoot = file.toPath().getParent().resolve("backups");
            // ensure the backups root and dated folder exist
            Path dated = backupsRoot.resolve(timestamp);
            try {
                Files.createDirectories(dated);
            } catch (Exception dirEx) {
                LOGGER.atWarning().log("Failed to create backup directories for %s: %s", file.getName(),
                        dirEx.getMessage());
                throw dirEx;
            }

            Path backupPath = dated.resolve(file.getName());
            Files.copy(file.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.atInfo().log("Backed up %s to %s before migration.", file.getName(), backupPath.toString());

        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to backup %s before migration: %s", file.getName(), e.getMessage());
        }

        Map<String, Object> migrated = new LinkedHashMap<>(originalMap);
        int v = fileVersion;
        while (v < currentVersion) {
            switch (v) {
                case 1 -> {
                    // v1 -> v2: add 'prestige' default 0
                    if (!migrated.containsKey("prestige")) {
                        migrated.put("prestige", 0);
                    }
                    v = 2;
                    migrated.put("version", v);
                    LOGGER.atInfo().log("Migrated %s from v1 to v2.", file.getName());
                }
                default -> {
                    v++;
                    migrated.put("version", v);
                    LOGGER.atInfo().log("Bumped %s to version %d (default migration).", file.getName(), v);
                }
            }
        }

        // Write migrated YAML back to disk
        try (StringWriter buffer = new StringWriter(); FileWriter writer = new FileWriter(file)) {
            yaml.dump(migrated, buffer);
            String yamlContent = buffer.toString()
                    .replace("\nattributes:", "\n\nattributes:")
                    .replace("\noptions:", "\n\noptions:")
                    .replace("\npassives:", "\n\npassives:");
            writer.write(yamlContent);
            LOGGER.atInfo().log("Wrote migrated PlayerData to %s (now v%d).", file.getName(), v);
        } catch (Exception e) {
            LOGGER.atSevere().log("Failed to write migrated PlayerData for %s: %s", file.getName(), e.getMessage());
            e.printStackTrace();
        }

        return migrated;
    }
}
