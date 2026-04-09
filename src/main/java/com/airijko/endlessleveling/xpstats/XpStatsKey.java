package com.airijko.endlessleveling.xpstats;

import java.util.UUID;

/**
 * Composite cache key for per-profile XP stats data.
 */
public record XpStatsKey(UUID uuid, int profileIndex) {
}
