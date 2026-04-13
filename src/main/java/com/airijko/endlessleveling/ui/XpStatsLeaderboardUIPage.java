package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService;
import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService.LeaderboardEntry;
import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService.LeaderboardType;
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
import java.util.List;
import java.util.Locale;

/**
 * Global XP leaderboard page with tab-based sorting.
 */
public class XpStatsLeaderboardUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final int PAGE_SIZE = 100;

    private final XpStatsLeaderboardService leaderboardService;
    private LeaderboardType activeType = LeaderboardType.XP_24H;
    private int currentPage = 0;

    public XpStatsLeaderboardUIPage(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull XpStatsLeaderboardService leaderboardService) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        this.leaderboardService = leaderboardService;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/XpStats/XpStatsLeaderboardPage.ui");
        NavUIHelper.applyNavVersion(ui, playerRef, "xpstats",
                "Common/UI/Custom/Pages/XpStats/XpStatsLeaderboardPage.ui", "#LbTitle");
        NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/XpStats/XpStatsLeaderboardPage.ui");

        events.addEventBinding(Activating, "#TabXp24h", of("Action", "xplb:tab:xp24h"), false);
        events.addEventBinding(Activating, "#TabXp7d", of("Action", "xplb:tab:xp7d"), false);
        events.addEventBinding(Activating, "#TabTotalXp", of("Action", "xplb:tab:totalxp"), false);
        events.addEventBinding(Activating, "#TabMomentum", of("Action", "xplb:tab:momentum"), false);
        events.addEventBinding(Activating, "#PrevPageButton", of("Action", "xplb:page:prev"), false);
        events.addEventBinding(Activating, "#NextPageButton", of("Action", "xplb:page:next"), false);

        renderLeaderboard(ui);
    }

    private void renderLeaderboard(@Nonnull UICommandBuilder ui) {
        List<LeaderboardEntry> entries = leaderboardService.getLeaderboard(activeType, Integer.MAX_VALUE);

        String tabLabel = switch (activeType) {
            case XP_24H -> "XP 24H";
            case XP_7D -> "XP 7D";
            case TOTAL_XP -> "TOTAL XP";
            case MOMENTUM -> "MOMENTUM";
        };

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        currentPage = Math.min(currentPage, totalPages - 1);
        int pageStart = currentPage * PAGE_SIZE;
        int pageEnd = Math.min(pageStart + PAGE_SIZE, entries.size());

        ui.set("#ActiveTabLabel.Text", "Sorted by: " + tabLabel);
        ui.set("#HeaderValue.Text", tabLabel);
        ui.set("#PlayerCountLabel.Text", entries.size() + " entries");

        ui.clear("#RowCards");
        for (int i = pageStart; i < pageEnd; i++) {
            LeaderboardEntry entry = entries.get(i);
            int rowIdx = i - pageStart;

            String rowUi;
            if (i == 0) rowUi = "Pages/XpStats/XpStatsRowFirst.ui";
            else if (i == 1) rowUi = "Pages/XpStats/XpStatsRowSecond.ui";
            else if (i == 2) rowUi = "Pages/XpStats/XpStatsRowThird.ui";
            else rowUi = "Pages/XpStats/XpStatsRow.ui";

            ui.append("#RowCards", rowUi);
            String base = "#RowCards[" + rowIdx + "]";
            ui.set(base + " #Rank.Text", (i + 1) + ".");
            ui.set(base + " #Name.Text", entry.playerName());
            ui.set(base + " #Profile.Text", entry.profileName());
            ui.set(base + " #Prestige.Text", String.valueOf(entry.prestige()));
            ui.set(base + " #Level.Text", String.valueOf(entry.level()));

            String value = switch (activeType) {
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
            case "xplb:tab:xp24h" -> { activeType = LeaderboardType.XP_24H; currentPage = 0; changed = true; }
            case "xplb:tab:xp7d" -> { activeType = LeaderboardType.XP_7D; currentPage = 0; changed = true; }
            case "xplb:tab:totalxp" -> { activeType = LeaderboardType.TOTAL_XP; currentPage = 0; changed = true; }
            case "xplb:tab:momentum" -> { activeType = LeaderboardType.MOMENTUM; currentPage = 0; changed = true; }
            case "xplb:page:prev" -> { if (currentPage > 0) { currentPage--; changed = true; } }
            case "xplb:page:next" -> { currentPage++; changed = true; }
        }

        if (changed) {
            UICommandBuilder ui = new UICommandBuilder();
            renderLeaderboard(ui);
            sendUpdate(ui, false);
        }
    }

    private static String formatXp(double xp) {
        return String.format(Locale.ROOT, "%,.0f", xp);
    }
}
