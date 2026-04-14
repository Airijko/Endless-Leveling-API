package com.airijko.endlessleveling.util;

import com.airijko.endlessleveling.managers.LanguageManager;

import javax.annotation.Nonnull;

/**
 * Centralized localization key registry for chat notifications and HUD labels.
 *
 * Each key maps to a translation entry in both:
 * - Hytale I18n: Server/Languages/{locale}/endlessleveling.lang (auto-prefixed as "endlessleveling.{key}")
 * - Legacy YAML: lang/{locale}.yml (flattened dot-key)
 *
 * Add new keys here first, then consume them via {@link Lang#tr(LocalizationKey, Object...)}.
 */
public enum LocalizationKey {
    NOTIFY_SKILLS_CHAT_HAVE("notify.skills.chat.have", "You still have "),
    NOTIFY_SKILLS_CHAT_USE("notify.skills.chat.use", " skill points. Use "),
    NOTIFY_SKILLS_CHAT_END("notify.skills.chat.end", " to spend them."),
    NOTIFY_SKILLS_COMMAND("notify.skills.command", FixedValue.ROOT_COMMAND.value()),

    NOTIFY_AUGMENTS_AVAILABLE_HEADER("notify.augments.available.header",
            "You have augments available to choose from:"),
    NOTIFY_AUGMENTS_AVAILABLE_FOOTER("notify.augments.available.footer",
            "Use " + FixedValue.ROOT_COMMAND.value() + " augments to choose."),

    NOTIFY_PASSIVES_SWIFTNESS_FADED("notify.passives.swiftness.faded", "Swiftness has faded."),
    NOTIFY_AUGMENTS_COOLDOWN_READY("notify.augments.cooldown.ready", "{0} is ready again!"),
    NOTIFY_PASSIVES_ADRENALINE_TRIGGERED("notify.passives.adrenaline.triggered",
            "Adrenaline triggered! Restoring {0}% stamina over {1}s"),

    NOTIFY_PASSIVES_GENERIC("notify.passives.generic", "{0}"),
    NOTIFY_AUGMENTS_GENERIC("notify.augments.generic", "{0}"),

    NOTIFY_AUGMENTS_SYNC_SUMMARY("notify.augments.sync.summary", "{0} player(s) have mismatched augment slot counts:"),
    NOTIFY_AUGMENTS_SYNC_ENTRY("notify.augments.sync.entry",
            "- {0} [{1}: has {2}, should have {3}] -> " + FixedValue.ROOT_COMMAND.value() + " augments reset {0}"),
    NOTIFY_AUGMENTS_SYNC_FIXALL("notify.augments.sync.fixall",
            "To fix all at once: " + FixedValue.ROOT_COMMAND.value() + " augments resetallplayers"),

    HUD_COMMON_UNAVAILABLE("hud.common.unavailable", "--"),
    HUD_RACE_NONE("hud.race.none", "No Race"),
    HUD_RACE_PREFIX("hud.race.prefix", "Race: "),
    HUD_CLASS_NONE("hud.class.none", "None"),
    HUD_MOB_PREFIX("hud.mob.prefix", "Local mobs: "),
    HUD_MOB_LEVEL_SINGLE("hud.mob.level.single", "Lv. {0}"),
    HUD_MOB_LEVEL_SINGLE_COMPACT("hud.mob.level.single_compact", "Lv {0}"),
    HUD_MOB_LEVEL_RANGE("hud.mob.level.range", "Lv. {0}-{1}"),
    HUD_MOB_LEVEL_MIXED("hud.mob.level.mixed", "Lv {0} | Lv {1}-{2}"),
    HUD_MOB_LEVEL_TIERED_WITH_BOSS("hud.mob.level.tiered_with_boss", "Lv {0}-{1}, Boss Lv {2}"),
    HUD_LEVEL_NO_DATA("hud.level.no_data", "LVL --   XP: 0 / --"),
    HUD_LEVEL_WITHOUT_LEVELING("hud.level.without_leveling", "LVL {0}   XP: {1}"),
    HUD_LEVEL_MAX("hud.level.max", "LVL {0}   MAX LEVEL"),
    HUD_LEVEL_PROGRESS("hud.level.progress", "LVL {0}   XP: {1} / {2}"),
    HUD_LEVEL_ONLY("hud.level.only", "LVL {0}"),
    HUD_LEVEL_ONLY_NO_DATA("hud.level.only.no_data", "LVL --"),
    HUD_XP_NO_DATA("hud.xp.no_data", "0 / --"),
    HUD_XP_PROGRESS("hud.xp.progress", "{0} / {1}"),
    HUD_XP_WITHOUT_LEVELING("hud.xp.without_leveling", "{0}"),
    HUD_XP_MAX("hud.xp.max", "MAX"),
    HUD_PRESTIGE("hud.prestige", "P{0}"),

    UI_AUGMENTS_REMAINING_HEADER("ui.augments.remaining.header",
            "You still have more augments to choose from:"),
    UI_AUGMENTS_REMAINING_FOOTER("ui.augments.remaining.footer", "Choose again now.");

    private final String key;
    private final String fallback;

    LocalizationKey(String key, String fallback) {
        this.key = key;
        this.fallback = fallback;
    }

    /** The translation key used in both YAML and .lang files (without I18n prefix). */
    public String key() {
        return key;
    }

    /** The fully-qualified key for Hytale's I18nModule (with "endlessleveling." prefix). */
    @Nonnull
    public String i18nKey() {
        return LanguageManager.I18N_KEY_PREFIX + key;
    }

    /** Fallback English text if no translation is found. */
    public String fallback() {
        return fallback;
    }
}
