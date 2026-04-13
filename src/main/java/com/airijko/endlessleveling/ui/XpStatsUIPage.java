package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.xpstats.XpStatsData;
import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService;
import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService.LeaderboardEntry;
import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService.LeaderboardType;
import com.airijko.endlessleveling.xpstats.XpStatsManager;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Unified XP stats page with tabs: My Stats, Leaderboard, Admin.
 * The Admin tab is only visible to operators.
 */
public class XpStatsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final int PAGE_SIZE = 100;
    private static final double MOMENTUM_THRESHOLD = 3.0;
    private static final double XP24H_THRESHOLD = 500_000;

    private final XpStatsManager xpStatsManager;
    private boolean isAdmin;

    private enum Tab { MY_STATS, LEADERBOARD, ADMIN_LB, ADMIN_FLAGGED }
    private Tab activeTab = Tab.MY_STATS;
    private LeaderboardType lbType = LeaderboardType.XP_24H;
    private int currentPage = 0;

    public XpStatsUIPage(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull XpStatsManager xpStatsManager) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        this.xpStatsManager = xpStatsManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/XpStats/XpStatsPage.ui");
        NavUIHelper.applyNavVersion(ui, playerRef, "xpstats",
                "Common/UI/Custom/Pages/XpStats/XpStatsPage.ui", "#XpStatsTitle");
        NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/XpStats/XpStatsPage.ui");

        // Bind tab buttons
        events.addEventBinding(Activating, "#TabMyStats", of("Action", "xps:tab:mystats"), false);
        events.addEventBinding(Activating, "#TabLeaderboard", of("Action", "xps:tab:lb"), false);

        // Admin tab: only visible and functional for operators
        isAdmin = OperatorHelper.hasAdministrativeAccess(playerRef);
        ui.set("#TabAdmin.Visible", isAdmin);
        if (isAdmin) {
            events.addEventBinding(Activating, "#TabAdmin", of("Action", "xps:tab:admin"), false);
        }
        events.addEventBinding(Activating, "#PrevPageButton", of("Action", "xps:page:prev"), false);
        events.addEventBinding(Activating, "#NextPageButton", of("Action", "xps:page:next"), false);

        renderContent(ui);
    }

    private void renderContent(@Nonnull UICommandBuilder ui) {
        ui.clear("#ContentRows");
        ui.set("#PaginationBar.Visible", false);

        switch (activeTab) {
            case MY_STATS -> renderMyStats(ui);
            case LEADERBOARD -> renderLeaderboard(ui);
            case ADMIN_LB -> renderAdminLeaderboard(ui);
            case ADMIN_FLAGGED -> renderAdminFlagged(ui);
        }
    }

    // ------------------------------------------------------------------
    // MY STATS tab
    // ------------------------------------------------------------------

    private void renderMyStats(@Nonnull UICommandBuilder ui) {
        ui.set("#SubtitleText.Text", "Your XP progression trends");
        ui.set("#ActiveTabLabel.Text", "");

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) return;

        var playerDataManager = plugin.getPlayerDataManager();
        PlayerData playerData = playerDataManager != null ? playerDataManager.get(playerRef.getUuid()) : null;
        int profileIndex = playerData != null ? playerData.getActiveProfileIndex() : 1;

        XpStatsData data = xpStatsManager.getOrLoad(playerRef.getUuid(), profileIndex);
        if (data == null) data = new XpStatsData();
        data.rotateBuckets();

        // Summary row as a text entry
        ui.append("#ContentRows", "Pages/XpStats/XpStatsRow.ui");
        ui.set("#ContentRows[0] #Rank.Text", "");
        ui.set("#ContentRows[0] #Name.Text", "TOTAL XP");
        ui.set("#ContentRows[0] #Profile.Text", formatXp(data.getTotalXp()));
        ui.set("#ContentRows[0] #Prestige.Text", "24H: " + formatXp(data.getXp24h()));
        ui.set("#ContentRows[0] #Level.Text", "7D: " + formatXp(data.getXp7d()));
        ui.set("#ContentRows[0] #Value.Text", "M: " + String.format(Locale.ROOT, "%.2f", data.getMomentum()));

        // Hourly breakdown - only active hours
        double[] hourly = data.getHourly();
        int rowIndex = 1;
        boolean hasHourly = false;
        for (int i = 0; i < 24; i++) {
            if (hourly[i] > 0) {
                hasHourly = true;
                ui.append("#ContentRows", "Pages/XpStats/XpStatsHourlyBar.ui");
                String base = "#ContentRows[" + rowIndex + "]";
                ui.set(base + " #HourLabel.Text", String.format("%02d:00", i));
                ui.set(base + " #ValueLabel.Text", formatXp(hourly[i]));
                rowIndex++;
            }
        }
        if (!hasHourly) {
            ui.append("#ContentRows", "Pages/XpStats/XpStatsHourlyBar.ui");
            ui.set("#ContentRows[" + rowIndex + "] #HourLabel.Text", "---");
            ui.set("#ContentRows[" + rowIndex + "] #ValueLabel.Text", "No hourly activity");
            rowIndex++;
        }

        // Daily breakdown
        double[] daily = data.getDaily();
        for (int i = 0; i < 7; i++) {
            ui.append("#ContentRows", "Pages/XpStats/XpStatsDailyBar.ui");
            String base = "#ContentRows[" + rowIndex + "]";
            ui.set(base + " #DayLabel.Text", DAY_NAMES[i]);
            ui.set(base + " #ValueLabel.Text", formatXp(daily[i]));
            rowIndex++;
        }

        // Prestige history
        for (var event : data.getPrestigeHistory()) {
            ui.append("#ContentRows", "Pages/XpStats/XpStatsPrestigeEntry.ui");
            String base = "#ContentRows[" + rowIndex + "]";
            ui.set(base + " #PrestigeLevel.Text", "P" + event.prestige());
            String ts = Instant.ofEpochSecond(event.timestamp())
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            ui.set(base + " #PrestigeDate.Text", ts + " UTC");
            rowIndex++;
        }
    }

    // ------------------------------------------------------------------
    // LEADERBOARD tab
    // ------------------------------------------------------------------

    private void renderLeaderboard(@Nonnull UICommandBuilder ui) {
        String tabLabel = switch (lbType) {
            case XP_24H -> "XP 24H";
            case XP_7D -> "XP 7D";
            case TOTAL_XP -> "TOTAL XP";
            case MOMENTUM -> "MOMENTUM";
        };
        ui.set("#SubtitleText.Text", "Sorted by: " + tabLabel);
        ui.set("#ActiveTabLabel.Text", "Click LEADERBOARD to cycle sort");

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) return;
        XpStatsLeaderboardService lbService = plugin.getXpStatsLeaderboardService();
        if (lbService == null) return;

        List<LeaderboardEntry> entries = lbService.getLeaderboard(lbType, Integer.MAX_VALUE);

        if (entries.isEmpty()) {
            ui.append("#ContentRows", "Pages/XpStats/XpStatsRow.ui");
            ui.set("#ContentRows[0] #Rank.Text", "--");
            ui.set("#ContentRows[0] #Name.Text", "No XP stats data yet.");
            ui.set("#ContentRows[0] #Profile.Text", "");
            ui.set("#ContentRows[0] #Prestige.Text", "");
            ui.set("#ContentRows[0] #Level.Text", "");
            ui.set("#ContentRows[0] #Value.Text", "");
            return;
        }

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        currentPage = Math.min(currentPage, totalPages - 1);
        int pageStart = currentPage * PAGE_SIZE;
        int pageEnd = Math.min(pageStart + PAGE_SIZE, entries.size());

        for (int i = pageStart; i < pageEnd; i++) {
            LeaderboardEntry entry = entries.get(i);
            int rowIdx = i - pageStart;

            String rowUi;
            if (i == 0) rowUi = "Pages/XpStats/XpStatsRowFirst.ui";
            else if (i == 1) rowUi = "Pages/XpStats/XpStatsRowSecond.ui";
            else if (i == 2) rowUi = "Pages/XpStats/XpStatsRowThird.ui";
            else rowUi = "Pages/XpStats/XpStatsRow.ui";

            ui.append("#ContentRows", rowUi);
            String base = "#ContentRows[" + rowIdx + "]";
            ui.set(base + " #Rank.Text", (i + 1) + ".");
            ui.set(base + " #Name.Text", entry.playerName());
            ui.set(base + " #Profile.Text", entry.profileName());
            ui.set(base + " #Prestige.Text", String.valueOf(entry.prestige()));
            ui.set(base + " #Level.Text", String.valueOf(entry.level()));

            String value = switch (lbType) {
                case XP_24H -> formatXp(entry.xp24h());
                case XP_7D -> formatXp(entry.xp7d());
                case TOTAL_XP -> formatXp(entry.totalXp());
                case MOMENTUM -> String.format(Locale.ROOT, "%.2f", entry.momentum());
            };
            ui.set(base + " #Value.Text", value);
        }

        ui.set("#PaginationBar.Visible", totalPages > 1);
        ui.set("#PageLabel.Text", String.format("Page %d/%d", currentPage + 1, totalPages));
    }

    // ------------------------------------------------------------------
    // ADMIN tabs
    // ------------------------------------------------------------------

    private void renderAdminLeaderboard(@Nonnull UICommandBuilder ui) {
        ui.set("#SubtitleText.Text", "Admin — Top XP/24h");
        ui.set("#ActiveTabLabel.Text", "Click ADMIN to switch to Flagged");

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) return;
        XpStatsLeaderboardService lbService = plugin.getXpStatsLeaderboardService();
        if (lbService == null) return;

        List<LeaderboardEntry> entries = lbService.getLeaderboard(LeaderboardType.XP_24H, Integer.MAX_VALUE);

        if (entries.isEmpty()) {
            ui.append("#ContentRows", "Pages/XpStats/XpStatsRow.ui");
            ui.set("#ContentRows[0] #Name.Text", "No data.");
            return;
        }

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        currentPage = Math.min(currentPage, totalPages - 1);
        int pageStart = currentPage * PAGE_SIZE;
        int pageEnd = Math.min(pageStart + PAGE_SIZE, entries.size());

        for (int i = pageStart; i < pageEnd; i++) {
            LeaderboardEntry entry = entries.get(i);
            int rowIdx = i - pageStart;

            String rowUi = (i < 3)
                    ? "Pages/XpStats/XpStatsRow" + (i == 0 ? "First" : i == 1 ? "Second" : "Third") + ".ui"
                    : "Pages/XpStats/XpStatsRow.ui";

            ui.append("#ContentRows", rowUi);
            String base = "#ContentRows[" + rowIdx + "]";
            ui.set(base + " #Rank.Text", (i + 1) + ".");
            ui.set(base + " #Name.Text", entry.playerName());
            ui.set(base + " #Profile.Text", entry.profileName());
            ui.set(base + " #Prestige.Text", String.valueOf(entry.prestige()));
            ui.set(base + " #Level.Text", String.valueOf(entry.level()));
            ui.set(base + " #Value.Text", String.format(Locale.ROOT, "%,.0f (m:%.1f)",
                    entry.xp24h(), entry.momentum()));
        }

        ui.set("#PaginationBar.Visible", totalPages > 1);
        ui.set("#PageLabel.Text", String.format("Page %d/%d", currentPage + 1, totalPages));
    }

    private void renderAdminFlagged(@Nonnull UICommandBuilder ui) {
        ui.set("#SubtitleText.Text", "Admin — Flagged (momentum > " +
                String.format(Locale.ROOT, "%.1f", MOMENTUM_THRESHOLD) + ")");
        ui.set("#ActiveTabLabel.Text", "Click ADMIN to switch to Leaderboard");

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) return;
        XpStatsLeaderboardService lbService = plugin.getXpStatsLeaderboardService();
        if (lbService == null) return;

        List<LeaderboardEntry> flagged = lbService.getFlaggedPlayers(MOMENTUM_THRESHOLD, XP24H_THRESHOLD);

        if (flagged.isEmpty()) {
            ui.append("#ContentRows", "Pages/XpStats/XpStatsRow.ui");
            ui.set("#ContentRows[0] #Rank.Text", "--");
            ui.set("#ContentRows[0] #Name.Text", "No flagged players detected.");
            ui.set("#ContentRows[0] #Profile.Text", "");
            ui.set("#ContentRows[0] #Prestige.Text", "");
            ui.set("#ContentRows[0] #Level.Text", "");
            ui.set("#ContentRows[0] #Value.Text", "");
            return;
        }

        int totalPages = Math.max(1, (flagged.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        currentPage = Math.min(currentPage, totalPages - 1);
        int pageStart = currentPage * PAGE_SIZE;
        int pageEnd = Math.min(pageStart + PAGE_SIZE, flagged.size());

        for (int i = pageStart; i < pageEnd; i++) {
            LeaderboardEntry entry = flagged.get(i);
            int rowIdx = i - pageStart;
            ui.append("#ContentRows", "Pages/XpStats/XpStatsRow.ui");
            String base = "#ContentRows[" + rowIdx + "]";
            ui.set(base + " #Rank.Text", (i + 1) + ".");
            ui.set(base + " #Name.Text", entry.playerName());
            ui.set(base + " #Profile.Text", entry.profileName());
            ui.set(base + " #Prestige.Text", String.valueOf(entry.prestige()));
            ui.set(base + " #Level.Text", String.valueOf(entry.level()));
            ui.set(base + " #Value.Text", String.format(Locale.ROOT, "m:%.1f xp:%,.0f",
                    entry.momentum(), entry.xp24h()));
        }

        ui.set("#PaginationBar.Visible", totalPages > 1);
        ui.set("#PageLabel.Text", String.format("Page %d/%d", currentPage + 1, totalPages));
    }

    // ------------------------------------------------------------------
    // Event handling
    // ------------------------------------------------------------------

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);
        if (data.action == null || data.action.isEmpty()) return;

        if (NavUIHelper.handleNavAction(data.action, ref, store, playerRef)) return;

        String action = data.action.trim().toLowerCase(Locale.ROOT);
        boolean changed = false;
        switch (action) {
            case "xps:tab:mystats" -> {
                activeTab = Tab.MY_STATS;
                currentPage = 0;
                changed = true;
            }
            case "xps:tab:lb" -> {
                if (activeTab == Tab.LEADERBOARD) {
                    lbType = switch (lbType) {
                        case XP_24H -> LeaderboardType.XP_7D;
                        case XP_7D -> LeaderboardType.TOTAL_XP;
                        case TOTAL_XP -> LeaderboardType.MOMENTUM;
                        case MOMENTUM -> LeaderboardType.XP_24H;
                    };
                } else {
                    activeTab = Tab.LEADERBOARD;
                }
                currentPage = 0;
                changed = true;
            }
            case "xps:tab:admin" -> {
                if (!isAdmin) break;
                if (activeTab == Tab.ADMIN_LB) {
                    activeTab = Tab.ADMIN_FLAGGED;
                } else {
                    activeTab = Tab.ADMIN_LB;
                }
                currentPage = 0;
                changed = true;
            }
            case "xps:page:prev" -> {
                if (currentPage > 0) {
                    currentPage--;
                    changed = true;
                }
            }
            case "xps:page:next" -> {
                currentPage++;
                changed = true;
            }
        }

        if (changed) {
            UICommandBuilder ui = new UICommandBuilder();
            renderContent(ui);
            sendUpdate(ui, false);
        }
    }

    private static String formatXp(double xp) {
        return String.format(Locale.ROOT, "%,.0f", xp);
    }
}
