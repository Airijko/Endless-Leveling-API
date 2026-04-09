package com.airijko.endlessleveling.xpstats;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Computes XP leaderboard rankings on demand and provides exploit detection.
 * Rankings are computed dynamically from cached + on-disk XP stats data.
 */
public class XpStatsLeaderboardService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final XpStatsManager xpStatsManager;
    private final PlayerDataManager playerDataManager;

    public XpStatsLeaderboardService(XpStatsManager xpStatsManager, PlayerDataManager playerDataManager) {
        this.xpStatsManager = xpStatsManager;
        this.playerDataManager = playerDataManager;
    }

    // ------------------------------------------------------------------
    // Leaderboard computation
    // ------------------------------------------------------------------

    /**
     * Returns the top N entries ranked by the given leaderboard type.
     */
    public List<LeaderboardEntry> getLeaderboard(LeaderboardType type, int limit) {
        List<XpStatsManager.XpStatsEntry> allEntries = xpStatsManager.loadAllEntries();
        List<LeaderboardEntry> enriched = enrichEntries(allEntries);

        Comparator<LeaderboardEntry> comparator = switch (type) {
            case XP_24H -> Comparator.comparingDouble(LeaderboardEntry::xp24h).reversed();
            case XP_7D -> Comparator.comparingDouble(LeaderboardEntry::xp7d).reversed();
            case TOTAL_XP -> Comparator.comparingDouble(LeaderboardEntry::totalXp).reversed();
            case MOMENTUM -> Comparator.comparingDouble(LeaderboardEntry::momentum).reversed();
        };

        enriched.sort(comparator);
        if (limit > 0 && enriched.size() > limit) {
            return enriched.subList(0, limit);
        }
        return enriched;
    }

    /**
     * Returns all profile stats for a single player, sorted by xp24h descending.
     */
    public List<LeaderboardEntry> getPlayerProfiles(UUID uuid) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        PlayerData playerData = playerDataManager.get(uuid);
        if (playerData == null) return entries;

        for (var profileEntry : playerData.getProfiles().entrySet()) {
            int profileIndex = profileEntry.getKey();
            XpStatsData data = xpStatsManager.getOrLoad(uuid, profileIndex);
            if (data != null) {
                data.rotateBuckets();
                entries.add(buildEntry(uuid, profileIndex, data, playerData));
            }
        }

        entries.sort(Comparator.comparingDouble(LeaderboardEntry::xp24h).reversed());
        return entries;
    }

    /**
     * Returns entries flagged as suspicious based on momentum and xp24h thresholds.
     */
    public List<LeaderboardEntry> getFlaggedPlayers(double momentumThreshold, double xp24hThreshold) {
        List<XpStatsManager.XpStatsEntry> allEntries = xpStatsManager.loadAllEntries();
        List<LeaderboardEntry> flagged = new ArrayList<>();

        for (XpStatsManager.XpStatsEntry entry : allEntries) {
            XpStatsData data = entry.data();
            double momentum = data.getMomentum();
            double xp24h = data.getXp24h();

            if (momentum > momentumThreshold || xp24h > xp24hThreshold) {
                PlayerData playerData = playerDataManager.get(entry.uuid());
                flagged.add(buildEntry(entry.uuid(), entry.profileIndex(), data, playerData));
            }
        }

        flagged.sort(Comparator.comparingDouble(LeaderboardEntry::momentum).reversed());
        return flagged;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private List<LeaderboardEntry> enrichEntries(List<XpStatsManager.XpStatsEntry> raw) {
        List<LeaderboardEntry> result = new ArrayList<>(raw.size());
        for (XpStatsManager.XpStatsEntry entry : raw) {
            PlayerData playerData = playerDataManager.get(entry.uuid());
            result.add(buildEntry(entry.uuid(), entry.profileIndex(), entry.data(), playerData));
        }
        return result;
    }

    private LeaderboardEntry buildEntry(UUID uuid, int profileIndex, XpStatsData data, PlayerData playerData) {
        String playerName = playerData != null ? playerData.getPlayerName() : uuid.toString().substring(0, 8);
        String profileName = playerData != null ? playerData.getProfileName(profileIndex) : "Profile " + profileIndex;
        int prestige = 0;
        int level = 0;
        if (playerData != null) {
            // Get profile-specific level/prestige if available
            var profile = playerData.getProfiles().get(profileIndex);
            if (profile != null) {
                prestige = profile.getPrestigeLevel();
                level = profile.getLevel();
            }
        }

        return new LeaderboardEntry(
                uuid,
                profileIndex,
                playerName,
                profileName,
                data.getXp24h(),
                data.getXp7d(),
                data.getTotalXp(),
                data.getMomentum(),
                prestige,
                level
        );
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    public enum LeaderboardType {
        XP_24H,
        XP_7D,
        TOTAL_XP,
        MOMENTUM
    }

    public record LeaderboardEntry(
            UUID uuid,
            int profileIndex,
            String playerName,
            String profileName,
            double xp24h,
            double xp7d,
            double totalXp,
            double momentum,
            int prestige,
            int level
    ) {
    }
}
