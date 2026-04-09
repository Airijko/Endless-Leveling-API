package com.airijko.endlessleveling.listeners;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.ELNotificationType;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.passives.PassiveManager;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.ui.PlayerHud;
import com.airijko.endlessleveling.ui.PlayerHudHide;
import com.airijko.endlessleveling.util.ChatMessageTemplate;
import com.airijko.endlessleveling.util.FixedValue;
import com.airijko.endlessleveling.util.Lang;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.airijko.endlessleveling.util.WorldContextUtil;
import com.airijko.endlessleveling.systems.PlayerNameplateSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.lang.reflect.Method;

public class PlayerDataListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private final PlayerDataManager playerDataManager;
    private final PassiveManager passiveManager;
    private final SkillManager skillManager;
    private final RaceManager raceManager;
    private final AugmentUnlockManager augmentUnlockManager;

    public PlayerDataListener(PlayerDataManager playerDataManager, PassiveManager passiveManager,
            SkillManager skillManager, RaceManager raceManager, AugmentUnlockManager augmentUnlockManager) {
        this.playerDataManager = playerDataManager;
        this.passiveManager = passiveManager;
        this.skillManager = skillManager;
        this.raceManager = raceManager;
        this.augmentUnlockManager = augmentUnlockManager;
    }

    /** Called when a player joins */
    public void onPlayerReady(PlayerReadyEvent event) {
        var player = event.getPlayer();
        Ref<EntityStore> entityRef = event.getPlayerRef();
        Store<EntityStore> store = entityRef != null ? entityRef.getStore() : null;
        UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            LOGGER.atWarning().log("Unable to resolve joining player UUID.");
            return;
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
            LOGGER.atWarning().log("Unable to find PlayerRef for joining player %s", playerUuid);
            return;
        }
        UUID uuid = playerRef.getUuid();

        // Load or create PlayerData
        PlayerData playerData = playerDataManager.loadOrCreate(uuid, playerRef.getUsername());
        if (playerData == null) {
            LOGGER.atWarning().log("Unable to load PlayerData for joining player %s", playerRef.getUsername());
            return;
        }

        var world = player.getWorld();
        if (world == null) {
            boolean inInstanceWorld = false;
            processPlayerReadyOnWorldThread(playerData, playerRef, entityRef, store, null, inInstanceWorld);
            return;
        }

        try {
            world.execute(() -> {
                boolean inInstanceWorld = WorldContextUtil.isInstanceContext(world, entityRef, store);
                processPlayerReadyOnWorldThread(playerData, playerRef, entityRef, store, world, inInstanceWorld);
            });
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log(
                    "Failed to enqueue PlayerReady world task for %s; applying fallback path",
                    playerRef.getUsername());
            boolean inInstanceWorld = WorldContextUtil.isInstanceWorld(world);
            processPlayerReadyOnWorldThread(playerData, playerRef, null, null, world, inInstanceWorld);
        }
    }

        private void processPlayerReadyOnWorldThread(@Nonnull PlayerData playerData,
            @Nonnull PlayerRef playerRef,
            Ref<EntityStore> entityRef,
            Store<EntityStore> store,
                World world,
            boolean inInstanceWorld) {
        UUID uuid = playerRef.getUuid();

            if (!inInstanceWorld && world != null && entityRef != null && store != null) {
                ensureSafeLoginPosition(playerRef, entityRef, store, world);
            }

        MobLevelingManager mobLevelingManager = EndlessLeveling.getInstance().getMobLevelingManager();
        if (mobLevelingManager != null && store != null) {
            mobLevelingManager.syncTierLevelOverridesForDungeon(store, uuid);
            mobLevelingManager.syncFixedLevelOverridesForDungeon(store, uuid);
        }

        var movementHasteSystem = EndlessLeveling.getInstance().getMovementHasteSystem();
        if (movementHasteSystem != null && entityRef != null) {
            movementHasteSystem.registerPlayer(uuid, entityRef);
        }

        if (passiveManager != null) {
            passiveManager.resetRuntimeState(uuid);
            passiveManager.syncPassives(playerData);
            playerDataManager.save(playerData);
        }

        if (skillManager != null) {
            SkillManager.VanguardCritRestrictionResult restrictionResult = skillManager
                    .enforceVanguardCritRestrictions(playerData);
            if (restrictionResult.adjusted()) {
                LOGGER.atInfo().log(
                        "Applied Vanguard crit restriction refund on login for %s (total=%d precision=%d ferocity=%d)",
                        playerRef.getUsername(),
                        restrictionResult.totalRefunded(),
                        restrictionResult.precisionRefunded(),
                        restrictionResult.ferocityRefunded());
                playerDataManager.save(playerData);
            }
        }

        if (skillManager != null && entityRef != null && store != null) {
            try {
                boolean applied = skillManager.applyAllSkillModifiers(entityRef, store, playerData);
                if (!applied) {
                    scheduleSkillRetry(uuid, playerRef.getUsername());
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to apply skill modifiers for %s: %s", playerRef.getUsername(),
                        e.getMessage());
            }
        } else if (skillManager != null) {
            scheduleSkillRetry(uuid, playerRef.getUsername());
        }

        if (raceManager != null && !inInstanceWorld) {
            raceManager.applyRaceModelOnLogin(playerData);
        }

        if (augmentUnlockManager != null) {
            augmentUnlockManager.ensureUnlocksForAllProfiles(playerData);

            // Audit after ensureUnlocks so that routine TOO_FEW cases (missing
            // offers that ensureUnlocks just filled) don't trigger false alerts.
            // Only real anomalies (TOO_MANY, or TOO_FEW when the pool was empty)
            // will reach online operators.
            var plugin = EndlessLeveling.getInstance();
            if (plugin != null && plugin.getAugmentSyncValidator() != null) {
                plugin.getAugmentSyncValidator().auditOnLogin(playerData);
            }

            notifyAvailableAugments(playerRef, playerData);
        }

        var partyManager = EndlessLeveling.getInstance().getPartyManager();
        if (partyManager != null) {
            partyManager.updatePartyHudCustomText(playerData);
        }

        LOGGER.atInfo().log("Loaded PlayerData for player: %s", playerRef.getUsername());

        if (playerData.getSkillPoints() > 0) {
            notifyAvailableSkillPoints(playerRef, playerData.getSkillPoints());
        }

        PlayerNameplateSystem playerNameplateSystem = EndlessLeveling.getInstance().getPlayerNameplateSystem();
        if (playerNameplateSystem != null) {
            playerNameplateSystem.requestRefresh(uuid);
        }
    }

    private void ensureSafeLoginPosition(@Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world) {
        Transform currentTransform = playerRef.getTransform();
        if (currentTransform == null || currentTransform.getPosition() == null) {
            return;
        }

        int blockX = MathUtil.floor(currentTransform.getPosition().x);
        int blockZ = MathUtil.floor(currentTransform.getPosition().z);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        if (world.getChunkIfLoaded(chunkIndex) != null) {
            return;
        }

        // Skip if a teleport is already in-flight (e.g. death-eject from a gate instance).
        // Adding a second Teleport component triggers a duplicate JoinWorld cycle that
        // desynchronises the teleportId counter and disconnects the player on the next
        // teleport-bearing command.
        try {
            Archetype<EntityStore> archetype = store.getArchetype(entityRef);
            if (archetype != null
                    && (archetype.contains(Teleport.getComponentType())
                            || archetype.contains(PendingTeleport.getComponentType()))) {
                LOGGER.atFine().log(
                        "Skipping safe-login teleport for %s: teleport already in-flight in world %s",
                        playerRef.getUsername(), world.getName());
                return;
            }
        } catch (Exception ex) {
            LOGGER.atFine().withCause(ex).log(
                    "Teleport guard check failed for %s; skipping safe-login teleport to be safe.",
                    playerRef.getUsername());
            return;
        }

        Transform spawnTransform = world.getWorldConfig() != null && world.getWorldConfig().getSpawnProvider() != null
                ? world.getWorldConfig().getSpawnProvider().getSpawnPoint(world, playerRef.getUuid())
                : null;
        if (spawnTransform == null) {
            return;
        }

        // Use the in-place variant (no world arg) since this is a same-world reposition.
        // The world-bearing variant triggers a full JoinWorld cycle (drain -> add ->
        // ClientReady) even for same-world teleports, which desynchronises teleportId.
        store.addComponent(entityRef, Teleport.getComponentType(), Teleport.createForPlayer(spawnTransform));
        LOGGER.atWarning().log(
                "Queued safe login teleport for %s: current chunk (%d,%d) not loaded in world %s; moving to spawn.",
                playerRef.getUsername(),
                blockX,
                blockZ,
                world.getName());
    }

    private void scheduleSkillRetry(@Nonnull UUID uuid, @Nonnull String username) {
        LOGGER.atFine().log("Skill modifiers scheduled for retry for %s", username);
        var retrySystem = EndlessLeveling.getInstance().getPlayerRaceStatSystem();
        if (retrySystem != null) {
            retrySystem.scheduleRetry(uuid);
        }
    }

    /** Called when a player leaves */
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        var playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();

        try {
            PlayerHud.unregister(uuid);
            PlayerHudHide.unregister(uuid);
        } catch (LinkageError | RuntimeException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to clean up HUD state for %s on disconnect.", uuid);
        }

        PlayerData data = playerDataManager.get(uuid);
        if (data != null) {
            playerDataManager.save(data); // persist to uuid.json
            playerDataManager.remove(uuid); // remove from cache
            LOGGER.atInfo().log("Saved and removed PlayerData for %s on disconnect.", uuid);
        }

        if (raceManager != null) {
            raceManager.clearModelApplyGuard(uuid);
        }

        if (passiveManager != null) {
            passiveManager.resetRuntimeState(uuid);
        }

        var archetypePassiveManager = EndlessLeveling.getInstance().getArchetypePassiveManager();
        if (archetypePassiveManager != null) {
            archetypePassiveManager.clearSnapshot(uuid);
        }

		var movementHasteSystem = EndlessLeveling.getInstance().getMovementHasteSystem();
		if (movementHasteSystem != null) {
			movementHasteSystem.clearPlayer(uuid);
		}

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        Store<EntityStore> playerStore = playerEntityRef != null ? playerEntityRef.getStore() : null;
        PlayerNameplateSystem playerNameplateSystem = EndlessLeveling.getInstance().getPlayerNameplateSystem();
        if (playerNameplateSystem != null) {
            playerNameplateSystem.forgetPlayer(uuid);
        }
        if (playerEntityRef != null && playerStore != null && !playerStore.isShutdown()) {
            queuePlayerRuntimeCleanup(playerRef, playerEntityRef, playerStore, playerNameplateSystem);
        }
        ArmyOfTheDeadPassive.cleanupOwnerSummonsOnDisconnect(uuid, playerStore);
    }

    private void queuePlayerRuntimeCleanup(@Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull Store<EntityStore> playerStore,
            PlayerNameplateSystem playerNameplateSystem) {
        Object world = null;
        try {
            if (playerStore.getExternalData() != null) {
                world = playerStore.getExternalData().getWorld();
            }
        } catch (Throwable ignored) {
            world = null;
        }

        if (world == null) {
            return;
        }

        try {
            Method executeMethod = world.getClass().getMethod("execute", Runnable.class);
            executeMethod.invoke(world, (Runnable) () -> {
                try {
                    if (!playerEntityRef.isValid()) {
                        return;
                    }

                    Store<EntityStore> liveStore = playerEntityRef.getStore();
                    if (liveStore == null || liveStore.isShutdown()) {
                        return;
                    }

                    if (skillManager != null) {
                        skillManager.removeAllSkillModifiers(playerEntityRef, liveStore);
                    }
                    if (playerNameplateSystem != null) {
                        playerNameplateSystem.removeNameplateForPlayerRef(playerEntityRef, liveStore, playerRef);
                    }
                } catch (Throwable ex) {
                    LOGGER.atFine().withCause(ex)
                            .log("Failed deferred player runtime cleanup for %s", playerRef.getUuid());
                }
            });
        } catch (Throwable ex) {
            LOGGER.atFine().withCause(ex)
                    .log("Unable to queue world-thread runtime cleanup for %s", playerRef.getUuid());
        }
    }

        private void notifyAvailableSkillPoints(@Nonnull PlayerRef playerRef, int skillPoints) {
        if (skillPoints <= 0
                || EndlessLevelingAPI.get().isNotificationSuppressed(ELNotificationType.UNSPENT_SKILL_POINTS)) {
            return;
        }

        var packetHandler = playerRef.getPacketHandler();
        var primaryMessage = Message.raw(
            Lang.tr(playerRef.getUuid(), "notify.skills.unspent.primary",
                "You have {0} unspent skill points!", skillPoints))
                .color("#ffc300");
        var secondaryMessage = Message.join(
            Lang.message("notify.skills.unspent.secondary.open")
                        .color("#ff9d00"),
            Message.raw(nn(FixedValue.ROOT_COMMAND.value(), "/lvl"))
                .color("#4fd7f7"),
            Lang.message("notify.skills.unspent.secondary.close")
                        .color("#ff9d00"));
        var icon = new ItemStack("Ingredient_Ice_Essence", 1).toPacket();
        NotificationUtil.sendNotification(packetHandler, primaryMessage, secondaryMessage, icon);

        var chatMessage = Message.join(
            Lang.message(ChatMessageTemplate.SKILLS_CHAT_HAVE.localizationKey())
                .color(nn(ChatMessageTemplate.SKILLS_CHAT_HAVE.colorHex(), "#ff9d00")),
                Message.raw(String.valueOf(skillPoints)).color("#4fd7f7"),
            Lang.message(ChatMessageTemplate.SKILLS_CHAT_USE.localizationKey())
                .color(nn(ChatMessageTemplate.SKILLS_CHAT_USE.colorHex(), "#ff9d00")),
            Message.raw(nn(PlayerChatNotifier.text(playerRef, ChatMessageTemplate.SKILLS_COMMAND),
                "/lvl skills"))
                .color(nn(ChatMessageTemplate.SKILLS_COMMAND.colorHex(), "#4fd7f7")),
            Lang.message(ChatMessageTemplate.SKILLS_CHAT_END.localizationKey())
                .color(nn(ChatMessageTemplate.SKILLS_CHAT_END.colorHex(), "#ff9d00")));
        PlayerChatNotifier.send(playerRef, chatMessage);
    }

        private static String nn(String value, String fallback) {
        return Objects.requireNonNullElse(value, fallback);
        }

    private void notifyAvailableAugments(PlayerRef playerRef, PlayerData playerData) {
        if (playerRef == null || playerData == null || augmentUnlockManager == null) {
            return;
        }
        if (!playerData.isAugmentNotifEnabled()
                || EndlessLevelingAPI.get().isNotificationSuppressed(ELNotificationType.AUGMENT_AVAILABILITY)) {
            return;
        }
        List<PassiveTier> tiers = augmentUnlockManager.getPendingOfferTiers(playerData);
        if (tiers.isEmpty()) {
            return;
        }

        PlayerChatNotifier.sendAugmentAvailability(playerRef, tiers);
    }
}
