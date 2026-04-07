package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.types.CommonAugment;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.enums.themes.AugmentTheme;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.util.Lang;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.MouseEntered;
import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.MouseExited;
import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Searchable augment browser page that uses a row-based item grid.
 */
public class AugmentsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final int GRID_ITEMS_PER_ROW = 5;
    private static final int INFO_SECTION_LIMIT = 5;
    private static final String[] INFO_SECTION_CONTAINERS = {
        "#AugmentInfoSection1",
        "#AugmentInfoSection2",
        "#AugmentInfoSection3",
        "#AugmentInfoSection4",
        "#AugmentInfoSection5"
    };
    private static final String[] INFO_SECTION_TITLES = {
        "#AugmentInfoSection1Title",
        "#AugmentInfoSection2Title",
        "#AugmentInfoSection3Title",
        "#AugmentInfoSection4Title",
        "#AugmentInfoSection5Title"
    };
    private static final String[] INFO_SECTION_BODIES = {
        "#AugmentInfoSection1Body",
        "#AugmentInfoSection2Body",
        "#AugmentInfoSection3Body",
        "#AugmentInfoSection4Body",
        "#AugmentInfoSection5Body"
    };
    private static final String DURATION_TITLE = "duration";
    private static final String COOLDOWN_TITLE = "cooldown";

    private final AugmentManager augmentManager;
    private final AugmentUnlockManager augmentUnlockManager;
    private final PlayerDataManager playerDataManager;
    private final PlayerRef playerRef;
    private final AugmentPresentationMapper augmentPresentationMapper;

    private String searchQuery = "";
    private String selectedAugmentId = null;

    public AugmentsUIPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.augmentManager = plugin != null ? plugin.getAugmentManager() : null;
        this.augmentUnlockManager = plugin != null ? plugin.getAugmentUnlockManager() : null;
        this.playerDataManager = plugin != null ? plugin.getPlayerDataManager() : null;
        this.playerRef = playerRef;
        this.augmentPresentationMapper = new AugmentPresentationMapper(this::tr);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/Augments/AugmentsPage.ui");
        NavUIHelper.applyNavVersion(ui, playerRef, "augments",
            "Common/UI/Custom/Pages/Augments/AugmentsPage.ui",
            "#AugmentsPageTitle");
        applyStaticLabels(ui);
        ui.set("#SearchInput.Value", this.searchQuery);
        NavUIHelper.bindNavEvents(events);
        events.addEventBinding(ValueChanged, "#SearchInput", of("@SearchQuery", "#SearchInput.Value"), false);
        events.addEventBinding(Activating, "#OpenAugmentsChooseButton", of("Action", "augment:open_choose"),
                false);
        events.addEventBinding(Activating, "#AugmentInfoRerollButton", of("Action", "augment:reroll:selected"),
            false);

        buildGrid(ui, events);
        applyInfoPanel(ui, selectedAugmentId);
    }

    private void applyStaticLabels(@Nonnull UICommandBuilder ui) {
        ui.set("#OpenAugmentsChooseButton.Text", tr("ui.augments.page.choose_button", "CHOOSE AUGMENTS"));
        ui.set("#AugmentsOverviewDescription.Text", tr("ui.augments.page.left.description",
                "Augments are powerful passive enhancements that permanently strengthen your character. Earn them by reaching level milestones and through prestige."));
        ui.set("#AugmentsCollectionTitle.Text", tr("ui.augments.page.left.collection_title", "YOUR COLLECTION"));
        ui.set("#AugmentsRerollsTitle.Text", tr("ui.augments.page.left.rerolls_available_title", "REROLLS AVAILABLE"));
        ui.set("#AugmentRerollTotal.Text", tr("ui.augments.page.rerolls.total", "Total: {0}", 0));
        ui.set("#AugmentsInfoText.Text",
                tr("ui.augments.page.left.hover_hint", "Hover over an augment to preview it."));
        ui.set("#SearchInput.PlaceholderText", tr("ui.augments.page.search_placeholder", "Search augments..."));
        ui.set("#UnlockedHeader.Text", tr("ui.augments.page.section.unlocked", "UNLOCKED"));
        ui.set("#MythicHeader.Text", tr("ui.augments.page.section.mythic", "MYTHIC"));
        ui.set("#LegendaryHeader.Text", tr("ui.augments.page.section.legendary", "LEGENDARY"));
        ui.set("#EliteHeader.Text", tr("ui.augments.page.section.elite", "ELITE"));
        ui.set("#CommonHeader.Text", tr("ui.augments.page.section.common", "COMMON"));
        ui.set("#AugmentInfoPanel.Text", tr("ui.augments.page.info_title", "AUGMENT INFO"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action != null && !data.action.isBlank()) {
            if (NavUIHelper.handleNavAction(data.action, ref, store, playerRef)) {
                return;
            }
        }

        if (data.action != null && !data.action.isBlank()) {
            String action = data.action.trim();
            if ("augment:open_choose".equalsIgnoreCase(action)) {
                openChoosePage(ref, store);
                return;
            }
            if ("augment:reroll:selected".equalsIgnoreCase(action)) {
                handleSelectedAugmentReroll(ref, store);
                return;
            }
            if (action.startsWith("augment:hover:")) {
                String augmentId = action.substring("augment:hover:".length());

                UICommandBuilder commandBuilder = new UICommandBuilder();
                UIEventBuilder eventBuilder = new UIEventBuilder();

                if (this.selectedAugmentId == null) {
                    applyInfoPanel(commandBuilder, augmentId.isBlank() ? null : augmentId);
                }

                this.sendUpdate(commandBuilder, eventBuilder, false);
                return;
            }
            if (action.startsWith("augment:hoverend:")) {
                if (this.selectedAugmentId == null) {
                    UICommandBuilder commandBuilder = new UICommandBuilder();
                    UIEventBuilder eventBuilder = new UIEventBuilder();
                    applyInfoPanel(commandBuilder, null);
                    this.sendUpdate(commandBuilder, eventBuilder, false);
                }
                return;
            }
            if (action.startsWith("augment:select:")) {
                String id = action.substring("augment:select:".length());
                this.selectedAugmentId = id.isBlank() ? null : id;
                UICommandBuilder commandBuilder = new UICommandBuilder();
                UIEventBuilder eventBuilder = new UIEventBuilder();
                applyInfoPanel(commandBuilder, this.selectedAugmentId);
                this.sendUpdate(commandBuilder, eventBuilder, false);
                return;
            }
        }

        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase(Locale.ROOT);
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            commandBuilder.set("#SearchInput.Value", data.searchQuery);
            buildGrid(commandBuilder, eventBuilder);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }
    }

    private void buildGrid(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events) {
        // Clear all section grids
        ui.clear("#UnlockedCards");
        ui.clear("#MythicCards");
        ui.clear("#LegendaryCards");
        ui.clear("#EliteCards");
        ui.clear("#CommonCards");

        if (augmentManager == null) {
            ui.set("#AugmentsResultLabel.Text", tr("ui.augments.page.results", "Results: {0}", 0));
            ui.set("#AugmentsChooseAvailabilityLabel.Text",
                    tr("ui.augments.page.choose_unavailable", "No augments available to choose."));
            ui.set("#AugmentsChooseAvailabilityLabel.Style.TextColor", AugmentTheme.chooseAvailabilityColor(false));
            ui.set("#UnlockedSection.Visible", false);
            ui.set("#MythicSection.Visible", false);
            ui.set("#LegendarySection.Visible", false);
            ui.set("#EliteSection.Visible", false);
            ui.set("#CommonSection.Visible", false);
            return;
        }

        PlayerData playerData = playerDataManager != null ? playerDataManager.get(playerRef.getUuid()) : null;
        if (playerData != null && augmentUnlockManager != null) {
            augmentUnlockManager.ensureUnlocks(playerData);
        }
        applyChooseAvailability(ui, playerData);
        applyLeftPanel(ui, playerData);

        Set<String> ownedIds = resolveOwnedIds(playerData);
        Set<String> ownedCommonAttributes = resolveOwnedCommonAttributes(playerData);
        List<OwnedAugmentCard> unlockedCards = applySearchOwned(buildOwnedCards(playerData));
        Collection<AugmentDefinition> all = augmentManager.getAugments().values();

        // Organize augments into sections
        List<AugmentDefinition> mythicAugments = new java.util.ArrayList<>();
        List<AugmentDefinition> legendaryAugments = new java.util.ArrayList<>();
        List<AugmentDefinition> eliteAugments = new java.util.ArrayList<>();
        List<AugmentDefinition> additionalCommonAugments = new java.util.ArrayList<>();
        AugmentDefinition baseCommonDefinition = null;

        for (AugmentDefinition def : all) {
            boolean owned = ownedIds.contains(def.getId());
            if (!owned) {
                switch (def.getTier()) {
                    case MYTHIC:
                        mythicAugments.add(def);
                        break;
                    case LEGENDARY:
                        legendaryAugments.add(def);
                        break;
                    case ELITE:
                        eliteAugments.add(def);
                        break;
                    case COMMON:
                        if (CommonAugment.ID.equalsIgnoreCase(def.getId())) {
                            baseCommonDefinition = def;
                        } else {
                            additionalCommonAugments.add(def);
                        }
                        break;
                }
            }
        }

        // Sort each section
        mythicAugments.sort(Comparator.comparing(AugmentDefinition::getName));
        legendaryAugments.sort(Comparator.comparing(AugmentDefinition::getName));
        eliteAugments.sort(Comparator.comparing(AugmentDefinition::getName));
        additionalCommonAugments.sort(Comparator.comparing(AugmentDefinition::getName));

        // Apply search filter to all sections
        mythicAugments = applySearch(mythicAugments);
        legendaryAugments = applySearch(legendaryAugments);
        eliteAugments = applySearch(eliteAugments);
        additionalCommonAugments = applySearch(additionalCommonAugments);

        List<String> commonVariantIds = buildCommonVariantIds(baseCommonDefinition, ownedCommonAttributes);
        commonVariantIds.addAll(additionalCommonAugments.stream().map(AugmentDefinition::getId).toList());
        commonVariantIds = applySearchAugmentIds(commonVariantIds);

        int totalResults = unlockedCards.size() + mythicAugments.size() + legendaryAugments.size()
            + eliteAugments.size()
                + commonVariantIds.size();
        ui.set("#AugmentsResultLabel.Text", tr("ui.augments.page.results", "Results: {0}", totalResults));

        // Build UNLOCKED section
        buildOwnedSection(ui, events, unlockedCards, "#UnlockedCards", "#UnlockedSection");

        // Build MYTHIC section
        buildSection(ui, events, mythicAugments, "#MythicCards", "#MythicSection", ownedIds);

        // Build LEGENDARY section
        buildSection(ui, events, legendaryAugments, "#LegendaryCards", "#LegendarySection", ownedIds);

        // Build ELITE section
        buildSection(ui, events, eliteAugments, "#EliteCards", "#EliteSection", ownedIds);

        // Build COMMON section
        buildSectionById(ui, events, commonVariantIds, "#CommonCards", "#CommonSection");
    }

    private void applyInfoPanel(@Nonnull UICommandBuilder ui, String augmentId) {
        PlayerData playerData = playerDataManager != null ? playerDataManager.get(playerRef.getUuid()) : null;
        AugmentDefinition def = (augmentId != null && augmentManager != null)
                ? augmentManager.getAugment(augmentId)
                : null;
        applyInfoPanelBackground(ui, def != null ? def.getTier() : null);

        if (def == null) {
            ui.set("#AugmentInfoIcon.Visible", false);
            ui.set("#AugmentInfoName.Text", tr("ui.augments.page.info_select_prompt", "Select an augment"));
            ui.set("#AugmentInfoName.Style.TextColor", "#9fb6d3");
            ui.set("#AugmentInfoTier.Visible", false);
            ui.set("#AugmentInfoDivider.Visible", false);
            ui.set("#AugmentInfoDescription.Visible", false);
            ui.set("#AugmentInfoRerollButton.Visible", false);
            applyInfoSections(ui, List.of());
            return;
        }

        AugmentPresentationMapper.AugmentPresentationData presentation = augmentPresentationMapper.map(def, augmentId);
        ui.set("#AugmentInfoIcon.ItemId", presentation.iconItemId());
        ui.set("#AugmentInfoIcon.Visible", true);

        ui.set("#AugmentInfoName.Text", presentation.displayName());
        ui.set("#AugmentInfoName.Style.TextColor", tierColor(def.getTier()));

        String tierName = def.getTier() != null ? def.getTier().name() : "";
        ui.set("#AugmentInfoTier.Text", tierName);
        ui.set("#AugmentInfoTier.Style.TextColor", tierColor(def.getTier()));
        ui.set("#AugmentInfoTier.Visible", !tierName.isBlank());

        int remainingForSelectedTier = resolveRemainingRerolls(playerData, def.getTier());
        boolean privilegedRerollBypass = hasPrivilegedRerollAccess();
        boolean isPersistedSelection = isSelectedAugmentOwned(playerData, augmentId, def);
        boolean showRerollButton = isPersistedSelection && (remainingForSelectedTier > 0 || privilegedRerollBypass)
            && this.selectedAugmentId != null
            && this.selectedAugmentId.equalsIgnoreCase(augmentId);
        ui.set("#AugmentInfoRerollButton.Text",
            privilegedRerollBypass
                ? tr("ui.augments.page.info.reroll_button_infinite", "REROLL (INF)")
                : tr("ui.augments.page.info.reroll_button", "REROLL ({0})", remainingForSelectedTier));
        ui.set("#AugmentInfoRerollButton.Visible", showRerollButton);

        ui.set("#AugmentInfoDivider.Visible", true);

        // Common augment variants can either be selected rolls or catalog entries.
        // Selected entries show rolled values, while catalog entries show configured ranges.
        if (presentation.commonStatOffer() != null && CommonAugment.ID.equalsIgnoreCase(def.getId())) {
            CommonAugment.CommonStatOffer statOffer = presentation.commonStatOffer();
            boolean selectedCommonOffer = isOwnedCommonSelection(playerData, augmentId);
            boolean catalogCommonVariant = statOffer.rolledValue() < 0.0D;
            String statLine = (selectedCommonOffer || !catalogCommonVariant)
                    ? formatCommonStatRolledLine(statOffer)
                    : formatCommonStatRangeLine(def, statOffer.attributeKey());

            String desc = def.getDescription();
            boolean hasDesc = desc != null && !desc.isBlank();
            ui.set("#AugmentInfoDescription.Text", hasDesc ? desc : "");
            ui.set("#AugmentInfoDescription.Visible", hasDesc);
            applyInfoSections(ui, List.of(new InfoSection("", statLine, "#c5d4e8")));
        } else {
            String desc = def.getDescription();
            boolean hasDesc = desc != null && !desc.isBlank();
            ui.set("#AugmentInfoDescription.Text", hasDesc ? desc : "");
            ui.set("#AugmentInfoDescription.Visible", hasDesc);
            applyInfoSections(ui, buildYamlInfoSections(def));
        }
    }

    private List<InfoSection> buildYamlInfoSections(@Nonnull AugmentDefinition definition) {
        List<InfoSection> sections = new ArrayList<>();
        for (AugmentDefinition.UiSection section : definition.getUiSections()) {
            if (section == null) {
                continue;
            }
            String rawTitle = section.title() == null ? "" : section.title().trim();
            String body = normalizeMultilineText(section.body());
            String title = rawTitle;
            if (isCompactTimingSection(rawTitle)) {
                title = "";
                body = compactTimingBody(rawTitle, body);
            }
            if (title.isBlank() && body.isBlank()) {
                continue;
            }
            sections.add(new InfoSection(title, body, normalizeColor(section.color(), "#c5d4e8")));
            if (sections.size() >= INFO_SECTION_LIMIT) {
                break;
            }
        }
        return sections;
    }

    private void applyInfoSections(@Nonnull UICommandBuilder ui, @Nonnull List<InfoSection> sections) {
        List<InfoSection> safeSections = sections == null ? List.of() : sections;
        boolean hasAny = false;

        for (int index = 0; index < INFO_SECTION_LIMIT; index++) {
            String containerSelector = INFO_SECTION_CONTAINERS[index];
            String titleSelector = INFO_SECTION_TITLES[index];
            String bodySelector = INFO_SECTION_BODIES[index];

            if (index >= safeSections.size()) {
                ui.set(containerSelector + ".Visible", false);
                ui.set(titleSelector + ".Visible", false);
                ui.set(bodySelector + ".Visible", false);
                continue;
            }

            InfoSection section = safeSections.get(index);
            String title = section.title() == null ? "" : section.title().trim();
            String body = normalizeMultilineText(section.body());
            String color = normalizeColor(section.color(), "#c5d4e8");
            boolean hasTitle = !title.isBlank();
            boolean hasBody = !body.isBlank();
            boolean visible = hasTitle || hasBody;

            ui.set(containerSelector + ".Visible", visible);
            ui.set(titleSelector + ".Text", title);
            ui.set(titleSelector + ".Visible", hasTitle);
            ui.set(bodySelector + ".Text", body);
            ui.set(bodySelector + ".Visible", hasBody);
            ui.set(bodySelector + ".Style.TextColor", color);

            if (visible) {
                hasAny = true;
            }
        }

        ui.set("#AugmentInfoDivider2.Visible", hasAny);
        ui.set("#AugmentInfoValues.Visible", hasAny);
    }

    private String normalizeColor(String color, String fallback) {
        if (color == null) {
            return fallback;
        }
        String trimmed = color.trim();
        if (trimmed.matches("#?[0-9a-fA-F]{6}")) {
            return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
        }
        return fallback;
    }

    private String normalizeMultilineText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        boolean lastWasBlank = true;
        for (String line : text.split("\\n", -1)) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isBlank()) {
                if (!lastWasBlank) {
                    lines.add("");
                    lastWasBlank = true;
                }
                continue;
            }
            lines.add(trimmed);
            lastWasBlank = false;
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return String.join("\n", lines);
    }

    private boolean isCompactTimingSection(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        return DURATION_TITLE.equals(normalized) || COOLDOWN_TITLE.equals(normalized);
    }

    private String compactTimingBody(String title, String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        boolean isDuration = DURATION_TITLE.equals(title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
        List<String> compactLines = new ArrayList<>();
        for (String rawLine : body.split("\\n", -1)) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            compactLines.add(isDuration ? normalizeDurationLine(line) : line);
        }
        return String.join(" | ", compactLines);
    }

    private String normalizeDurationLine(String line) {
        String normalized = line.replaceFirst("(?i)^duration\\s+per\\s+stack\\s*:\\s*\\+?([0-9]+(?:\\.[0-9]+)?)x?$", "Duration: $1s");
        normalized = normalized.replaceFirst("(?i)^duration\\s*:\\s*\\+?([0-9]+(?:\\.[0-9]+)?)x$", "Duration: $1s");
        normalized = normalized.replaceFirst("(?i)^seconds\\s*:\\s*\\+?([0-9]+(?:\\.[0-9]+)?)$", "Duration: $1s");
        return normalized;
    }

    private record InfoSection(String title, String body, String color) {
    }

    private void openChoosePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new AugmentsChoosePage(playerRef, CustomPageLifetime.CanDismiss));
    }

    private void applyChooseAvailability(@Nonnull UICommandBuilder ui, PlayerData playerData) {
        boolean available = hasPendingAugmentChoices(playerData);
        if (available) {
            ui.set("#AugmentsChooseAvailabilityLabel.Text",
                    tr("ui.augments.page.choose_available", "Augments available to choose."));
            ui.set("#AugmentsChooseAvailabilityLabel.Style.TextColor", AugmentTheme.chooseAvailabilityColor(true));
            return;
        }

        ui.set("#AugmentsChooseAvailabilityLabel.Text",
                tr("ui.augments.page.choose_unavailable", "No augments available to choose."));
        ui.set("#AugmentsChooseAvailabilityLabel.Style.TextColor", AugmentTheme.chooseAvailabilityColor(false));
    }

    private boolean hasPendingAugmentChoices(PlayerData playerData) {
        if (playerData == null) {
            return false;
        }

        Map<String, List<String>> offersByTier = playerData.getAugmentOffersSnapshot();
        for (List<String> offers : offersByTier.values()) {
            if (offers != null && !offers.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void applyLeftPanel(@Nonnull UICommandBuilder ui, PlayerData playerData) {
        Set<String> ownedIds = resolveOwnedIds(playerData);
        Collection<AugmentDefinition> allDefs = augmentManager != null ? augmentManager.getAugments().values()
                : List.of();

        ui.set("#AugmentStatCommon.Style.TextColor", AugmentTheme.gridOwnedColor(PassiveTier.COMMON));
        ui.set("#AugmentRerollCommon.Style.TextColor", AugmentTheme.gridOwnedColor(PassiveTier.COMMON));
        ui.set("#AugmentStatLegendary.Style.TextColor", AugmentTheme.gridOwnedColor(PassiveTier.LEGENDARY));
        ui.set("#AugmentRerollLegendary.Style.TextColor", AugmentTheme.gridOwnedColor(PassiveTier.LEGENDARY));

        long totalMythic = allDefs.stream().filter(d -> d.getTier() == PassiveTier.MYTHIC).count();
        long totalLegendary = allDefs.stream().filter(d -> d.getTier() == PassiveTier.LEGENDARY).count();
        long totalElite = allDefs.stream().filter(d -> d.getTier() == PassiveTier.ELITE).count();

        int mythicOwned = countSelectedForTier(playerData, PassiveTier.MYTHIC);
        int legendaryOwned = countSelectedForTier(playerData, PassiveTier.LEGENDARY);
        int eliteOwned = countSelectedForTier(playerData, PassiveTier.ELITE);
        int commonOwned = countSelectedForTier(playerData, PassiveTier.COMMON);
        int totalOwned = mythicOwned + legendaryOwned + eliteOwned + commonOwned;

        ui.set("#AugmentStatTotal.Text",
                tr("ui.augments.page.stats.total", "Total: {0} / {1}", totalOwned, allDefs.size()));
        ui.set("#AugmentStatMythic.Text",
                tr("ui.augments.page.stats.mythic", "Mythic: {0} / {1}", mythicOwned, totalMythic));
        ui.set("#AugmentStatLegendary.Text",
            tr("ui.augments.page.stats.legendary", "Legendary: {0} / {1}", legendaryOwned, totalLegendary));
        ui.set("#AugmentStatElite.Text",
                tr("ui.augments.page.stats.elite", "Elite: {0} / {1}", eliteOwned, totalElite));
        ui.set("#AugmentStatCommon.Text", tr("ui.augments.page.stats.common", "Common: {0}", commonOwned));

        int mythicRerolls = resolveRemainingRerolls(playerData, PassiveTier.MYTHIC);
        int legendaryRerolls = resolveRemainingRerolls(playerData, PassiveTier.LEGENDARY);
        int eliteRerolls = resolveRemainingRerolls(playerData, PassiveTier.ELITE);
        int commonRerolls = resolveRemainingRerolls(playerData, PassiveTier.COMMON);
        int totalRerolls = mythicRerolls + legendaryRerolls + eliteRerolls + commonRerolls;

        ui.set("#AugmentRerollTotal.Text",
            tr("ui.augments.page.rerolls.total", "Total: {0}", totalRerolls));

        ui.set("#AugmentRerollMythic.Text",
                tr("ui.augments.page.rerolls.mythic", "Mythic: {0}", mythicRerolls));
        ui.set("#AugmentRerollLegendary.Text",
            tr("ui.augments.page.rerolls.legendary", "Legendary: {0}", legendaryRerolls));
        ui.set("#AugmentRerollElite.Text",
                tr("ui.augments.page.rerolls.elite", "Elite: {0}", eliteRerolls));
        ui.set("#AugmentRerollCommon.Text",
                tr("ui.augments.page.rerolls.common", "Common: {0}", commonRerolls));
    }

    private int resolveRemainingRerolls(PlayerData playerData, PassiveTier tier) {
        if (playerData == null || tier == null || augmentUnlockManager == null) {
            return 0;
        }
        return Math.max(0, augmentUnlockManager.getRemainingRerolls(playerData, tier));
    }

    private boolean hasPrivilegedRerollAccess() {
        return OperatorHelper.hasAdministrativeAccess(playerRef);
    }

    private boolean isSelectedAugmentOwned(PlayerData playerData, String augmentId, AugmentDefinition selectedDef) {
        if (playerData == null || augmentId == null || augmentId.isBlank()) {
            return false;
        }

        String normalizedTarget = augmentId.trim().toLowerCase(Locale.ROOT);
        String selectedCanonical = selectedDef != null && selectedDef.getId() != null
                ? selectedDef.getId().trim().toLowerCase(Locale.ROOT)
                : normalizedTarget;

        for (String selectedId : playerData.getSelectedAugmentsSnapshot().values()) {
            if (selectedId == null || selectedId.isBlank()) {
                continue;
            }

            String normalizedSelected = selectedId.trim().toLowerCase(Locale.ROOT);
            if (normalizedSelected.equals(normalizedTarget)) {
                return true;
            }

            AugmentDefinition ownedDef = augmentManager != null ? augmentManager.getAugment(selectedId) : null;
            if (ownedDef != null && ownedDef.getId() != null) {
                String ownedCanonical = ownedDef.getId().trim().toLowerCase(Locale.ROOT);
                if (ownedCanonical.equals(selectedCanonical)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleSelectedAugmentReroll(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (selectedAugmentId == null || selectedAugmentId.isBlank()) {
            playerRef.sendMessage(Message.raw(tr("ui.augments.error.reroll_nothing_selected",
                    "Select an owned augment first.")).color("#ff9900"));
            return;
        }

        if (playerDataManager == null || augmentUnlockManager == null || augmentManager == null) {
            playerRef.sendMessage(Message.raw(tr("ui.augments.error.data_unavailable",
                    "Augment data unavailable.")).color("#ff6666"));
            return;
        }

        PlayerData playerData = playerDataManager.get(playerRef.getUuid());
        if (playerData == null) {
            playerRef.sendMessage(Message.raw(tr("ui.augments.error.playerdata_unavailable",
                    "Unable to load your player data. Try reopening this page.")).color("#ff6666"));
            return;
        }

        String slotKey = null;
        String selectedCanonical = selectedAugmentId.trim().toLowerCase(Locale.ROOT);
        AugmentDefinition selectedDef = augmentManager.getAugment(selectedAugmentId);
        if (selectedDef != null && selectedDef.getId() != null) {
            selectedCanonical = selectedDef.getId().trim().toLowerCase(Locale.ROOT);
        }

        for (Map.Entry<String, String> entry : playerData.getSelectedAugmentsSnapshot().entrySet()) {
            String candidateId = entry.getValue();
            if (candidateId == null || candidateId.isBlank()) {
                continue;
            }

            String candidateCanonical = candidateId.trim().toLowerCase(Locale.ROOT);
            AugmentDefinition candidateDef = augmentManager.getAugment(candidateId);
            if (candidateDef != null && candidateDef.getId() != null) {
                candidateCanonical = candidateDef.getId().trim().toLowerCase(Locale.ROOT);
            }

            if (candidateCanonical.equals(selectedCanonical)) {
                slotKey = entry.getKey();
                break;
            }
        }

        if (slotKey == null || slotKey.isBlank()) {
            playerRef.sendMessage(Message.raw(tr("ui.augments.error.reroll_not_owned",
                    "That augment is not in your selected unlocks.")).color("#ff9900"));
            return;
        }

        PassiveTier tier;
        try {
            String tierKey = slotKey;
            int hashIndex = tierKey.indexOf('#');
            if (hashIndex >= 0) {
                tierKey = tierKey.substring(0, hashIndex);
            }
            int colonIndex = tierKey.indexOf(':');
            if (colonIndex >= 0) {
                tierKey = tierKey.substring(0, colonIndex);
            }
            tier = PassiveTier.valueOf(tierKey.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            playerRef.sendMessage(Message.raw(tr("ui.augments.error.tier_unresolved",
                    "Unable to resolve augment tier.")).color("#ff6666"));
            return;
        }

        int remaining = augmentUnlockManager.getRemainingRerolls(playerData, tier);
        boolean privilegedBypass = hasPrivilegedRerollAccess();
        if (remaining <= 0 && !privilegedBypass) {
            playerRef.sendMessage(Message.raw(tr("ui.augments.error.no_rerolls_tier",
                    "No rerolls available for {0} tier.", tier.name())).color("#ff9900"));
            return;
        }

        String tierKey = tier.name();
        int offersBefore = playerData.getAugmentOffersForTier(tierKey).size();
        playerData.setSelectedAugmentForTier(slotKey, null);
        if (!privilegedBypass) {
            playerData.incrementAugmentRerollsUsedForTier(tierKey);
        }
        augmentUnlockManager.ensureUnlocks(playerData);

        int offersAfter = playerData.getAugmentOffersForTier(tierKey).size();
        if (offersAfter <= offersBefore) {
            boolean forced = augmentUnlockManager.forceOfferBundleForTier(playerData, tier);
            if (!forced) {
                playerDataManager.save(playerData);
                playerRef.sendMessage(Message.raw(tr("ui.augments.error.reroll_failed",
                        "Reroll failed. Try again.")).color("#ff6666"));
                return;
            }
        }

        playerDataManager.save(playerData);
        this.selectedAugmentId = null;

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        buildGrid(commandBuilder, eventBuilder);
        applyInfoPanel(commandBuilder, null);
        this.sendUpdate(commandBuilder, eventBuilder, false);

        int remainingAfter = augmentUnlockManager.getRemainingRerolls(playerData, tier);
        String successMessage = privilegedBypass
            ? tr("ui.augments.reroll.success_bypass",
                "Rerolled {0} selection. Operator/Admin bypass active (no reroll consumed).", tier.name())
            : tr("ui.augments.reroll.success",
                "Rerolled {0} selection. Remaining rerolls: {1}", tier.name(), remainingAfter);
        playerRef.sendMessage(Message.raw(successMessage).color("#4fd7f7"));
    }

    private int countSelectedForTier(PlayerData playerData, PassiveTier tier) {
        if (playerData == null || tier == null) {
            return 0;
        }

        String normalizedTier = tier.name().toUpperCase(Locale.ROOT);
        int count = 0;
        for (String key : playerData.getSelectedAugmentsSnapshot().keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalizedKey = key.trim().toUpperCase(Locale.ROOT);
            if (normalizedKey.equals(normalizedTier) || normalizedKey.startsWith(normalizedTier + "#")) {
                count++;
            }
        }
        return count;
    }

    private void buildSection(@Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull List<AugmentDefinition> augments,
            @Nonnull String cardsSelector,
            @Nonnull String sectionSelector,
            @Nonnull Set<String> ownedIds) {
        boolean hasContent = !augments.isEmpty();
        ui.set(sectionSelector + ".Visible", hasContent);

        if (!hasContent) {
            return;
        }

        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (AugmentDefinition def : augments) {
            if (cardsInCurrentRow == 0) {
                ui.appendInline(cardsSelector, "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            AugmentPresentationMapper.AugmentPresentationData presentation = augmentPresentationMapper.map(def,
                    def.getId());

            ui.append(cardsSelector + "[" + rowIndex + "]", "Pages/Augments/AugmentGridEntry.ui");
            String base = cardsSelector + "[" + rowIndex + "][" + cardsInCurrentRow + "]";

            ui.set(base + " #ItemIcon.ItemId", presentation.iconItemId());
            ui.set(base + " #ItemName.Text", presentation.displayName());
            ui.set(base + " #ItemName.Style.TextColor", tierColor(def.getTier()));
            applyGridEntryTierBackground(ui, base, def.getTier());

            String hitButtonSelector = base + " #HitButton";
            events.addEventBinding(Activating, hitButtonSelector, of("Action", "augment:select:" + def.getId()), false);
            events.addEventBinding(MouseEntered, hitButtonSelector, of("Action", "augment:hover:" + def.getId()), false);
            events.addEventBinding(MouseExited, hitButtonSelector, of("Action", "augment:hoverend:" + def.getId()), false);

            cardsInCurrentRow++;
            if (cardsInCurrentRow >= GRID_ITEMS_PER_ROW) {
                cardsInCurrentRow = 0;
                rowIndex++;
            }
        }
    }

    private void buildOwnedSection(@Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull List<OwnedAugmentCard> cards,
            @Nonnull String cardsSelector,
            @Nonnull String sectionSelector) {
        boolean hasContent = !cards.isEmpty();
        ui.set(sectionSelector + ".Visible", hasContent);

        if (!hasContent) {
            return;
        }

        List<OwnedAugmentCard> mythicCards = new ArrayList<>();
        List<OwnedAugmentCard> legendaryCards = new ArrayList<>();
        List<OwnedAugmentCard> eliteCards = new ArrayList<>();
        List<OwnedAugmentCard> commonCards = new ArrayList<>();
        List<OwnedAugmentCard> uncategorizedCards = new ArrayList<>();

        for (OwnedAugmentCard card : cards) {
            AugmentDefinition definition = augmentManager != null ? augmentManager.getAugment(card.id()) : null;
            PassiveTier tier = definition != null ? definition.getTier() : null;
            if (tier == PassiveTier.MYTHIC) {
                mythicCards.add(card);
            } else if (tier == PassiveTier.LEGENDARY) {
                legendaryCards.add(card);
            } else if (tier == PassiveTier.ELITE) {
                eliteCards.add(card);
            } else if (tier == PassiveTier.COMMON) {
                commonCards.add(card);
            } else {
                uncategorizedCards.add(card);
            }
        }

        int rowIndex = 0;
        boolean hasPreviousTierGroup = false;

        rowIndex = appendOwnedTierGroup(ui, events, cardsSelector, mythicCards, rowIndex, hasPreviousTierGroup);
        hasPreviousTierGroup = hasPreviousTierGroup || !mythicCards.isEmpty();

        rowIndex = appendOwnedTierGroup(ui, events, cardsSelector, legendaryCards, rowIndex, hasPreviousTierGroup);
        hasPreviousTierGroup = hasPreviousTierGroup || !legendaryCards.isEmpty();

        rowIndex = appendOwnedTierGroup(ui, events, cardsSelector, eliteCards, rowIndex, hasPreviousTierGroup);
        hasPreviousTierGroup = hasPreviousTierGroup || !eliteCards.isEmpty();

        rowIndex = appendOwnedTierGroup(ui, events, cardsSelector, commonCards, rowIndex, hasPreviousTierGroup);
        hasPreviousTierGroup = hasPreviousTierGroup || !commonCards.isEmpty();

        appendOwnedTierGroup(ui, events, cardsSelector, uncategorizedCards, rowIndex, hasPreviousTierGroup);
    }

    private int appendOwnedTierGroup(@Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull String cardsSelector,
            @Nonnull List<OwnedAugmentCard> tierCards,
            int rowIndex,
            boolean addTopGap) {
        if (tierCards.isEmpty()) {
            return rowIndex;
        }

        int cardsInCurrentRow = 0;
        boolean firstRowInGroup = true;

        for (OwnedAugmentCard card : tierCards) {
            if (cardsInCurrentRow == 0) {
                String rowLayout = (addTopGap && firstRowInGroup)
                        ? "Group { LayoutMode: Left; Anchor: (Bottom: 0); Padding: (Top: 14); }"
                        : "Group { LayoutMode: Left; Anchor: (Bottom: 0); }";
                ui.appendInline(cardsSelector, rowLayout);
                firstRowInGroup = false;
            }

            ui.append(cardsSelector + "[" + rowIndex + "]", "Pages/Augments/AugmentGridEntry.ui");
            String base = cardsSelector + "[" + rowIndex + "][" + cardsInCurrentRow + "]";

            ui.set(base + " #ItemIcon.ItemId", card.iconItemId());
            ui.set(base + " #ItemName.Text", card.displayName());

            AugmentDefinition definition = augmentManager != null ? augmentManager.getAugment(card.id()) : null;
            ui.set(base + " #ItemName.Style.TextColor",
                    definition != null ? tierColor(definition.getTier()) : AugmentTheme.gridUnownedColor());
                applyGridEntryTierBackground(ui, base, definition != null ? definition.getTier() : null);

            String hitButtonSelector = base + " #HitButton";
            events.addEventBinding(Activating, hitButtonSelector, of("Action", "augment:select:" + card.id()), false);
                events.addEventBinding(MouseEntered, hitButtonSelector, of("Action", "augment:hover:" + card.id()), false);
                events.addEventBinding(MouseExited, hitButtonSelector, of("Action", "augment:hoverend:" + card.id()), false);

            cardsInCurrentRow++;
            if (cardsInCurrentRow >= GRID_ITEMS_PER_ROW) {
                cardsInCurrentRow = 0;
                rowIndex++;
            }
        }

        if (cardsInCurrentRow > 0) {
            rowIndex++;
        }
        return rowIndex;
    }

    private void buildSectionById(@Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull List<String> augmentIds,
            @Nonnull String cardsSelector,
            @Nonnull String sectionSelector) {
        boolean hasContent = !augmentIds.isEmpty();
        ui.set(sectionSelector + ".Visible", hasContent);

        if (!hasContent) {
            return;
        }

        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (String augmentId : augmentIds) {
            AugmentDefinition def = augmentManager != null ? augmentManager.getAugment(augmentId) : null;
            if (def == null) {
                continue;
            }

            if (cardsInCurrentRow == 0) {
                ui.appendInline(cardsSelector, "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            AugmentPresentationMapper.AugmentPresentationData presentation = augmentPresentationMapper.map(def, augmentId);

            ui.append(cardsSelector + "[" + rowIndex + "]", "Pages/Augments/AugmentGridEntry.ui");
            String base = cardsSelector + "[" + rowIndex + "][" + cardsInCurrentRow + "]";

            ui.set(base + " #ItemIcon.ItemId", presentation.iconItemId());
            ui.set(base + " #ItemName.Text", presentation.displayName());
            ui.set(base + " #ItemName.Style.TextColor", tierColor(def.getTier()));
            applyGridEntryTierBackground(ui, base, def.getTier());

            String hitButtonSelector = base + " #HitButton";
            events.addEventBinding(Activating, hitButtonSelector, of("Action", "augment:select:" + augmentId), false);
            events.addEventBinding(MouseEntered, hitButtonSelector, of("Action", "augment:hover:" + augmentId), false);
            events.addEventBinding(MouseExited, hitButtonSelector, of("Action", "augment:hoverend:" + augmentId), false);

            cardsInCurrentRow++;
            if (cardsInCurrentRow >= GRID_ITEMS_PER_ROW) {
                cardsInCurrentRow = 0;
                rowIndex++;
            }
        }
    }

    private List<AugmentDefinition> applySearch(List<AugmentDefinition> source) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return source;
        }
        return source.stream()
                .filter(def -> matchesSearch(def, searchQuery))
                .collect(Collectors.toList());
    }

    private List<String> applySearchAugmentIds(@Nonnull List<String> source) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return source;
        }
        return source.stream()
                .filter(id -> {
                    AugmentDefinition def = augmentManager != null ? augmentManager.getAugment(id) : null;
                    AugmentPresentationMapper.AugmentPresentationData presentation = augmentPresentationMapper.map(def, id);
                    String normalizedId = id == null ? "" : id.toLowerCase(Locale.ROOT);
                    String normalizedName = presentation.displayName() == null
                            ? ""
                            : presentation.displayName().toLowerCase(Locale.ROOT);
                    return normalizedId.contains(searchQuery) || normalizedName.contains(searchQuery);
                })
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(AugmentDefinition def, String query) {
        String id = def.getId() != null ? def.getId().toLowerCase(Locale.ROOT) : "";
        String name = def.getName() != null ? def.getName().toLowerCase(Locale.ROOT) : "";
        return id.contains(query) || name.contains(query);
    }

    private List<OwnedAugmentCard> buildOwnedCards(PlayerData playerData) {
        if (playerData == null || augmentManager == null) {
            return List.of();
        }

        Map<String, OwnedAugmentCard> firstCardByGroup = new java.util.LinkedHashMap<>();
        Map<String, Integer> countByGroup = new HashMap<>();
        Map<String, Double> totalCommonValueByGroup = new HashMap<>();

        for (String id : playerData.getSelectedAugmentsSnapshot().values()) {
            if (id != null && !id.isBlank()) {
                String rawId = id;
                AugmentDefinition definition = augmentManager.getAugment(rawId);
                if (definition == null) {
                    continue;
                }

                AugmentPresentationMapper.AugmentPresentationData presentation = augmentPresentationMapper.map(
                        definition,
                        rawId);
                String icon = presentation.iconItemId();
                String groupKey;

                CommonAugment.CommonStatOffer offer = presentation.commonStatOffer();
                if (offer != null && CommonAugment.ID.equalsIgnoreCase(definition.getId())) {
                    String attributeKey = offer.attributeKey() == null ? "" : offer.attributeKey().trim();
                    groupKey = "common_stat:" + attributeKey.toLowerCase(Locale.ROOT);
                    totalCommonValueByGroup.merge(groupKey, offer.rolledValue(), Double::sum);
                    String displayName = augmentPresentationMapper.formatCommonStatDisplayName(attributeKey);
                    firstCardByGroup.putIfAbsent(groupKey, new OwnedAugmentCard(rawId, displayName, icon));
                } else {
                    String canonicalId = definition.getId();
                    if (canonicalId == null || canonicalId.isBlank()) {
                        canonicalId = rawId;
                    }
                    groupKey = canonicalId.toLowerCase(Locale.ROOT);
                    firstCardByGroup.putIfAbsent(groupKey, new OwnedAugmentCard(rawId, presentation.displayName(), icon));
                }
                countByGroup.merge(groupKey, 1, Integer::sum);
            }
        }

        List<OwnedAugmentCard> cards = new ArrayList<>(firstCardByGroup.size());
        for (Map.Entry<String, OwnedAugmentCard> entry : firstCardByGroup.entrySet()) {
            String groupKey = entry.getKey();
            OwnedAugmentCard baseCard = entry.getValue();
            int count = Math.max(1, countByGroup.getOrDefault(groupKey, 1));

            String infoId = baseCard.id();
            String displayName = baseCard.displayName();
            if (groupKey.startsWith("common_stat:")) {
                String attributeKey = groupKey.substring("common_stat:".length());
                double totalValue = Math.round(totalCommonValueByGroup.getOrDefault(groupKey, 0.0D) * 100.0D) / 100.0D;
                infoId = CommonAugment.buildStatOfferId(attributeKey, totalValue);
                // Format value string without trailing zeros
                String statValueStr = (totalValue == (long) totalValue) ? String.format(Locale.ROOT, "%d", (long) totalValue) : String.format(Locale.ROOT, "%.2f", totalValue);
                displayName = displayName + ": " + statValueStr;
            }
            if (count > 1 && !groupKey.startsWith("common_stat:")) {
                displayName = tr("ui.augments.unlocked.count_suffix", "{0} x{1}", displayName, count);
            }
            cards.add(new OwnedAugmentCard(infoId, displayName, baseCard.iconItemId()));
        }
        return cards;
    }

    private List<OwnedAugmentCard> applySearchOwned(List<OwnedAugmentCard> source) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return source;
        }
        return source.stream()
                .filter(card -> {
                    String id = card.id() == null ? "" : card.id().toLowerCase(Locale.ROOT);
                    String name = card.displayName() == null ? "" : card.displayName().toLowerCase(Locale.ROOT);
                    return id.contains(searchQuery) || name.contains(searchQuery);
                })
                .collect(Collectors.toList());
    }

    private Set<String> resolveOwnedIds(PlayerData playerData) {
        if (playerData == null) {
            return Set.of();
        }
        Map<String, String> selected = playerData.getSelectedAugmentsSnapshot();
        Set<String> ids = new HashSet<>();
        for (String id : selected.values()) {
            if (id != null && !id.isBlank()) {
                AugmentDefinition definition = augmentManager != null ? augmentManager.getAugment(id) : null;
                if (definition != null && definition.getId() != null && !definition.getId().isBlank()) {
                    ids.add(definition.getId());
                } else {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private Set<String> resolveOwnedCommonAttributes(PlayerData playerData) {
        if (playerData == null) {
            return Set.of();
        }

        Set<String> ownedAttributes = new HashSet<>();
        for (String selectedId : playerData.getSelectedAugmentsSnapshot().values()) {
            CommonAugment.CommonStatOffer offer = CommonAugment.parseStatOfferId(selectedId);
            if (offer != null && offer.attributeKey() != null && !offer.attributeKey().isBlank()) {
                ownedAttributes.add(offer.attributeKey().toLowerCase(Locale.ROOT));
            }
        }
        return ownedAttributes;
    }

    private List<String> buildCommonVariantIds(AugmentDefinition commonDefinition, Set<String> ownedCommonAttributes) {
        if (commonDefinition == null) {
            return new ArrayList<>();
        }

        Map<String, Object> buffs = AugmentValueReader.getMap(commonDefinition.getPassives(), "buffs");
        List<String> attributes = new ArrayList<>(buffs.keySet());
        attributes.sort(Comparator.comparing(augmentPresentationMapper::formatCommonStatDisplayName));

        List<String> variantIds = new ArrayList<>();
        for (String attributeKey : attributes) {
            if (attributeKey == null || attributeKey.isBlank()) {
                continue;
            }
            String normalized = attributeKey.trim().toLowerCase(Locale.ROOT);
            if (ownedCommonAttributes.contains(normalized)) {
                continue;
            }

            variantIds.add(CommonAugment.buildStatOfferId(normalized, -1.0D));
        }

        return variantIds;
    }

    private boolean isOwnedCommonSelection(PlayerData playerData, String augmentId) {
        if (playerData == null || augmentId == null || augmentId.isBlank()) {
            return false;
        }

        String normalizedTarget = augmentId.trim().toLowerCase(Locale.ROOT);
        for (String selectedId : playerData.getSelectedAugmentsSnapshot().values()) {
            if (selectedId != null && selectedId.trim().toLowerCase(Locale.ROOT).equals(normalizedTarget)) {
                return true;
            }
        }
        return false;
    }

    private String formatCommonStatRolledLine(CommonAugment.CommonStatOffer statOffer) {
        String statName = augmentPresentationMapper.formatCommonStatDisplayName(statOffer.attributeKey());
        String statValueStr = formatCommonValue(statOffer.rolledValue());
        return statName + ": " + statValueStr;
    }

    private String formatCommonStatRangeLine(AugmentDefinition definition, String attributeKey) {
        String statName = augmentPresentationMapper.formatCommonStatDisplayName(attributeKey);
        Map<String, Object> buffs = AugmentValueReader.getMap(definition.getPassives(), "buffs");
        Map<String, Object> section = AugmentValueReader.getMap(buffs, attributeKey);

        double baseValue = AugmentValueReader.getDouble(section, "value", 0.0D);
        double minValue = AugmentValueReader.getDouble(section, "min_value", baseValue);
        double maxValue = AugmentValueReader.getDouble(section, "max_value", baseValue);

        if (Math.abs(maxValue - minValue) < 0.0001D) {
            return statName + ": " + formatCommonValue(minValue);
        }

        return formatCommonValue(minValue) + "-" + formatCommonValue(maxValue) + " " + statName;
    }

    private String formatCommonValue(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        return (rounded == (long) rounded)
                ? String.format(Locale.ROOT, "%d", (long) rounded)
                : String.format(Locale.ROOT, "%.2f", rounded);
    }

    private record OwnedAugmentCard(String id, String displayName, String iconItemId) {
    }

    private List<AugmentDefinition> buildSortedList(Set<String> ownedIds) {
        Collection<AugmentDefinition> all = augmentManager.getAugments().values();

        Comparator<AugmentDefinition> comparator = Comparator
                .<AugmentDefinition, Integer>comparing(d -> ownedIds.contains(d.getId()) ? 0 : 1)
                .thenComparingInt(d -> AugmentTheme.tierSortOrder(d.getTier()))
                .thenComparing(AugmentDefinition::getName);

        return all.stream().sorted(comparator).collect(Collectors.toList());
    }

    private String tr(String key, String fallback, Object... args) {
        return Lang.tr(playerRef.getUuid(), key, fallback, args);
    }

    private void applyGridEntryTierBackground(@Nonnull UICommandBuilder ui, @Nonnull String base, PassiveTier tier) {
        PassiveTier resolvedTier = tier == null ? PassiveTier.COMMON : tier;
        ui.set(base + " #ItemBgCommon.Visible", resolvedTier == PassiveTier.COMMON);
        ui.set(base + " #ItemBgElite.Visible", resolvedTier == PassiveTier.ELITE);
        ui.set(base + " #ItemBgLegendary.Visible", resolvedTier == PassiveTier.LEGENDARY);
        ui.set(base + " #ItemBgMythic.Visible", resolvedTier == PassiveTier.MYTHIC);
    }

    private void applyInfoPanelBackground(@Nonnull UICommandBuilder ui, PassiveTier tier) {
        PassiveTier resolvedTier = tier == null ? PassiveTier.COMMON : tier;
        ui.set("#AugmentInfoBgCommon.Visible", resolvedTier == PassiveTier.COMMON);
        ui.set("#AugmentInfoBgElite.Visible", resolvedTier == PassiveTier.ELITE);
        ui.set("#AugmentInfoBgLegendary.Visible", resolvedTier == PassiveTier.LEGENDARY);
        ui.set("#AugmentInfoBgMythic.Visible", resolvedTier == PassiveTier.MYTHIC);
    }

    private String tierColor(PassiveTier tier) {
        return AugmentTheme.gridOwnedColor(tier);
    }
}
