package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Language manager that loads translations from the mod's .lang files at
 * {@code Server/Languages/{locale}/endlessleveling.lang}.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Deployed .lang file on disk (allows server operator customisation)</li>
 *   <li>Bundled .lang file from the JAR (guaranteed baseline)</li>
 *   <li>Hytale I18nModule (in case the asset-pack has additional keys)</li>
 * </ol>
 */
public class LanguageManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String DEFAULT_LOCALE = "en_US";
    /** Prefix that Hytale's I18n system applies to keys from endlessleveling.lang */
    public static final String I18N_KEY_PREFIX = "endlessleveling.";
    private static final String LANG_RESOURCE_DIR = "Server/Languages/";
    private static final String LANG_FILE_NAME = "endlessleveling.lang";
    /** Bundled locales in Hytale format (must match src/main/resources/Server/Languages/ directories). */
    private static final List<String> BUNDLED_HYTALE_LOCALES = List.of(
            "de-DE", "en-US", "es-ES", "fr-FR", "hu-HU",
            "it-IT", "pl-PL", "pt-BR", "tr-TR");

    private final ConfigManager configManager;
    /** Deployed Server/Languages/ directory on disk (mod root). */
    private final Path serverLanguagesDir;

    private volatile String activeLocale = DEFAULT_LOCALE;
    private volatile String fallbackLocale = DEFAULT_LOCALE;
    private volatile Map<String, String> activeTranslations = Collections.emptyMap();
    private volatile Map<String, String> fallbackTranslations = Collections.emptyMap();
    private final Map<String, Map<String, String>> localeCache = new ConcurrentHashMap<>();

    public LanguageManager(PluginFilesManager filesManager, ConfigManager configManager) {
        this.configManager = configManager;
        this.serverLanguagesDir = filesManager.getPluginFolder().toPath()
                .resolve("Server").resolve("Languages");
        reload();
    }

    public void reload() {
        String configuredLocale = normalizeLocale(Objects.toString(
                configManager != null ? configManager.get("language.locale", DEFAULT_LOCALE, false) : DEFAULT_LOCALE,
                DEFAULT_LOCALE));

        String configuredFallback = normalizeLocale(Objects.toString(
                configManager != null ? configManager.get("language.fallback_locale", DEFAULT_LOCALE, false)
                        : DEFAULT_LOCALE,
                DEFAULT_LOCALE));

        localeCache.clear();

        Map<String, String> loadedFallback = loadLangTranslations(configuredFallback);
        Map<String, String> loadedActive;
        if (configuredLocale.equalsIgnoreCase(configuredFallback)) {
            loadedActive = loadedFallback;
        } else {
            loadedActive = loadLangTranslations(configuredLocale);
        }

        fallbackLocale = configuredFallback;
        activeLocale = configuredLocale;
        fallbackTranslations = loadedFallback;
        activeTranslations = loadedActive;

        LOGGER.atInfo().log("Language loaded: locale=%s, fallback=%s, lang_keys=%d",
                activeLocale, fallbackLocale, activeTranslations.size());
    }

    public String getActiveLocale() {
        return activeLocale;
    }

    public List<String> getAvailableLocales() {
        // Start from bundled JAR locales (always available)
        List<String> locales = new ArrayList<>();
        for (String hytaleLocale : BUNDLED_HYTALE_LOCALES) {
            locales.add(fromHytaleLocale(hytaleLocale));
        }

        // Add any additional locales from the deployed Server/Languages/ directory
        if (Files.isDirectory(serverLanguagesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(serverLanguagesDir,
                    entry -> Files.isDirectory(entry)
                            && Files.exists(entry.resolve(LANG_FILE_NAME)))) {
                for (Path dir : stream) {
                    String locale = fromHytaleLocale(dir.getFileName().toString());
                    if (!locales.contains(locale)) {
                        locales.add(locale);
                    }
                }
            } catch (IOException e) {
                LOGGER.atWarning().log("Failed to scan language directories: %s", e.getMessage());
            }
        }

        locales.sort(Comparator.naturalOrder());
        return Collections.unmodifiableList(locales);
    }

    public boolean isLocaleAvailable(String locale) {
        String hytaleLocale = toHytaleLocale(normalizeLocale(locale));
        // Deployed directory check
        Path langFile = serverLanguagesDir.resolve(hytaleLocale).resolve(LANG_FILE_NAME);
        if (Files.exists(langFile)) {
            return true;
        }
        // JAR resource check
        String resourcePath = LANG_RESOURCE_DIR + hytaleLocale + "/" + LANG_FILE_NAME;
        return LanguageManager.class.getClassLoader().getResource(resourcePath) != null;
    }

    public void invalidateLocaleCache(String locale) {
        String normalized = normalizeLocale(locale);
        localeCache.remove(normalized);
    }

    /**
     * Translates a key using the server's active locale.
     * Resolution order:
     * 1. .lang translations (active locale)
     * 2. Hytale I18nModule (active locale, prefixed key)
     * 3. .lang translations (fallback locale)
     * 4. Hytale I18nModule (fallback locale, prefixed key)
     * 5. Hytale I18nModule (en-US)
     * 6. Provided fallback string
     * 7. The key itself
     */
    public String tr(String key, String fallback, Object... args) {
        String template = resolveTemplate(key, fallback);
        return format(template, args);
    }

    /**
     * Translates a key using a specific player's preferred locale.
     */
    public String tr(UUID playerUuid, String key, String fallback, Object... args) {
        String locale = resolvePlayerLocale(playerUuid);
        String template = resolveTemplate(locale, key, fallback);
        return format(template, args);
    }

    /**
     * Resolves a translation key via Hytale's I18nModule directly.
     * Returns null if the key is not found.
     */
    @Nullable
    public static String i18n(@Nullable String language, String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            return null;
        }
        String hytaleLocale = toHytaleLocale(language);
        String prefixedKey = I18N_KEY_PREFIX + key;
        return i18n.getMessage(hytaleLocale, prefixedKey);
    }

    /**
     * Resolves a fully-qualified I18n key (already prefixed with "endlessleveling.").
     */
    @Nullable
    public static String i18nFullKey(@Nullable String language, String fullKey) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            return null;
        }
        return i18n.getMessage(toHytaleLocale(language), fullKey);
    }

    private String resolvePlayerLocale(UUID playerUuid) {
        if (playerUuid == null) {
            return activeLocale;
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null || plugin.getPlayerDataManager() == null) {
            return activeLocale;
        }

        PlayerData playerData = plugin.getPlayerDataManager().get(playerUuid);
        if (playerData == null) {
            return activeLocale;
        }

        String preferred = playerData.getLanguage();
        if (preferred == null || preferred.isBlank()) {
            return activeLocale;
        }
        return normalizeLocale(preferred);
    }

    private String resolveTemplate(String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback != null ? fallback : "";
        }

        // 1. .lang translations (active locale)
        String translated = activeTranslations.get(key);
        if (translated != null) {
            return translated;
        }

        // 2. Hytale I18nModule (active locale)
        translated = i18n(activeLocale, key);
        if (translated != null) {
            return translated;
        }

        // 3. .lang translations (fallback locale)
        translated = fallbackTranslations.get(key);
        if (translated != null) {
            return translated;
        }

        // 4. Hytale I18nModule (fallback locale)
        if (!activeLocale.equalsIgnoreCase(fallbackLocale)) {
            translated = i18n(fallbackLocale, key);
            if (translated != null) {
                return translated;
            }
        }

        // 5. Hytale I18nModule en-US (always check base language)
        translated = i18n("en_US", key);
        if (translated != null) {
            return translated;
        }

        return fallback != null ? fallback : key;
    }

    private String resolveTemplate(String locale, String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback != null ? fallback : "";
        }

        String normalizedLocale = normalizeLocale(locale);

        // 1. .lang translations (requested locale)
        Map<String, String> localeTranslations = getLocaleTranslations(normalizedLocale);
        String translated = localeTranslations.get(key);
        if (translated != null) {
            return translated;
        }

        // 2. Hytale I18nModule (requested locale)
        translated = i18n(normalizedLocale, key);
        if (translated != null) {
            return translated;
        }

        // 3. .lang fallback translations
        translated = fallbackTranslations.get(key);
        if (translated != null) {
            return translated;
        }

        // 4. Hytale I18nModule (fallback locale)
        translated = i18n(fallbackLocale, key);
        if (translated != null) {
            return translated;
        }

        // 5. .lang active translations
        translated = activeTranslations.get(key);
        if (translated != null) {
            return translated;
        }

        // 6. Hytale I18nModule en-US
        translated = i18n("en_US", key);
        if (translated != null) {
            return translated;
        }

        return fallback != null ? fallback : key;
    }

    private Map<String, String> getLocaleTranslations(String locale) {
        String normalized = normalizeLocale(locale);
        if (normalized.equalsIgnoreCase(activeLocale)) {
            return activeTranslations;
        }
        if (normalized.equalsIgnoreCase(fallbackLocale)) {
            return fallbackTranslations;
        }
        return localeCache.computeIfAbsent(normalized, this::loadLangTranslations);
    }

    /**
     * Converts an EL-style locale code (en_US) to Hytale's format (en-US).
     */
    public static String toHytaleLocale(@Nullable String locale) {
        if (locale == null || locale.isBlank()) {
            return "en-US";
        }
        return locale.trim().replace('_', '-');
    }

    /**
     * Converts a Hytale-style locale code (en-US) to EL's format (en_US).
     */
    public static String fromHytaleLocale(@Nullable String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return normalizeLocale(locale.trim().replace('-', '_'));
    }

    // ---- .lang file loading ----

    /**
     * Loads translations from the endlessleveling.lang file for the given locale.
     * Tries the deployed Server/Languages/ directory first (allows server-operator
     * customisation), then falls back to the bundled JAR resource.
     */
    private Map<String, String> loadLangTranslations(String locale) {
        String hytaleLocale = toHytaleLocale(locale);

        // Try deployed file first
        Path deployedFile = serverLanguagesDir.resolve(hytaleLocale).resolve(LANG_FILE_NAME);
        if (Files.exists(deployedFile)) {
            try (BufferedReader reader = Files.newBufferedReader(deployedFile, StandardCharsets.UTF_8)) {
                Map<String, String> map = parseLangFile(reader);
                LOGGER.atInfo().log("Loaded %d lang keys from deployed file for %s", map.size(), hytaleLocale);
                return map;
            } catch (IOException e) {
                LOGGER.atWarning().log("Failed to load deployed .lang file %s: %s", deployedFile, e.getMessage());
            }
        }

        // Fall back to JAR resources
        String resourcePath = LANG_RESOURCE_DIR + hytaleLocale + "/" + LANG_FILE_NAME;
        try (InputStream is = LanguageManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.atWarning().log("Language .lang file not found: %s", resourcePath);
                return Collections.emptyMap();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                Map<String, String> map = parseLangFile(reader);
                LOGGER.atInfo().log("Loaded %d lang keys from JAR resources for %s", map.size(), hytaleLocale);
                return map;
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to load .lang file from resources %s: %s", resourcePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> parseLangFile(BufferedReader reader) throws IOException {
        Map<String, String> map = new ConcurrentHashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    static String normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String trimmed = value.trim().replace('-', '_');
        String[] parts = trimmed.split("_");
        if (parts.length == 1) {
            return parts[0].toLowerCase(Locale.ROOT);
        }
        return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
    }

    static String format(String template, Object... args) {
        if (template == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return template;
        }

        String formatted = template;
        for (int i = 0; i < args.length; i++) {
            String replacement = String.valueOf(args[i]);
            formatted = formatted.replace("{" + i + "}", replacement);
        }
        return formatted;
    }

}
