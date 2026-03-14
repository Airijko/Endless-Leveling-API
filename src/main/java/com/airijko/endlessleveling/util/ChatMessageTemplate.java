package com.airijko.endlessleveling.util;

/**
 * Centralized templates for player chat notifications.
 *
 * Each template carries a language key, fallback text, and default body color.
 */
public enum ChatMessageTemplate {
    SKILLS_CHAT_HAVE(LocalizationKey.NOTIFY_SKILLS_CHAT_HAVE,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),
    SKILLS_CHAT_USE(LocalizationKey.NOTIFY_SKILLS_CHAT_USE,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),
    SKILLS_CHAT_END(LocalizationKey.NOTIFY_SKILLS_CHAT_END,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),
    SKILLS_COMMAND(LocalizationKey.NOTIFY_SKILLS_COMMAND,
            ChatMessageStrings.Color.INFO_CYAN, true),

    AUGMENTS_AVAILABLE_HEADER(LocalizationKey.NOTIFY_AUGMENTS_AVAILABLE_HEADER, ChatMessageStrings.Color.INFO_CYAN,
            false),
    AUGMENTS_AVAILABLE_FOOTER(LocalizationKey.NOTIFY_AUGMENTS_AVAILABLE_FOOTER, ChatMessageStrings.Color.INFO_CYAN,
            true),

    SWIFTNESS_FADED(LocalizationKey.NOTIFY_PASSIVES_SWIFTNESS_FADED,
            ChatMessageStrings.Color.INFO_CYAN, false),
    AUGMENT_READY_AGAIN(LocalizationKey.NOTIFY_AUGMENTS_COOLDOWN_READY,
            ChatMessageStrings.Color.INFO_CYAN, false),
    ADRENALINE_TRIGGERED(LocalizationKey.NOTIFY_PASSIVES_ADRENALINE_TRIGGERED, ChatMessageStrings.Color.INFO_CYAN,
            false),

    PASSIVE_GENERIC(LocalizationKey.NOTIFY_PASSIVES_GENERIC,
            ChatMessageStrings.Color.INFO_CYAN, false),
    AUGMENT_GENERIC(LocalizationKey.NOTIFY_AUGMENTS_GENERIC,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),

    AUGMENT_SYNC_SUMMARY(LocalizationKey.NOTIFY_AUGMENTS_SYNC_SUMMARY, ChatMessageStrings.Color.WARNING_ORANGE,
            false),
    AUGMENT_SYNC_ENTRY(LocalizationKey.NOTIFY_AUGMENTS_SYNC_ENTRY, ChatMessageStrings.Color.TIER_MYTHIC, true),
    AUGMENT_SYNC_FIX_ALL(LocalizationKey.NOTIFY_AUGMENTS_SYNC_FIXALL, ChatMessageStrings.Color.INFO_CYAN, true);

    private final LocalizationKey localizationKey;
    private final String colorHex;
    private final boolean lockCommandPrefix;

    ChatMessageTemplate(LocalizationKey localizationKey, String colorHex, boolean lockCommandPrefix) {
        this.localizationKey = localizationKey;
        this.colorHex = colorHex;
        this.lockCommandPrefix = lockCommandPrefix;
    }

    public LocalizationKey localizationKey() {
        return localizationKey;
    }

    public String key() {
        return localizationKey.key();
    }

    public String fallback() {
        return localizationKey.fallback();
    }

    public String colorHex() {
        return colorHex;
    }

    public boolean lockCommandPrefix() {
        return lockCommandPrefix;
    }
}