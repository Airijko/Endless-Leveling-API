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
 * Admin panel for XP stats: leaderboards, flagged players, and exploit detection.
 */
public class XpStatsAdminUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final double DEFAULT_MOMENTUM_THRESHOLD = 3.0;
    private static final double DEFAULT_XP24H_THRESHOLD = 500_000;
    private static final int MAX_ENTRIES = 100;

    private final XpStatsLeaderboardService leaderboardService;

    private enum AdminTab { LEADERBOARD, FLAGGED }
    private AdminTab activeTab = AdminTab.LEADERBOARD;

    public XpStatsAdminUIPage(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
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
        ui.append("Pages/XpStats/XpStatsAdminPage.ui");
        NavUIHelper.applyNavVersion(ui, playerRef, "xpstats",
                "Common/UI/Custom/Pages/XpStats/XpStatsAdminPage.ui", "#AdminTitle");
        NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/XpStats/XpStatsAdminPage.ui");

        events.addEventBinding(Activating, "#TabLeaderboard", of("Action", "admin:tab:lb"), false);
        events.addEventBinding(Activating, "#TabFlagged", of("Action", "admin:tab:flagged"), false);
        events.addEventBinding(Activating, "#TabRefresh", of("Action", "admin:refresh"), false);

        renderContent(ui);
    }

    private void renderContent(@Nonnull UICommandBuilder ui) {
        ui.clear("#RowCards");

        switch (activeTab) {
            case LEADERBOARD -> renderLeaderboard(ui);
            case FLAGGED -> renderFlagged(ui);
        }
    }

    private void renderLeaderboard(@Nonnull UICommandBuilder ui) {
        ui.set("#ActiveTabLabel.Text", "Leaderboard — Top XP/24h");
        ui.set("#StatusLabel.Text", "Admin Panel");

        List<LeaderboardEntry> entries = leaderboardService.getLeaderboard(LeaderboardType.XP_24H, MAX_ENTRIES);

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);

            String rowUi;
            if (i == 0) rowUi = "Pages/XpStats/XpStatsRowFirst.ui";
            else if (i == 1) rowUi = "Pages/XpStats/XpStatsRowSecond.ui";
            else if (i == 2) rowUi = "Pages/XpStats/XpStatsRowThird.ui";
            else rowUi = "Pages/XpStats/XpStatsRow.ui";

            ui.append("#RowCards", rowUi);
            String base = "#RowCards[" + i + "]";
            ui.set(base + " #Rank.Text", (i + 1) + ".");
            ui.set(base + " #Name.Text", entry.playerName());
            ui.set(base + " #Profile.Text", entry.profileName());
            ui.set(base + " #Prestige.Text", String.valueOf(entry.prestige()));
            ui.set(base + " #Level.Text", String.valueOf(entry.level()));
            ui.set(base + " #Value.Text", String.format(Locale.ROOT, "%,.0f (m:%.1f)",
                    entry.xp24h(), entry.momentum()));
        }
    }

    private void renderFlagged(@Nonnull UICommandBuilder ui) {
        ui.set("#ActiveTabLabel.Text", "Flagged Players — Momentum > " +
                String.format(Locale.ROOT, "%.1f", DEFAULT_MOMENTUM_THRESHOLD));
        ui.set("#StatusLabel.Text", "Exploit Detection");

        List<LeaderboardEntry> flagged = leaderboardService.getFlaggedPlayers(
                DEFAULT_MOMENTUM_THRESHOLD, DEFAULT_XP24H_THRESHOLD);

        if (flagged.isEmpty()) {
            // No flagged players - show a message row
            ui.append("#RowCards", "Pages/XpStats/XpStatsRow.ui");
            ui.set("#RowCards[0] #Rank.Text", "--");
            ui.set("#RowCards[0] #Name.Text", "No flagged players detected.");
            ui.set("#RowCards[0] #Profile.Text", "");
            ui.set("#RowCards[0] #Prestige.Text", "");
            ui.set("#RowCards[0] #Level.Text", "");
            ui.set("#RowCards[0] #Value.Text", "");
            return;
        }

        for (int i = 0; i < flagged.size(); i++) {
            LeaderboardEntry entry = flagged.get(i);

            // All flagged rows use the standard style
            ui.append("#RowCards", "Pages/XpStats/XpStatsRow.ui");
            String base = "#RowCards[" + i + "]";
            ui.set(base + " #Rank.Text", (i + 1) + ".");
            ui.set(base + " #Name.Text", entry.playerName());
            ui.set(base + " #Profile.Text", entry.profileName());
            ui.set(base + " #Prestige.Text", String.valueOf(entry.prestige()));
            ui.set(base + " #Level.Text", String.valueOf(entry.level()));
            ui.set(base + " #Value.Text", String.format(Locale.ROOT, "m:%.1f xp:%,.0f",
                    entry.momentum(), entry.xp24h()));
        }
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
            case "admin:tab:lb" -> { activeTab = AdminTab.LEADERBOARD; changed = true; }
            case "admin:tab:flagged" -> { activeTab = AdminTab.FLAGGED; changed = true; }
            case "admin:refresh" -> { changed = true; }
        }

        if (changed) {
            UICommandBuilder ui = new UICommandBuilder();
            renderContent(ui);
            sendUpdate(ui, false);
        }
    }
}
