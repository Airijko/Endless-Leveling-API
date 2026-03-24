package com.airijko.endlessleveling.leveling;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.managers.PluginFilesManager;

/**
 * Backwards-compatible wrapper around {@link ConfigManager} for leveling.yml.
 *
 * This guarantees leveling.yml uses the same migration + template-preserving
 * writer logic as config.yml/worlds.yml and other YAML resources.
 */
public class LevelingConfigManager {

    private final ConfigManager delegate;

    public LevelingConfigManager(java.io.File levelingFile) {
        this(null, levelingFile);
    }

    public LevelingConfigManager(PluginFilesManager filesManager, java.io.File levelingFile) {
        this.delegate = new ConfigManager(filesManager, levelingFile);
    }

    public void load() {
        delegate.load();
    }

    public int getInt(String key, int defaultValue) {
        Object value = delegate.get(key, defaultValue, false);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public Object get(String key, Object defaultValue) {
        return delegate.get(key, defaultValue, false);
    }
}
