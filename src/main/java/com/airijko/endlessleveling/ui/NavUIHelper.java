package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.airijko.endlessleveling.util.Lang;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.hypixel.hytale.server.core.universe.Universe;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Shared helper for wiring left navigation buttons to UI page navigation.
 */
public final class NavUIHelper {

        private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
        private static final String NAV_VERSION = resolveVersion();
        private static final String BRAND_TITLE_FALLBACK = "ENDLESS LEVELING";
        private static final String BRAND_NAV_HEADER_FALLBACK = "ENDLESS";
        private static final String BRAND_NAV_SUB_HEADER_FALLBACK = "LEVELING";
        private static final String NAV_RESOURCE_PATH = "Common/UI/Custom/Pages/Nav/LeftNavPanel.ui";
        private static final Map<String, String> RESOURCE_TEXT_CACHE = new ConcurrentHashMap<>();
        private static final Set<String> MISSING_SELECTOR_WARNED = ConcurrentHashMap.newKeySet();
        private static final Set<String> MISSING_RESOURCE_WARNED = ConcurrentHashMap.newKeySet();
        private static final Value<String> NAV_BUTTON_STYLE = Value.ref("Pages/Nav/LeftNavPanel.ui",
                        "LeftNavButtonStyle");
        private static final Value<String> NAV_BUTTON_STYLE_SELECTED = Value.ref("Pages/Nav/LeftNavPanel.ui",
                        "LeftNavButtonStyleSelected");
        private static final Value<String> TOP_NAV_BUTTON_STYLE = Value.ref("Pages/Nav/TopNavBar.ui",
                        "TopNavButtonStyle");
        private static final Value<String> TOP_NAV_BUTTON_STYLE_SELECTED = Value.ref("Pages/Nav/TopNavBar.ui",
                        "TopNavButtonStyleSelected");

        private NavUIHelper() {
        }

        /**
         * Plugin version string ("v{Manifest.Version}") resolved from the bundled manifest.json.
         * Falls back to "v?.?" when unavailable.
         */
        @Nonnull
        public static String getNavVersion() {
                return NAV_VERSION;
        }

        /**
         * Write the current plugin version into the shared nav panel.
         */
        public static void applyNavVersion(
                        @Nonnull UICommandBuilder ui,
                        @Nonnull PlayerRef playerRef,
                        @Nonnull String activeNav) {
                applyNavVersion(ui, playerRef, activeNav, null, null);
        }

        public static void applyNavVersion(
                        @Nonnull UICommandBuilder ui,
                        @Nonnull PlayerRef playerRef,
                        @Nonnull String activeNav,
                        String pageTitleSelector) {
                applyNavVersion(ui, playerRef, activeNav, null, pageTitleSelector);
        }

        public static void applyNavVersion(
                        @Nonnull UICommandBuilder ui,
                        @Nonnull PlayerRef playerRef,
                        @Nonnull String activeNav,
                        String pageResourcePath,
                        String pageTitleSelector) {
                boolean topNav = pageUsesTopNavBar(pageResourcePath);

                if (topNav) {
                        // New top navbar layout: text labels live in dedicated #Nav<Name>Label widgets.
                        ui.set("#NavProfileLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.profile", "PROFILE"));
                        ui.set("#NavSkillsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.skills", "SKILLS"));
                        ui.set("#NavAugmentsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.augments", "AUGMENTS"));
                        ui.set("#NavRacesLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.races", "RACES"));
                        ui.set("#NavClassesLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.classes", "CLASSES"));
                        ui.set("#NavGatesLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.gates", "GATES"));
                        ui.set("#NavDungeonsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.dungeons", "DUNGEONS"));
                        ui.set("#NavQuestsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.quests", "QUESTS"));
                        ui.set("#NavLeaderboardsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.leaderboards", "LEADERBOARDS"));
                        ui.set("#NavXpStatsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.xpstats", "XP STATS"));
                        ui.set("#NavAddonsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.addons", "ADDONS"));
                        ui.set("#NavSupportLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.support", "SUPPORT"));
                        ui.set("#NavServersLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.servers", "SERVERS"));
                        ui.set("#NavServersContainer.Visible", !isAuthorizedPartnerAddonPresent());
                        ui.set("#NavSettingsLabel.Text", Lang.tr(playerRef.getUuid(), "ui.nav.settings", "SETTINGS"));
                } else {
                        // Legacy left navbar layout: each MenuItem renders its own Text directly.
                        ui.set("#NavProfile.Text", Lang.tr(playerRef.getUuid(), "ui.nav.profile", "PROFILE"));
                        ui.set("#NavSkills.Text", Lang.tr(playerRef.getUuid(), "ui.nav.skills", "SKILLS"));
                        ui.set("#NavRaces.Text", Lang.tr(playerRef.getUuid(), "ui.nav.races", "RACES"));
                        ui.set("#NavClasses.Text", Lang.tr(playerRef.getUuid(), "ui.nav.classes", "CLASSES"));
                        ui.set("#NavAugments.Text", Lang.tr(playerRef.getUuid(), "ui.nav.augments", "AUGMENTS"));
                        ui.set("#NavDungeons.Text", Lang.tr(playerRef.getUuid(), "ui.nav.dungeons", "DUNGEONS"));
                        ui.set("#NavAddons.Text", Lang.tr(playerRef.getUuid(), "ui.nav.addons", "ADDONS"));
                        ui.set("#NavLeaderboards.Text", Lang.tr(playerRef.getUuid(), "ui.nav.leaderboards", "LEADERBOARDS"));
                        ui.set("#NavParty.Text", Lang.tr(playerRef.getUuid(), "ui.nav.party", "PARTY"));

                        boolean partyAvailable = false;
                        EndlessLeveling plugin = EndlessLeveling.getInstance();
                        if (plugin != null && plugin.getPartyManager() != null && plugin.getPartyManager().isAvailable()) {
                                partyAvailable = true;
                        }
                        ui.set("#NavParty.Visible", partyAvailable);

                        ui.set("#NavSupport.Text", Lang.tr(playerRef.getUuid(), "ui.nav.support", "SUPPORT"));
                        ui.set("#NavSettings.Text", Lang.tr(playerRef.getUuid(), "ui.nav.settings", "SETTINGS"));
                        ui.set("#NavVersion.Text", NAV_VERSION);
                }

                applyBrandingEnforcement(ui, pageResourcePath, pageTitleSelector, topNav);
                applySelectedNavStyle(ui, activeNav, topNav);
        }

        /**
         * Returns true if the supplied page resource imports the new TopNavBar layout.
         * Falls back to {@code false} (legacy LeftNavPanel) when the file is not readable.
         */
        private static boolean pageUsesTopNavBar(String pageResourcePath) {
                if (pageResourcePath == null || pageResourcePath.isBlank()) {
                        return false;
                }
                String content = RESOURCE_TEXT_CACHE.computeIfAbsent(pageResourcePath, NavUIHelper::readResourceText);
                return content != null && content.contains("TopNavBar.ui");
        }

        private static void applyBrandingEnforcement(
                        @Nonnull UICommandBuilder ui,
                        String pageResourcePath,
                        String pageTitleSelector,
                        boolean topNav) {
                RuntimeBranding branding = resolveRuntimeBranding();
                safeSetText(ui, pageResourcePath, pageTitleSelector, branding.pageTitle());

                // Nav header is split into two labels in LeftNavPanel.ui.
                // TopNavBar pages don't embed LeftNavPanel, so skip these assignments.
                if (!topNav) {
                        safeSetText(ui, NAV_RESOURCE_PATH, "#NavHeader", branding.navHeader());
                        safeSetText(ui, NAV_RESOURCE_PATH, "#NavSubHeader", branding.navSubHeader());
                }
        }

        private static RuntimeBranding resolveRuntimeBranding() {
                EndlessLeveling plugin = EndlessLeveling.getInstance();
                String brand = plugin != null ? plugin.getBrandName() : null;
                if (brand == null || brand.isBlank()) {
                        return new RuntimeBranding(BRAND_TITLE_FALLBACK, BRAND_NAV_HEADER_FALLBACK,
                                        BRAND_NAV_SUB_HEADER_FALLBACK);
                }

                String normalizedBrand = brand.trim().replaceAll("\\s+", " ").toUpperCase();
                String headerOverride = plugin != null ? plugin.getNavHeaderOverride() : null;
                String subHeaderOverride = plugin != null ? plugin.getNavSubHeaderOverride() : null;
                if (headerOverride != null || subHeaderOverride != null) {
                        String header = headerOverride == null ? "" : headerOverride.trim().replaceAll("\\s+", " ")
                                        .toUpperCase();
                        String subHeader = subHeaderOverride == null ? ""
                                        : subHeaderOverride.trim().replaceAll("\\s+", " ").toUpperCase();
                        if (header.isBlank() && subHeader.isBlank()) {
                                return new RuntimeBranding(normalizedBrand, BRAND_NAV_HEADER_FALLBACK,
                                                BRAND_NAV_SUB_HEADER_FALLBACK);
                        }
                        return new RuntimeBranding(normalizedBrand, header, subHeader);
                }

                int splitIndex = normalizedBrand.indexOf(' ');
                if (splitIndex < 0) {
                        return new RuntimeBranding(normalizedBrand, "", normalizedBrand);
                }

                String header = normalizedBrand.substring(0, splitIndex).trim();
                String subHeader = normalizedBrand.substring(splitIndex + 1).trim();
                if (header.isBlank()) {
                        header = BRAND_NAV_HEADER_FALLBACK;
                }
                if (subHeader.isBlank()) {
                        subHeader = BRAND_NAV_SUB_HEADER_FALLBACK;
                }
                return new RuntimeBranding(normalizedBrand, header, subHeader);
        }

        private record RuntimeBranding(String pageTitle, String navHeader, String navSubHeader) {
        }

        private static void safeSetText(
                        @Nonnull UICommandBuilder ui,
                        String resourcePath,
                        String selector,
                        String text) {
                if (selector == null || selector.isBlank()) {
                        return;
                }

                if (resourcePath != null && !resourcePath.isBlank()
                                && !resourceContainsSelector(resourcePath, selector)) {
                        return;
                }

                ui.set(selector + ".Text", text);
        }

        private static boolean resourceContainsSelector(String resourcePath, String selector) {
                String resourceContent = RESOURCE_TEXT_CACHE.computeIfAbsent(resourcePath, NavUIHelper::readResourceText);
                if (resourceContent == null) {
                        if (MISSING_RESOURCE_WARNED.add(resourcePath)) {
                                LOGGER.atWarning().log("NavUIHelper: resource '%s' not found for selector guard", resourcePath);
                        }
                        return false;
                }

                String selectorId = selector.startsWith("#") ? selector.substring(1) : selector;
                Pattern selectorPattern = Pattern.compile("#" + Pattern.quote(selectorId) + "\\b");
                boolean exists = selectorPattern.matcher(resourceContent).find();
                if (!exists) {
                        String warningKey = resourcePath + "|" + selector;
                        if (MISSING_SELECTOR_WARNED.add(warningKey)) {
                                LOGGER.atWarning().log("NavUIHelper: selector '%s' missing in %s; skipping write",
                                                selector,
                                                resourcePath);
                        }
                }
                return exists;
        }

        private static String readResourceText(String resourcePath) {
                try (InputStream in = NavUIHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
                        if (in == null) {
                                return null;
                        }
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                        LOGGER.atWarning().log("NavUIHelper: failed reading resource %s: %s", resourcePath, e.getMessage());
                        return null;
                }
        }

        private static void applySelectedNavStyle(@Nonnull UICommandBuilder ui, @Nonnull String activeNav,
                        boolean topNav) {
                if (topNav) {
                        setTopNavButtonSelected(ui, "#NavProfile", "profile".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavSkills", "skills".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavAugments", "augments".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavRaces", "races".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavClasses", "classes".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavGates", "gates".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavDungeons", "dungeons".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavQuests", "quests".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavLeaderboards", "leaderboards".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavXpStats", "xpstats".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavAddons", "addons".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavSupport", "support".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavServers", "servers".equalsIgnoreCase(activeNav));
                        setTopNavButtonSelected(ui, "#NavSettings", "settings".equalsIgnoreCase(activeNav));
                        return;
                }

                setNavButtonSelected(ui, "#NavProfile", "profile".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavSkills", "skills".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavAugments", "augments".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavDungeons", "dungeons".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavAddons", "addons".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavRaces", "races".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavClasses", "classes".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavLeaderboards", "leaderboards".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavParty", "party".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavSupport", "support".equalsIgnoreCase(activeNav));
                setNavButtonSelected(ui, "#NavSettings", "settings".equalsIgnoreCase(activeNav));
        }

        private static void setNavButtonSelected(@Nonnull UICommandBuilder ui, @Nonnull String selector,
                        boolean selected) {
                ui.set(selector + ".Style", selected ? NAV_BUTTON_STYLE_SELECTED : NAV_BUTTON_STYLE);
        }

        private static void setTopNavButtonSelected(@Nonnull UICommandBuilder ui, @Nonnull String selector,
                        boolean selected) {
                ui.set(selector + ".Style", selected ? TOP_NAV_BUTTON_STYLE_SELECTED : TOP_NAV_BUTTON_STYLE);
        }

        /**
         * Bind nav button click events for the common left nav panel.
         */
        public static void bindNavEvents(@Nonnull UIEventBuilder events) {
                bindNavEvents(events, null);
        }

        /**
         * Bind nav button click events for either the legacy LeftNavPanel or the new
         * TopNavBar layout. When {@code pageResourcePath} points at a page that imports
         * TopNavBar.ui only the selectors that exist in that layout get bound, so the
         * client doesn't fail with "target element not found" for missing legacy buttons.
         */
        public static void bindNavEvents(@Nonnull UIEventBuilder events, String pageResourcePath) {
                boolean topNav = pageUsesTopNavBar(pageResourcePath);

                if (topNav) {
                        events.addEventBinding(Activating, "#NavProfile", of("Action", "nav:profile"), false);
                        events.addEventBinding(Activating, "#NavSkills", of("Action", "nav:skills"), false);
                        events.addEventBinding(Activating, "#NavAugments", of("Action", "nav:augments"), false);
                        events.addEventBinding(Activating, "#NavRaces", of("Action", "nav:races"), false);
                        events.addEventBinding(Activating, "#NavClasses", of("Action", "nav:classes"), false);
                        events.addEventBinding(Activating, "#NavGates", of("Action", "nav:gates"), false);
                        events.addEventBinding(Activating, "#NavDungeons", of("Action", "nav:dungeons"), false);
                        events.addEventBinding(Activating, "#NavQuests", of("Action", "nav:quests"), false);
                        events.addEventBinding(Activating, "#NavLeaderboards", of("Action", "nav:leaderboards"), false);
                        events.addEventBinding(Activating, "#NavXpStats", of("Action", "nav:xpstats"), false);
                        events.addEventBinding(Activating, "#NavAddons", of("Action", "nav:addons"), false);
                        events.addEventBinding(Activating, "#NavSupport", of("Action", "nav:support"), false);
                        events.addEventBinding(Activating, "#NavServers", of("Action", "nav:servers"), false);
                        events.addEventBinding(Activating, "#NavSettings", of("Action", "nav:settings"), false);
                        return;
                }

                events.addEventBinding(Activating, "#NavProfile", of("Action", "nav:profile"), false);
                events.addEventBinding(Activating, "#NavRaces", of("Action", "nav:races"), false);
                events.addEventBinding(Activating, "#NavClasses", of("Action", "nav:classes"), false);
                events.addEventBinding(Activating, "#NavSkills", of("Action", "nav:skills"), false);
                events.addEventBinding(Activating, "#NavAugments", of("Action", "nav:augments"), false);
                events.addEventBinding(Activating, "#NavDungeons", of("Action", "nav:dungeons"), false);
                events.addEventBinding(Activating, "#NavAddons", of("Action", "nav:addons"), false);
                events.addEventBinding(Activating, "#NavLeaderboards", of("Action", "nav:leaderboards"), false);
                events.addEventBinding(Activating, "#NavParty", of("Action", "nav:party"), false);
                events.addEventBinding(Activating, "#NavSupport", of("Action", "nav:support"), false);
                events.addEventBinding(Activating, "#NavSettings", of("Action", "nav:settings"), false);
        }

        /**
         * Handle a nav: action and open the appropriate page.
         *
         * @return true if the action was a navigation action and a page was opened.
         */
        public static boolean handleNavAction(
                        @Nonnull String action,
                        @Nonnull Ref<EntityStore> ref,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull PlayerRef playerRef) {

                if (!action.startsWith("nav:")) {
                        return false;
                }

                String target = action.substring("nav:".length()).toLowerCase();

                // Resolve the Player entity from the current EntityStore, like other UI pages
                // do
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                        LOGGER.atSevere().log("NavUIHelper: player component is null for %s", playerRef.getUuid());
                        return false;
                }

                LOGGER.atInfo().log("NavUIHelper: navigating to '%s' for %s", target, playerRef.getUuid());

                switch (target) {
                        case "skills" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new SkillsUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "profile" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new ProfileUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "races" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new RacesUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "classes" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new ClassesUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "augments" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new AugmentsUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "dungeons" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new DungeonsUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "quests" -> {
                                // Quests UI lives in the EndlessQuestAndRewards addon and is exposed via /quests.
                                if (!openQuestsGui(playerRef)) {
                                        if (com.airijko.endlessleveling.util.PartnerConsoleGuard.isPartnerAddonPresent()) {
                                                // Partner servers don't ship EndlessQuestAndRewards; quests are simply off.
                                                PlayerChatNotifier.send(playerRef,
                                                        Message.raw("Quests are disabled.").color("#ff6666"));
                                        } else {
                                                PlayerChatNotifier.send(playerRef, Message.join(
                                                        Message.raw("Quests is a Patreon exclusive feature. ").color("#ff6666"),
                                                        Message.raw("[CLICK HERE]")
                                                                .link("https://www.patreon.com/posts/endless-quests-156146244")
                                                                .color("#ffd08a")
                                                ));
                                        }
                                }
                        }
                        case "addons" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new AddonsUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "gates" -> {
                                // Gates UI lives in the EndlessDungeons addon and is exposed via /gate.
                                // Dispatch the command as the player so the addon can handle it the same
                                // way it would for a manual chat invocation.
                                if (!openGatesGui(playerRef)) {
                                        if (com.airijko.endlessleveling.util.PartnerConsoleGuard.isPartnerAddonPresent()) {
                                                // ARank / Partner servers don't ship EndlessDungeons; gates are simply off.
                                                PlayerChatNotifier.send(playerRef,
                                                        Message.raw("Gates are disabled.").color("#ff6666"));
                                        } else {
                                                PlayerChatNotifier.send(playerRef, Message.join(
                                                        Message.raw("Gates is a Patreon exclusive feature. ").color("#ff6666"),
                                                        Message.raw("[CLICK HERE]")
                                                                .link("https://www.patreon.com/posts/dungeons-gates-155609841")
                                                                .color("#ffd08a")
                                                ));
                                        }
                                }
                        }
                        case "leaderboards" -> player.getPageManager()
                                        .openCustomPage(ref, store, new LeaderboardsUIPage(playerRef,
                                                        CustomPageLifetime.CanDismiss));
                        case "xpstats" -> {
                                EndlessLeveling xpPlugin = EndlessLeveling.getInstance();
                                if (xpPlugin != null && xpPlugin.getXpStatsManager() != null) {
                                        player.getPageManager()
                                                        .openCustomPage(ref, store, new XpStatsUIPage(playerRef,
                                                                        CustomPageLifetime.CanDismiss,
                                                                        xpPlugin.getXpStatsManager()));
                                }
                        }
                        case "party" -> {
                                if (!openPartyGui(playerRef)) {
                                        playerRef.sendMessage(Message.raw("PartyPro is not available or cannot be opened right now.").color("#ff6666"));
                                }
                        }
                        case "settings" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new SettingsUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "support" -> player.getPageManager()
                                        .openCustomPage(ref, store,
                                                        new SupportUIPage(playerRef, CustomPageLifetime.CanDismiss));
                        case "servers" -> {
                                if (isAuthorizedPartnerAddonPresent()) {
                                        LOGGER.atInfo().log(
                                                        "NavUIHelper: servers nav blocked (authorized Partner addon present)");
                                } else {
                                        player.getPageManager()
                                                        .openCustomPage(ref, store,
                                                                        new ServersUIPage(playerRef,
                                                                                        CustomPageLifetime.CanDismiss));
                                }
                        }
                        default -> {
                                LOGGER.atWarning().log("NavUIHelper: unknown nav target '%s'", target);
                                return false;
                        }
                }

                return true;
        }

        private static final String[] ENDLESS_DUNGEONS_CLASSES = {
                        "com.airijko.endlessleveling.EndlessRiftsAndRaids",
                        "com.airijko.endlessleveling.EndlessDungeonsAndGates",
        };

        private static final String[] ENDLESS_QUESTS_CLASSES = {
                        "com.airijko.endlessleveling.questsandrewards.EndlessQuestAndRewards",
        };

        private static final String PARTNER_ADDON_MAIN_CLASS =
                        "com.airijko.endlessleveling.EndlessLevelingPartnerAddon";

        /**
         * Returns true iff the EndlessLevelingPartnerAddon class is on the classpath and the
         * plugin has marked the partner addon as authorized. Explicitly excludes ARank.
         */
        private static boolean isAuthorizedPartnerAddonPresent() {
                try {
                        Class.forName(PARTNER_ADDON_MAIN_CLASS, false, NavUIHelper.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                        return false;
                }
                EndlessLeveling plugin = EndlessLeveling.getInstance();
                return plugin != null && plugin.isPartnerAddonAuthorized();
        }

        /** Returns true only when the EndlessDungeons/RiftsAndRaids addon is present on the classpath. */
        private static boolean isEndlessDungeonsPresent() {
                ClassLoader cl = NavUIHelper.class.getClassLoader();
                for (String name : ENDLESS_DUNGEONS_CLASSES) {
                        try {
                                Class.forName(name, false, cl);
                                return true;
                        } catch (ClassNotFoundException ignored) {
                        }
                }
                return false;
        }

        /** Returns true only when the EndlessQuestAndRewards addon is present on the classpath. */
        private static boolean isQuestsAddonPresent() {
                ClassLoader cl = NavUIHelper.class.getClassLoader();
                for (String name : ENDLESS_QUESTS_CLASSES) {
                        try {
                                Class.forName(name, false, cl);
                                return true;
                        } catch (ClassNotFoundException ignored) {
                        }
                }
                return false;
        }

        private static boolean openQuestsGui(@Nonnull PlayerRef playerRef) {
                if (playerRef == null) {
                        return false;
                }
                if (!isQuestsAddonPresent()) {
                        return false;
                }
                try {
                        CommandManager.get().handleCommand(playerRef, "quests");
                        return true;
                } catch (Exception ignored) {
                }
                return runCommandAsPlayer(playerRef, "quests");
        }

        private static boolean openGatesGui(@Nonnull PlayerRef playerRef) {
                if (playerRef == null) {
                        return false;
                }

                // CommandManager.handleCommand() does not throw when a command is missing —
                // it sends "Command not found!" to the player and returns normally, which
                // would make this method always return true. Guard with a classpath check
                // so we only dispatch when EndlessDungeons is actually loaded.
                if (!isEndlessDungeonsPresent()) {
                        return false;
                }

                try {
                        // Primary path: dispatch /gate as the player through the server command manager.
                        // The EndlessDungeons addon registers this command and opens GateListUIPage when
                        // the sender is a player.
                        CommandManager.get().handleCommand(playerRef, "gate");
                        return true;
                } catch (Exception ignored) {
                }

                return runCommandAsPlayer(playerRef, "gate");
        }

        private static boolean openPartyGui(@Nonnull PlayerRef playerRef) {
                if (playerRef == null) {
                        return false;
                }

                EndlessLeveling plugin = EndlessLeveling.getInstance();
                if (plugin == null) {
                        return false;
                }

                PartyManager partyManager = plugin.getPartyManager();
                if (partyManager == null || !partyManager.isAvailable()) {
                        return false;
                }

                try {
                        // Primary path: dispatch as player through the server command manager.
                        CommandManager.get().handleCommand(playerRef, "p");
                        return true;
                } catch (Exception ignored) {
                }

                if (runCommandAsPlayer(playerRef, "p")) {
                        return true;
                }

                return runCommandAsPlayer(playerRef, "p");
        }

        private static boolean runCommandAsPlayer(@Nonnull PlayerRef playerRef, @Nonnull String command) {
                if (playerRef == null || command == null || command.isBlank()) {
                        return false;
                }

                String[] candidates = new String[] {"executeCommand", "dispatchCommand", "runCommand", "execute"};
                for (String candidate : candidates) {
                        try {
                                var method = playerRef.getClass().getMethod(candidate, String.class);
                                method.setAccessible(true);
                                Object result = method.invoke(playerRef, command);
                                if (result == null || Boolean.TRUE.equals(result)) {
                                        return true;
                                }
                        } catch (NoSuchMethodException ignored) {
                        } catch (Exception ignored) {
                        }
                }

                Object universe = Universe.get();
                if (universe != null) {
                        for (String candidate : candidates) {
                                try {
                                        var method = universe.getClass().getMethod(candidate, PlayerRef.class, String.class);
                                        method.setAccessible(true);
                                        Object result = method.invoke(universe, playerRef, command);
                                        if (result == null || Boolean.TRUE.equals(result)) {
                                                return true;
                                        }
                                } catch (NoSuchMethodException ignored) {
                                } catch (Exception ignored) {
                                }
                        }
                }
                return false;
        }

        private static String resolveVersion() {
                // Try to read the plugin manifest bundled in resources. Fallback to a safe
                // placeholder if unavailable.
                try (java.io.InputStream in = NavUIHelper.class.getClassLoader().getResourceAsStream("manifest.json")) {
                        if (in == null) {
                                LOGGER.atWarning().log("NavUIHelper: manifest.json not found on classpath");
                                return "v?.?";
                        }

                        String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        int keyIndex = json.indexOf("\"Version\"");
                        if (keyIndex == -1) {
                                LOGGER.atWarning().log("NavUIHelper: Version key missing in manifest.json");
                                return "v?.?";
                        }

                        int colonIndex = json.indexOf(':', keyIndex);
                        if (colonIndex == -1) {
                                return "v?.?";
                        }

                        int firstQuote = json.indexOf('"', colonIndex);
                        int secondQuote = firstQuote >= 0 ? json.indexOf('"', firstQuote + 1) : -1;
                        if (firstQuote >= 0 && secondQuote > firstQuote) {
                                String version = json.substring(firstQuote + 1, secondQuote).trim();
                                if (!version.isEmpty()) {
                                        return "v" + version;
                                }
                        }
                        return "v?.?";
                } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("NavUIHelper: failed to read manifest version");
                        return "v?.?";
                }
        }

}
