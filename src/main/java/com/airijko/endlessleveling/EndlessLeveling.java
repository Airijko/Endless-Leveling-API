/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling;

import com.airijko.endlessleveling.analytics.HStats;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.api.gates.InstanceDungeonDefinition;
import com.airijko.endlessleveling.classes.ClassWeaponResolver;
import com.airijko.endlessleveling.classes.WeaponConfig;
import com.airijko.endlessleveling.augments.AugmentExecutor;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.augments.AugmentSyncValidator;
import com.airijko.endlessleveling.augments.MobAugmentExecutor;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.commands.CommandRegistrar;
import com.airijko.endlessleveling.commands.classes.ChurchManager;
import com.airijko.endlessleveling.listeners.OpenPlayerHudListener;
import com.airijko.endlessleveling.listeners.PartyListener;
import com.airijko.endlessleveling.listeners.PlayerDataListener;
import com.airijko.endlessleveling.listeners.DungeonTierJoinNotificationListener;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.managers.EventHookManager;
import com.airijko.endlessleveling.managers.LanguageManager;
import com.airijko.endlessleveling.managers.LoggingManager;
import com.airijko.endlessleveling.managers.PluginFilesManager;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.PlayerAttributeManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.passives.PassiveManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveManager;
import com.airijko.endlessleveling.security.PartnerBrandingAllowlist;
import com.airijko.endlessleveling.security.UiTitleIntegrityGuard;
import com.airijko.endlessleveling.shutdown.EndlessLevelingShutdownCoordinator;
import com.airijko.endlessleveling.systems.BreakBlockEntitySystem;
import com.airijko.endlessleveling.systems.ArmyOfTheDeadDeathSystem;
import com.airijko.endlessleveling.systems.PlayerDeathXpPenaltySystem;
import com.airijko.endlessleveling.systems.OutlanderBridgePlayerDeathSystem;
import com.airijko.endlessleveling.systems.ArmyOfTheDeadCleanupSystem;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeRewardCooldowns;
import com.airijko.endlessleveling.drops.MobDropTaggingSystem;
import com.airijko.endlessleveling.systems.PassiveRegenSystem;
import com.airijko.endlessleveling.mob.MobDamageScalingSystem;
import com.airijko.endlessleveling.mob.MobLevelingSystem;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.airijko.endlessleveling.drops.LuckDoubleDropSystem;
import com.airijko.endlessleveling.systems.PlayerCombatPostApplyProbeSystem;
import com.airijko.endlessleveling.systems.PlayerCombatSystem;
import com.airijko.endlessleveling.systems.PlayerDefenseSystem;
import com.airijko.endlessleveling.systems.MovementHasteSystem;
import com.airijko.endlessleveling.systems.PlayerNameplateSystem;
import com.airijko.endlessleveling.systems.PlayerRaceStatSystem;
import com.airijko.endlessleveling.systems.PeriodicSkillModifierSystem;
import com.airijko.endlessleveling.systems.SwiftnessKillSystem;
import com.airijko.endlessleveling.systems.HudRefreshSystem;
import com.airijko.endlessleveling.systems.OutlanderBridgeWaveHudRefreshSystem;
import com.airijko.endlessleveling.systems.UiIntegrityAlertSystem;
import com.airijko.endlessleveling.systems.WitherEffectSystem;
import com.airijko.endlessleveling.leveling.XpEventSystem;
import com.airijko.endlessleveling.util.FixedValue;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Locale;
import java.util.Optional;

public class EndlessLeveling extends JavaPlugin {

    private static EndlessLeveling INSTANCE;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    public static final String DEFAULT_BRAND_NAME = "Endless Leveling";
    public static final String DEFAULT_COMMAND_PREFIX = "/lvl";
    public static final String DEFAULT_MESSAGE_PREFIX = "[EndlessLeveling] ";
    private static final String HSTATS_MOD_UUID = "70cf8395-17a1-4d0e-8165-1bb208e2c1f3";
    private static final String HSTATS_MOD_VERSION = "7.2.0";
    private static final String PARTNER_ADDON_MAIN_CLASS = "com.airijko.endlessleveling.EndlessLevelingPartnerAddon";
    private static final String ARANK_ADDON_MAIN_CLASS = "com.airijko.endlessleveling.EndlessLevelingARankAddon";

    // ------------------------
    // Shared managers (singleton)
    // ------------------------
    private PluginFilesManager filesManager;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private PlayerDataManager playerDataManager;
    private LevelingManager levelingManager;
    private MobLevelingManager mobLevelingManager;
    private SkillManager skillManager;
    private PassiveManager passiveManager;
    private EventHookManager eventHookManager;
    private PartyManager partyManager;
    private RaceManager raceManager;
    private ClassManager classManager;
    private ArchetypePassiveManager archetypePassiveManager;
    private PlayerAttributeManager playerAttributeManager;
    private PlayerRaceStatSystem playerRaceStatSystem;
    private MovementHasteSystem movementHasteSystem;
    private MobLevelingSystem mobLevelingSystem;
    private PlayerNameplateSystem playerNameplateSystem;
    private AugmentManager augmentManager;
    private AugmentRuntimeManager augmentRuntimeManager;
    private AugmentUnlockManager augmentUnlockManager;
    private AugmentSyncValidator augmentSyncValidator;
    private AugmentExecutor augmentExecutor;
    private MobAugmentExecutor mobAugmentExecutor;
    private UiTitleIntegrityGuard uiTitleIntegrityGuard;
    private UiIntegrityAlertSystem uiIntegrityAlertSystem;
    private EndlessLevelingShutdownCoordinator shutdownCoordinator;
    private com.airijko.endlessleveling.xpstats.XpStatsManager xpStatsManager;
    private com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService xpStatsLeaderboardService;
    private volatile String brandName = DEFAULT_BRAND_NAME;
    private volatile String commandPrefix = DEFAULT_COMMAND_PREFIX;
    private volatile String messagePrefix = DEFAULT_MESSAGE_PREFIX;
    private volatile String navHeaderOverride;
    private volatile String navSubHeaderOverride;
    private volatile boolean partnerAddonAuthorized;
    private volatile boolean warnedUnauthorizedBrandingOverride;

    // Getter for SkillManager
    public SkillManager getSkillManager() {
        return skillManager;
    }

    // Getter for PlayerDataManager
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public LevelingManager getLevelingManager() {
        return levelingManager;
    }

    public MobLevelingManager getMobLevelingManager() {
        return mobLevelingManager;
    }

    public PassiveManager getPassiveManager() {
        return passiveManager;
    }

    public EventHookManager getEventHookManager() {
        return eventHookManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PluginFilesManager getFilesManager() {
        return filesManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public RaceManager getRaceManager() {
        return raceManager;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public PlayerAttributeManager getPlayerAttributeManager() {
        return playerAttributeManager;
    }

    public PlayerRaceStatSystem getPlayerRaceStatSystem() {
        return playerRaceStatSystem;
    }

    public MovementHasteSystem getMovementHasteSystem() {
        return movementHasteSystem;
    }

    public MobLevelingSystem getMobLevelingSystem() {
        return mobLevelingSystem;
    }

    public PlayerNameplateSystem getPlayerNameplateSystem() {
        return playerNameplateSystem;
    }

    public AugmentRuntimeManager getAugmentRuntimeManager() {
        return augmentRuntimeManager;
    }

    public AugmentExecutor getAugmentExecutor() {
        return augmentExecutor;
    }

    public MobAugmentExecutor getMobAugmentExecutor() {
        return mobAugmentExecutor;
    }

    public UiIntegrityAlertSystem getUiIntegrityAlertSystem() {
        return uiIntegrityAlertSystem;
    }

    public UiTitleIntegrityGuard getUiTitleIntegrityGuard() {
        return uiTitleIntegrityGuard;
    }

    public AugmentManager getAugmentManager() {
        return augmentManager;
    }

    public AugmentUnlockManager getAugmentUnlockManager() {
        return augmentUnlockManager;
    }

    public AugmentSyncValidator getAugmentSyncValidator() {
        return augmentSyncValidator;
    }

    public com.airijko.endlessleveling.xpstats.XpStatsManager getXpStatsManager() {
        return xpStatsManager;
    }

    public com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService getXpStatsLeaderboardService() {
        return xpStatsLeaderboardService;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getCommandPrefix() {
        return commandPrefix;
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public String getNavHeaderOverride() {
        return navHeaderOverride;
    }

    public String getNavSubHeaderOverride() {
        return navSubHeaderOverride;
    }

    public boolean isPartnerAddonAuthorized() {
        return partnerAddonAuthorized;
    }

    synchronized void applyPartnerBrandingInternal(String requestedBrandName,
            String requestedCommandPrefix,
            String requestedMessagePrefix,
            String declaredServerHostsCsv,
            String requestedNavHeaderOverride,
            String requestedNavSubHeaderOverride) {
        String normalizedBrandName = normalizeBrandName(requestedBrandName);
        String normalizedCommandPrefix = normalizeCommandPrefix(requestedCommandPrefix);
        String normalizedMessagePrefix = normalizeMessagePrefix(requestedMessagePrefix);
        String normalizedNavHeaderOverride = normalizeOptionalNavText(requestedNavHeaderOverride);
        String normalizedNavSubHeaderOverride = normalizeOptionalNavText(requestedNavSubHeaderOverride);
        var declaredServerHosts = PartnerBrandingAllowlist.parseDeclaredHostsCsv(declaredServerHostsCsv);
        boolean premiumAddonAvailable = isPremiumPartnerAddonAvailable();
        boolean partnerDomainAuthorized = PartnerBrandingAllowlist.hasAuthorizedHost(declaredServerHosts);
        partnerAddonAuthorized = premiumAddonAvailable && partnerDomainAuthorized;

        // Always update authorization status on guard and alert system
        // This ensures that invalid domains immediately disable authorization skipping
        if (uiTitleIntegrityGuard != null) {
            uiTitleIntegrityGuard.setAuthorizedPartner(partnerAddonAuthorized);
        }
        if (uiIntegrityAlertSystem != null) {
            uiIntegrityAlertSystem.setAuthorizedPartner(partnerAddonAuthorized);
        }

        boolean overrideRequest = !DEFAULT_BRAND_NAME.equals(normalizedBrandName)
                || !DEFAULT_COMMAND_PREFIX.equals(normalizedCommandPrefix)
                || !DEFAULT_MESSAGE_PREFIX.equals(normalizedMessagePrefix);
        if (overrideRequest && !premiumAddonAvailable) {
            if (uiTitleIntegrityGuard != null) {
                // Trigger integrity alerts for unauthorized override attempts.
                uiTitleIntegrityGuard.updateBranding(normalizedBrandName, normalizedMessagePrefix);
            }
            if (!warnedUnauthorizedBrandingOverride) {
                warnedUnauthorizedBrandingOverride = true;
                LOGGER.atWarning().log(
                        "Rejected branding override because EL-Partner-Addon is not available. Keeping core branding defaults.");
            }
            return;
        }
        if (overrideRequest && !partnerDomainAuthorized) {
            if (uiTitleIntegrityGuard != null) {
                // Trigger integrity alerts for unauthorized override attempts.
                uiTitleIntegrityGuard.updateBranding(normalizedBrandName, normalizedMessagePrefix);
            }
            if (!warnedUnauthorizedBrandingOverride) {
                warnedUnauthorizedBrandingOverride = true;
                LOGGER.atWarning().log(
                        "Rejected branding override because declared partner server host is not in core allowlist.");
            }
            return;
        }

        brandName = normalizedBrandName;
        commandPrefix = normalizedCommandPrefix;
        messagePrefix = normalizedMessagePrefix;
        navHeaderOverride = normalizedNavHeaderOverride;
        navSubHeaderOverride = normalizedNavSubHeaderOverride;

        FixedValue.ROOT_COMMAND.setValue(normalizedCommandPrefix);
        FixedValue.CHAT_PREFIX.setValue(normalizedMessagePrefix);
        if (uiTitleIntegrityGuard != null) {
            // Core UI resources keep Endless Leveling branding and partner UI is bypassed,
            // so the guard should validate core branding against the core default.
            uiTitleIntegrityGuard.updateBranding(DEFAULT_BRAND_NAME, normalizedMessagePrefix);
        }
    }

    private boolean isPremiumPartnerAddonAvailable() {
        return isKnownPartnerAddonPresent(PARTNER_ADDON_MAIN_CLASS)
                || isKnownPartnerAddonPresent(ARANK_ADDON_MAIN_CLASS);
    }

    private boolean isKnownPartnerAddonPresent(String addonMainClass) {
        try {
            Class<?> addonClass = Class.forName(addonMainClass);
            CodeSource codeSource = addonClass.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return true;
            }

            String location = codeSource.getLocation().toString().toLowerCase(Locale.ROOT);
            return location.contains("endlesslevelingpartneraddon")
                    || location.contains("endlesslevelingarankaddon")
                    || location.contains("endlessdungeonsandgates")
                    || location.contains("el-partner-addon");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String normalizeBrandName(String requestedBrandName) {
        if (requestedBrandName == null) {
            return DEFAULT_BRAND_NAME;
        }
        String normalized = requestedBrandName.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 48) {
            return DEFAULT_BRAND_NAME;
        }
        return normalized;
    }

    private String normalizeCommandPrefix(String requestedCommandPrefix) {
        if (requestedCommandPrefix == null) {
            return DEFAULT_COMMAND_PREFIX;
        }
        String normalized = requestedCommandPrefix.trim();
        if (normalized.isBlank()) {
            return DEFAULT_COMMAND_PREFIX;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.matches("/[A-Za-z][A-Za-z0-9_-]{0,31}")) {
            return DEFAULT_COMMAND_PREFIX;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeMessagePrefix(String requestedMessagePrefix) {
        if (requestedMessagePrefix == null) {
            return DEFAULT_MESSAGE_PREFIX;
        }
        String normalized = requestedMessagePrefix.trim();
        if (normalized.isBlank() || normalized.length() > 64) {
            return DEFAULT_MESSAGE_PREFIX;
        }
        if (!normalized.endsWith(" ")) {
            normalized = normalized + " ";
        }
        return normalized;
    }

    private String normalizeOptionalNavText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48).trim();
        }
        return normalized;
    }

    public ArchetypePassiveManager getArchetypePassiveManager() {
        return archetypePassiveManager;
    }

    /** Singleton access to the mod instance */
    public static EndlessLeveling getInstance() {
        return INSTANCE;
    }

    public EndlessLeveling(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    protected void setup() {
        // Initialize all folders and managers
        filesManager = new PluginFilesManager(this);
        configManager = new ConfigManager(filesManager, filesManager.getConfigFile());
        filesManager.syncBuiltinWorldSettingsIfNeeded(configManager);
        languageManager = new LanguageManager(filesManager, configManager);

        // Load weapon ID and keyword overrides before systems start.
        ClassWeaponResolver.configure(WeaponConfig.load(filesManager.getWeaponsFile()));

        LoggingManager.configureFromConfig(configManager);
        new HStats(HSTATS_MOD_UUID, resolvePluginManifestVersion());

        raceManager = new RaceManager(configManager, filesManager);
        classManager = new ClassManager(configManager, filesManager);
        archetypePassiveManager = new ArchetypePassiveManager(raceManager, classManager);
        playerAttributeManager = new PlayerAttributeManager(raceManager);
        passiveManager = new PassiveManager(configManager);
        eventHookManager = new EventHookManager(new ConfigManager(filesManager, filesManager.getEventsFile()));
        augmentManager = new AugmentManager(filesManager.getAugmentsFolder().toPath(), filesManager, configManager);
        augmentManager.load();
        augmentRuntimeManager = new AugmentRuntimeManager();
        mobAugmentExecutor = new MobAugmentExecutor();
        uiTitleIntegrityGuard = new UiTitleIntegrityGuard();
        applyPartnerBrandingInternal(DEFAULT_BRAND_NAME, DEFAULT_COMMAND_PREFIX, DEFAULT_MESSAGE_PREFIX, null,
                null, null);
        skillManager = new SkillManager(filesManager,
                classManager,
                playerAttributeManager,
                archetypePassiveManager,
                passiveManager,
                augmentRuntimeManager);
        augmentExecutor = new AugmentExecutor(augmentManager, augmentRuntimeManager, skillManager);
        playerDataManager = new PlayerDataManager(filesManager, configManager, skillManager, raceManager,
                classManager);
        ConfigManager levelingConfigManager = new ConfigManager(filesManager, filesManager.getLevelingFile());
        augmentUnlockManager = new AugmentUnlockManager(configManager, levelingConfigManager, augmentManager,
                playerDataManager,
                classManager,
                skillManager,
                archetypePassiveManager);
        augmentSyncValidator = new AugmentSyncValidator(playerDataManager, augmentUnlockManager);
        xpStatsManager = new com.airijko.endlessleveling.xpstats.XpStatsManager(filesManager);
        levelingManager = new LevelingManager(playerDataManager, filesManager, skillManager, archetypePassiveManager,
                passiveManager, augmentUnlockManager, eventHookManager);
        mobLevelingManager = new MobLevelingManager(filesManager, playerDataManager);
        partyManager = new PartyManager(playerDataManager, levelingManager);
        if (!partyManager.isAvailable()) {
            LOGGER.atWarning().log("PartyPro not detected; party features will stay disabled.");
        }

        // Register event listeners
        PlayerDataListener playerDataListener = new PlayerDataListener(playerDataManager, passiveManager, skillManager,
                raceManager, augmentUnlockManager);
        DungeonTierJoinNotificationListener dungeonTierJoinNotificationListener = new DungeonTierJoinNotificationListener();
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, playerDataListener::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class,
                dungeonTierJoinNotificationListener::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, playerDataListener::onPlayerDisconnect);

        if (partyManager.isAvailable()) {
            PartyListener partyListener = new PartyListener(partyManager);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, partyListener::onPlayerDisconnect);
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, OpenPlayerHudListener::openGui);

        // Outlander Bridge wave HUD: unregister before entity removal
        this.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, event -> {
            com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> holder = event.getHolder();
            if (holder == null) return;
            com.hypixel.hytale.server.core.universe.PlayerRef pr = holder.getComponent(
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (pr == null) return;
            com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager.get().onPlayerDrain(pr.getUuid());
        });

        // Outlander Bridge banking: failsafe drain on disconnect. Protects
        // against XP-loss when a player disconnects mid-session (or crashes)
        // and DrainPlayerFromWorldEvent does not fire before reconnect.
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            com.hypixel.hytale.server.core.universe.PlayerRef pr = event.getPlayerRef();
            if (pr == null) return;
            com.airijko.endlessleveling.mob.outlander.OutlanderBridgeXpBank.get()
                    .onPlayerDrain(pr.getUuid());
        });

        LuckDoubleDropSystem luckDoubleDropSystem = new LuckDoubleDropSystem(playerDataManager, passiveManager);
        resolveInventoryChangeEventClass().ifPresentOrElse(eventClass -> {
            this.getEventRegistry().registerGlobal((Class) eventClass, luckDoubleDropSystem::onInventoryChangeCompat);
            LOGGER.atInfo().log("Registered inventory change listener using event class %s", eventClass.getName());
        }, () -> LOGGER.atWarning().log(
                "No supported inventory change event class was found; luck double-drop inventory listener disabled."));
        this.getEntityStoreRegistry().registerSystem(new BreakBlockEntitySystem(luckDoubleDropSystem));
        this.getEntityStoreRegistry().registerSystem(new com.airijko.endlessleveling.systems.OutlanderBridgeBlockDamageGuardSystem());
        this.getEntityStoreRegistry().registerSystem(new MobDropTaggingSystem(luckDoubleDropSystem));
        this.getEntityStoreRegistry()
                .registerSystem(new XpEventSystem(playerDataManager, levelingManager, partyManager, passiveManager,
                        mobLevelingManager, archetypePassiveManager, luckDoubleDropSystem));
        this.getEntityStoreRegistry()
                .registerSystem(new PlayerCombatSystem(playerDataManager, skillManager, passiveManager,
                        archetypePassiveManager, classManager, augmentExecutor, mobAugmentExecutor,
                        mobLevelingManager));
        this.getEntityStoreRegistry()
                .registerSystem(new PlayerCombatPostApplyProbeSystem(mobLevelingManager));
        this.getEntityStoreRegistry()
                .registerSystem(new SwiftnessKillSystem(playerDataManager, passiveManager, archetypePassiveManager,
                        skillManager, augmentExecutor));
        this.getEntityStoreRegistry().registerSystem(new ArmyOfTheDeadDeathSystem());
        this.getEntityStoreRegistry().registerSystem(new PlayerDeathXpPenaltySystem(levelingManager));
        this.getEntityStoreRegistry().registerSystem(new OutlanderBridgePlayerDeathSystem());
        this.getEntityStoreRegistry().registerSystem(new ArmyOfTheDeadCleanupSystem());
        this.getEntityStoreRegistry().registerSystem(new MobDamageScalingSystem(mobLevelingManager));
        movementHasteSystem = new MovementHasteSystem(playerDataManager, skillManager, augmentRuntimeManager);
        this.getEntityStoreRegistry().registerSystem(movementHasteSystem);
        this.getEntityStoreRegistry()
                .registerSystem(new PlayerDefenseSystem(playerDataManager, skillManager, passiveManager,
                        archetypePassiveManager, augmentExecutor, mobAugmentExecutor, mobLevelingManager,
                        movementHasteSystem));
        this.getEntityStoreRegistry()
                .registerSystem(new PassiveRegenSystem(playerDataManager, passiveManager, archetypePassiveManager,
                        skillManager, augmentRuntimeManager, augmentExecutor));
        // Register periodic skill modifier reapplication system
        this.getEntityStoreRegistry().registerSystem(new PeriodicSkillModifierSystem(playerDataManager, skillManager));
        playerRaceStatSystem = new PlayerRaceStatSystem(playerDataManager, skillManager);
        this.getEntityStoreRegistry().registerSystem(playerRaceStatSystem);
        playerNameplateSystem = new PlayerNameplateSystem(playerDataManager);
        this.getEntityStoreRegistry().registerSystem(playerNameplateSystem);
        mobLevelingSystem = new MobLevelingSystem();
        this.getEntityStoreRegistry().registerSystem(mobLevelingSystem);
        this.getEntityStoreRegistry().registerSystem(new HudRefreshSystem());
        this.getEntityStoreRegistry().registerSystem(OutlanderBridgeWaveManager.get());
        this.getEntityStoreRegistry().registerSystem(new OutlanderBridgeWaveHudRefreshSystem());
        uiIntegrityAlertSystem = new UiIntegrityAlertSystem(uiTitleIntegrityGuard);
        this.getEntityStoreRegistry().registerSystem(uiIntegrityAlertSystem);
        this.getEntityStoreRegistry().registerSystem(new WitherEffectSystem());
        this.getEntityStoreRegistry()
                .registerSystem(new com.airijko.endlessleveling.systems.XpStatsAutosaveSystem(xpStatsManager));
        shutdownCoordinator = new EndlessLevelingShutdownCoordinator(this);
        resolveRemoveWorldEventClass().ifPresentOrElse(eventClass -> {
            this.getEventRegistry().registerGlobal((Class) eventClass,
                    event -> shutdownCoordinator.runPreShutdownEntityCleanup("RemoveWorldEvent"));
            LOGGER.atInfo().log("Registered pre-shutdown cleanup listener using world event class %s",
                    eventClass.getName());
        }, () -> LOGGER.atWarning().log(
                "No supported remove-world event class found; cleanup will rely on ShutdownEvent/plugin shutdown paths."));
        resolveShutdownEventClass().ifPresentOrElse(eventClass -> {
            this.getEventRegistry().registerGlobal((Class) eventClass,
                    event -> shutdownCoordinator.runPreShutdownEntityCleanup("ShutdownEvent"));
            LOGGER.atInfo().log("Registered pre-shutdown cleanup listener using event class %s", eventClass.getName());
        }, () -> LOGGER.atWarning().log(
                "No supported shutdown event class found; relying on plugin shutdown() cleanup path."));

        // Register commands via helper class to keep main class clean.
        ChurchManager.init(filesManager.getPluginFolder().toPath());
        xpStatsLeaderboardService = new com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService(
                xpStatsManager, playerDataManager);
        String commandRoot = CommandRegistrar.registerCommands(
                this.getCommandRegistry(),
                this.getEventRegistry(),
                partyManager,
                raceManager,
                classManager,
                playerDataManager,
                augmentManager,
                xpStatsManager,
                xpStatsLeaderboardService);

        if (augmentSyncValidator != null) {
            augmentSyncValidator.auditAndNotify();
        }

        registerCoreInstanceDungeons();

        OutlanderBridgeWaveManager.get().load();
        OutlanderBridgeRewardCooldowns.init(filesManager.getPluginFolder().toPath());
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class,
                event -> OutlanderBridgeWaveManager.get().onPlayerEntered(event.getWorld(), event.getHolder()));
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            if (event == null || event.getPlayer() == null) return;
            java.util.UUID playerUuid = event.getPlayer().getUuid();
            if (playerUuid == null) return;
            com.hypixel.hytale.server.core.universe.Universe universe =
                    com.hypixel.hytale.server.core.universe.Universe.get();
            if (universe == null) return;
            com.hypixel.hytale.server.core.universe.PlayerRef pr = universe.getPlayer(playerUuid);
            if (pr == null || !pr.isValid()) return;
            OutlanderBridgeWaveManager.get().handlePlayerReady(pr, event.getPlayerRef());
        });

        LOGGER.atInfo().log("Plugin initialized! Plugin folder: %s",
                filesManager.getPluginFolder().getAbsolutePath());
    }

    private void registerCoreInstanceDungeons() {
        EndlessLevelingAPI api = EndlessLevelingAPI.get();
        InstanceDungeonDefinition outlanderBridge = new InstanceDungeonDefinition(
                "outlander-bridge",
                "Endless_Outlander_Bridge",
                "EL_Portal_Outlander_Bridge",
                "endless_outlander_bridge",
                "Outlander Bridge",
                "Endless_Outlander_Bridge",
                "Endless_Outlander_Bridge");
        api.registerInstanceDungeon(outlanderBridge, true);
    }

    protected void shutdown() {
        if (shutdownCoordinator == null) {
            shutdownCoordinator = new EndlessLevelingShutdownCoordinator(this);
        }
        shutdownCoordinator.handlePluginShutdown();
    }

    private String resolvePluginManifestVersion() {
        try (var in = EndlessLeveling.class.getClassLoader().getResourceAsStream("manifest.json")) {
            if (in == null) {
                return HSTATS_MOD_VERSION;
            }

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int keyIndex = json.indexOf("\"Version\"");
            if (keyIndex < 0) {
                return HSTATS_MOD_VERSION;
            }

            int colon = json.indexOf(':', keyIndex);
            int firstQuote = json.indexOf('"', colon + 1);
            int secondQuote = json.indexOf('"', firstQuote + 1);
            if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
                return HSTATS_MOD_VERSION;
            }

            String parsed = json.substring(firstQuote + 1, secondQuote).trim();
            return parsed.isEmpty() ? HSTATS_MOD_VERSION : parsed;
        } catch (Exception ignored) {
            return HSTATS_MOD_VERSION;
        }
    }

    private Optional<Class<?>> resolveInventoryChangeEventClass() {
        String[] candidates = {
                "com.hypixel.hytale.server.core.event.events.entity.InventoryChangeEvent",
                "com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent"
        };

        for (String className : candidates) {
            try {
                return Optional.of(Class.forName(className));
            } catch (ClassNotFoundException ignored) {
                // Try the next known event class name.
            }
        }

        return Optional.empty();
    }

    private Optional<Class<?>> resolveShutdownEventClass() {
        String[] candidates = {
                "com.hypixel.hytale.server.core.event.events.ShutdownEvent"
        };

        for (String className : candidates) {
            try {
                return Optional.of(Class.forName(className));
            } catch (ClassNotFoundException ignored) {
                // Try the next known shutdown event class name.
            }
        }

        return Optional.empty();
    }

    private Optional<Class<?>> resolveRemoveWorldEventClass() {
        String[] candidates = {
                "com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent"
        };

        for (String className : candidates) {
            try {
                return Optional.of(Class.forName(className));
            } catch (ClassNotFoundException ignored) {
                // Try the next known remove-world event class name.
            }
        }

        return Optional.empty();
    }

    public void appendShutlog(String message) {
        if (shutdownCoordinator == null) {
            return;
        }
        shutdownCoordinator.appendShutlog(message);
    }

}
