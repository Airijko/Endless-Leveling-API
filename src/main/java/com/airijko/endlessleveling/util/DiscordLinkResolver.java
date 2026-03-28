package com.airijko.endlessleveling.util;

/**
 * Resolves the hardcoded support Discord link.
 */
public final class DiscordLinkResolver {

    public static final String DEFAULT_DISCORD_INVITE_URL = "https://discord.gg/hfMeu9KWsh";

    private DiscordLinkResolver() {
    }

    public static String getDiscordInviteUrl() {
        return DEFAULT_DISCORD_INVITE_URL;
    }
}