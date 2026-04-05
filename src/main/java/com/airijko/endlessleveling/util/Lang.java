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
 *   <li>{@link #message} methods return {@link Message} objects backed by
 *       {@code Message.translation()} so the <b>client</b> resolves the
 *       translation in the player's own language</li>
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

    // ---- Message-based translation (client-side resolution via Hytale I18n) ----

    /**
     * Returns a {@link Message} that the client resolves in the player's language.
     * Uses the fully-qualified I18n key ("endlessleveling.{key}").
     *
     * Use this instead of {@code Message.raw(Lang.tr(...))} when sending messages
     * to players, so the client displays text in their own language.
     */
    @Nonnull
    public static Message message(@Nonnull LocalizationKey localizationKey) {
        return Message.translation(localizationKey.i18nKey());
    }

    /**
     * Returns a {@link Message} for the given translation key (without prefix).
     * The "endlessleveling." prefix is added automatically.
     */
    @Nonnull
    public static Message message(@Nonnull String key) {
        return Message.translation(LanguageManager.I18N_KEY_PREFIX + key);
    }

    /**
     * Returns a {@link Message} for the given fully-qualified I18n key.
     * Use this when the key already includes the "endlessleveling." prefix.
     */
    @Nonnull
    public static Message messageFullKey(@Nonnull String fullI18nKey) {
        return Message.translation(fullI18nKey);
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
