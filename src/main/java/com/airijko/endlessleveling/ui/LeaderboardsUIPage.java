package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.races.RaceDefinition;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Leaderboards page showing players ordered by level (and XP) descending.
 */
public class LeaderboardsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String FILTER_ALL = "ALL";

    private SortField sortField = SortField.PRESTIGE;
    private boolean sortAscending = false;
    private String raceFilter = FILTER_ALL;
    private String classFilter = FILTER_ALL;
    private boolean podiumView = true;

    public LeaderboardsUIPage(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        boolean partnerAuthorized = EndlessLeveling.getInstance() != null
                && EndlessLeveling.getInstance().isPartnerAddonAuthorized();
        String pagePath = partnerAuthorized
                ? "Pages/Leaderboards/LeaderboardsPagePartner.ui"
                : "Pages/Leaderboards/LeaderboardsPage.ui";
        String fullPagePath = partnerAuthorized
                ? "Common/UI/Custom/Pages/Leaderboards/LeaderboardsPagePartner.ui"
                : "Common/UI/Custom/Pages/Leaderboards/LeaderboardsPage.ui";
        ui.append(pagePath);
        NavUIHelper.applyNavVersion(ui, playerRef, "leaderboards", fullPagePath, "#LeaderboardsTitle");
        NavUIHelper.bindNavEvents(events, fullPagePath);

        events.addEventBinding(Activating, "#SortByButton", of("Action", "lb:sort:field"), false);
        events.addEventBinding(Activating, "#SortOrderButton", of("Action", "lb:sort:order"), false);
        events.addEventBinding(Activating, "#RaceFilterButton", of("Action", "lb:filter:race"), false);
        events.addEventBinding(Activating, "#ClassFilterButton", of("Action", "lb:filter:class"), false);
        events.addEventBinding(Activating, "#ResetFiltersButton", of("Action", "lb:reset"), false);
        events.addEventBinding(Activating, "#ViewToggleButton", of("Action", "lb:view:toggle"), false);

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            LOGGER.atWarning().log("LeaderboardsUIPage: plugin unavailable during build");
            return;
        }

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        RaceManager raceManager = plugin.getRaceManager();
        ClassManager classManager = plugin.getClassManager();
        renderLeaderboard(ui, dataManager, raceManager, classManager);
    }

    private void renderLeaderboard(@Nonnull UICommandBuilder ui,
            PlayerDataManager dataManager,
            RaceManager raceManager,
            ClassManager classManager) {
        List<PlayerData> allPlayers = dataManager != null
                ? new ArrayList<>(dataManager.getAllPlayersSortedByLevel())
                : new ArrayList<>();

        List<String> raceOptions = buildRaceFilterOptions(allPlayers, raceManager);
        List<String> classOptions = buildClassFilterOptions(allPlayers, classManager);

        raceFilter = normalizeFilterSelection(raceFilter, raceOptions);
        classFilter = normalizeFilterSelection(classFilter, classOptions);

        List<PlayerData> visible = applyFilters(allPlayers, raceManager, classManager);
        visible.sort(buildComparator(raceManager, classManager));

        LOGGER.atInfo().log("LeaderboardsUIPage: rendering %d/%d players (sort=%s %s, race=%s, class=%s)",
                visible.size(), allPlayers.size(), sortField.name(), sortAscending ? "ASC" : "DESC", raceFilter,
                classFilter);

        String sortLabel = resolveSortFieldLabel(sortField);
        String orderLabel = sortAscending
                ? tr("ui.leaderboards.order.asc", "ASC")
                : tr("ui.leaderboards.order.desc", "DESC");
        String raceFilterDisplay = abbreviateFilter(displayFilterValue(raceFilter));
        String classFilterDisplay = abbreviateFilter(displayFilterValue(classFilter));

        ui.set("#LeaderboardsTitleLabel.Text", tr("ui.leaderboards.page.title", "Leaderboards"));
        ui.set("#LeaderboardsIntroText.Text",
                tr("ui.leaderboards.page.subtitle",
                        "Top champions ranked with live sort/filter controls. ({0} players)",
                        allPlayers.size()));
        ui.set("#SortLabel.Text", tr("ui.leaderboards.controls.sort", "Sort"));
        ui.set("#FilterLabel.Text", tr("ui.leaderboards.controls.filter", "Filter"));
        ui.set("#SortByButton.Text", tr("ui.leaderboards.controls.sort_by_value", "BY: {0}", sortLabel));
        ui.set("#SortOrderButton.Text", tr("ui.leaderboards.controls.sort_order_value", "ORDER: {0}", orderLabel));
        ui.set("#RaceFilterButton.Text", tr("ui.leaderboards.controls.race_value", "RACE: {0}", raceFilterDisplay));
        ui.set("#ClassFilterButton.Text", tr("ui.leaderboards.controls.class_value", "CLASS: {0}", classFilterDisplay));
        ui.set("#ResetFiltersButton.Text", tr("ui.leaderboards.controls.reset", "RESET"));
        ui.set("#LeaderboardsFilterSummary.Text",
                tr("ui.leaderboards.summary",
                        "Showing {0}/{1} players • Sort: {2} {3} • Filters: Race={4}, Class={5}",
                        visible.size(), allPlayers.size(), sortLabel, orderLabel,
                        displayFilterValue(raceFilter), displayFilterValue(classFilter)));
        ui.set("#HeaderRank.Text", tr("ui.leaderboards.column.rank", "Rank"));
        ui.set("#HeaderPlayer.Text", tr("ui.leaderboards.column.player", "Player"));
        ui.set("#HeaderRace.Text", tr("ui.leaderboards.column.race", "Race"));
        ui.set("#HeaderClass.Text", tr("ui.leaderboards.column.class", "Class"));
        ui.set("#HeaderPrestige.Text", tr("ui.leaderboards.column.prestige", "Prestige"));
        ui.set("#HeaderLevel.Text", tr("ui.leaderboards.column.level", "Level"));
        ui.set("#HeaderXp.Text", tr("ui.leaderboards.column.xp", "XP"));

        String viewLabel = podiumView
                ? tr("ui.leaderboards.view.podium", "VIEW: PODIUM")
                : tr("ui.leaderboards.view.table", "VIEW: TABLE");
        ui.set("#ViewToggleButton.Text", viewLabel);

        ui.clear("#PodiumCards");
        ui.clear("#RowCards");

        if (podiumView) {
            int podiumCount = Math.min(visible.size(), 3);
            ui.set("#PodiumSection.Visible", podiumCount > 0);
            ui.set("#TableSection.Visible", visible.size() > 3);
            ui.set("#PodiumTitle.Text", tr("ui.leaderboards.podium.title", "TOP CHAMPIONS"));

            // Podium: vertical stack 1st → 2nd → 3rd
            String[] podiumTemplates = {
                "Pages/Leaderboards/LeaderboardsPodiumFirst.ui",
                "Pages/Leaderboards/LeaderboardsPodiumSecond.ui",
                "Pages/Leaderboards/LeaderboardsPodiumThird.ui"
            };
            String[] podiumIcons = {
                "Ingredient_Ice_Essence",
                "Ingredient_Lightning_Essence",
                "Ingredient_Life_Essence"
            };

            for (int i = 0; i < podiumCount; i++) {
                ui.append("#PodiumCards", podiumTemplates[i]);

                PlayerData pd = visible.get(i);
                String base = "#PodiumCards[" + i + "]";
                String rankLabel;
                if (i == 0) rankLabel = "1ST";
                else if (i == 1) rankLabel = "2ND";
                else rankLabel = "3RD";

                ui.set(base + " #Rank.Text", rankLabel);
                ui.set(base + " #Name.Text", pd.getPlayerName());
                ui.set(base + " #PodiumIcon.ItemId", podiumIcons[i]);
                ui.set(base + " #Race.Text", resolveRaceLabel(raceManager, pd));
                ui.set(base + " #Class.Text", resolvePrimaryClassLabel(classManager, pd));
                ui.set(base + " #Prestige.Text", String.valueOf(pd.getPrestigeLevel()));
                ui.set(base + " #Level.Text", String.valueOf(pd.getLevel()));
                ui.set(base + " #Exp.Text", formatXp(pd.getXp()));
            }

            // Table: 4th place onward
            for (int i = 3; i < visible.size(); i++) {
                PlayerData pd = visible.get(i);
                ui.append("#RowCards", "Pages/Leaderboards/LeaderboardsRow.ui");

                int rowIdx = i - 3;
                String base = "#RowCards[" + rowIdx + "]";
                ui.set(base + " #Rank.Text", (i + 1) + ".");
                ui.set(base + " #Name.Text", pd.getPlayerName());
                ui.set(base + " #Race.Text", resolveRaceLabel(raceManager, pd));
                ui.set(base + " #Class.Text", resolvePrimaryClassLabel(classManager, pd));
                ui.set(base + " #Prestige.Text", String.valueOf(pd.getPrestigeLevel()));
                ui.set(base + " #Level.Text", String.valueOf(pd.getLevel()));
                ui.set(base + " #Exp.Text", formatXp(pd.getXp()));

                if (rowIdx % 2 == 1) {
                    ui.set(base + " #RowBgAlt.Visible", true);
                }
            }
        } else {
            // Table-only view: all players in rows, no podium
            ui.set("#PodiumSection.Visible", false);
            ui.set("#TableSection.Visible", !visible.isEmpty());

            for (int i = 0; i < visible.size(); i++) {
                PlayerData pd = visible.get(i);
                ui.append("#RowCards", "Pages/Leaderboards/LeaderboardsRow.ui");

                String base = "#RowCards[" + i + "]";
                ui.set(base + " #Rank.Text", (i + 1) + ".");
                ui.set(base + " #Name.Text", pd.getPlayerName());
                ui.set(base + " #Race.Text", resolveRaceLabel(raceManager, pd));
                ui.set(base + " #Class.Text", resolvePrimaryClassLabel(classManager, pd));
                ui.set(base + " #Prestige.Text", String.valueOf(pd.getPrestigeLevel()));
                ui.set(base + " #Level.Text", String.valueOf(pd.getLevel()));
                ui.set(base + " #Exp.Text", formatXp(pd.getXp()));

                if (i % 2 == 1) {
                    ui.set(base + " #RowBgAlt.Visible", true);
                }
            }
        }
    }

    private List<PlayerData> applyFilters(List<PlayerData> source,
            RaceManager raceManager,
            ClassManager classManager) {
        List<PlayerData> filtered = new ArrayList<>();
        for (PlayerData playerData : source) {
            if (!FILTER_ALL.equalsIgnoreCase(raceFilter)) {
                String playerRace = resolveRaceLabel(raceManager, playerData);
                if (!raceFilter.equalsIgnoreCase(playerRace)) {
                    continue;
                }
            }
            if (!FILTER_ALL.equalsIgnoreCase(classFilter)) {
                String playerClass = resolvePrimaryClassLabel(classManager, playerData);
                if (!classFilter.equalsIgnoreCase(playerClass)) {
                    continue;
                }
            }
            filtered.add(playerData);
        }
        return filtered;
    }

    private Comparator<PlayerData> buildComparator(RaceManager raceManager, ClassManager classManager) {
        Comparator<PlayerData> comparator;
        switch (sortField) {
            case LEVEL -> comparator = Comparator
                    .comparingInt(PlayerData::getLevel)
                    .thenComparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingDouble(PlayerData::getXp)
                    .thenComparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER);
            case XP -> comparator = Comparator
                    .comparingDouble(PlayerData::getXp)
                    .thenComparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingInt(PlayerData::getLevel)
                    .thenComparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER);
            case NAME -> comparator = Comparator
                    .comparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingInt(PlayerData::getLevel)
                    .thenComparingDouble(PlayerData::getXp);
            case RACE -> comparator = Comparator
                    .comparing((PlayerData pd) -> resolveRaceLabel(raceManager, pd), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingInt(PlayerData::getLevel)
                    .thenComparingDouble(PlayerData::getXp);
            case CLASS -> comparator = Comparator
                    .comparing((PlayerData pd) -> resolvePrimaryClassLabel(classManager, pd),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingInt(PlayerData::getLevel)
                    .thenComparingDouble(PlayerData::getXp);
            case PRESTIGE -> comparator = Comparator
                    .comparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingInt(PlayerData::getLevel)
                    .thenComparingDouble(PlayerData::getXp)
                    .thenComparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER);
            default -> comparator = Comparator
                    .comparingInt(PlayerData::getPrestigeLevel)
                    .thenComparingInt(PlayerData::getLevel)
                    .thenComparingDouble(PlayerData::getXp)
                    .thenComparing(PlayerData::getPlayerName, String.CASE_INSENSITIVE_ORDER);
        }

        if (!sortAscending) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private String normalizeFilterSelection(String selected, List<String> options) {
        if (selected == null || selected.isBlank()) {
            return FILTER_ALL;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(selected)) {
                return option;
            }
        }
        return FILTER_ALL;
    }

    private List<String> buildRaceFilterOptions(List<PlayerData> source, RaceManager raceManager) {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerData data : source) {
            values.add(resolveRaceLabel(raceManager, data));
        }
        List<String> options = new ArrayList<>();
        options.add(FILTER_ALL);
        options.addAll(values);
        return options;
    }

    private List<String> buildClassFilterOptions(List<PlayerData> source, ClassManager classManager) {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerData data : source) {
            values.add(resolvePrimaryClassLabel(classManager, data));
        }
        List<String> options = new ArrayList<>();
        options.add(FILTER_ALL);
        options.addAll(values);
        return options;
    }

    private String cycleFilterValue(String current, List<String> options) {
        if (options.isEmpty()) {
            return FILTER_ALL;
        }
        int currentIndex = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(current)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % options.size();
        return options.get(nextIndex);
    }

    private String abbreviateFilter(String value) {
        if (value == null || value.isBlank()) {
            return tr("ui.leaderboards.filter.all", "ALL");
        }
        if (value.length() <= 24) {
            return value;
        }
        return value.substring(0, 21) + "...";
    }

    private String displayFilterValue(String value) {
        if (value == null || value.isBlank() || FILTER_ALL.equalsIgnoreCase(value)) {
            return tr("ui.leaderboards.filter.all", "ALL");
        }
        return value;
    }

    private String resolveSortFieldLabel(SortField field) {
        return switch (field) {
            case PRESTIGE -> tr("ui.leaderboards.sort.prestige", "PRESTIGE");
            case LEVEL -> tr("ui.leaderboards.sort.level", "LEVEL");
            case XP -> tr("ui.leaderboards.sort.xp", "XP");
            case NAME -> tr("ui.leaderboards.sort.alphabetical", "ALPHABETICAL");
            case RACE -> tr("ui.leaderboards.sort.race", "RACE");
            case CLASS -> tr("ui.leaderboards.sort.class", "CLASS");
        };
    }

    private String tr(String key, String fallback, Object... args) {
        return com.airijko.endlessleveling.util.Lang.tr(playerRef.getUuid(), key, fallback, args);
    }

    private String resolveRaceLabel(RaceManager raceManager, PlayerData data) {
        if (data == null) {
            return "--";
        }
        if (raceManager != null) {
            RaceDefinition resolved = raceManager.getPlayerRace(data);
            if (resolved != null && resolved.getDisplayName() != null && !resolved.getDisplayName().isBlank()) {
                return resolved.getDisplayName();
            }
        }
        String raceId = data.getRaceId();
        return (raceId == null || raceId.isBlank()) ? "--" : raceId;
    }

    private String resolvePrimaryClassLabel(ClassManager classManager, PlayerData data) {
        if (data == null) {
            return "--";
        }
        if (classManager != null) {
            CharacterClassDefinition primary = classManager.getPlayerPrimaryClass(data);
            if (primary != null && primary.getDisplayName() != null && !primary.getDisplayName().isBlank()) {
                return primary.getDisplayName();
            }
        }
        String classId = data.getPrimaryClassId();
        return (classId == null || classId.isBlank()) ? "--" : classId;
    }

    private String formatXp(double xp) {
        return String.format(Locale.ROOT, "%,.0f", xp);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action != null && !data.action.isEmpty()) {
            if (NavUIHelper.handleNavAction(data.action, ref, store, playerRef)) {
                return;
            }

            EndlessLeveling plugin = EndlessLeveling.getInstance();
            if (plugin == null) {
                return;
            }

            PlayerDataManager dataManager = plugin.getPlayerDataManager();
            RaceManager raceManager = plugin.getRaceManager();
            ClassManager classManager = plugin.getClassManager();
            List<PlayerData> allPlayers = dataManager != null
                    ? new ArrayList<>(dataManager.getAllPlayersSortedByLevel())
                    : new ArrayList<>();
            String action = data.action.trim().toLowerCase(Locale.ROOT);

            boolean changed = false;
            switch (action) {
                case "lb:sort:field" -> {
                    sortField = sortField.next();
                    changed = true;
                }
                case "lb:sort:order" -> {
                    sortAscending = !sortAscending;
                    changed = true;
                }
                case "lb:filter:race" -> {
                    raceFilter = cycleFilterValue(raceFilter, buildRaceFilterOptions(allPlayers, raceManager));
                    changed = true;
                }
                case "lb:filter:class" -> {
                    classFilter = cycleFilterValue(classFilter, buildClassFilterOptions(allPlayers, classManager));
                    changed = true;
                }
                case "lb:view:toggle" -> {
                    podiumView = !podiumView;
                    changed = true;
                }
                case "lb:reset", "reset", "filters:reset", "lb:filters:reset" -> {
                    sortField = SortField.PRESTIGE;
                    sortAscending = false;
                    raceFilter = FILTER_ALL;
                    classFilter = FILTER_ALL;
                    changed = true;
                }
                default -> {
                }
            }

            if (changed) {
                UICommandBuilder ui = new UICommandBuilder();
                renderLeaderboard(ui, dataManager, raceManager, classManager);
                sendUpdate(ui, false);
            }
        }
    }

    private enum SortField {
        PRESTIGE,
        LEVEL,
        XP,
        NAME,
        RACE,
        CLASS;

        private SortField next() {
            SortField[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
