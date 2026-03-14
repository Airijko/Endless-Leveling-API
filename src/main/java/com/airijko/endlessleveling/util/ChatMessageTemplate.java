package com.airijko.endlessleveling.util;

/**
 * Centralized templates for player chat notifications.
 *
 * Each template carries a language key, fallback text, and default body color.
 */
public enum ChatMessageTemplate {
    SKILLS_CHAT_HAVE(ChatMessageStrings.Keys.SKILLS_CHAT_HAVE, ChatMessageStrings.Fallback.SKILLS_CHAT_HAVE,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),
    SKILLS_CHAT_USE(ChatMessageStrings.Keys.SKILLS_CHAT_USE, ChatMessageStrings.Fallback.SKILLS_CHAT_USE,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),
    SKILLS_CHAT_END(ChatMessageStrings.Keys.SKILLS_CHAT_END, ChatMessageStrings.Fallback.SKILLS_CHAT_END,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),
    SKILLS_COMMAND(ChatMessageStrings.Keys.SKILLS_COMMAND, ChatMessageStrings.Fallback.SKILLS_COMMAND,
            ChatMessageStrings.Color.INFO_CYAN, true),

    AUGMENTS_AVAILABLE_HEADER(ChatMessageStrings.Keys.AUGMENTS_AVAILABLE_HEADER,
            ChatMessageStrings.Fallback.AUGMENTS_AVAILABLE_HEADER, ChatMessageStrings.Color.INFO_CYAN, false),
    AUGMENTS_AVAILABLE_FOOTER(ChatMessageStrings.Keys.AUGMENTS_AVAILABLE_FOOTER,
            ChatMessageStrings.Fallback.AUGMENTS_AVAILABLE_FOOTER, ChatMessageStrings.Color.INFO_CYAN, true),

    SWIFTNESS_FADED(ChatMessageStrings.Keys.SWIFTNESS_FADED, ChatMessageStrings.Fallback.SWIFTNESS_FADED,
            ChatMessageStrings.Color.INFO_CYAN, false),
    AUGMENT_READY_AGAIN(ChatMessageStrings.Keys.AUGMENT_READY_AGAIN, ChatMessageStrings.Fallback.AUGMENT_READY_AGAIN,
            ChatMessageStrings.Color.INFO_CYAN, false),
    ADRENALINE_TRIGGERED(ChatMessageStrings.Keys.ADRENALINE_TRIGGERED,
            ChatMessageStrings.Fallback.ADRENALINE_TRIGGERED, ChatMessageStrings.Color.INFO_CYAN, false),

    PASSIVE_GENERIC(ChatMessageStrings.Keys.PASSIVE_GENERIC, ChatMessageStrings.Fallback.PASSIVE_GENERIC,
            ChatMessageStrings.Color.INFO_CYAN, false),
    AUGMENT_GENERIC(ChatMessageStrings.Keys.AUGMENT_GENERIC, ChatMessageStrings.Fallback.AUGMENT_GENERIC,
            ChatMessageStrings.Color.HIGHLIGHT_GOLD, false),

    AUGMENT_SYNC_SUMMARY(ChatMessageStrings.Keys.AUGMENT_SYNC_SUMMARY,
            ChatMessageStrings.Fallback.AUGMENT_SYNC_SUMMARY, ChatMessageStrings.Color.WARNING_ORANGE, false),
    AUGMENT_SYNC_ENTRY(ChatMessageStrings.Keys.AUGMENT_SYNC_ENTRY,
            ChatMessageStrings.Fallback.AUGMENT_SYNC_ENTRY, ChatMessageStrings.Color.TIER_MYTHIC, true),
    AUGMENT_SYNC_FIX_ALL(ChatMessageStrings.Keys.AUGMENT_SYNC_FIX_ALL,
            ChatMessageStrings.Fallback.AUGMENT_SYNC_FIX_ALL, ChatMessageStrings.Color.INFO_CYAN, true);

    private final String key;
    private final String fallback;
    private final String colorHex;
    private final boolean lockCommandPrefix;

    ChatMessageTemplate(String key, String fallback, String colorHex, boolean lockCommandPrefix) {
        this.key = key;
        this.fallback = fallback;
        this.colorHex = colorHex;
        this.lockCommandPrefix = lockCommandPrefix;
    }

    public String key() {
        return key;
    }

    public String fallback() {
        return fallback;
    }

    public String colorHex() {
        return colorHex;
    }

    public boolean lockCommandPrefix() {
        return lockCommandPrefix;
    }
}