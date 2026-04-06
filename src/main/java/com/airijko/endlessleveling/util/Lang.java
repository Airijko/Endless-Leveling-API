package com.airijko.endlessleveling.util;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.LanguageManager;
import com.hypixel.hytale.server.core.Message;

import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Static facade for the localization system.
 *
 * Provides two styles of translation:
 * <ul>
 *   <li>{@link #tr} methods return pre-resolved {@code String} values
 *       (server-side resolution, positional args like {0}, {1})</li>
 *   <li>{@link #message} methods return {@link Message} objects with
 *       server-resolved text wrapped in {@code Message.raw()}</li>
 * </ul>
 */
public final class Lang {

    private Lang() {
    }

    // ---- String-based translation (server-side resolution) ----

    public static String tr(String key, String fallback, Object... args) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return fallbackOrKey(fallback, key, args);
        }

        LanguageManager languageManager = plugin.getLanguageManager();
        if (languageManager == null) {
            return fallbackOrKey(fallback, key, args);
        }

        return languageManager.tr(key, fallback, args);
    }

    public static String tr(LocalizationKey localizationKey, Object... args) {
        if (localizationKey == null) {
            return "";
        }
        return tr(localizationKey.key(), localizationKey.fallback(), args);
    }

    public static String tr(UUID playerUuid, String key, String fallback, Object... args) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return fallbackOrKey(fallback, key, args);
        }

        LanguageManager languageManager = plugin.getLanguageManager();
        if (languageManager == null) {
            return fallbackOrKey(fallback, key, args);
        }

        return languageManager.tr(playerUuid, key, fallback, args);
    }

    public static String tr(UUID playerUuid, LocalizationKey localizationKey, Object... args) {
        if (localizationKey == null) {
            return "";
        }
        return tr(playerUuid, localizationKey.key(), localizationKey.fallback(), args);
    }

    // ---- Message-based translation (server-side resolution) ----

    /**
     * Returns a {@link Message} with server-resolved translation text.
     *
     * Mod translation keys are only loaded into the server's I18nModule;
     * the Hytale client does not receive mod .lang files, so
     * {@code Message.translation()} would display raw keys in chat,
     * notifications, and event titles.  Resolving server-side via
     * {@code Message.raw(tr(...))} ensures players always see real text.
     */
    @Nonnull
    public static Message message(@Nonnull LocalizationKey localizationKey) {
        return Message.raw(tr(localizationKey));
    }

    /**
     * Returns a server-resolved {@link Message} for the given translation key
     * (without the "endlessleveling." prefix — it is added internally).
     */
    @Nonnull
    public static Message message(@Nonnull String key) {
        return Message.raw(tr(key, key));
    }

    /**
     * Returns a server-resolved {@link Message} for the given fully-qualified
     * I18n key.  Use when the key already includes the "endlessleveling." prefix.
     */
    @Nonnull
    public static Message messageFullKey(@Nonnull String fullI18nKey) {
        String stripped = fullI18nKey.startsWith(LanguageManager.I18N_KEY_PREFIX)
                ? fullI18nKey.substring(LanguageManager.I18N_KEY_PREFIX.length())
                : fullI18nKey;
        return Message.raw(tr(stripped, fullI18nKey));
    }

    // ---- Internal ----

    private static String fallbackOrKey(String fallback, String key, Object... args) {
        String template = fallback != null ? fallback : (key != null ? key : "");
        if (args == null || args.length == 0) {
            return template;
        }
        String formatted = template;
        for (int i = 0; i < args.length; i++) {
            formatted = formatted.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return formatted;
    }
}
