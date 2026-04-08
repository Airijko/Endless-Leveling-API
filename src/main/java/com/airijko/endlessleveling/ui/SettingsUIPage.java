package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.util.Lang;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.ValueChanged;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Settings page that exposes per-player options stored in playerdata JSON.
 */
public class SettingsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

        private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

        // Nav button active/inactive colors
        private static final String NAV_ACTIVE_BG      = "#142034";
        private static final String NAV_ACTIVE_COLOR   = "#6fe3ff";
        private static final String NAV_INACTIVE_BG    = "#0b141f";
        private static final String NAV_INACTIVE_COLOR = "#c0cee5";

        // Config tab active/inactive colors (matches @ConfigTabStyleSelected / @ConfigTabStyle)
        private static final String TAB_ACTIVE_BG    = "#142034";
        private static final String TAB_ACTIVE_COLOR = "#6fe3ff";
        private static final String TAB_INACTIVE_BG    = "#0d1926";
        private static final String TAB_INACTIVE_COLOR = "#6e7da1";

        private static final String[] CONFIG_TABS = {
                "configYml", "levelingYml", "worldSettings", "eventsYml", "weaponsJson"
        };

        // Cycle value lists for cycle buttons
        private static final String[] RACE_VISUALS_CYCLE = { "off", "on", "disabled" };
        private static final String[] LOG_MODE_CYCLE = { "LOG10", "LN" };
        private static final String[] MOB_LEVEL_MODE_CYCLE = { "FIXED", "PLAYER", "MIXED", "DISTANCE", "TIERS" };
        private static final String[] XP_SCALING_MODE_CYCLE = { "LINEAR", "EXPONENTIAL", "QUADRATIC" };

        /** true = Player Settings visible, false = Config Settings visible */
        private boolean showingPlayerSection = true;

        /** Currently active config sub-tab */
        private String activeConfigTab = "configYml";

        /** Cached admin status for the player */
        private boolean isAdmin = false;

        /** Currently active world-settings file tab */
        private String activeWsFile = null;

        /** Cached world-settings file names (load order) */
        private List<String> wsFileNames = List.of();

        /** Pending config changes keyed by prefix (cfg/lvl/evt) then dot-path → value. */
        private final Map<String, Map<String, Object>> pendingChanges = new HashMap<>();

        public SettingsUIPage(@Nonnull PlayerRef playerRef,
                        @Nonnull CustomPageLifetime lifetime) {
                super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        }

        @Override
        public void build(@Nonnull Ref<EntityStore> ref,
                        @Nonnull UICommandBuilder rawUi,
                        @Nonnull UIEventBuilder events,
                        @Nonnull Store<EntityStore> store) {

                SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
                ui.append("Pages/Settings/SettingsPage.ui");
                NavUIHelper.applyNavVersion(ui, playerRef, "settings",
                                "Common/UI/Custom/Pages/Settings/SettingsPage.ui",
                                "#SettingsTitle");

                // Bind navigation on the left side
                NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/Settings/SettingsPage.ui");

                // Determine admin status and hide config section for non-admins
                isAdmin = OperatorHelper.hasAdministrativeAccess(playerRef);
                ui.set("#NavConfigSettings.Visible", isAdmin);
                if (!isAdmin) {
                        showingPlayerSection = true;
                }

                // Bind toggle buttons
                events.addEventBinding(Activating, "#PlayerHudToggle", of("Action", "toggle:playerHud"), false);
                events.addEventBinding(Activating, "#CriticalNotifToggle", of("Action", "toggle:criticalNotif"), false);
                events.addEventBinding(Activating, "#XpNotifToggle", of("Action", "toggle:xpNotif"), false);
                events.addEventBinding(Activating, "#PassiveLevelUpNotifToggle",
                                of("Action", "toggle:passiveLevelUpNotif"),
                                false);
                events.addEventBinding(Activating, "#LuckDoubleDropsNotifToggle",
                                of("Action", "toggle:luckDoubleDropsNotif"),
                                false);
                events.addEventBinding(Activating, "#HealthRegenNotifToggle", of("Action", "toggle:healthRegenNotif"),
                                false);
                events.addEventBinding(Activating, "#AugmentNotifToggle", of("Action", "toggle:augmentNotif"), false);
                events.addEventBinding(Activating, "#RaceModelToggle", of("Action", "toggle:raceModel"), false);
                events.addEventBinding(Activating, "#SupportPveModeToggle",
                                of("Action", "toggle:supportPveMode"), false);
                events.addEventBinding(Activating, "#NecromancerPveModeToggle",
                                of("Action", "toggle:necromancerPveMode"), false);

                // Section navigation
                events.addEventBinding(Activating, "#NavPlayerSettings", of("Action", "nav:playerSettings"), false);
                events.addEventBinding(Activating, "#NavConfigSettings", of("Action", "nav:configSettings"), false);
                events.addEventBinding(Activating, "#SaveSettings", of("Action", "action:save"), false);
                events.addEventBinding(Activating, "#ReloadSettings", of("Action", "action:reload"), false);

                // Config tab buttons and config event bindings — admin only
                if (isAdmin) {
                events.addEventBinding(Activating, "#TabConfigYml",     of("Action", "tab:configYml"),     false);
                events.addEventBinding(Activating, "#TabLevelingYml",   of("Action", "tab:levelingYml"),   false);
                events.addEventBinding(Activating, "#TabWorldSettings", of("Action", "tab:worldSettings"), false);
                events.addEventBinding(Activating, "#TabEventsYml",     of("Action", "tab:eventsYml"),     false);
                events.addEventBinding(Activating, "#TabWeaponsJson",   of("Action", "tab:weaponsJson"),   false);

                // ---- Config.yml toggle bindings ----
                events.addEventBinding(Activating, "#CfgForceConfigToggle",          of("Action", "cfg:t:force_builtin_config"), false);
                events.addEventBinding(Activating, "#CfgForceLevelingToggle",        of("Action", "cfg:t:force_builtin_leveling"), false);
                events.addEventBinding(Activating, "#CfgForceEventsToggle",          of("Action", "cfg:t:force_builtin_events"), false);
                events.addEventBinding(Activating, "#CfgForceRacesToggle",           of("Action", "cfg:t:force_builtin_races"), false);
                events.addEventBinding(Activating, "#CfgForceClassesToggle",         of("Action", "cfg:t:force_builtin_classes"), false);
                events.addEventBinding(Activating, "#CfgForceAugmentsToggle",        of("Action", "cfg:t:force_builtin_augments"), false);
                events.addEventBinding(Activating, "#CfgForceLanguagesToggle",       of("Action", "cfg:t:force_builtin_languages"), false);
                events.addEventBinding(Activating, "#CfgForceWorldSettingsToggle",   of("Action", "cfg:t:force_builtin_world_settings"), false);
                events.addEventBinding(Activating, "#CfgEnableRacesToggle",          of("Action", "cfg:t:enable_races"), false);
                events.addEventBinding(Activating, "#CfgEnableClassesToggle",        of("Action", "cfg:t:enable_classes"), false);
                events.addEventBinding(Activating, "#CfgEnableSecondaryClassToggle", of("Action", "cfg:t:enable_secondary_class"), false);
                events.addEventBinding(Activating, "#CfgSwapConsumeEnabledToggle",   of("Action", "cfg:t:swap_anti_exploit.consume_at_level_enabled"), false);
                events.addEventBinding(Activating, "#CfgEnableBuiltinRacesToggle",   of("Action", "cfg:t:enable_builtin_races"), false);
                events.addEventBinding(Activating, "#CfgEnableBuiltinClassesToggle", of("Action", "cfg:t:enable_builtin_classes"), false);
                events.addEventBinding(Activating, "#CfgEnableBuiltinAugmentsToggle",of("Action", "cfg:t:enable_builtin_augments"), false);
                events.addEventBinding(Activating, "#CfgEnableLoggingToggle",        of("Action", "cfg:t:enable_logging"), false);

                // Config.yml cycle binding
                events.addEventBinding(Activating, "#CfgGlobalRaceVisualsCycle", of("Action", "cfg:c:global_race_visuals_setting"), false);

                // Config.yml number field bindings
                events.addEventBinding(ValueChanged, "#CfgRaceCooldownInput",
                                new EventData().append("Action", "cfg:n:choose_race_cooldown").append("@ConfigValue", "#CfgRaceCooldownInput.Value"), false);
                events.addEventBinding(ValueChanged, "#CfgRaceMaxSwitchesInput",
                                new EventData().append("Action", "cfg:n:race_max_switches").append("@ConfigValue", "#CfgRaceMaxSwitchesInput.Value"), false);
                events.addEventBinding(ValueChanged, "#CfgClassCooldownInput",
                                new EventData().append("Action", "cfg:n:choose_class_cooldown").append("@ConfigValue", "#CfgClassCooldownInput.Value"), false);
                events.addEventBinding(ValueChanged, "#CfgClassMaxSwitchesInput",
                                new EventData().append("Action", "cfg:n:class_max_switches").append("@ConfigValue", "#CfgClassMaxSwitchesInput.Value"), false);
                events.addEventBinding(ValueChanged, "#CfgSwapConsumeThresholdInput",
                                new EventData().append("Action", "cfg:n:swap_anti_exploit.consume_at_level_threshold").append("@ConfigValue", "#CfgSwapConsumeThresholdInput.Value"), false);

                // ---- Leveling.yml toggle bindings ----
                events.addEventBinding(Activating, "#LvlPartyXpEnabledToggle",         of("Action", "lvl:t:party_xp_share.enabled"), false);
                events.addEventBinding(Activating, "#LvlPrestigeEnabledToggle",         of("Action", "lvl:t:prestige.enabled"), false);
                events.addEventBinding(Activating, "#LvlMobXpEnabledToggle",            of("Action", "lvl:t:Mob_Leveling.Experience.Enabled"), false);
                events.addEventBinding(Activating, "#LvlNameplateEnabledToggle",        of("Action", "lvl:t:Mob_Leveling.Nameplate.Enabled"), false);
                events.addEventBinding(Activating, "#LvlNameplateShowLevelToggle",      of("Action", "lvl:t:Mob_Leveling.Nameplate.Show_Level"), false);
                events.addEventBinding(Activating, "#LvlNameplateShowNameToggle",       of("Action", "lvl:t:Mob_Leveling.Nameplate.Show_Name"), false);
                events.addEventBinding(Activating, "#LvlNameplateShowHealthToggle",     of("Action", "lvl:t:Mob_Leveling.Nameplate.Show_Health"), false);
                events.addEventBinding(Activating, "#LvlPlayerNameplateEnabledToggle",  of("Action", "lvl:t:Mob_Leveling.Nameplate.Player_Nameplate_Enabled"), false);

                // Leveling.yml cycle bindings
                events.addEventBinding(Activating, "#LvlLogModeCycle",          of("Action", "lvl:c:default.log_mode"), false);
                events.addEventBinding(Activating, "#LvlMobLevelModeCycle",     of("Action", "lvl:c:Mob_Leveling.Level_Source.Mode"), false);
                events.addEventBinding(Activating, "#LvlMobXpScalingModeCycle", of("Action", "lvl:c:Mob_Leveling.Experience.Scaling.Mode"), false);

                // Leveling.yml number field bindings
                events.addEventBinding(ValueChanged, "#LvlBaseInput",
                                new EventData().append("Action", "lvl:n:default.base").append("@ConfigValue", "#LvlBaseInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlBaseSkillPointsInput",
                                new EventData().append("Action", "lvl:n:baseSkillPoints").append("@ConfigValue", "#LvlBaseSkillPointsInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlSkillPointsPerLevelInput",
                                new EventData().append("Action", "lvl:n:skillPointsPerLevel").append("@ConfigValue", "#LvlSkillPointsPerLevelInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlPlayerLevelCapInput",
                                new EventData().append("Action", "lvl:n:player_level_cap").append("@ConfigValue", "#LvlPlayerLevelCapInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlPartyXpRangeInput",
                                new EventData().append("Action", "lvl:n:party_xp_share.range").append("@ConfigValue", "#LvlPartyXpRangeInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlPartyXpSharePercentInput",
                                new EventData().append("Action", "lvl:n:party_xp_share.member_share_percent").append("@ConfigValue", "#LvlPartyXpSharePercentInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlPrestigeLevelCapIncInput",
                                new EventData().append("Action", "lvl:n:prestige.level_cap_increase_per_prestige").append("@ConfigValue", "#LvlPrestigeLevelCapIncInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlPrestigeXpIncInput",
                                new EventData().append("Action", "lvl:n:prestige.base_xp_increase_per_prestige").append("@ConfigValue", "#LvlPrestigeXpIncInput.Value"), false);
                events.addEventBinding(ValueChanged, "#LvlGlobalXpMultiplierInput",
                                new EventData().append("Action", "lvl:n:Mob_Leveling.Experience.Global_XP_Multiplier").append("@ConfigValue", "#LvlGlobalXpMultiplierInput.Value"), false);

                // ---- Events.yml toggle bindings ----
                events.addEventBinding(Activating, "#EvtPrestigeEnabledToggle",        of("Action", "evt:t:events.prestige_level_up.enabled"), false);
                events.addEventBinding(Activating, "#EvtPrestigeAsPlayerToggle",       of("Action", "evt:t:events.prestige_level_up.as_player"), false);
                events.addEventBinding(Activating, "#EvtLevelUpEnabledToggle",         of("Action", "evt:t:events.player_level_up.enabled"), false);
                events.addEventBinding(Activating, "#EvtLevelUpAsPlayerToggle",        of("Action", "evt:t:events.player_level_up.as_player"), false);
                events.addEventBinding(Activating, "#EvtLevelUpRepeatPrestigesToggle", of("Action", "evt:t:events.player_level_up.repeat_access_prestiges"), false);
                } // end isAdmin config bindings

                // Apply section visibility
                ui.set("#PlayerSettingsPanel.Visible", showingPlayerSection);
                ui.set("#ConfigSettingsPanel.Visible", !showingPlayerSection);
                ui.set("#PlayerSettingsInfoPanel.Visible", showingPlayerSection);
                ui.set("#ConfigSettingsInfoPanel.Visible", !showingPlayerSection);
                applyNavButtonStyles(ui);
                applyConfigTabStyles(ui);
                if (showingPlayerSection) {
                        ui.set("#SettingsNavInfoTitle.Text",
                                        Lang.tr(playerRef.getUuid(), "ui.settings.nav.player_title",
                                                        "Player Settings"));
                        ui.set("#SettingsNavInfoDesc.Text",
                                        Lang.tr(playerRef.getUuid(), "ui.settings.nav.player_desc",
                                                        "Manage your personal gameplay preferences including HUD display and notification settings."));
                } else {
                        ui.set("#SettingsNavInfoTitle.Text",
                                        Lang.tr(playerRef.getUuid(), "ui.settings.nav.config_title",
                                                        "Config Settings"));
                        ui.set("#SettingsNavInfoDesc.Text",
                                        Lang.tr(playerRef.getUuid(), "ui.settings.nav.config_desc",
                                                        "Server-wide configuration for leveling, worlds, events, and weapons."));
                }

                // Populate current values from PlayerData
                PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                if (player == null) {
                        return;
                }

                PlayerData data = EndlessLeveling.getInstance()
                                .getPlayerDataManager()
                                .get(playerRef.getUuid());

                if (data == null) {
                        return;
                }

                var raceManager = EndlessLeveling.getInstance().getRaceManager();
                boolean raceModelsDisabled = raceManager != null && raceManager.isRaceModelGloballyDisabled();

                ui.set("#PlayerHudLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.player_hud.label", "Player HUD"));
                ui.set("#PlayerHudValue.Text", data.isPlayerHudEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#CriticalNotifLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.critical_notif.label",
                                                "Critical Hit Notifications"));
                ui.set("#CriticalNotifValue.Text", data.isCriticalNotifEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#XpNotifLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.xp_notif.label", "XP Gain Notifications"));
                ui.set("#XpNotifValue.Text", data.isXpNotifEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#PassiveLevelUpNotifLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.passive_levelup_notif.label",
                                                "Passive Level-Up Notifications"));
                ui.set("#PassiveLevelUpNotifValue.Text", data.isPassiveLevelUpNotifEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#LuckDoubleDropsNotifLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.luck_double_notif.label",
                                                "Luck Double-Drop Notifications"));
                ui.set("#LuckDoubleDropsNotifValue.Text", data.isLuckDoubleDropsNotifEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#HealthRegenNotifLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.health_regen_notif.label",
                                                "Health Regen Notifications"));
                ui.set("#HealthRegenNotifValue.Text", data.isHealthRegenNotifEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#AugmentNotifLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.augment_notif.label",
                                                "Augment Notifications"));
                ui.set("#AugmentNotifValue.Text", data.isAugmentNotifEnabled()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#RaceModelLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.race_model.label", "Race Model Visuals"));
                ui.set("#RaceModelValue.Text", raceModelsDisabled
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.disabled", "DISABLED")
                                : data.isUseRaceModel() ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#SupportPveModeLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.support_pve_mode.label",
                                                "Bard / Priest / Magistrate PVE Mode"));
                ui.set("#SupportPveModeValue.Text", data.isSupportPveMode()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#NecromancerPveModeLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.necromancer_pve_mode.label",
                                                "Necromancer PVE Mode"));
                ui.set("#NecromancerPveModeValue.Text", data.isNecromancerPveMode()
                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));

                ui.set("#SettingsTitleLabel.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.page.title", "Settings"));
                ui.set("#SettingsIntroText.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.page.subtitle",
                                                "Manage personal HUD, notifications, and visual preferences."));

                String toggleText = Lang.tr(playerRef.getUuid(), "ui.settings.page.toggle_button", "TOGGLE");
                ui.set("#PlayerHudToggle.Text", toggleText);
                ui.set("#CriticalNotifToggle.Text", toggleText);
                ui.set("#XpNotifToggle.Text", toggleText);
                ui.set("#PassiveLevelUpNotifToggle.Text", toggleText);
                ui.set("#LuckDoubleDropsNotifToggle.Text", toggleText);
                ui.set("#HealthRegenNotifToggle.Text", toggleText);
                ui.set("#AugmentNotifToggle.Text", toggleText);
                ui.set("#RaceModelToggle.Text", toggleText);
                ui.set("#SupportPveModeToggle.Text", toggleText);
                ui.set("#NecromancerPveModeToggle.Text", toggleText);

                ui.set("#PlayerHudDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.player_hud",
                                                "Show or hide the Endless Leveling HUD overlay."));
                ui.set("#CriticalNotifDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.critical_notif",
                                                "Toggle floating alerts for critical strike events."));
                ui.set("#XpNotifDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.xp_notif",
                                                "Show notifications whenever you gain experience."));
                ui.set("#PassiveLevelUpNotifDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.passive_levelup_notif",
                                                "Display updates when passives rank up during play."));
                ui.set("#LuckDoubleDropsNotifDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.luck_double_notif",
                                                "Toggle bonus drop proc notifications from luck effects."));
                ui.set("#HealthRegenNotifDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.health_regen_notif",
                                                "Show notifications when health regeneration triggers."));
                ui.set("#AugmentNotifDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.augment_notif",
                                                "Toggle chat notifications for augment status events."));
                ui.set("#RaceModelDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.race_model",
                                                "Enable race-specific character visuals when available."));
                ui.set("#SupportPveModeDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.support_pve_mode",
                                                "Supporting passives/abilities apply to all nearby players, instead of just party members."));
                ui.set("#NecromancerPveModeDescription.Text",
                                Lang.tr(playerRef.getUuid(), "ui.settings.description.necromancer_pve_mode",
                                                "Summons focus on mobs only, never target players, and are immune to all player damage."));

                // Populate config settings values from server config files
                populateConfigSettings(ui);

                // Build dynamic world-settings tabs and content
                if (isAdmin) {
                        buildWorldSettingsTabs(ui, events);
                }
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
                }

                if (data.action == null || data.action.isEmpty()) {
                        return;
                }

                String action = data.action;
                LOGGER.atInfo().log("SettingsUIPage handleDataEvent action=%s for player %s", action,
                                playerRef.getUuid());

                // ---- Save pending config changes to disk ----
                if ("action:save".equalsIgnoreCase(action)) {
                        if (!showingPlayerSection && isAdmin) {
                                flushPendingChanges();
                        }
                        rebuild();
                        return;
                }

                // ---- Reload all configs from disk (discards pending) ----
                if ("action:reload".equalsIgnoreCase(action)) {
                        if (!showingPlayerSection && isAdmin) {
                                pendingChanges.clear();
                                var plugin = EndlessLeveling.getInstance();
                                plugin.getConfigManager().load();
                                plugin.getMobLevelingManager().reloadConfig();
                                plugin.getEventHookManager().reload();
                                PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                                if (player != null) {
                                        player.sendMessage(Message.raw("Config files reloaded from disk. Pending changes discarded.").color("#7dd87d"));
                                }
                        }
                        rebuild();
                        return;
                }

                // ---- Section navigation (client-side toggles, no save needed) ----
                if ("nav:playerSettings".equalsIgnoreCase(action)) {
                        showingPlayerSection = true;
                        rebuild();
                        return;
                } else if ("nav:configSettings".equalsIgnoreCase(action)) {
                        if (!OperatorHelper.hasAdministrativeAccess(playerRef)) {
                                PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                                if (player != null) {
                                        player.sendMessage(Message.raw("You do not have permission to access config settings.").color("#ff6666"));
                                }
                                return;
                        }
                        showingPlayerSection = false;
                        rebuild();
                        return;
                }

                // ---- Config tab switching ----
                if (action.startsWith("tab:")) {
                        String tab = action.substring(4);
                        for (String t : CONFIG_TABS) {
                                if (t.equalsIgnoreCase(tab)) {
                                        activeConfigTab = t;
                                        rebuild();
                                        return;
                                }
                        }
                }

                // ---- World-settings file navigation (prev/next) ----
                if ("ws:prevFile".equalsIgnoreCase(action)) {
                        if (!wsFileNames.isEmpty()) {
                                int idx = wsFileNames.indexOf(activeWsFile);
                                activeWsFile = wsFileNames.get((idx - 1 + wsFileNames.size()) % wsFileNames.size());
                        }
                        rebuild();
                        return;
                }
                if ("ws:nextFile".equalsIgnoreCase(action)) {
                        if (!wsFileNames.isEmpty()) {
                                int idx = wsFileNames.indexOf(activeWsFile);
                                activeWsFile = wsFileNames.get((idx + 1) % wsFileNames.size());
                        }
                        rebuild();
                        return;
                }

                // ---- Config file actions (cfg: / lvl: / evt:) ----
                if (action.startsWith("cfg:") || action.startsWith("lvl:") || action.startsWith("evt:")) {
                        handleConfigAction(action, data, ref, store);
                        return;
                }

                PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                if (player == null) {
                        LOGGER.atWarning().log("SettingsUIPage: PlayerRef not found in Universe for %s",
                                        playerRef.getUuid());
                        return;
                }

                PlayerData playerData = EndlessLeveling.getInstance()
                                .getPlayerDataManager()
                                .get(playerRef.getUuid());
                if (playerData == null) {
                        LOGGER.atWarning().log("SettingsUIPage: PlayerData is null for %s", playerRef.getUuid());
                        return;
                }
                var raceManager = EndlessLeveling.getInstance().getRaceManager();
                Player playerEntity = store.getComponent(ref, Player.getComponentType());

                boolean changed = false;
                if ("toggle:playerHud".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isPlayerHudEnabled();
                        playerData.setPlayerHudEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                                        "ui.settings.player_hud.toggled",
                                        "Player HUD {0}",
                                        newValue ? Lang.tr(playerRef.getUuid(), "ui.common.state.enabled", "enabled")
                                                        : Lang.tr(playerRef.getUuid(), "ui.common.state.disabled",
                                                                        "disabled")))
                                        .color("#ffc300"));

                        if (playerEntity != null) {
                                PlayerHud.openPreferred(playerEntity, playerRef);
                        }
                } else if ("toggle:criticalNotif".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isCriticalNotifEnabled();
                        playerData.setCriticalNotifEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                                        "ui.settings.critical_notif.toggled",
                                        "Critical hit notifications {0}",
                                        newValue ? Lang.tr(playerRef.getUuid(), "ui.common.state.enabled", "enabled")
                                                        : Lang.tr(playerRef.getUuid(), "ui.common.state.disabled",
                                                                        "disabled")))
                                        .color("#ffc300"));
                } else if ("toggle:xpNotif".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isXpNotifEnabled();
                        playerData.setXpNotifEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(), "ui.settings.xp_notif.toggled",
                                        "XP gain notifications {0}",
                                        newValue ? Lang.tr(playerRef.getUuid(), "ui.common.state.enabled", "enabled")
                                                        : Lang.tr(playerRef.getUuid(), "ui.common.state.disabled",
                                                                        "disabled")))
                                        .color("#ffc300"));
                } else if ("toggle:passiveLevelUpNotif".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isPassiveLevelUpNotifEnabled();
                        playerData.setPassiveLevelUpNotifEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message
                                        .raw(Lang.tr(playerRef.getUuid(), "ui.settings.passive_levelup_notif.toggled",
                                                        "Passive level-up notifications {0}",
                                                        newValue ? Lang.tr(playerRef.getUuid(),
                                                                        "ui.common.state.enabled", "enabled")
                                                                        : Lang.tr(playerRef.getUuid(),
                                                                                        "ui.common.state.disabled",
                                                                                        "disabled")))
                                        .color("#ffc300"));
                } else if ("toggle:luckDoubleDropsNotif".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isLuckDoubleDropsNotifEnabled();
                        playerData.setLuckDoubleDropsNotifEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message
                                        .raw(Lang.tr(playerRef.getUuid(), "ui.settings.luck_double_notif.toggled",
                                                        "Luck double-drop notifications {0}",
                                                        newValue ? Lang.tr(playerRef.getUuid(),
                                                                        "ui.common.state.enabled", "enabled")
                                                                        : Lang.tr(playerRef.getUuid(),
                                                                                        "ui.common.state.disabled",
                                                                                        "disabled")))
                                        .color("#ffc300"));
                } else if ("toggle:healthRegenNotif".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isHealthRegenNotifEnabled();
                        playerData.setHealthRegenNotifEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message
                                        .raw(Lang.tr(playerRef.getUuid(), "ui.settings.health_regen_notif.toggled",
                                                        "Health regen notifications {0}",
                                                        newValue ? Lang.tr(playerRef.getUuid(),
                                                                        "ui.common.state.enabled", "enabled")
                                                                        : Lang.tr(playerRef.getUuid(),
                                                                                        "ui.common.state.disabled",
                                                                                        "disabled")))
                                        .color("#ffc300"));
                } else if ("toggle:augmentNotif".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isAugmentNotifEnabled();
                        playerData.setAugmentNotifEnabled(newValue);
                        changed = true;
                        player.sendMessage(Message
                                        .raw(Lang.tr(playerRef.getUuid(), "ui.settings.augment_notif.toggled",
                                                        "Augment notifications {0}",
                                                        newValue ? Lang.tr(playerRef.getUuid(),
                                                                        "ui.common.state.enabled", "enabled")
                                                                        : Lang.tr(playerRef.getUuid(),
                                                                                        "ui.common.state.disabled",
                                                                                        "disabled")))
                                        .color("#ffc300"));
                } else if ("toggle:supportPveMode".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isSupportPveMode();
                        playerData.setSupportPveMode(newValue);
                        changed = true;
                        player.sendMessage(Message
                                        .raw(Lang.tr(playerRef.getUuid(), "ui.settings.support_pve_mode.toggled",
                                                        "Bard / Priest / Magistrate PVE Mode {0}",
                                                        newValue ? Lang.tr(playerRef.getUuid(),
                                                                        "ui.common.state.enabled", "enabled")
                                                                        : Lang.tr(playerRef.getUuid(),
                                                                                        "ui.common.state.disabled",
                                                                                        "disabled")))
                                        .color("#d7baff"));
                } else if ("toggle:necromancerPveMode".equalsIgnoreCase(action)) {
                        boolean newValue = !playerData.isNecromancerPveMode();
                        playerData.setNecromancerPveMode(newValue);
                        changed = true;
                        player.sendMessage(Message
                                        .raw(Lang.tr(playerRef.getUuid(), "ui.settings.necromancer_pve_mode.toggled",
                                                        "Necromancer PVE Mode {0}",
                                                        newValue ? Lang.tr(playerRef.getUuid(),
                                                                        "ui.common.state.enabled", "enabled")
                                                                        : Lang.tr(playerRef.getUuid(),
                                                                                        "ui.common.state.disabled",
                                                                                        "disabled")))
                                        .color("#d7baff"));
                } else if ("toggle:raceModel".equalsIgnoreCase(action)) {
                        if (raceManager != null && raceManager.isRaceModelGloballyDisabled()) {
                                player.sendMessage(Message
                                                .raw(Lang.tr(playerRef.getUuid(),
                                                                "ui.settings.race_model.globally_disabled",
                                                                "Race model visuals are disabled by the server configuration."))
                                                .color("#ff6666"));
                                if (raceManager != null) {
                                        raceManager.resetRaceModelIfOnline(playerData);
                                }
                                rebuild();
                                return;
                        }
                        boolean newValue = !playerData.isUseRaceModel();
                        playerData.setUseRaceModel(newValue);
                        changed = true;
                        if (newValue) {
                                if (raceManager != null) {
                                        raceManager.applyRaceModelIfEnabled(playerData);
                                }
                                player.sendMessage(Message
                                                .raw(Lang.tr(playerRef.getUuid(), "ui.settings.race_model.enabled",
                                                                "Race model visuals enabled"))
                                                .color("#4fd7f7"));
                        } else {
                                if (raceManager != null) {
                                        raceManager.resetRaceModelIfOnline(playerData);
                                }
                                player.sendMessage(Message
                                                .raw(Lang.tr(playerRef.getUuid(), "ui.settings.race_model.disabled",
                                                                "Race model visuals disabled"))
                                                .color("#ff9900"));
                        }
                }

                if (changed) {
                        EndlessLeveling.getInstance().getPlayerDataManager().save(playerData);
                        sendValuesUpdate();
                }
        }

        private void applyConfigTabStyles(@Nonnull UICommandBuilder ui) {
                // Tab content panels
                ui.set("#ConfigYmlPanel.Visible",       "configYml".equals(activeConfigTab));
                ui.set("#LevelingYmlPanel.Visible",     "levelingYml".equals(activeConfigTab));
                ui.set("#WorldSettingsPanel.Visible",   "worldSettings".equals(activeConfigTab));
                ui.set("#EventsYmlPanel.Visible",       "eventsYml".equals(activeConfigTab));
                ui.set("#WeaponsJsonPanel.Visible",     "weaponsJson".equals(activeConfigTab));

                // Tab button highlight (background + label color)
                String[][] tabs = {
                        { "#TabConfigYml",     "configYml" },
                        { "#TabLevelingYml",   "levelingYml" },
                        { "#TabWorldSettings", "worldSettings" },
                        { "#TabEventsYml",     "eventsYml" },
                        { "#TabWeaponsJson",   "weaponsJson" },
                };
                for (String[] entry : tabs) {
                        String sel = entry[0];
                        boolean active = entry[1].equals(activeConfigTab);
                        String bg    = active ? TAB_ACTIVE_BG    : TAB_INACTIVE_BG;
                        String color = active ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR;
                        ui.set(sel + ".Style.Default.Background", bg);
                        ui.set(sel + ".Style.Hovered.Background", bg);
                        ui.set(sel + ".Style.Default.LabelStyle.TextColor", color);
                        ui.set(sel + ".Style.Hovered.LabelStyle.TextColor", color);
                }
        }

        private void applyNavButtonStyles(@Nonnull UICommandBuilder ui) {
                String playerBg    = showingPlayerSection ? NAV_ACTIVE_BG    : NAV_INACTIVE_BG;
                String playerColor = showingPlayerSection ? NAV_ACTIVE_COLOR : NAV_INACTIVE_COLOR;
                String configBg    = showingPlayerSection ? NAV_INACTIVE_BG    : NAV_ACTIVE_BG;
                String configColor = showingPlayerSection ? NAV_INACTIVE_COLOR : NAV_ACTIVE_COLOR;

                ui.set("#NavPlayerSettings.Style.Default.Background", playerBg);
                ui.set("#NavPlayerSettings.Style.Hovered.Background", playerBg);
                ui.set("#NavPlayerSettings.Style.Default.LabelStyle.TextColor", playerColor);
                ui.set("#NavPlayerSettings.Style.Hovered.LabelStyle.TextColor", playerColor);

                ui.set("#NavConfigSettings.Style.Default.Background", configBg);
                ui.set("#NavConfigSettings.Style.Hovered.Background", configBg);
                ui.set("#NavConfigSettings.Style.Default.LabelStyle.TextColor", configColor);
                ui.set("#NavConfigSettings.Style.Hovered.LabelStyle.TextColor", configColor);
        }

        // ------------------------------------------------------------------
        //  Config value population
        // ------------------------------------------------------------------

        private void populateConfigSettings(@Nonnull UICommandBuilder ui) {
                var plugin = EndlessLeveling.getInstance();
                ConfigManager cfg = plugin.getConfigManager();

                // ---- Content Sync (overview panel) ----
                ui.set("#CfgForceConfigValue.Text",         boolText(getConfigValue(cfg, "cfg", "force_builtin_config", true)));
                ui.set("#CfgForceLevelingValue.Text",       boolText(getConfigValue(cfg, "cfg", "force_builtin_leveling", true)));
                ui.set("#CfgForceEventsValue.Text",         boolText(getConfigValue(cfg, "cfg", "force_builtin_events", false)));
                ui.set("#CfgForceRacesValue.Text",          boolText(getConfigValue(cfg, "cfg", "force_builtin_races", true)));
                ui.set("#CfgForceClassesValue.Text",        boolText(getConfigValue(cfg, "cfg", "force_builtin_classes", true)));
                ui.set("#CfgForceAugmentsValue.Text",       boolText(getConfigValue(cfg, "cfg", "force_builtin_augments", true)));
                ui.set("#CfgForceLanguagesValue.Text",      boolText(getConfigValue(cfg, "cfg", "force_builtin_languages", true)));
                ui.set("#CfgForceWorldSettingsValue.Text",  boolText(getConfigValue(cfg, "cfg", "force_builtin_world_settings", true)));

                // ---- Config.yml tab ----
                // Race system
                ui.set("#CfgEnableRacesValue.Text",         boolText(getConfigValue(cfg, "cfg", "enable_races", true)));
                ui.set("#CfgDefaultRaceValue.Text",         String.valueOf(cfg.get("default_race", "Human")));
                ui.set("#CfgRaceCooldownInput.Value",       toDouble(getConfigValue(cfg, "cfg", "choose_race_cooldown", 3600)));
                ui.set("#CfgRaceMaxSwitchesInput.Value",    toDouble(getConfigValue(cfg, "cfg", "race_max_switches", 1)));
                ui.set("#CfgGlobalRaceVisualsValue.Text",   String.valueOf(getConfigValue(cfg, "cfg", "global_race_visuals_setting", "off")));

                // Class system
                ui.set("#CfgEnableClassesValue.Text",            boolText(getConfigValue(cfg, "cfg", "enable_classes", true)));
                ui.set("#CfgEnableSecondaryClassValue.Text",     boolText(getConfigValue(cfg, "cfg", "enable_secondary_class", true)));
                ui.set("#CfgDefaultPrimaryClassValue.Text",      String.valueOf(cfg.get("default_primary_class", "Adventurer")));
                ui.set("#CfgOffClassPenaltyValue.Text",          String.valueOf(cfg.get("weapon_off_class_damage_penalty", "-40%")));
                ui.set("#CfgClassCooldownInput.Value",           toDouble(getConfigValue(cfg, "cfg", "choose_class_cooldown", 1800)));
                ui.set("#CfgClassMaxSwitchesInput.Value",        toDouble(getConfigValue(cfg, "cfg", "class_max_switches", 1)));

                // Swap anti-exploit
                ui.set("#CfgSwapConsumeEnabledValue.Text",       boolText(getConfigValue(cfg, "cfg", "swap_anti_exploit.consume_at_level_enabled", true)));
                ui.set("#CfgSwapConsumeThresholdInput.Value",    toDouble(getConfigValue(cfg, "cfg", "swap_anti_exploit.consume_at_level_threshold", 20)));

                // Language
                ui.set("#CfgLocaleValue.Text",                   String.valueOf(cfg.get("language.locale", "en_US")));
                ui.set("#CfgFallbackLocaleValue.Text",           String.valueOf(cfg.get("language.fallback_locale", "en_US")));

                // Builtin content
                ui.set("#CfgEnableBuiltinRacesValue.Text",       boolText(getConfigValue(cfg, "cfg", "enable_builtin_races", true)));
                ui.set("#CfgEnableBuiltinClassesValue.Text",     boolText(getConfigValue(cfg, "cfg", "enable_builtin_classes", true)));
                ui.set("#CfgEnableBuiltinAugmentsValue.Text",    boolText(getConfigValue(cfg, "cfg", "enable_builtin_augments", true)));

                // Debug
                ui.set("#CfgEnableLoggingValue.Text",            boolText(getConfigValue(cfg, "cfg", "enable_logging", false)));

                // ---- Leveling.yml tab ----
                ConfigManager lvl = plugin.getMobLevelingManager().getLevelingConfig();
                ui.set("#LvlExpressionValue.Text",          String.valueOf(lvl.get("default.expression", "base * ((log(level)+1) * (sqrt(level)))^1.8")));
                ui.set("#LvlLogModeValue.Text",             String.valueOf(getConfigValue(lvl, "lvl", "default.log_mode", "LOG10")));
                ui.set("#LvlBaseInput.Value",               toDouble(getConfigValue(lvl, "lvl", "default.base", 100)));
                ui.set("#LvlBaseSkillPointsInput.Value",    toDouble(getConfigValue(lvl, "lvl", "baseSkillPoints", 12)));
                ui.set("#LvlSkillPointsPerLevelInput.Value", toDouble(getConfigValue(lvl, "lvl", "skillPointsPerLevel", 5)));
                ui.set("#LvlPlayerLevelCapInput.Value",     toDouble(getConfigValue(lvl, "lvl", "player_level_cap", 100)));

                // Party XP
                ui.set("#LvlPartyXpEnabledValue.Text",     boolText(getConfigValue(lvl, "lvl", "party_xp_share.enabled", true)));
                ui.set("#LvlPartyXpRangeInput.Value",      toDouble(getConfigValue(lvl, "lvl", "party_xp_share.range", 25)));
                ui.set("#LvlPartyXpSharePercentInput.Value", toDouble(getConfigValue(lvl, "lvl", "party_xp_share.member_share_percent", 100)));

                // Prestige
                ui.set("#LvlPrestigeEnabledValue.Text",        boolText(getConfigValue(lvl, "lvl", "prestige.enabled", true)));
                ui.set("#LvlPrestigeLevelCapValue.Text",       String.valueOf(lvl.get("prestige.prestige_level_cap", "ENDLESS")));
                ui.set("#LvlPrestigeLevelCapIncInput.Value",   toDouble(getConfigValue(lvl, "lvl", "prestige.level_cap_increase_per_prestige", 10)));
                ui.set("#LvlPrestigeXpIncInput.Value",         toDouble(getConfigValue(lvl, "lvl", "prestige.base_xp_increase_per_prestige", 20)));

                // Mob leveling
                ui.set("#LvlMobLevelModeValue.Text",       String.valueOf(getConfigValue(lvl, "lvl", "Mob_Leveling.Level_Source.Mode", "FIXED")));
                ui.set("#LvlMobXpEnabledValue.Text",       boolText(getConfigValue(lvl, "lvl", "Mob_Leveling.Experience.Enabled", true)));
                ui.set("#LvlMobXpScalingModeValue.Text",   String.valueOf(getConfigValue(lvl, "lvl", "Mob_Leveling.Experience.Scaling.Mode", "LINEAR")));
                ui.set("#LvlGlobalXpMultiplierInput.Value", toDouble(getConfigValue(lvl, "lvl", "Mob_Leveling.Experience.Global_XP_Multiplier", 1.0)));

                // Nameplates
                ui.set("#LvlNameplateEnabledValue.Text",        boolText(getConfigValue(lvl, "lvl", "Mob_Leveling.Nameplate.Enabled", true)));
                ui.set("#LvlNameplateShowLevelValue.Text",      boolText(getConfigValue(lvl, "lvl", "Mob_Leveling.Nameplate.Show_Level", true)));
                ui.set("#LvlNameplateShowNameValue.Text",       boolText(getConfigValue(lvl, "lvl", "Mob_Leveling.Nameplate.Show_Name", true)));
                ui.set("#LvlNameplateShowHealthValue.Text",     boolText(getConfigValue(lvl, "lvl", "Mob_Leveling.Nameplate.Show_Health", true)));
                ui.set("#LvlPlayerNameplateEnabledValue.Text",  boolText(getConfigValue(lvl, "lvl", "Mob_Leveling.Nameplate.Player_Nameplate_Enabled", false)));

                // ---- Events.yml tab ----
                ConfigManager evt = plugin.getEventHookManager().getEventsConfig();
                ui.set("#EvtPrestigeEnabledValue.Text",         boolText(getConfigValue(evt, "evt", "events.prestige_level_up.enabled", true)));
                ui.set("#EvtPrestigeAsPlayerValue.Text",        boolText(getConfigValue(evt, "evt", "events.prestige_level_up.as_player", false)));
                ui.set("#EvtLevelUpEnabledValue.Text",          boolText(getConfigValue(evt, "evt", "events.player_level_up.enabled", true)));
                ui.set("#EvtLevelUpAsPlayerValue.Text",         boolText(getConfigValue(evt, "evt", "events.player_level_up.as_player", false)));
                ui.set("#EvtLevelUpRepeatPrestigesValue.Text",  boolText(getConfigValue(evt, "evt", "events.player_level_up.repeat_access_prestiges", true)));

                // ---- Weapons.json tab is read-only; static labels defined in .ui file ----
        }

        // ------------------------------------------------------------------
        //  World-settings dynamic tabs
        // ------------------------------------------------------------------

        @SuppressWarnings("unchecked")
        private void buildWorldSettingsTabs(@Nonnull UICommandBuilder ui,
                        @Nonnull UIEventBuilder events) {
                var mobMgr = EndlessLeveling.getInstance().getMobLevelingManager();
                wsFileNames = mobMgr.getWorldSettingsFileNames();
                if (wsFileNames.isEmpty()) {
                        ui.set("#WsCurrentFileName.Text", "No files found");
                        ui.set("#WsFilePosition.Text", "0 / 0");
                        return;
                }

                // Default to first file if not set or invalid
                if (activeWsFile == null || !wsFileNames.contains(activeWsFile)) {
                        activeWsFile = wsFileNames.get(0);
                }

                // Bind prev/next file navigation
                events.addEventBinding(Activating, "#WsPrevFile", of("Action", "ws:prevFile"), false);
                events.addEventBinding(Activating, "#WsNextFile", of("Action", "ws:nextFile"), false);

                // Update file nav bar labels
                int fileIdx = wsFileNames.indexOf(activeWsFile) + 1;
                ui.set("#WsCurrentFileName.Text", activeWsFile);
                ui.set("#WsFilePosition.Text", fileIdx + " / " + wsFileNames.size());

                // Build content for the active file
                Map<String, Object> content = mobMgr.readWorldSettingsFile(activeWsFile);
                if (content.isEmpty()) {
                        ui.appendInline("#WsFileContent", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
                        ui.append("#WsFileContent[0]", "Pages/Settings/WsRows/WsValueRow.ui");
                        ui.set("#WsFileContent[0][0] #WsRowKey.Text", "empty");
                        ui.set("#WsFileContent[0][0] #WsRowVal.Text", "File is empty or could not be read.");
                        return;
                }

                buildJsonContent(ui, content, activeWsFile, 0, 0);
        }

        @SuppressWarnings("unchecked")
        private int buildJsonContent(@Nonnull UICommandBuilder ui,
                        @Nonnull Map<String, Object> map,
                        @Nonnull String filename,
                        int depth,
                        int rowIdx) {
                String[] sectionColors = { "#f0cf78", "#6fe3ff", "#d7baff", "#9b7edb", "#ff7878", "#7dd87d" };
                String sectionColor = sectionColors[depth % sectionColors.length];

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();

                        if (value instanceof Map<?, ?> nested) {
                                // Section row — bare Group slot + template
                                ui.appendInline("#WsFileContent", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
                                ui.append("#WsFileContent[" + rowIdx + "]", "Pages/Settings/WsRows/WsSectionRow.ui");
                                String base = "#WsFileContent[" + rowIdx + "][0]";
                                ui.set(base + " #WsRowKey.Text", key);
                                ui.set(base + " #WsSectionBar.Background", sectionColor);
                                ui.set(base + " #WsRowKey.Style.TextColor", sectionColor);
                                rowIdx++;

                                rowIdx = buildJsonContent(ui, (Map<String, Object>) nested, filename, depth + 1, rowIdx);

                        } else {
                                // Value row — bare Group slot + template
                                String displayVal;
                                String valColor;
                                if (value instanceof Boolean boolVal) {
                                        displayVal = String.valueOf(boolVal);
                                        valColor = boolVal ? "#7dd87d" : "#ff7878";
                                } else if (value instanceof List<?> listVal) {
                                        StringBuilder sb = new StringBuilder();
                                        for (int i = 0; i < listVal.size(); i++) {
                                                if (i > 0) sb.append(", ");
                                                sb.append(String.valueOf(listVal.get(i)));
                                                if (sb.length() > 200) { sb.append(" ..."); break; }
                                        }
                                        displayVal = "[" + sb + "]";
                                        valColor = "#9ab0ca";
                                } else {
                                        displayVal = String.valueOf(value);
                                        valColor = value instanceof Number ? "#6fe3ff" : "#f0cf78";
                                }

                                ui.appendInline("#WsFileContent", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
                                ui.append("#WsFileContent[" + rowIdx + "]", "Pages/Settings/WsRows/WsValueRow.ui");
                                String base = "#WsFileContent[" + rowIdx + "][0]";
                                ui.set(base + " #WsRowKey.Text", key);
                                ui.set(base + " #WsRowVal.Text", displayVal);
                                ui.set(base + " #WsRowVal.Style.TextColor", valColor);
                                rowIdx++;

                                // 3px gap between value rows — matches config tab spacing
                                ui.appendInline("#WsFileContent", "Group { Anchor: (Height: 3); }");
                                rowIdx++;
                        }
                }
                return rowIdx;
        }

        // ------------------------------------------------------------------
        //  Config action handler
        // ------------------------------------------------------------------

        private void handleConfigAction(@Nonnull String action,
                        @Nonnull SkillsUIPage.Data data,
                        @Nonnull Ref<EntityStore> ref,
                        @Nonnull Store<EntityStore> store) {
                if (!OperatorHelper.hasAdministrativeAccess(playerRef)) {
                        PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                        if (player != null) {
                                player.sendMessage(Message.raw("You do not have permission to modify config settings.").color("#ff6666"));
                        }
                        return;
                }
                var plugin = EndlessLeveling.getInstance();
                // Parse action: "cfg:t:path", "lvl:n:path", "evt:c:path" etc.
                String[] parts = action.split(":", 3);
                if (parts.length < 3) return;

                String filePrefix = parts[0]; // cfg, lvl, evt
                String type = parts[1];       // t (toggle), c (cycle), n (number)
                String path = parts[2];       // config path

                ConfigManager mgr;
                switch (filePrefix) {
                        case "cfg" -> mgr = plugin.getConfigManager();
                        case "lvl" -> mgr = plugin.getMobLevelingManager().getLevelingConfig();
                        case "evt" -> mgr = plugin.getEventHookManager().getEventsConfig();
                        default -> { return; }
                }

                Map<String, Object> pending = pendingChanges.computeIfAbsent(filePrefix, k -> new HashMap<>());

                switch (type) {
                        case "t" -> { // Toggle boolean
                                boolean current = toBool(getConfigValue(mgr, filePrefix, path, false));
                                pending.put(path, !current);
                        }
                        case "c" -> { // Cycle through values
                                String current = String.valueOf(getConfigValue(mgr, filePrefix, path, ""));
                                String[] cycle = getCycleValues(path);
                                if (cycle != null) {
                                        pending.put(path, cycleNext(current, cycle));
                                }
                        }
                        case "n" -> { // Number field
                                if (data.configValue == null) return;
                                double val = data.configValue;
                                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                                        pending.put(path, (int) val);
                                } else {
                                        pending.put(path, val);
                                }
                        }
                        default -> { return; }
                }

                sendValuesUpdate();
        }

        /**
         * Sends a partial UI update that only refreshes config/player values
         * without clearing/rebuilding the page, preserving scroll position.
         */
        private void sendValuesUpdate() {
                UICommandBuilder ui = new UICommandBuilder();
                populateConfigSettings(ui);

                // Also refresh player toggle values
                PlayerData data = EndlessLeveling.getInstance()
                                .getPlayerDataManager()
                                .get(playerRef.getUuid());
                if (data != null) {
                        var raceManager = EndlessLeveling.getInstance().getRaceManager();
                        boolean raceModelsDisabled = raceManager != null && raceManager.isRaceModelGloballyDisabled();
                        ui.set("#PlayerHudValue.Text", data.isPlayerHudEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#CriticalNotifValue.Text", data.isCriticalNotifEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#XpNotifValue.Text", data.isXpNotifEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#PassiveLevelUpNotifValue.Text", data.isPassiveLevelUpNotifEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#LuckDoubleDropsNotifValue.Text", data.isLuckDoubleDropsNotifEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#HealthRegenNotifValue.Text", data.isHealthRegenNotifEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#AugmentNotifValue.Text", data.isAugmentNotifEnabled()
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                        : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                        ui.set("#RaceModelValue.Text", raceModelsDisabled
                                        ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.disabled", "DISABLED")
                                        : data.isUseRaceModel()
                                                ? Lang.tr(playerRef.getUuid(), "ui.common.toggle.on", "ON")
                                                : Lang.tr(playerRef.getUuid(), "ui.common.toggle.off", "OFF"));
                }

                sendUpdate(ui);
        }

        // ------------------------------------------------------------------
        //  Pending changes helpers
        // ------------------------------------------------------------------

        /**
         * Returns the effective value for a config path: pending value if one
         * exists, otherwise the live value from the ConfigManager.
         */
        private Object getConfigValue(ConfigManager mgr, String prefix, String path, Object fallback) {
                Map<String, Object> pending = pendingChanges.get(prefix);
                if (pending != null && pending.containsKey(path)) {
                        return pending.get(path);
                }
                return mgr.get(path, fallback);
        }

        /**
         * Writes all pending config changes to their respective ConfigManagers
         * and saves each modified file to disk.
         */
        private void flushPendingChanges() {
                if (pendingChanges.isEmpty()) return;
                var plugin = EndlessLeveling.getInstance();
                int count = 0;

                for (var entry : pendingChanges.entrySet()) {
                        String prefix = entry.getKey();
                        Map<String, Object> changes = entry.getValue();
                        if (changes.isEmpty()) continue;

                        ConfigManager mgr = switch (prefix) {
                                case "cfg" -> plugin.getConfigManager();
                                case "lvl" -> plugin.getMobLevelingManager().getLevelingConfig();
                                case "evt" -> plugin.getEventHookManager().getEventsConfig();
                                default -> null;
                        };
                        if (mgr == null) continue;

                        for (var change : changes.entrySet()) {
                                mgr.set(change.getKey(), change.getValue());
                                count++;
                        }
                        mgr.save();
                }

                pendingChanges.clear();

                PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                if (player != null) {
                        player.sendMessage(Message.raw(
                                        String.format("Saved %d config change(s) to disk.", count))
                                        .color("#7dd87d"));
                }
        }

        // ------------------------------------------------------------------
        //  Utility helpers
        // ------------------------------------------------------------------

        private String[] getCycleValues(String path) {
                return switch (path) {
                        case "global_race_visuals_setting" -> RACE_VISUALS_CYCLE;
                        case "default.log_mode" -> LOG_MODE_CYCLE;
                        case "Mob_Leveling.Level_Source.Mode" -> MOB_LEVEL_MODE_CYCLE;
                        case "Mob_Leveling.Experience.Scaling.Mode" -> XP_SCALING_MODE_CYCLE;
                        default -> null;
                };
        }

        private static String cycleNext(String current, String[] options) {
                for (int i = 0; i < options.length; i++) {
                        if (options[i].equalsIgnoreCase(current)) {
                                return options[(i + 1) % options.length];
                        }
                }
                return options[0];
        }

        private static boolean toBool(Object value) {
                if (value instanceof Boolean b) return b;
                if (value instanceof Number n)  return n.intValue() != 0;
                if (value instanceof String s)  return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
                return false;
        }

        private static String boolText(Object value) {
                return toBool(value) ? "true" : "false";
        }

        private static double toDouble(Object value) {
                if (value instanceof Number n) return n.doubleValue();
                if (value instanceof String s) {
                        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
                }
                return 0;
        }

        private void notifyConfigChanged(String path, Object newValue) {
                PlayerRef player = Universe.get().getPlayer(playerRef.getUuid());
                if (player != null) {
                        player.sendMessage(Message.raw(
                                        String.format("Config updated: %s = %s", path, newValue))
                                        .color("#7dd87d"));
                }
        }

        /** Converts a filename + path into a safe UI element ID (alphanumeric + underscores). */
        private static String safeElementId(String raw) {
                return raw.replaceAll("[^a-zA-Z0-9]", "_");
        }

        /** Escapes special characters for inline .ui string literals. */
        private static String escUi(String text) {
                if (text == null) return "";
                return text.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ");
        }
}
