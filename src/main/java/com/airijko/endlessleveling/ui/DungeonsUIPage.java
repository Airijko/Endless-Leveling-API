package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.util.PartnerConsoleGuard;
import com.airijko.endlessleveling.api.gates.InstanceDungeonDefinition;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeRewardCooldowns;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.builtin.portals.integrations.PortalGameplayConfig;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

public class DungeonsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final String OVERVIEW_DESCRIPTION = "Browse dungeon integrations for Endless Leveling. Native Endless Leveling dungeons teleport you directly; other dungeon packs must be entered through their own portals.";
    private static final String IMAGE_SOURCE_BASE = "Common/UI/Custom/Pages/Dungeons/Cards/Images/";
    private static final String FROZEN_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndgameFrozenPlaceholder.png";
    private static final String SWAMP_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndgameSwampPlaceholder.png";
    private static final String VOID_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndgameVoidPlaceholder.png";
    private static final String AZAROTH_IMAGE_PATH = IMAGE_SOURCE_BASE + "MajorAzarothPlaceholder.png";
    private static final String KATHERINA_IMAGE_PATH = IMAGE_SOURCE_BASE + "MajorKatherinaPlaceholder.png";
    private static final String BARON_IMAGE_PATH = IMAGE_SOURCE_BASE + "MajorBaronPlaceholder.png";
    private static final String OUTLANDER_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndlessOutlanderBridgePlaceholder.png";
    private static final String ENDGAME_MAIN_CLASS = "endgame.plugin.EndgameQoL";
    private static final String MAJOR_MAIN_CLASS = "com.major76.majordungeons.MajorDungeons";
    private static final String ENDGAME_DOWNLOAD_URL = "https://www.curseforge.com/hytale/mods/endgame-qol";
    private static final String MAJOR_DOWNLOAD_URL = "https://www.curseforge.com/hytale/mods/major-dungeons";
    private static final String PATREON_URL = "https://www.patreon.com/cw/airijko";

    private static final String DAILY_BOSSES_ID = "daily-bosses";
    private static final String WEEKLY_BOSSES_ID = "weekly-bosses";

    private static final String OUTLANDER_BRIDGE_ID = "outlander-bridge";
    private static final String OUTLANDER_BRIDGE_DESCRIPTION =
            "A wave-based instance dungeon with escalating mob tiers. Clear waves to bank XP; claim rewards at the end to walk away with it. Ends 1-hour claim cooldown.";

    private static final String CATEGORY_ALL = "ALL";
    private static final String CATEGORY_ENDLESS = "ENDLESS";
    private static final String CATEGORY_ENDGAME = "ENDGAME";
    private static final String CATEGORY_MAJOR = "MAJOR";
    private static final List<String> CATEGORY_CYCLE = List.of(CATEGORY_ALL, CATEGORY_ENDLESS, CATEGORY_ENDGAME, CATEGORY_MAJOR);

    /** Dungeon display metadata keyed by dungeon id. */
    private static final Map<String, DungeonMeta> DUNGEONS = buildDungeonMeta();

    /** Which dungeon the detail view currently shows. Null = nothing selected. */
    @Nullable
    private volatile String selectedDungeonId;

    /** True when the detail view is showing; false when carousel is showing. */
    private volatile boolean detailViewActive;

    /** Active category filter. */
    @Nonnull
    private volatile String categoryFilter = CATEGORY_ALL;

    /**
     * Active native-dungeon instance worlds keyed by a party grouping UUID
     * (party leader's UUID, or the solo player's UUID). Lets party members
     * clicking the same dungeon from the UI converge into a single instance
     * instead of each spawning their own copy.
     */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, CompletableFuture<World>> ACTIVE_PARTY_INSTANCES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DungeonsUIPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/Dungeons/DungeonsPage.ui");
        ui.set("#DungeonsOverviewText.Text", OVERVIEW_DESCRIPTION);

        // Endless + Endgame + Major cards discovered via folder listing.
        ModularCardUiAppender.appendFolder(ui,
            "#EndlessCarouselCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Endless",
            "Pages/Dungeons/Cards/Endless",
            99);
        ModularCardUiAppender.appendFolder(ui,
            "#EndgameCarouselCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Endgame",
            "Pages/Dungeons/Cards/Endgame",
            99);
        ModularCardUiAppender.appendFolder(ui,
            "#MajorCarouselCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Major",
            "Pages/Dungeons/Cards/Major",
            99);

        // Banner images on carousel cards.
        setBannerImage(ui, "#OutlanderBridgeBannerImage", OUTLANDER_IMAGE_PATH);
        setBannerImage(ui, "#DailyBossesBannerImage", IMAGE_SOURCE_BASE + "EndlessDailyBossPlaceholder.png");
        setBannerImage(ui, "#WeeklyBossesBannerImage", IMAGE_SOURCE_BASE + "EndlessWeeklyBossPlaceholder.png");
        setBannerImage(ui, "#FrozenBannerImage", FROZEN_IMAGE_PATH);
        setBannerImage(ui, "#SwampBannerImage", SWAMP_IMAGE_PATH);
        setBannerImage(ui, "#VoidBannerImage", VOID_IMAGE_PATH);
        setBannerImage(ui, "#AzarothBannerImage", AZAROTH_IMAGE_PATH);
        setBannerImage(ui, "#KatherinaBannerImage", KATHERINA_IMAGE_PATH);
        setBannerImage(ui, "#BaronBannerImage", BARON_IMAGE_PATH);

        boolean endgameInstalled = isClassPresent(ENDGAME_MAIN_CLASS);
        boolean majorInstalled = isClassPresent(MAJOR_MAIN_CLASS);
        ui.set("#EndgameNotInstalledLabel.Visible", !endgameInstalled);
        ui.set("#EndgameInstallButton.Visible", !endgameInstalled);
        ui.set("#MajorNotInstalledLabel.Visible", !majorInstalled);
        ui.set("#MajorInstallButton.Visible", !majorInstalled);

        // Patreon-exclusive cards:
        //   - Base mod (no partner addons): "Coming Soon - Patreon Exclusive" + show Patreon hint.
        //   - Partner addons present (ARank + Partner): "Coming Soon" only, hide Patreon hint.
        boolean partnerActive = isPartnerActive();
        String patreonStatus = partnerActive ? "Coming Soon" : "Coming Soon - Patreon Exclusive";
        ui.set("#DailyBossesStatus.Text", patreonStatus);
        ui.set("#WeeklyBossesStatus.Text", patreonStatus);
        ui.set("#DailyBossesPatreonHint.Visible", !partnerActive);
        ui.set("#WeeklyBossesPatreonHint.Visible", !partnerActive);

        // Outlander Bridge reward status label on carousel card.
        updateOutlanderBridgeCardStatus(ui);

        // Detail view starts blank; carousel visible.
        resetDetailView(ui);
        applyViewState(ui);
        applyFilter(ui);
        updateCategoryFilterLabel(ui);
        updateDungeonCountLabel(ui);

        // Install button bindings (unchanged actions).
        events.addEventBinding(Activating, "#EndgameInstallButton", of("Action", "dungeons:install:endgame"), false);
        events.addEventBinding(Activating, "#MajorInstallButton", of("Action", "dungeons:install:major"), false);

        // Outlander Bridge card has its own action buttons (no whole-card click).
        events.addEventBinding(Activating, "#OutlanderTeleportButton", of("Action", "dungeon:outlander:teleport"), false);
        events.addEventBinding(Activating, "#OutlanderDetailsButton", of("Action", "dungeon:outlander:flip"), false);
        events.addEventBinding(Activating, "#OutlanderBackButton", of("Action", "dungeon:outlander:unflip"), false);

        // Patreon-exclusive cards: click sends Patreon link to chat (no detail-view rerouting).
        events.addEventBinding(Activating, "#CardDailyBosses", of("Action", "dungeon:patreon"), false);
        events.addEventBinding(Activating, "#CardWeeklyBosses", of("Action", "dungeon:patreon"), false);

        // Card flip bindings (converted from detail-view cards).
        events.addEventBinding(Activating, "#FrozenDetailsButton", of("Action", "dungeon:frozen:flip"), false);
        events.addEventBinding(Activating, "#FrozenBackButton", of("Action", "dungeon:frozen:unflip"), false);
        events.addEventBinding(Activating, "#SwampDetailsButton", of("Action", "dungeon:swamp:flip"), false);
        events.addEventBinding(Activating, "#SwampBackButton", of("Action", "dungeon:swamp:unflip"), false);
        events.addEventBinding(Activating, "#VoidDetailsButton", of("Action", "dungeon:void:flip"), false);
        events.addEventBinding(Activating, "#VoidBackButton", of("Action", "dungeon:void:unflip"), false);
        events.addEventBinding(Activating, "#AzarothDetailsButton", of("Action", "dungeon:azaroth:flip"), false);
        events.addEventBinding(Activating, "#AzarothBackButton", of("Action", "dungeon:azaroth:unflip"), false);
        events.addEventBinding(Activating, "#KatherinaDetailsButton", of("Action", "dungeon:katherina:flip"), false);
        events.addEventBinding(Activating, "#KatherinaBackButton", of("Action", "dungeon:katherina:unflip"), false);

        // Baron still uses whole-card click → detail view.
        events.addEventBinding(Activating, "#CardBaron", of("Action", "dungeon:view:baron"), false);

        // Detail view buttons.
        events.addEventBinding(Activating, "#BackToCarouselButton", of("Action", "dungeon:back"), false);
        events.addEventBinding(Activating, "#DetailEnterButton", of("Action", "dungeon:native:enter"), false);
        events.addEventBinding(Activating, "#DetailConfirmProceedButton", of("Action", "dungeon:native:proceed"), false);
        events.addEventBinding(Activating, "#DetailConfirmCancelButton", of("Action", "dungeon:native:cancel-proceed"), false);
        events.addEventBinding(Activating, "#DetailPatreonButton", of("Action", "dungeon:patreon"), false);

        // Filter row.
        events.addEventBinding(Activating, "#CategoryFilterButton", of("Action", "dungeon:filter:cycle"), false);
        events.addEventBinding(Activating, "#ResetFiltersButton", of("Action", "dungeon:filter:reset"), false);

        NavUIHelper.applyNavVersion(ui, playerRef, "dungeons",
            "Common/UI/Custom/Pages/Dungeons/DungeonsPage.ui",
                "#DungeonsTitle");
        NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/Dungeons/DungeonsPage.ui");
    }

    private void applyViewState(@Nonnull UICommandBuilder ui) {
        ui.set("#CarouselView.Visible", !detailViewActive);
        ui.set("#DetailView.Visible", detailViewActive);
    }

    private void applyFilter(@Nonnull UICommandBuilder ui) {
        boolean showEndless = CATEGORY_ALL.equals(categoryFilter) || CATEGORY_ENDLESS.equals(categoryFilter);
        boolean showEndgame = CATEGORY_ALL.equals(categoryFilter) || CATEGORY_ENDGAME.equals(categoryFilter);
        boolean showMajor = CATEGORY_ALL.equals(categoryFilter) || CATEGORY_MAJOR.equals(categoryFilter);
        ui.set("#EndlessCarouselCards.Visible", showEndless);
        ui.set("#EndgameCarouselCards.Visible", showEndgame);
        ui.set("#MajorCarouselCards.Visible", showMajor);
    }

    private void updateCategoryFilterLabel(@Nonnull UICommandBuilder ui) {
        ui.set("#CategoryFilterButton.Text", "CATEGORY: " + categoryFilter);
    }

    private void updateDungeonCountLabel(@Nonnull UICommandBuilder ui) {
        long count = DUNGEONS.values().stream()
                .filter(meta -> CATEGORY_ALL.equals(categoryFilter) || meta.category().equals(categoryFilter))
                .count();
        ui.set("#DungeonCountLabel.Text", count + (count == 1 ? " dungeon" : " dungeons"));
    }

    private void cycleCategoryFilter() {
        int idx = CATEGORY_CYCLE.indexOf(categoryFilter);
        if (idx < 0) {
            categoryFilter = CATEGORY_ALL;
            return;
        }
        categoryFilter = CATEGORY_CYCLE.get((idx + 1) % CATEGORY_CYCLE.size());
    }

    private void resetDetailView(@Nonnull UICommandBuilder ui) {
        ui.set("#SelectedDungeonLabel.Text", "Dungeon");
        ui.set("#DetailCategoryTag.Text", "");
        ui.set("#DetailTag.Text", "");
        ui.set("#DetailAuthor.Text", "");
        ui.set("#DetailDescription.Text", "Select a dungeon to view details.");
        ui.set("#DetailRewardHeader.Visible", false);
        ui.set("#DetailRewardStatus.Visible", false);
        ui.set("#DetailRewardDetail.Visible", false);
        ui.set("#DetailConfirmPrompt.Visible", false);
        ui.set("#DetailEnterButton.Visible", false);
        ui.set("#DetailConfirmCancelButton.Visible", false);
        ui.set("#DetailConfirmProceedButton.Visible", false);
        ui.set("#DetailPatreonButton.Visible", false);
        hideAllDetailBanners(ui);
    }

    private void hideAllDetailBanners(@Nonnull UICommandBuilder ui) {
        ui.set("#DetailBannerOutlanderBridge.Visible", false);
        ui.set("#DetailBannerDailyBosses.Visible", false);
        ui.set("#DetailBannerWeeklyBosses.Visible", false);
        ui.set("#DetailBannerFrozen.Visible", false);
        ui.set("#DetailBannerSwamp.Visible", false);
        ui.set("#DetailBannerVoid.Visible", false);
        ui.set("#DetailBannerAzaroth.Visible", false);
        ui.set("#DetailBannerKatherina.Visible", false);
        ui.set("#DetailBannerBaron.Visible", false);
    }

    private void populateDetailView(@Nonnull UICommandBuilder ui, @Nonnull String dungeonId) {
        DungeonMeta meta = DUNGEONS.get(dungeonId);
        if (meta == null) {
            resetDetailView(ui);
            return;
        }

        ui.set("#DetailCategoryTag.Text", meta.categoryLabel());
        ui.set("#SelectedDungeonLabel.Text", meta.displayName());
        ui.set("#DetailTag.Text", meta.tag());
        ui.set("#DetailAuthor.Text", meta.author());
        ui.set("#DetailDescription.Text", meta.description());

        hideAllDetailBanners(ui);
        ui.set(meta.detailBannerSelector() + ".Visible", true);

        if (OUTLANDER_BRIDGE_ID.equals(dungeonId)) {
            populateOutlanderBridgeRewardSection(ui);
            ui.set("#DetailEnterButton.Visible", true);
            ui.set("#DetailPatreonButton.Visible", false);
        } else if (DAILY_BOSSES_ID.equals(dungeonId) || WEEKLY_BOSSES_ID.equals(dungeonId)) {
            boolean partner = isPartnerActive();
            ui.set("#DetailRewardHeader.Visible", true);
            ui.set("#DetailRewardHeader.Text", "STATUS");
            ui.set("#DetailRewardStatus.Visible", true);
            ui.set("#DetailRewardStatus.Text", partner ? "Coming Soon" : "Coming Soon - Patreon Exclusive");
            ui.set("#DetailRewardDetail.Visible", true);
            ui.set("#DetailRewardDetail.Text", partner
                    ? "Thanks for being a partner — this content is launching soon."
                    : "Support development on Patreon to unlock this content when it launches.");
            ui.set("#DetailEnterButton.Visible", false);
            ui.set("#DetailPatreonButton.Visible", !partner);
        } else {
            ui.set("#DetailRewardHeader.Visible", false);
            ui.set("#DetailRewardStatus.Visible", false);
            ui.set("#DetailRewardDetail.Visible", true);
            ui.set("#DetailRewardDetail.Text",
                    "This dungeon is hosted by an external mod. Enter through its in-world portal to begin.");
            ui.set("#DetailEnterButton.Visible", false);
            ui.set("#DetailPatreonButton.Visible", false);
        }
        ui.set("#DetailConfirmPrompt.Visible", false);
        ui.set("#DetailConfirmCancelButton.Visible", false);
        ui.set("#DetailConfirmProceedButton.Visible", false);
    }

    private void populateOutlanderBridgeRewardSection(@Nonnull UICommandBuilder ui) {
        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();
        boolean onCooldown = cd != null && uuid != null && cd.isOnCooldown(uuid);

        ui.set("#DetailRewardHeader.Visible", true);
        ui.set("#DetailRewardStatus.Visible", true);
        ui.set("#DetailRewardDetail.Visible", true);

        if (onCooldown) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            ui.set("#DetailRewardStatus.Text", "On Cooldown");
            ui.set("#DetailRewardDetail.Text",
                    "XP claim is locked for " + remainingMin + "m. You can still enter, but XP earned this run will not be claimable until the cooldown ends.");
        } else {
            ui.set("#DetailRewardStatus.Text", "Available");
            ui.set("#DetailRewardDetail.Text",
                    "XP rewards are claimable at the end of a successful run. A 1-hour cooldown begins on claim.");
        }
    }

    private void updateOutlanderBridgeCardStatus(@Nonnull UICommandBuilder ui) {
        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();
        if (cd != null && uuid != null && cd.isOnCooldown(uuid)) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            ui.set("#CardOutlanderBridgeStatus.Text", "Claim Cooldown: " + remainingMin + "m");
        } else {
            ui.set("#CardOutlanderBridgeStatus.Text", "Rewards Available");
        }
    }

    private void setBannerImage(@Nonnull UICommandBuilder ui,
            @Nonnull String selector,
            @Nonnull String imagePath) {
        boolean exists = resourceExists(imagePath)
                || (imagePath.startsWith("Common/UI/Custom/")
                        && resourceExists(imagePath.substring("Common/UI/Custom/".length())));
        ui.set(selector + ".Visible", exists);
    }

    private boolean resourceExists(@Nonnull String path) {
        ClassLoader loader = DungeonsUIPage.class.getClassLoader();
        if (loader.getResource(path) != null) {
            return true;
        }
        if (loader.getResource('/' + path) != null) {
            return true;
        }
        try (java.io.InputStream in = loader.getResourceAsStream(path)) {
            if (in != null) {
                return true;
            }
        } catch (java.io.IOException ignored) {
        }

        Path rawPath = Path.of(path);
        if (Files.exists(rawPath)) {
            return true;
        }
        Path srcResourcePath = Path.of("src", "main", "resources").resolve(path);
        if (Files.exists(srcResourcePath)) {
            return true;
        }
        if (path.startsWith("Common/UI/Custom/")) {
            Path trimmed = Path.of(path.substring("Common/UI/Custom/".length()));
            if (Files.exists(trimmed)) {
                return true;
            }
            if (Files.exists(Path.of("src", "main", "resources").resolve(trimmed))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPartnerActive() {
        return PartnerConsoleGuard.isPartnerAddonPresent();
    }

    private boolean isClassPresent(@Nonnull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        if ("dungeons:install:endgame".equalsIgnoreCase(data.action)) {
            playerRef.sendMessage(Message.raw("[Endgame & QoL] Not installed on this server. Click the link below to download:").color("#d2ecff"));
            playerRef.sendMessage(Message.raw(">> Click here to open the download page (CurseForge) <<").link(ENDGAME_DOWNLOAD_URL).color("#6fe3ff"));
            return;
        }

        if ("dungeons:install:major".equalsIgnoreCase(data.action)) {
            playerRef.sendMessage(Message.raw("[Major Dungeons] Not installed on this server. Click the link below to download:").color("#ffd58a"));
            playerRef.sendMessage(Message.raw(">> Click here to open the download page (CurseForge) <<").link(MAJOR_DOWNLOAD_URL).color("#6fe3ff"));
            return;
        }

        if (data.action.startsWith("dungeon:view:")) {
            String id = data.action.substring("dungeon:view:".length());
            handleViewDungeon(id);
            return;
        }

        if ("dungeon:back".equalsIgnoreCase(data.action)) {
            handleBackToCarousel();
            return;
        }

        if ("dungeon:filter:cycle".equalsIgnoreCase(data.action)) {
            cycleCategoryFilter();
            UICommandBuilder ui = new UICommandBuilder();
            applyFilter(ui);
            updateCategoryFilterLabel(ui);
            updateDungeonCountLabel(ui);
            sendUpdate(ui, false);
            return;
        }

        if ("dungeon:filter:reset".equalsIgnoreCase(data.action)) {
            categoryFilter = CATEGORY_ALL;
            UICommandBuilder ui = new UICommandBuilder();
            applyFilter(ui);
            updateCategoryFilterLabel(ui);
            updateDungeonCountLabel(ui);
            sendUpdate(ui, false);
            return;
        }

        if ("dungeon:native:enter".equalsIgnoreCase(data.action)) {
            handleEnterRequest(ref, store);
            return;
        }

        if ("dungeon:native:proceed".equalsIgnoreCase(data.action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#DetailConfirmPrompt.Visible", false);
            ui.set("#DetailConfirmCancelButton.Visible", false);
            ui.set("#DetailConfirmProceedButton.Visible", false);
            ui.set("#DetailEnterButton.Visible", true);
            sendUpdate(ui, false);
            teleportToSelected(ref, store);
            return;
        }

        if ("dungeon:patreon".equalsIgnoreCase(data.action)) {
            if (isPartnerActive()) {
                playerRef.sendMessage(Message.raw("[Patreon] Thanks for being a partner! This content is launching soon.").color("#6cff78"));
                return;
            }
            playerRef.sendMessage(Message.raw("[Patreon] Support development & unlock exclusive content:").color("#ff9a3c"));
            playerRef.sendMessage(Message.raw(">> patreon.com/cw/airijko <<").link(PATREON_URL).color("#ff9a3c"));
            return;
        }

        if ("dungeon:native:cancel-proceed".equalsIgnoreCase(data.action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#DetailConfirmPrompt.Visible", false);
            ui.set("#DetailConfirmCancelButton.Visible", false);
            ui.set("#DetailConfirmProceedButton.Visible", false);
            ui.set("#DetailEnterButton.Visible", true);
            sendUpdate(ui, false);
            return;
        }

        if ("dungeon:outlander:teleport".equalsIgnoreCase(data.action)) {
            handleOutlanderTeleport(ref, store);
            return;
        }

        if ("dungeon:outlander:flip".equalsIgnoreCase(data.action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#OutlanderCardFront.Visible", false);
            ui.set("#OutlanderCardBack.Visible", true);
            sendUpdate(ui, false);
            return;
        }

        if ("dungeon:outlander:unflip".equalsIgnoreCase(data.action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#OutlanderCardFront.Visible", true);
            ui.set("#OutlanderCardBack.Visible", false);
            sendUpdate(ui, false);
            return;
        }

        if (handleCardFlip(data.action, "frozen", "#FrozenCardFront", "#FrozenCardBack")) return;
        if (handleCardFlip(data.action, "swamp", "#SwampCardFront", "#SwampCardBack")) return;
        if (handleCardFlip(data.action, "void", "#VoidCardFront", "#VoidCardBack")) return;
        if (handleCardFlip(data.action, "azaroth", "#AzarothCardFront", "#AzarothCardBack")) return;
        if (handleCardFlip(data.action, "katherina", "#KatherinaCardFront", "#KatherinaCardBack")) return;

        NavUIHelper.handleNavAction(data.action, ref, store, playerRef);
    }

    private boolean handleCardFlip(@Nonnull String action,
            @Nonnull String dungeonKey,
            @Nonnull String frontSelector,
            @Nonnull String backSelector) {
        String flipAction = "dungeon:" + dungeonKey + ":flip";
        String unflipAction = "dungeon:" + dungeonKey + ":unflip";
        if (flipAction.equalsIgnoreCase(action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set(frontSelector + ".Visible", false);
            ui.set(backSelector + ".Visible", true);
            sendUpdate(ui, false);
            return true;
        }
        if (unflipAction.equalsIgnoreCase(action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set(frontSelector + ".Visible", true);
            ui.set(backSelector + ".Visible", false);
            sendUpdate(ui, false);
            return true;
        }
        return false;
    }

    private void handleOutlanderTeleport(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        selectedDungeonId = OUTLANDER_BRIDGE_ID;
        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();
        if (cd != null && uuid != null && cd.isOnCooldown(uuid)) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            playerRef.sendMessage(Message.raw(
                    "[Outlander Bridge] XP claim is on cooldown (" + remainingMin
                            + "m). Entering anyway — XP earned this run will not be claimable.")
                    .color("#ffc98b"));
        }
        teleportToSelected(ref, store);
    }

    private void handleViewDungeon(@Nonnull String dungeonId) {
        if (!DUNGEONS.containsKey(dungeonId)) {
            return;
        }
        selectedDungeonId = dungeonId;
        detailViewActive = true;

        UICommandBuilder ui = new UICommandBuilder();
        populateDetailView(ui, dungeonId);
        applyViewState(ui);
        sendUpdate(ui, false);
    }

    private void handleBackToCarousel() {
        detailViewActive = false;

        UICommandBuilder ui = new UICommandBuilder();
        applyViewState(ui);
        // Refresh outlander carousel card status in case cooldown changed during this session.
        updateOutlanderBridgeCardStatus(ui);
        sendUpdate(ui, false);
    }

    private void handleEnterRequest(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String dungeonId = selectedDungeonId;
        if (dungeonId == null) {
            return;
        }

        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();

        if (OUTLANDER_BRIDGE_ID.equals(dungeonId)
                && cd != null && uuid != null && cd.isOnCooldown(uuid)) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#DetailConfirmPrompt.Text",
                    "XP claim on cooldown (" + remainingMin + "m). Any XP earned this run will not be claimable. Proceed?");
            ui.set("#DetailConfirmPrompt.Visible", true);
            ui.set("#DetailEnterButton.Visible", false);
            ui.set("#DetailConfirmCancelButton.Visible", true);
            ui.set("#DetailConfirmProceedButton.Visible", true);
            sendUpdate(ui, false);
            return;
        }

        teleportToSelected(ref, store);
    }

    private void teleportToSelected(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String dungeonId = selectedDungeonId;
        if (dungeonId == null) {
            return;
        }
        InstanceDungeonDefinition def = EndlessLevelingAPI.get().getInstanceDungeon(dungeonId);
        if (def == null) {
            playerRef.sendMessage(Message.raw("Dungeon definition missing.").color("#ff6666"));
            return;
        }
        InstancesPlugin plugin = InstancesPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.raw("Instance plugin unavailable.").color("#ff6666"));
            return;
        }

        final String portalTypeId = def.basePortalBlockId();
        PortalType portalType = PortalType.getAssetMap().getAsset(portalTypeId);
        if (portalType == null) {
            playerRef.sendMessage(Message.raw("Portal type missing: " + portalTypeId).color("#ff6666"));
            return;
        }

        World sourceWorld = store.getExternalData().getWorld();
        if (sourceWorld == null) {
            playerRef.sendMessage(Message.raw("Teleport failed: source world unloaded.").color("#ff6666"));
            return;
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        final Transform returnTransform = tc != null ? tc.getTransform().clone() : new Transform();
        final String displayName = def.displayName();
        final int timeLimitSeconds = OUTLANDER_BRIDGE_ID.equals(dungeonId) ? 1800 : 1800;
        final PortalType finalPortalType = portalType;
        final World finalSourceWorld = sourceWorld;

        final UUID partyKey = resolvePartyInstanceKey(playerRef.getUuid());
        final String instancePrefix = "instance-" + finalPortalType.getInstanceId().toLowerCase(java.util.Locale.ROOT) + "-";

        sourceWorld.execute(() -> {
            try {
                CompletableFuture<World> instanceWorld = ACTIVE_PARTY_INSTANCES.compute(partyKey, (key, existing) -> {
                    if (existing != null) {
                        if (!existing.isDone()) {
                            // Party member is mid-spawn; join the in-flight future.
                            return existing;
                        }
                        World w = existing.getNow(null);
                        if (w != null && w.isAlive()
                                && w.getName() != null
                                && w.getName().toLowerCase(java.util.Locale.ROOT).startsWith(instancePrefix)) {
                            playerRef.sendMessage(Message.raw("Joining party instance: " + displayName).color("#6cff78"));
                            return existing;
                        }
                    }
                    playerRef.sendMessage(Message.raw("Entering " + displayName + "...").color("#6cff78"));
                    return plugin
                            .spawnInstance(finalPortalType.getInstanceId(), finalSourceWorld, returnTransform)
                            .thenApply(spawnedWorld -> {
                                WorldConfig worldConfig = spawnedWorld.getWorldConfig();
                                worldConfig.setDeleteOnUniverseStart(true);
                                worldConfig.setDeleteOnRemove(true);
                                worldConfig.setGameplayConfig(finalPortalType.getGameplayConfigId());
                                InstanceWorldConfig instanceConfig = InstanceWorldConfig.ensureAndGet(worldConfig);
                                PortalRemovalCondition removal = new PortalRemovalCondition(timeLimitSeconds);
                                instanceConfig.setRemovalConditions(removal);
                                PortalWorld portalWorld = spawnedWorld
                                        .getEntityStore().getStore()
                                        .getResource(PortalWorld.getResourceType());
                                GameplayConfig gp = finalPortalType.getGameplayConfig();
                                PortalGameplayConfig portalGameplayConfig = gp != null
                                        ? gp.getPluginConfig().get(PortalGameplayConfig.class)
                                        : null;
                                portalWorld.init(finalPortalType, timeLimitSeconds, removal, portalGameplayConfig);
                                return spawnedWorld;
                            });
                });
                InstancesPlugin.teleportPlayerToLoadingInstance(ref, store, instanceWorld, null);
            } catch (Throwable t) {
                playerRef.sendMessage(Message.raw("Teleport failed. Try again in a moment.").color("#ff6666"));
            }
        });

        // Flip back to carousel after initiating teleport.
        selectedDungeonId = null;
        detailViewActive = false;
        UICommandBuilder ui = new UICommandBuilder();
        resetDetailView(ui);
        applyViewState(ui);
        sendUpdate(ui, false);
    }

    /**
     * Key used to group party members into the same native-dungeon instance.
     * Returns the party leader's UUID when the player is in a party, otherwise
     * the player's own UUID (solo → each player gets their own instance).
     */
    @Nonnull
    private static UUID resolvePartyInstanceKey(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return new UUID(0L, 0L);
        }
        try {
            PartyManager pm = EndlessLeveling.getInstance().getPartyManager();
            if (pm != null) {
                UUID leader = pm.getPartyLeader(playerUuid);
                if (leader != null) {
                    return leader;
                }
            }
        } catch (Throwable ignored) {
        }
        return playerUuid;
    }

    private static Map<String, DungeonMeta> buildDungeonMeta() {
        Map<String, DungeonMeta> m = new LinkedHashMap<>();
        m.put(OUTLANDER_BRIDGE_ID, new DungeonMeta(
                CATEGORY_ENDLESS, "ENDLESS LEVELING",
                "Outlander Bridge", "WAVE INSTANCE",
                "By Endless Leveling", OUTLANDER_BRIDGE_DESCRIPTION,
                OUTLANDER_IMAGE_PATH, "#DetailBannerOutlanderBridge"));
        m.put(DAILY_BOSSES_ID, new DungeonMeta(
                CATEGORY_ENDLESS, "ENDLESS LEVELING",
                "Daily Bosses", "DAILY ROTATION",
                "By Airijko",
                "Daily rotating boss encounters with unique loot pools. Fresh targets every 24 hours with curated reward tables and escalating difficulty tiers.",
                IMAGE_SOURCE_BASE + "EndlessDailyBossPlaceholder.png", "#DetailBannerDailyBosses"));
        m.put(WEEKLY_BOSSES_ID, new DungeonMeta(
                CATEGORY_ENDLESS, "ENDLESS LEVELING",
                "Weekly Bosses", "WEEKLY RAID",
                "By Airijko",
                "High-stakes weekly raid bosses with prestige rewards and leaderboard glory. Designed for coordinated groups chasing top-tier loot and global rankings.",
                IMAGE_SOURCE_BASE + "EndlessWeeklyBossPlaceholder.png", "#DetailBannerWeeklyBosses"));
        m.put("frozen", new DungeonMeta(
                CATEGORY_ENDGAME, "ENDGAME & QOL",
                "Frozen Dungeon", "ICE / ELITE",
                "By Lewaii",
                "A frostbound stronghold filled with freezing hazards, glacial corridors, and elite ice-tuned enemies. Requires the Endgame & QoL mod.",
                FROZEN_IMAGE_PATH, "#DetailBannerFrozen"));
        m.put("swamp", new DungeonMeta(
                CATEGORY_ENDGAME, "ENDGAME & QOL",
                "Swamp Dungeon", "POISON / AMBUSH",
                "By Lewaii",
                "A murky marsh dungeon packed with poison pressure, tight terrain, and relentless ambush waves. Requires the Endgame & QoL mod.",
                SWAMP_IMAGE_PATH, "#DetailBannerSwamp"));
        m.put("void", new DungeonMeta(
                CATEGORY_ENDGAME, "ENDGAME & QOL",
                "Void Golem Realm", "VOID / ARENA",
                "By Lewaii",
                "A high-threat void arena where golem guardians and unstable rifts punish weak positioning. Requires the Endgame & QoL mod.",
                VOID_IMAGE_PATH, "#DetailBannerVoid"));
        m.put("azaroth", new DungeonMeta(
                CATEGORY_MAJOR, "MAJOR DUNGEONS",
                "Azaroth", "COMBAT / BOSS",
                "By MAJOR76",
                "The opening Major Dungeons run, designed as a combat-heavy initiation with aggressive boss pacing. Requires the Major Dungeons mod.",
                AZAROTH_IMAGE_PATH, "#DetailBannerAzaroth"));
        m.put("katherina", new DungeonMeta(
                CATEGORY_MAJOR, "MAJOR DUNGEONS",
                "Katherina", "MECHANICS / CONTROL",
                "By MAJOR76",
                "A mid-chain dungeon focused on sustained mechanics, control checks, and layered encounter phases. Requires the Major Dungeons mod.",
                KATHERINA_IMAGE_PATH, "#DetailBannerKatherina"));
        m.put("baron", new DungeonMeta(
                CATEGORY_MAJOR, "MAJOR DUNGEONS",
                "Baron", "BURST / FINALE",
                "By MAJOR76",
                "The final Major Dungeons route, culminating in a punishing finale built around burst damage windows. Requires the Major Dungeons mod.",
                BARON_IMAGE_PATH, "#DetailBannerBaron"));
        return java.util.Collections.unmodifiableMap(m);
    }

    private record DungeonMeta(
            @Nonnull String category,
            @Nonnull String categoryLabel,
            @Nonnull String displayName,
            @Nonnull String tag,
            @Nonnull String author,
            @Nonnull String description,
            @Nonnull String bannerImagePath,
            @Nonnull String detailBannerSelector) {
    }
}
