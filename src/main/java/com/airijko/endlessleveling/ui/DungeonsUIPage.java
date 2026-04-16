package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
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
    private static final String ENDGAME_MAIN_CLASS = "endgame.plugin.EndgameQoL";
    private static final String MAJOR_MAIN_CLASS = "com.major76.majordungeons.MajorDungeons";
    private static final String ENDGAME_DOWNLOAD_URL = "https://www.curseforge.com/hytale/mods/endgame-qol";
    private static final String MAJOR_DOWNLOAD_URL = "https://www.curseforge.com/hytale/mods/major-dungeons";

    private static final String OUTLANDER_BRIDGE_ID = "outlander-bridge";
    private static final String OUTLANDER_BRIDGE_DESCRIPTION =
            "A wave-based instance dungeon with escalating mob tiers. Clear waves to bank XP; claim rewards at the end to walk away with it. Ends 1-hour claim cooldown.";

    /** Which native dungeon the side panel currently shows. Null = panel closed/empty. */
    @Nullable
    private volatile String selectedNativeDungeonId;

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
        ModularCardUiAppender.appendFolder(ui,
            "#EndgameCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Endgame",
            "Pages/Dungeons/Cards/Endgame",
            3);
        ModularCardUiAppender.appendFolder(ui,
            "#MajorCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Major",
            "Pages/Dungeons/Cards/Major",
            3);

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
        ui.set("#EndgameDimOverlay.Visible", !endgameInstalled);
        ui.set("#MajorNotInstalledLabel.Visible", !majorInstalled);
        ui.set("#MajorInstallButton.Visible", !majorInstalled);
        ui.set("#MajorDimOverlay.Visible", !majorInstalled);

        // Native dungeon card — reward availability label.
        updateNativeCardStatus(ui);

        // Side panel starts with placeholder; reward section + buttons hidden until a dungeon is selected.
        resetSidePanel(ui);

        events.addEventBinding(Activating, "#EndgameInstallButton", of("Action", "dungeons:install:endgame"), false);
        events.addEventBinding(Activating, "#MajorInstallButton", of("Action", "dungeons:install:major"), false);

        events.addEventBinding(Activating, "#NativeCardOutlanderBridge",
                of("Action", "dungeon:native:select:" + OUTLANDER_BRIDGE_ID), false);
        events.addEventBinding(Activating, "#NativeCardOutlanderBridgeEnter",
                of("Action", "dungeon:native:quickenter:" + OUTLANDER_BRIDGE_ID), false);
        events.addEventBinding(Activating, "#SidePanelEnterButton",
                of("Action", "dungeon:native:enter"), false);
        events.addEventBinding(Activating, "#SidePanelConfirmProceedButton",
                of("Action", "dungeon:native:proceed"), false);
        events.addEventBinding(Activating, "#SidePanelConfirmCancelButton",
                of("Action", "dungeon:native:cancel-proceed"), false);

        NavUIHelper.applyNavVersion(ui, playerRef, "dungeons",
            "Common/UI/Custom/Pages/Dungeons/DungeonsPage.ui",
                "#DungeonsTitle");
        NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/Dungeons/DungeonsPage.ui");
    }

    private void resetSidePanel(@Nonnull UICommandBuilder ui) {
        ui.set("#SidePanelTitle.Text", "DUNGEON INFO");
        ui.set("#SidePanelTag.Text", "");
        ui.set("#SidePanelDescription.Text", "Select a dungeon to view details.");
        ui.set("#SidePanelRewardHeader.Visible", false);
        ui.set("#SidePanelRewardStatus.Visible", false);
        ui.set("#SidePanelRewardDetail.Visible", false);
        ui.set("#SidePanelConfirmPrompt.Visible", false);
        ui.set("#SidePanelEnterButton.Visible", false);
        ui.set("#SidePanelConfirmCancelButton.Visible", false);
        ui.set("#SidePanelConfirmProceedButton.Visible", false);
    }

    private void updateNativeCardStatus(@Nonnull UICommandBuilder ui) {
        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();
        if (cd != null && uuid != null && cd.isOnCooldown(uuid)) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            ui.set("#NativeCardOutlanderBridgeStatus.Text",
                    "Claim Cooldown: " + remainingMin + "m");
        } else {
            ui.set("#NativeCardOutlanderBridgeStatus.Text", "Rewards Available");
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

        if (data.action.startsWith("dungeon:native:select:")) {
            String id = data.action.substring("dungeon:native:select:".length());
            handleSelectNative(id);
            return;
        }

        if (data.action.startsWith("dungeon:native:quickenter:")) {
            String id = data.action.substring("dungeon:native:quickenter:".length());
            handleSelectNative(id);
            handleEnterRequest(ref, store);
            return;
        }

        if ("dungeon:native:enter".equalsIgnoreCase(data.action)) {
            handleEnterRequest(ref, store);
            return;
        }

        if ("dungeon:native:proceed".equalsIgnoreCase(data.action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#SidePanelConfirmPrompt.Visible", false);
            ui.set("#SidePanelConfirmCancelButton.Visible", false);
            ui.set("#SidePanelConfirmProceedButton.Visible", false);
            ui.set("#SidePanelEnterButton.Visible", true);
            sendUpdate(ui, false);
            teleportToSelected(ref, store);
            return;
        }

        if ("dungeon:native:cancel-proceed".equalsIgnoreCase(data.action)) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#SidePanelConfirmPrompt.Visible", false);
            ui.set("#SidePanelConfirmCancelButton.Visible", false);
            ui.set("#SidePanelConfirmProceedButton.Visible", false);
            ui.set("#SidePanelEnterButton.Visible", true);
            sendUpdate(ui, false);
            return;
        }

        NavUIHelper.handleNavAction(data.action, ref, store, playerRef);
    }

    private void handleSelectNative(@Nonnull String dungeonId) {
        InstanceDungeonDefinition def = EndlessLevelingAPI.get().getInstanceDungeon(dungeonId);
        if (def == null) {
            return;
        }
        selectedNativeDungeonId = dungeonId;

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#SidePanelTitle.Text", def.displayName().toUpperCase());
        ui.set("#SidePanelTag.Text", "ENDLESS LEVELING");
        ui.set("#SidePanelDescription.Text", descriptionFor(dungeonId));

        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();
        if (OUTLANDER_BRIDGE_ID.equals(dungeonId)
                && cd != null && uuid != null && cd.isOnCooldown(uuid)) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            ui.set("#SidePanelRewardStatus.Text", "On Cooldown");
            ui.set("#SidePanelRewardDetail.Text",
                    "XP claim is locked for " + remainingMin + "m. You can still enter, but XP earned this run will not be claimable until the cooldown ends.");
        } else {
            ui.set("#SidePanelRewardStatus.Text", "Available");
            ui.set("#SidePanelRewardDetail.Text", "XP rewards are claimable at the end of a successful run. A 1-hour cooldown begins on claim.");
        }

        ui.set("#SidePanelRewardHeader.Visible", true);
        ui.set("#SidePanelRewardStatus.Visible", true);
        ui.set("#SidePanelRewardDetail.Visible", true);
        ui.set("#SidePanelEnterButton.Visible", true);
        ui.set("#SidePanelConfirmPrompt.Visible", false);
        ui.set("#SidePanelConfirmCancelButton.Visible", false);
        ui.set("#SidePanelConfirmProceedButton.Visible", false);
        sendUpdate(ui, false);
    }

    private void handleEnterRequest(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String dungeonId = selectedNativeDungeonId;
        if (dungeonId == null) {
            return;
        }

        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID uuid = playerRef.getUuid();

        if (OUTLANDER_BRIDGE_ID.equals(dungeonId)
                && cd != null && uuid != null && cd.isOnCooldown(uuid)) {
            long remainingMin = (cd.remainingMs(uuid) + 59_999L) / 60_000L;
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#SidePanelConfirmPrompt.Text",
                    "XP claim on cooldown (" + remainingMin + "m). Any XP earned this run will not be claimable. Proceed?");
            ui.set("#SidePanelConfirmPrompt.Visible", true);
            ui.set("#SidePanelEnterButton.Visible", false);
            ui.set("#SidePanelConfirmCancelButton.Visible", true);
            ui.set("#SidePanelConfirmProceedButton.Visible", true);
            sendUpdate(ui, false);
            return;
        }

        teleportToSelected(ref, store);
    }

    private void teleportToSelected(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String dungeonId = selectedNativeDungeonId;
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

        // Reset side panel after initiating teleport.
        selectedNativeDungeonId = null;
        UICommandBuilder ui = new UICommandBuilder();
        resetSidePanel(ui);
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

    @Nonnull
    private static String descriptionFor(@Nonnull String dungeonId) {
        if (OUTLANDER_BRIDGE_ID.equals(dungeonId)) {
            return OUTLANDER_BRIDGE_DESCRIPTION;
        }
        return "";
    }
}
