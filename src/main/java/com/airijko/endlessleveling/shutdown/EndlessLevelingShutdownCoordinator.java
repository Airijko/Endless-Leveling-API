package com.airijko.endlessleveling.shutdown;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.types.BurnAugment;
import com.airijko.endlessleveling.augments.types.DeathBombAugment;
import com.airijko.endlessleveling.augments.types.EndurePainAugment;
import com.airijko.endlessleveling.augments.types.FrozenDomainAugment;
import com.airijko.endlessleveling.augments.types.ReckoningAugment;
import com.airijko.endlessleveling.augments.types.WitherAugment;
import com.airijko.endlessleveling.leveling.XpKillCreditTracker;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.airijko.endlessleveling.passives.type.HealingAuraPassive;
import com.airijko.endlessleveling.ui.PlayerHud;
import com.airijko.endlessleveling.ui.PlayerHudHide;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class EndlessLevelingShutdownCoordinator {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndlessLeveling");
    private static final String SHUTLOG_FILE_NAME = "shutdown.log";
    private static final Query<EntityStore> ENTITY_QUERY = Query.any();

    private final EndlessLeveling plugin;
    private final AtomicBoolean preShutdownCleanupExecuted = new AtomicBoolean(false);

    public EndlessLevelingShutdownCoordinator(EndlessLeveling plugin) {
        this.plugin = plugin;
    }

    public void handlePluginShutdown() {
        alwaysShutdownLog("Starting shutdown cleanup...");
        appendShutlog("shutdown() entered");
        runPreShutdownEntityCleanup("Plugin.shutdown()");

        var mobLevelingSystem = plugin.getMobLevelingSystem();
        var mobLevelingManager = plugin.getMobLevelingManager();
        if (mobLevelingSystem != null) {
            mobLevelingSystem.shutdownRuntimeState();
            alwaysShutdownLog("Mob leveling runtime state cleared.");
            appendShutlog("mob leveling runtime state cleared");
        } else if (mobLevelingManager != null) {
            mobLevelingManager.shutdownRuntimeState();
            alwaysShutdownLog("Mob leveling manager state cleared.");
            appendShutlog("mob leveling manager state cleared");
        }

        var playerDataManager = plugin.getPlayerDataManager();
        if (playerDataManager != null) {
            playerDataManager.saveAll();
            alwaysShutdownLog("All player data saved.");
            appendShutlog("player data saved");
        }

        var xpStatsManager = plugin.getXpStatsManager();
        if (xpStatsManager != null) {
            xpStatsManager.saveAll();
            alwaysShutdownLog("All XP stats data saved.");
            appendShutlog("xp stats saved");
        }

        var partyManager = plugin.getPartyManager();
        if (partyManager != null) {
            partyManager.saveAllParties();
            alwaysShutdownLog("All party data saved.");
            appendShutlog("party data saved");
        }

        cleanupRuntimeState();

        appendShutlog("shutdown() completed");
        alwaysShutdownLog("Shutdown complete!");
    }

    public void runPreShutdownEntityCleanup(String source) {
        if (!preShutdownCleanupExecuted.compareAndSet(false, true)) {
            if ("Plugin.shutdown()".equals(source)) {
                cleanupKnownWorldEntityStores();
                cleanupOnlinePlayerEntityState();
                alwaysShutdownLog("Pre-shutdown cleanup retry executed from Plugin.shutdown().");
                appendShutlog("pre-shutdown cleanup retry executed from Plugin.shutdown()");
            }
            appendShutlog("pre-shutdown cleanup skipped (already executed)");
            return;
        }

        cleanupKnownWorldEntityStores();
        cleanupOnlinePlayerEntityState();
        alwaysShutdownLog("Pre-shutdown cleanup executed from " + (source == null ? "unknown" : source) + ".");
        appendShutlog("pre-shutdown cleanup executed from " + (source == null ? "unknown" : source));
    }

    public void appendShutlog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        String line = Instant.now() + " | " + message + System.lineSeparator();
        try {
            Path logPath = resolveShutlogPath();
            if (logPath == null) {
                System.err.print("[EL_SHUTLOG] " + line);
                return;
            }

            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.print("[EL_SHUTLOG] write failure: " + ex.getMessage() + " :: " + line);
        }
    }

    @SuppressWarnings("unchecked")
    private void cleanupKnownWorldEntityStores() {
        Universe universe = Universe.get();

        Set<Store<EntityStore>> seenStores = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger visitedStores = new AtomicInteger();
        AtomicInteger clearedPlayerAttributeEntities = new AtomicInteger();
        AtomicInteger clearedPlayerNameplates = new AtomicInteger();

        if (universe != null) {
            Map<String, ?> worlds;
            try {
                worlds = universe.getWorlds();
            } catch (Throwable ignored) {
                worlds = null;
            }

            if (worlds != null && !worlds.isEmpty()) {
                for (Object world : worlds.values()) {
                    cleanupWorldStore(world,
                            seenStores,
                            visitedStores,
                            clearedPlayerAttributeEntities,
                            clearedPlayerNameplates);
                }
            }

            cleanupWorldStore(universe.getDefaultWorld(),
                    seenStores,
                    visitedStores,
                    clearedPlayerAttributeEntities,
                    clearedPlayerNameplates);
        }

        if (visitedStores.get() > 0) {
            alwaysShutdownLog(String.format(
                "Store sweep complete: stores=%d, player-modifiers=%d, player-nameplates=%d.",
                visitedStores.get(),
                clearedPlayerAttributeEntities.get(),
                clearedPlayerNameplates.get()));
            appendShutlog(String.format(
                    "store sweep: stores=%d playerModifierEntities=%d playerNameplates=%d",
                    visitedStores.get(),
                    clearedPlayerAttributeEntities.get(),
                    clearedPlayerNameplates.get()));
        } else {
            appendShutlog("store sweep: no live stores discovered");
        }
    }

    private void cleanupWorldStore(Object worldObject,
            Set<Store<EntityStore>> seenStores,
            AtomicInteger visitedStores,
            AtomicInteger clearedPlayerAttributeEntities,
            AtomicInteger clearedPlayerNameplates) {
        if (worldObject == null) {
            appendShutlog("cleanupWorldStore skipped: worldObject=null");
            return;
        }

        appendShutlog("cleanupWorldStore begin worldClass=" + worldObject.getClass().getName());
        alwaysShutdownLog("[CleanupDebug] cleanupWorldStore begin worldClass=" + worldObject.getClass().getName());

        Runnable cleanupTask = () -> {
            Store<EntityStore> store = resolveStoreFromWorldObject(worldObject);
            if (store == null) {
                appendShutlog("cleanupWorldStore task skipped: store is null");
                return;
            }

            long storeId = Integer.toUnsignedLong(System.identityHashCode(store));
            alwaysShutdownLog(String.format(
                    "[CleanupDebug] cleanupWorldStore task begin storeId=%d shutdown=%s",
                    storeId, store.isShutdown()));

            if (!seenStores.add(store)) {
                appendShutlog("cleanupWorldStore task skipped: store already cleaned storeId=" + storeId);
                return;
            }

            visitedStores.incrementAndGet();
            int playerModifierEntities = cleanupPlayerModifiersForStore(store);
            clearedPlayerAttributeEntities.addAndGet(playerModifierEntities);
            ArmyOfTheDeadPassive.cleanupPersistentSummons(store);
            int playerNameplates = 0;
            if (plugin.getPlayerNameplateSystem() != null) {
                playerNameplates = plugin.getPlayerNameplateSystem().removeAllNameplatesForStore(store);
                clearedPlayerNameplates.addAndGet(playerNameplates);
            }

            appendShutlog(String.format(
                    "cleanupWorldStore task complete: storeId=%d playerModifierEntities=%d playerNameplates=%d",
                    Integer.toUnsignedLong(System.identityHashCode(store)),
                    playerModifierEntities,
                    playerNameplates));
                alwaysShutdownLog(String.format(
                    "[CleanupDebug] cleanupWorldStore complete storeId=%d playerModifierEntities=%d playerNameplates=%d",
                    Integer.toUnsignedLong(System.identityHashCode(store)),
                    playerModifierEntities,
                    playerNameplates));
        };

        if (!runOnWorldThreadAndWait(worldObject, cleanupTask, 2000L)) {
            // Fallback best effort when world execution is unavailable late in shutdown.
            try {
                cleanupTask.run();
            } catch (Throwable ex) {
                appendShutlog("store sweep fallback cleanup failed for one world: " + ex.getClass().getSimpleName()
                        + " " + ex.getMessage());
                alwaysShutdownLog("[CleanupDebug] cleanupWorldStore fallback failed: "
                    + ex.getClass().getSimpleName() + " " + ex.getMessage());
            }
        }
    }

    private int cleanupPlayerModifiersForStore(Store<EntityStore> store) {
        if (store == null || plugin.getSkillManager() == null) {
            return 0;
        }

        final int[] cleaned = { 0 };
        try {
        store.forEachChunk(ENTITY_QUERY, (ArchetypeChunk<EntityStore> chunk,
                CommandBuffer<EntityStore> commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null) {
                    continue;
                }

                PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    continue;
                }

                if (plugin.getSkillManager().removeAllSkillModifiers(ref, commandBuffer)) {
                    cleaned[0]++;
                }
            }
        });
        } catch (Throwable ex) {
            appendShutlog("cleanupPlayerModifiersForStore forEachChunk threw (store may be shutdown): "
                    + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
        return cleaned[0];
    }

    @SuppressWarnings("unchecked")
    private Store<EntityStore> resolveStoreFromWorldObject(Object worldObject) {
        if (worldObject == null) {
            return null;
        }

        try {
            Method getEntityStore = worldObject.getClass().getMethod("getEntityStore");
            Object entityStoreObject = getEntityStore.invoke(worldObject);
            if (entityStoreObject instanceof Store) {
                return (Store<EntityStore>) entityStoreObject;
            }

            if (entityStoreObject == null) {
                return null;
            }

            Method getStore = entityStoreObject.getClass().getMethod("getStore");
            Object nestedStoreObject = getStore.invoke(entityStoreObject);
            if (nestedStoreObject instanceof Store) {
                return (Store<EntityStore>) nestedStoreObject;
            }
        } catch (Throwable ignored) {
            return null;
        }

        return null;
    }

    private boolean runOnWorldThreadAndWait(Object worldObject, Runnable task, long timeoutMillis) {
        if (worldObject == null || task == null) {
            return false;
        }

        try {
            if (isCurrentWorldThread(worldObject)) {
                task.run();
                return true;
            }

            Method executeMethod = worldObject.getClass().getMethod("execute", Runnable.class);
            CountDownLatch latch = new CountDownLatch(1);
            executeMethod.invoke(worldObject, (Runnable) () -> {
                try {
                    task.run();
                } finally {
                    latch.countDown();
                }
            });

            if (timeoutMillis <= 0L) {
                latch.await();
                return true;
            }
            boolean completed = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                appendShutlog("world-thread cleanup timed out after " + timeoutMillis + "ms");
            }
            return completed;
        } catch (Throwable ex) {
            appendShutlog("runOnWorldThreadAndWait failure: " + ex.getClass().getSimpleName() + " "
                    + ex.getMessage());
            return false;
        }
    }

    private boolean isCurrentWorldThread(Object worldObject) {
        if (worldObject == null) {
            return false;
        }
        try {
            Method isInThreadMethod = worldObject.getClass().getMethod("isInThread");
            Object inThread = isInThreadMethod.invoke(worldObject);
            if (inThread instanceof Boolean) {
                return (Boolean) inThread;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method getThreadMethod = worldObject.getClass().getMethod("getThread");
            Object worldThread = getThreadMethod.invoke(worldObject);
            return worldThread == Thread.currentThread();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void cleanupOnlinePlayerEntityState() {
        appendShutlog("cleanupOnlinePlayerEntityState skipped: handled by store sweep");
    }

    private void cleanupRuntimeState() {
        int movementStatesCleared = 0;
        var movementHasteSystem = plugin.getMovementHasteSystem();
        if (movementHasteSystem != null) {
            movementStatesCleared = movementHasteSystem.shutdownRuntimeState();
        }
        logShutdownSystemClear("MovementHasteSystem", movementStatesCleared);

        int raceRetryStatesCleared = 0;
        var playerRaceStatSystem = plugin.getPlayerRaceStatSystem();
        if (playerRaceStatSystem != null) {
            raceRetryStatesCleared = playerRaceStatSystem.shutdownRuntimeState();
        }
        logShutdownSystemClear("PlayerRaceStatSystem", raceRetryStatesCleared);

        int augmentRuntimeStatesCleared = 0;
        var augmentRuntimeManager = plugin.getAugmentRuntimeManager();
        if (augmentRuntimeManager != null) {
            augmentRuntimeStatesCleared = augmentRuntimeManager.getTrackedStateCount();
            augmentRuntimeManager.clearAll();
        }
        logShutdownSystemClear("AugmentRuntimeManager", augmentRuntimeStatesCleared);

        int mobAugmentRuntimeStatesCleared = 0;
        var mobAugmentExecutor = plugin.getMobAugmentExecutor();
        if (mobAugmentExecutor != null) {
            mobAugmentRuntimeStatesCleared = mobAugmentExecutor.clearRuntimeState();
        }
        logShutdownSystemClear("MobAugmentExecutor", mobAugmentRuntimeStatesCleared);

        int passiveRuntimeStatesCleared = 0;
        var passiveManager = plugin.getPassiveManager();
        if (passiveManager != null) {
            passiveRuntimeStatesCleared = passiveManager.clearAllRuntimeState();
        }
        logShutdownSystemClear("PassiveManager", passiveRuntimeStatesCleared);

        int raceRuntimeStatesCleared = 0;
        var raceManager = plugin.getRaceManager();
        if (raceManager != null) {
            raceRuntimeStatesCleared = raceManager.clearRuntimeState();
        }
        logShutdownSystemClear("RaceManager", raceRuntimeStatesCleared);

        int xpStatsEntriesCleared = 0;
        var xpStatsManager = plugin.getXpStatsManager();
        if (xpStatsManager != null) {
            xpStatsEntriesCleared = xpStatsManager.clearRuntimeState();
        }
        logShutdownSystemClear("XpStatsManager", xpStatsEntriesCleared);

        int uiAlertStatesCleared = 0;
        var uiIntegrityAlertSystem = plugin.getUiIntegrityAlertSystem();
        if (uiIntegrityAlertSystem != null) {
            uiAlertStatesCleared = uiIntegrityAlertSystem.clearRuntimeState();
        }
        logShutdownSystemClear("UiIntegrityAlertSystem", uiAlertStatesCleared);

        int summonRuntimeEntriesCleared = ArmyOfTheDeadPassive.clearAllRuntimeState();
        logShutdownSystemClear("ArmyOfTheDeadPassive", summonRuntimeEntriesCleared);
        int deathBombEntriesCleared = DeathBombAugment.clearAllRuntimeState();
        logShutdownSystemClear("DeathBombAugment", deathBombEntriesCleared);
        int burnEntriesCleared = BurnAugment.clearAllRuntimeState();
        logShutdownSystemClear("BurnAugment", burnEntriesCleared);
        int frozenEntriesCleared = FrozenDomainAugment.clearAllRuntimeState();
        logShutdownSystemClear("FrozenDomainAugment", frozenEntriesCleared);
        int healingAuraEntriesCleared = HealingAuraPassive.clearAllRuntimeState();
        logShutdownSystemClear("HealingAuraPassive", healingAuraEntriesCleared);
        int witherEntriesCleared = WitherAugment.clearAllRuntimeState();
        logShutdownSystemClear("WitherAugment", witherEntriesCleared);
        int reckoningEntriesCleared = ReckoningAugment.clearAllRuntimeState();
        logShutdownSystemClear("ReckoningAugment", reckoningEntriesCleared);
        int endurePainEntriesCleared = EndurePainAugment.clearAllRuntimeState();
        logShutdownSystemClear("EndurePainAugment", endurePainEntriesCleared);
        int killCreditEntriesCleared = XpKillCreditTracker.clearAll();
        logShutdownSystemClear("XpKillCreditTracker", killCreditEntriesCleared);
        int hiddenHudsCleared = PlayerHudHide.clearAllTrackedHuds();
        logShutdownSystemClear("PlayerHudHide", hiddenHudsCleared);
        int playerHudsCleared = PlayerHud.clearAllTrackedHuds();
        logShutdownSystemClear("PlayerHud", playerHudsCleared);

        alwaysShutdownLog("Runtime state cleanup pass complete.");
        appendShutlog(String.format(
                "runtime state cleared: movement=%d raceRetries=%d augmentStates=%d mobAugmentStates=%d passiveStates=%d raceStates=%d uiAlerts=%d summonEntries=%d deathBombEntries=%d burnEntries=%d frozenEntries=%d witherEntries=%d reckoningEntries=%d endurePainEntries=%d killCredits=%d huds=%d hiddenHuds=%d",
                movementStatesCleared,
                raceRetryStatesCleared,
                augmentRuntimeStatesCleared,
                mobAugmentRuntimeStatesCleared,
                passiveRuntimeStatesCleared,
                raceRuntimeStatesCleared,
                uiAlertStatesCleared,
                summonRuntimeEntriesCleared,
                deathBombEntriesCleared,
                burnEntriesCleared,
                frozenEntriesCleared,
                witherEntriesCleared,
                reckoningEntriesCleared,
                endurePainEntriesCleared,
                killCreditEntriesCleared,
                playerHudsCleared,
                hiddenHudsCleared));
    }

    private void logShutdownSystemClear(String systemName, int entriesCleared) {
        alwaysShutdownLog(String.format("Cleared %s state (entries=%d).", systemName, entriesCleared));
    }

    private void alwaysShutdownLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        LOGGER.atInfo().log("[Shutdown] %s", message);
    }

    private Path resolveShutlogPath() {
        if (plugin.getFilesManager() == null || plugin.getFilesManager().getPluginFolder() == null) {
            return null;
        }
        return plugin.getFilesManager().getPluginFolder().toPath().resolve(SHUTLOG_FILE_NAME);
    }
}
