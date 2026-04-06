package com.airijko.endlessleveling.mob;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.augments.MobAugmentExecutor;
import com.airijko.endlessleveling.compatibility.NameplateBuilderCompatibility;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.managers.LoggingManager;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies mob-level stat scaling. For now mobs are hard-coded to level 10.
 * Scales Health and Damage (if a "Damage" stat exists) using the multipliers
 * provided by `LevelingManager`.
 */
public class MobLevelingSystem extends DelayedSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final HytaleLogger NAMEPLATE_LOGGER = HytaleLogger.get("com.airijko.endlessleveling.mob.MobLevelingSystem.nameplate");
    private static final Query<EntityStore> ENTITY_QUERY = Query.any();
    private static final String MOB_HEALTH_SCALE_MODIFIER_KEY = "EL_MOB_HEALTH_SCALE";
    private static final String MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY = "EL_MOB_AUGMENT_LIFE_FORCE";
    private static final float SYSTEM_INTERVAL_SECONDS = 0.1f;
    private static final long STALE_ENTITY_TTL_MILLIS = 100_000L;
    private static final long PASSIVE_STAT_TICK_INTERVAL_MILLIS = 1000L;
    private static final long FLOW_HEALTH_RETRY_INTERVAL_MILLIS = 500L;
    private static final long FLOW_HEALTH_LOG_COOLDOWN_MILLIS = 5000L;
    private static final long BLACKLIST_RECHECK_INTERVAL_MILLIS = 5000L;
    private static final int CHUNK_BIT_SHIFT = 5;
    private static final int MIN_PLAYER_VIEW_RADIUS_CHUNKS = 1;
    private static final int PLAYER_VIEW_RADIUS_BUFFER_CHUNKS = 1;
    private static final long SUMMON_HEALTH_ANOMALY_LOG_COOLDOWN_MS = 3000L;
    private static final String DEBUG_SECTION_MOB_LEVEL_FLOW = "mob_level_flow";
    private static final String DEBUG_SECTION_MOB_COMMON_DEFENSE = "mob_common_defense";
    private static final String DEBUG_SECTION_MOB_LEVEL_NAMEPLATE = "mob_level_nameplate";
    private static final String DEBUG_SECTION_MOB_HEALTH_NAMEPLATE = "mob_health_nameplate";

    private final MobLevelingManager mobLevelingManager = EndlessLeveling.getInstance().getMobLevelingManager();
    private final java.util.Random random = new java.util.Random();

    private final AtomicBoolean fullMobRescaleRequested = new AtomicBoolean(false);
    private long systemTimeMillis = 0L;
    private java.lang.reflect.Method cachedGetTickMethod = null;
    private final Map<Long, EntityRuntimeState> entityStates = new ConcurrentHashMap<>();
    private final Set<Store<EntityStore>> knownStoresForCleanup = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Integer, Long> summonHealthAnomalyLogTimes = new ConcurrentHashMap<>();
    // Cached once per tick to avoid per-entity config lookups, string splitting, and collection iteration.
    private boolean cachedDebugMobLevelFlow = false;
    private boolean cachedDebugMobCommonDefense = false;
    private boolean cachedDebugMobLevelNameplate = false;
    private boolean cachedDebugMobHealthNameplate = false;

    public MobLevelingSystem() {
        super(SYSTEM_INTERVAL_SECONDS);
    }

    public void shutdownRuntimeState() {
        int runtimeStatesBefore = entityStates.size();
        LOGGER.atInfo().log(
            "[MOB_SHUTDOWN_DEBUG] shutdownRuntimeState begin runtimeStates=%d knownStores=%d",
                runtimeStatesBefore,
                knownStoresForCleanup.size());

        cleanupStoreRuntimeState(null);
        entityStates.clear();
        knownStoresForCleanup.clear();
        summonHealthAnomalyLogTimes.clear();
        if (mobLevelingManager != null) {
            mobLevelingManager.shutdownRuntimeState();
        }

        LOGGER.atInfo().log(
                "[MOB_SHUTDOWN_DEBUG] shutdownRuntimeState complete runtimeStatesAfter=%d",
                entityStates.size());
    }

    public int removeAllNameplatesForStore(Store<EntityStore> store) {
        return removeAllNameplatesForStore(store, 3000L);
    }

    public int removeAllKnownMobNameplates() {
        int removed = 0;
        for (Store<EntityStore> store : new ArrayList<>(knownStoresForCleanup)) {
            removed += removeAllNameplatesForStore(store);
        }
        return removed;
    }

    private int removeAllNameplatesForStore(Store<EntityStore> store, long timeoutMillis) {
        if (store == null) {
            LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] removeAllNameplatesForStore skipped: store is null");
            return 0;
        }
        if (store.isShutdown()) {
                LOGGER.atInfo().log(
                    "[MOB_SHUTDOWN_DEBUG] removeAllNameplatesForStore storeId=%d is shutdown; will attempt direct cleanup without world-thread dispatch",
                    Integer.toUnsignedLong(System.identityHashCode(store)));
            // Skip world-thread dispatch and fall through to direct attempt below.
            try {
                int removed = removeAllNameplatesForStoreCurrentThread(store);
                LOGGER.atInfo().log(
                        "[MOB_SHUTDOWN_DEBUG] shutdown-store storeId=%d direct cleanup removed=%d",
                        Integer.toUnsignedLong(System.identityHashCode(store)), removed);
                return removed;
            } catch (Throwable ex) {
                LOGGER.atInfo().log(
                        "[MOB_SHUTDOWN_DEBUG] shutdown-store storeId=%d direct cleanup threw: %s %s",
                        Integer.toUnsignedLong(System.identityHashCode(store)),
                        ex.getClass().getSimpleName(), ex.getMessage());
                return 0;
            }
        }

        long storeId = Integer.toUnsignedLong(System.identityHashCode(store));
        LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] removeAllNameplatesForStore begin storeId=%d timeoutMs=%d", storeId,
                timeoutMillis);

        Object world = null;
        try {
            EntityStore externalData = store.getExternalData();
            if (externalData != null) {
                world = externalData.getWorld();
            }
        } catch (Throwable ex) {
                LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] storeId=%d world resolve failed: %s %s",
                    storeId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }

        if (world != null && !isCurrentWorldThread(world)) {
            final int[] removed = { 0 };
            boolean executed = runOnWorldThreadAndWait(world,
                    () -> removed[0] = removeAllNameplatesForStoreCurrentThread(store),
                    timeoutMillis);
            if (executed) {
                LOGGER.atInfo().log(
                        "[MOB_SHUTDOWN_DEBUG] storeId=%d cleanup executed on world thread removed=%d",
                        storeId,
                        removed[0]);
                return removed[0];
            }
                LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] storeId=%d world-thread dispatch failed; trying direct path",
                    storeId);
        }

        try {
            int removed = removeAllNameplatesForStoreCurrentThread(store);
            LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] storeId=%d direct cleanup removed=%d", storeId, removed);
            return removed;
        } catch (Throwable ex) {
                LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] storeId=%d direct cleanup failed: %s %s",
                    storeId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return 0;
        }
    }

    int removeAllNameplatesForStoreCurrentThread(Store<EntityStore> store) {
        if (store == null) {
            return 0;
        }
        // Note: we intentionally do NOT guard on store.isShutdown() here.
        // During plugin shutdown the store is already marked shutdown; a try/catch
        // below handles any resulting error if the store rejects iteration.

        final int[] removed = { 0 };
        final int[] levelStateCleared = { 0 };
        final int[] scanned = { 0 };
        final int[] skippedPlayers = { 0 };
        final int[] candidates = { 0 };
        final int[] withNameplate = { 0 };
        final int[] withStatMap = { 0 };
        final int[] dirtyMarked = { 0 };
        final long storeIdForLog = Integer.toUnsignedLong(System.identityHashCode(store));

        try {
            store.forEachChunk(ENTITY_QUERY, (ArchetypeChunk<EntityStore> chunk,
                    CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (ref == null) {
                        continue;
                    }
                    scanned[0]++;

                    PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef != null && playerRef.isValid()) {
                        skippedPlayers[0]++;
                        continue;
                    }

                    NPCEntity npcEntity = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
                    Nameplate nameplate = commandBuffer.getComponent(ref, Nameplate.getComponentType());
                    EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
                    if (npcEntity == null && nameplate == null && statMap == null) {
                        continue;
                    }
                    candidates[0]++;
                    if (nameplate != null) {
                        withNameplate[0]++;
                    }
                    if (statMap != null) {
                        withStatMap[0]++;
                    }

                    stripMobHealthModifiers(ref, commandBuffer);
                    clearOrRemoveNameplate(ref, commandBuffer);
                    levelStateCleared[0] += clearMobLevelRuntimeStateForEntity(ref, commandBuffer);
                    if (markEntityChunkDirty(ref, commandBuffer)) {
                        dirtyMarked[0]++;
                    }
                    removed[0]++;
                }
            });
        } catch (Throwable ex) {
                LOGGER.atInfo().log(
                    "[MOB_SHUTDOWN_DEBUG] storeId=%d forEachChunk threw (store may already be shutdown): %s %s; partialScanned=%d partialRemoved=%d",
                    storeIdForLog,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    scanned[0],
                    removed[0]);
        }

        LOGGER.atInfo().log(
            "[MOB_SHUTDOWN_DEBUG] storeId=%d scanSummary shutdown=%s scanned=%d skippedPlayers=%d candidates=%d withNameplate=%d withStatMap=%d dirtyMarked=%d removed=%d levelStateClears=%d",
                storeIdForLog,
                store.isShutdown(),
                scanned[0],
                skippedPlayers[0],
                candidates[0],
                withNameplate[0],
                withStatMap[0],
                dirtyMarked[0],
                removed[0],
                levelStateCleared[0]);

        return removed[0];
    }

    private int clearMobLevelRuntimeStateForEntity(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || mobLevelingManager == null) {
            return 0;
        }

        int clearOps = 0;
        Store<EntityStore> store = ref.getStore();
        int entityId = ref.getIndex();

        if (entityId >= 0) {
            mobLevelingManager.clearEntityLevelOverride(store, entityId);
            summonHealthAnomalyLogTimes.remove(entityId);
            clearOps++;
        }

        long fallbackEntityKey = toEntityKey(store, entityId);
        mobLevelingManager.forgetEntityByKey(fallbackEntityKey);
        entityStates.remove(fallbackEntityKey);
        clearOps++;

        TrackingIdentity identity = resolveTrackingIdentity(ref, commandBuffer);
        if (identity != null && identity.key() != fallbackEntityKey) {
            mobLevelingManager.forgetEntityByKey(identity.key());
            entityStates.remove(identity.key());
            clearOps++;
        }

        return clearOps;
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

            var executeMethod = worldObject.getClass().getMethod("execute", Runnable.class);
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
                LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] world-thread wait timed out after %dms", timeoutMillis);
            }
            return completed;
        } catch (Throwable ex) {
                LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] world-thread dispatch failed: %s %s",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return false;
        }
    }

    private boolean isCurrentWorldThread(Object worldObject) {
        if (worldObject == null) {
            return false;
        }
        try {
            var isInThreadMethod = worldObject.getClass().getMethod("isInThread");
            Object inThread = isInThreadMethod.invoke(worldObject);
            if (inThread instanceof Boolean) {
                return (Boolean) inThread;
            }
        } catch (Throwable ignored) {
        }
        try {
            var getThreadMethod = worldObject.getClass().getMethod("getThread");
            Object worldThread = getThreadMethod.invoke(worldObject);
            return worldThread == Thread.currentThread();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void stripMobHealthModifiers(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthBefore = statMap.get(healthIndex);
        float previousCurrent = healthBefore != null ? healthBefore.get() : Float.NaN;
        float previousMax = healthBefore != null ? healthBefore.getMax() : Float.NaN;
        UUID entityUuid = resolveUuid(ref, commandBuffer);
        boolean hadMobAugments = false;
        List<String> clearedAugmentModifierKeys = Collections.emptyList();

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin != null) {
            MobAugmentExecutor mobAugmentExecutor = plugin.getMobAugmentExecutor();
            if (mobAugmentExecutor != null && entityUuid != null) {
                hadMobAugments = mobAugmentExecutor.hasMobAugments(entityUuid);
                clearedAugmentModifierKeys = mobAugmentExecutor.clearPersistentHealthModifiers(entityUuid, statMap);
                if (hadMobAugments) {
                    mobAugmentExecutor.unregisterMob(entityUuid);
                }
            }
        }

        statMap.removeModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY);
        statMap.removeModifier(healthIndex, MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY);

        // Apply the modifier removals first so getMax() reflects the restored baseline.
        statMap.update();

        EntityStatValue health = statMap.get(healthIndex);
        if (health != null) {
            float max = health.getMax();
            float clampedCurrent = Math.max(0.0f, Math.min(max, health.get()));
            if (Float.isFinite(clampedCurrent)) {
                statMap.setStatValue(healthIndex, clampedCurrent);
            }
        }

        statMap.update();

        EntityStatValue healthAfter = statMap.get(healthIndex);
        float updatedCurrent = healthAfter != null ? healthAfter.get() : Float.NaN;
        float updatedMax = healthAfter != null ? healthAfter.getMax() : Float.NaN;
        LOGGER.atInfo().log(
            "[MOB_SHUTDOWN_DEBUG] entity=%d uuid=%s clearedBaseHealthKeys=[%s,%s] augmentRuntime=%s clearedAugmentHealthKeys=%s healthBefore=%.2f/%.2f healthAfter=%.2f/%.2f",
            ref.getIndex(),
            entityUuid != null ? entityUuid : "none",
            MOB_HEALTH_SCALE_MODIFIER_KEY,
            MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY,
            hadMobAugments,
            clearedAugmentModifierKeys,
            previousCurrent,
            previousMax,
            updatedCurrent,
            updatedMax);
    }

    private boolean markEntityChunkDirty(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null) {
            return false;
        }

        TransformComponent transform = commandBuffer != null
                ? commandBuffer.getComponent(ref, TransformComponent.getComponentType())
                : null;
        if (transform == null && ref.getStore() != null) {
            transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        }
        if (transform == null) {
            return false;
        }

        try {
            transform.markChunkDirty(commandBuffer != null ? commandBuffer : ref.getStore());
            return true;
        } catch (Throwable ex) {
            LOGGER.atInfo().log(
                    "[MOB_SHUTDOWN_DEBUG] entity=%d failedToMarkChunkDirty=%s %s",
                    ref.getIndex(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return false;
        }
    }

    public void requestFullMobRescale() {
        fullMobRescaleRequested.set(true);
    }

    public int disableMobLevelsForStore(Store<EntityStore> store) {
        return disableMobLevelsForStore(store, true);
    }

    int disableMobLevelsForStoreCurrentThread(Store<EntityStore> store) {
        return disableMobLevelsForStore(store, false);
    }

    private int disableMobLevelsForStore(Store<EntityStore> store, boolean allowThreadDispatch) {
        if (store == null) {
            return 0;
        }

        int removed = allowThreadDispatch
                ? removeAllNameplatesForStore(store)
                : removeAllNameplatesForStoreCurrentThread(store);
        cleanupStoreRuntimeState(store);
        if (mobLevelingManager != null) {
            mobLevelingManager.disableDebugStoreMobLevels(store);
        }
        LOGGER.atInfo().log(
                "[MOB_SHUTDOWN_DEBUG] disableMobLevelsForStore storeId=%d removed=%d suppressed=true currentThread=%s",
                Integer.toUnsignedLong(System.identityHashCode(store)),
                removed,
                !allowThreadDispatch);
        return removed;
    }

    public int debugCleanupAndSuspendStore(Store<EntityStore> store) {
        return disableMobLevelsForStore(store);
    }

    public boolean debugToggleStore(Store<EntityStore> store) {
        if (isDebugStoreSuppressed(store)) {
            debugResumeStore(store);
            return false;
        }

        debugCleanupAndSuspendStore(store);
        return true;
    }

    public void debugResumeStore(Store<EntityStore> store) {
        if (store == null) {
            return;
        }

        if (mobLevelingManager != null) {
            mobLevelingManager.enableDebugStoreMobLevels(store);
        }
        requestFullMobRescale();
        LOGGER.atInfo().log(
                "[MOB_SHUTDOWN_DEBUG] debugResumeStore storeId=%d suppressed=false requestedFullRescale=true",
                Integer.toUnsignedLong(System.identityHashCode(store)));
    }

    public boolean isDebugStoreSuppressed(Store<EntityStore> store) {
        return mobLevelingManager != null && mobLevelingManager.isDebugStoreSuppressed(store);
    }

    @Override
    public void delayedTick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            cleanupStoreRuntimeState(store);
            return;
        }

        knownStoresForCleanup.add(store);

        if (mobLevelingManager != null && mobLevelingManager.isDebugStoreSuppressed(store)) {
            cleanupStoreRuntimeState(store);
            return;
        }

        if (mobLevelingManager == null || !mobLevelingManager.isMobLevelingEnabled(store))
            return;

        cachedDebugMobLevelFlow = isDebugSectionEnabled(DEBUG_SECTION_MOB_LEVEL_FLOW);
        cachedDebugMobCommonDefense = isDebugSectionEnabled(DEBUG_SECTION_MOB_COMMON_DEFENSE);
        cachedDebugMobLevelNameplate = isDebugSectionEnabled(DEBUG_SECTION_MOB_LEVEL_NAMEPLATE);
        cachedDebugMobHealthNameplate = isDebugSectionEnabled(DEBUG_SECTION_MOB_HEALTH_NAMEPLATE);

        boolean showMobLevelUi = mobLevelingManager.shouldRenderMobNameplate();
        boolean showLevelInNameplate = mobLevelingManager.shouldShowMobNameplateLevel();
        boolean showNameInNameplate = mobLevelingManager.shouldShowMobNameplateName();
        boolean showHealthInNameplate = mobLevelingManager.shouldShowMobNameplateHealth();
        int nameplateUpdateTicks = mobLevelingManager.getMobNameplateUpdateTicks();
        // Nameplate_Update_Ticks is compared directly against Hytale world ticks (20 TPS).
        // Obtained via store.getExternalData().getWorld().getTick() through reflection.
        boolean healthDisplayActive = showHealthInNameplate;
        boolean renderAnyNameplateText = showMobLevelUi
            && (showLevelInNameplate || showNameInNameplate || showHealthInNameplate);
        boolean shouldResetAllMobs = fullMobRescaleRequested.getAndSet(false);

        if (shouldResetAllMobs) {
            mobLevelingManager.clearAllEntityLevelOverrides();
            mobLevelingManager.clearMobTypeBlacklistCache();
            entityStates.clear();
        }

        long elapsedMillis = Math.max(1L, Math.round(Math.max(0.0f, deltaSeconds) * 1000.0f));
        long currentTimeMillis = (systemTimeMillis += elapsedMillis);
        List<PlayerChunkViewport> playerChunkViewports = snapshotPlayerChunkViewports(store);

        if (playerChunkViewports.isEmpty() && !shouldResetAllMobs) {
            pruneStaleEntities(currentTimeMillis);
            return;
        }

        long currentWorldTick = resolveWorldTick(store);
        store.forEachChunk(ENTITY_QUERY,
                (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        processEntity(
                                chunk.getReferenceTo(i),
                                commandBuffer,
                                store,
                                renderAnyNameplateText,
                                showLevelInNameplate,
                                showNameInNameplate,
                                healthDisplayActive,
                                nameplateUpdateTicks,
                                currentWorldTick,
                                currentTimeMillis,
                            playerChunkViewports);
                    }
                });
    }

    private long resolveWorldTick(Store<EntityStore> store) {
        try {
            EntityStore externalData = store.getExternalData();
            if (externalData == null) return 0L;
            Object world = externalData.getWorld();
            if (world == null) return 0L;
            if (cachedGetTickMethod == null) {
                cachedGetTickMethod = world.getClass().getMethod("getTick");
            }
            Object result = cachedGetTickMethod.invoke(world);
            return result instanceof Number n ? n.longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private void cleanupStoreRuntimeState(Store<EntityStore> store) {
        if (store != null) {
            knownStoresForCleanup.remove(store);
        } else if (!knownStoresForCleanup.isEmpty()) {
            knownStoresForCleanup.clear();
        }

        if (entityStates.isEmpty()) {
            if (store != null && mobLevelingManager != null) {
                mobLevelingManager.clearTierLockForStore(store);
            }
            return;
        }

        Iterator<Map.Entry<Long, EntityRuntimeState>> iterator = entityStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, EntityRuntimeState> entry = iterator.next();
            Long entityKey = entry.getKey();
            EntityRuntimeState state = entry.getValue();
            if (state == null) {
                iterator.remove();
                continue;
            }

            if (store != null && state.trackedStore != store) {
                continue;
            }

            iterator.remove();

            if (state.trackedEntityId >= 0) {
                summonHealthAnomalyLogTimes.remove(state.trackedEntityId);
                if (mobLevelingManager != null) {
                    mobLevelingManager.clearEntityLevelOverride(state.trackedStore, state.trackedEntityId);
                }
            }
            if (mobLevelingManager != null && entityKey != null) {
                mobLevelingManager.forgetEntityByKey(entityKey);
            }
        }

        if (store != null && mobLevelingManager != null) {
            mobLevelingManager.clearTierLockForStore(store);
        }
    }

    private void processEntity(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            Store<EntityStore> store,
            boolean showMobLevelUi,
            boolean showLevelInNameplate,
            boolean showNameInNameplate,
            boolean showHealthInNameplate,
            int nameplateUpdateTicks,
            long currentWorldTick,
            long currentTimeMillis,
            List<PlayerChunkViewport> playerChunkViewports) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.isValid()) {
            return;
        }

        NPCEntity npcEntity = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) {
            return;
        }

        TrackingIdentity trackingIdentity = resolveTrackingIdentity(ref, commandBuffer);
        long entityKey = trackingIdentity.key();
        EntityRuntimeState state = getOrCreateEntityState(entityKey, ref.getIndex(), store);
        state.lastSeenTimeMillis = currentTimeMillis;

        // Entity key migration: when a mob's tracking key changes (e.g., UUID component
        // becomes available after initial index-based tracking), carry over runtime state
        // and manager-side snapshots to prevent level/XP desync.
        if (state.lastTrackingKey != -1L && state.lastTrackingKey != entityKey) {
            // Key changed on a subsequent tick — migrate from old key
            EntityRuntimeState oldKeyState = entityStates.remove(state.lastTrackingKey);
            if (oldKeyState != null && oldKeyState != state && oldKeyState.appliedLevel > 0
                    && state.appliedLevel <= 0) {
                state.copyFrom(oldKeyState);
            }
            migrateEntityKeySnapshots(state.lastTrackingKey, entityKey);
            if (cachedDebugMobLevelFlow) {
                LOGGER.atInfo().log(
                        "[MOB_LEVEL_FLOW] entity=%d keyMigration oldKey=%d newKey=%d migratedLevel=%d",
                        ref.getIndex(), state.lastTrackingKey, entityKey, state.appliedLevel);
            }
        } else if (state.lastTrackingKey == -1L && state.appliedLevel <= 0
                && trackingIdentity.uuidBacked()) {
            // First tick with UUID-based key: check if an index-based state already exists
            // from an earlier tick when the UUID component wasn't available yet.
            long indexKey = toEntityKey(store, ref.getIndex());
            if (indexKey != entityKey) {
                EntityRuntimeState indexState = entityStates.remove(indexKey);
                if (indexState != null && indexState.appliedLevel > 0) {
                    state.copyFrom(indexState);
                    migrateEntityKeySnapshots(indexKey, entityKey);
                    if (cachedDebugMobLevelFlow) {
                        LOGGER.atInfo().log(
                                "[MOB_LEVEL_FLOW] entity=%d indexKeyMigration indexKey=%d uuidKey=%d migratedLevel=%d",
                                ref.getIndex(), indexKey, entityKey, state.appliedLevel);
                    }
                }
            }
        }
        state.lastTrackingKey = entityKey;

        boolean mobLevelingEnabled = mobLevelingManager.isMobLevelingEnabled(store);
        boolean worldBlacklisted = mobLevelingManager.isWorldXpBlacklisted(store);
        boolean entityBlacklisted;
        if (!mobLevelingEnabled || worldBlacklisted) {
            entityBlacklisted = false; // skip the expensive per-entity check
        } else if (state.cachedBlacklistStatus != 0
                && currentTimeMillis < state.nextBlacklistCheckMillis) {
            entityBlacklisted = state.cachedBlacklistStatus == 2;
        } else {
            entityBlacklisted = mobLevelingManager.isEntityBlacklisted(ref, store, commandBuffer);
            state.cachedBlacklistStatus = (byte) (entityBlacklisted ? 2 : 1);
            state.nextBlacklistCheckMillis = currentTimeMillis + BLACKLIST_RECHECK_INTERVAL_MILLIS;
        }
        if (!mobLevelingEnabled || worldBlacklisted || entityBlacklisted) {
            if (!state.blockedStateCleared) {
                clearMobLevelingStateForBlockedEntity(ref, commandBuffer, state);
                state.blockedStateCleared = true;
            }
            return;
        }
        // Entity transitioned from blocked to allowed — allow future re-clearing if needed.
        state.blockedStateCleared = false;

        if (!isWithinActivePlayerChunk(ref, commandBuffer, playerChunkViewports)) {
            clearTrackedNameplateIfNeeded(ref, commandBuffer, state);
            return;
        }

        ensureDeadComponentWhenZeroHp(ref, commandBuffer);
        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
            clearTrackedNameplateIfNeeded(ref, commandBuffer, state);
            return;
        }

        Integer mobLevel;
        if (state.appliedLevel > 0) {
            // Hot path optimization: once assigned, reuse the cached level and skip expensive
            // world/viewport resolution every tick.
            mobLevel = state.appliedLevel;
        } else {
            mobLevel = resolveAndAssignLevelOnce(ref, store, commandBuffer, ref.getIndex(), state);
        }

        if (mobLevel == null || mobLevel <= 0) {
            clearTrackedNameplateIfNeeded(ref, commandBuffer, state);
            return;
        }

        // Keep the manager-side level snapshot fresh so other systems (death XP, combat probes,
        // announcers) read the same assigned level that mob flow is using, even if they execute
        // outside this system's runtime-state map.
        if (state.lastRecordedResolvedLevel != mobLevel) {
            mobLevelingManager.recordEntityResolvedLevel(entityKey, mobLevel);
            long fallbackEntityKey = toEntityKey(store, ref.getIndex());
            if (fallbackEntityKey != entityKey) {
                mobLevelingManager.recordEntityResolvedLevel(fallbackEntityKey, mobLevel);
            }
            state.lastRecordedResolvedLevel = mobLevel;

            // Seed the health snapshot immediately on first level assignment so that XP
            // calculations are never stuck with cached=-1 when a mob dies before
            // applyHealthModifier runs.  This records the mob's current native max HP as a
            // conservative floor; applyHealthModifier will overwrite it with the scaled value
            // once health is processed.
            if (mobLevelingManager.getEntityMaxHealthSnapshot(entityKey) <= 0f) {
                EntityStatMap statMapForSeed = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
                if (statMapForSeed != null) {
                    EntityStatValue hpStat = statMapForSeed.get(DefaultEntityStatTypes.getHealth());
                    if (hpStat != null && Float.isFinite(hpStat.getMax()) && hpStat.getMax() > 0f) {
                        mobLevelingManager.recordEntityMaxHealth(entityKey, hpStat.getMax());
                        if (fallbackEntityKey != entityKey) {
                            mobLevelingManager.recordEntityMaxHealth(fallbackEntityKey, hpStat.getMax());
                        }
                    }
                }
            }
        }

        if (state.appliedLevel <= 0) {
            state.appliedLevel = mobLevel;
            // Notify external mods that a mob has been assigned its level for the first time.
            EndlessLevelingAPI.get().fireMobPostProcessListeners(ref, store, commandBuffer, mobLevel);
        }

        // Apply/retry mob augment registration and passive-stat ticks before health scaling.
        // This guarantees LIFE_FORCE and other health-related passive bonuses are available
        // when applyHealthModifier snapshots and applies max-health layers.
        applyMobAugments(ref, store, commandBuffer, currentTimeMillis, trackingIdentity, state);

        // Health scaling must happen regardless of nameplate display setting, as it affects
        // both mob durability and XP calculation. The Show_Health setting only controls UI display.
        // Only allow the re-settle trigger once the first passive tick has fired; while
        // augmentPassiveDeferActive is true the life_force bonus is not yet available, so
        // re-applying health early would cache a zero-lifeForce result and leave the flag
        // permanently pending. The applyMobAugments path forces settled=0 directly when
        // the passive fires so this guard does not prevent the post-passive re-settle.
        if (state.augmentHealthReconcilePending && !state.augmentPassiveDeferActive
                && state.settledHealthLevel == mobLevel) {
            // Augments became available after initial settle; do exactly one reconcile apply
            // so augmented health layers are included without recurring per-tick reapplication.
            state.settledHealthLevel = 0;
            state.nextHealthApplyAttemptMillis = currentTimeMillis;
        }

        if (state.settledHealthLevel != mobLevel) {
            boolean attemptedHealthApply = false;
            boolean healthApplied = false;
            if (currentTimeMillis >= state.nextHealthApplyAttemptMillis) {
                attemptedHealthApply = true;
                healthApplied = applyHealthModifier(ref, commandBuffer, mobLevel, entityKey);
                if (healthApplied) {
                    state.settledHealthLevel = mobLevel;
                    state.nextHealthApplyAttemptMillis = currentTimeMillis;
                    // Don't clear augmentHealthReconcilePending while the first passive tick
                    // is still deferred — life_force bonus is not yet in runtimeState, so
                    // the reconcile re-settle must fire again once the passive tick completes.
                    if (!state.augmentPassiveDeferActive) {
                        state.augmentHealthReconcilePending = false;
                    }
                    // Max HP just changed: force nameplate re-render on the next cycle.
                    // We null lastAppliedNameplateText rather than resetting the cached HP
                    // values to NaN: NaN comparisons always return false, which would
                    // permanently block the healthChangedForNameplate delta check and cause
                    // both the max HP and current HP to stop updating in the nameplate.
                    state.lastAppliedNameplateText = null;
                    state.lastKnownAtFullHealth = false;
                } else {
                    state.nextHealthApplyAttemptMillis = currentTimeMillis + FLOW_HEALTH_RETRY_INTERVAL_MILLIS;
                }
            }

            if (cachedDebugMobLevelFlow
                    && (healthApplied || (attemptedHealthApply
                            && currentTimeMillis - state.lastHealthFlowLogMillis >= FLOW_HEALTH_LOG_COOLDOWN_MILLIS))) {
                LOGGER.atInfo().log(
                        "[MOB_LEVEL_FLOW] entity=%d uuidBacked=%s phase=health level=%d applied=%s settled=%d nextRetryInMs=%d",
                        ref.getIndex(),
                        trackingIdentity.uuidBacked(),
                        mobLevel,
                        healthApplied,
                        state.settledHealthLevel,
                        Math.max(0L, state.nextHealthApplyAttemptMillis - currentTimeMillis));
                state.lastHealthFlowLogMillis = currentTimeMillis;
            }
        }

        if (showMobLevelUi) {
                EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
                EntityStatValue hp = (showHealthInNameplate && statMap != null)
                    ? statMap.get(DefaultEntityStatTypes.getHealth())
                    : null;
            float currentHpForNameplate = hp != null ? hp.get() : Float.NaN;
            float currentMaxHpForNameplate = hp != null ? hp.getMax() : Float.NaN;
            boolean hasFiniteHealthForNameplate = Float.isFinite(currentHpForNameplate)
                && Float.isFinite(currentMaxHpForNameplate)
                && currentMaxHpForNameplate > 0.0f;

            // Per-entity health update cadence: compare directly against Hytale world ticks
            // (20 TPS). currentWorldTick = 0 means the world tick is unavailable this cycle
            // (e.g. world not yet ready); treat as always elapsed to avoid blocking updates.
            boolean tickWindowElapsed = currentWorldTick <= 0L
                || nameplateUpdateTicks <= 1
                || (currentWorldTick - state.lastHealthUpdateTick) >= nameplateUpdateTicks;
            boolean healthChangedForNameplate =
                showHealthInNameplate
                    && tickWindowElapsed
                    && hasFiniteHealthForNameplate
                    && (Math.abs(currentHpForNameplate - state.lastNameplateHealthValue) > 0.0001f
                        || Math.abs(currentMaxHpForNameplate - state.lastNameplateMaxHealthValue) > 0.0001f);
            if (cachedDebugMobHealthNameplate && showHealthInNameplate) {
                MobLevelingManager.MobHealthCompositionSnapshot hcs =
                    mobLevelingManager.getEntityHealthCompositionSnapshot(entityKey);
                Float trueBase = mobLevelingManager.getTrueBaseHealth(entityKey);
                NAMEPLATE_LOGGER.atInfo().log(
                    "[MOB_HEALTH_NAMEPLATE] entity=%d level=%d settled=%d gameTick=%d lastTick=%d elapsed=%d threshold=%d tickWindowElapsed=%s"
                    + " hp=%.3f/%.3f lastHp=%.3f lastMax=%.3f hpDelta=%s changed=%s"
                    + " trueBase=%s baseMax=%s scaledMax=%s lifeForce=%s combinedMax=%s",
                    ref.getIndex(),
                    mobLevel,
                    state.settledHealthLevel,
                    currentWorldTick,
                    state.lastHealthUpdateTick,
                    currentWorldTick - state.lastHealthUpdateTick,
                    (long) nameplateUpdateTicks,
                    tickWindowElapsed,
                    currentHpForNameplate,
                    currentMaxHpForNameplate,
                    state.lastNameplateHealthValue,
                    state.lastNameplateMaxHealthValue,
                    hasFiniteHealthForNameplate ? String.format("%.3f", currentHpForNameplate - state.lastNameplateHealthValue) : "NaN",
                    healthChangedForNameplate,
                    trueBase != null ? String.format("%.3f", trueBase) : "null",
                    hcs != null ? String.format("%.3f", hcs.baseMax()) : "null",
                    hcs != null ? String.format("%.3f", hcs.scaledMax()) : "null",
                    hcs != null ? String.format("%.3f", hcs.lifeForceBonus()) : "null",
                    hcs != null ? String.format("%.3f", hcs.combinedMax()) : "null");
            }

            if (state.lastAppliedNameplateLevel != mobLevel
                    || state.lastAppliedShowLevelInNameplate != showLevelInNameplate
                    || state.lastAppliedShowNameInNameplate != showNameInNameplate
                    || state.lastAppliedShowHealthInNameplate != showHealthInNameplate
                    || state.lastAppliedNameplateText == null
                || healthChangedForNameplate) {
                if (cachedDebugMobLevelNameplate && state.lastAppliedNameplateLevel != mobLevel) {
                    int prevLevel = state.lastAppliedNameplateLevel == Integer.MIN_VALUE ? -1 : state.lastAppliedNameplateLevel;
                    NAMEPLATE_LOGGER.atInfo().log(
                        "[MOB_LEVEL_NAMEPLATE] entity=%d uuid=%s nameplateLevel=%d prevNameplateLevel=%d uuidBacked=%s",
                        ref.getIndex(),
                        resolveUuid(ref, commandBuffer),
                        mobLevel,
                        prevLevel,
                        trackingIdentity.uuidBacked());
                }
                boolean applied = applyNameplate(
                        ref,
                        commandBuffer,
                        showLevelInNameplate,
                        showNameInNameplate,
                        showHealthInNameplate,
                    mobLevel,
                    state);
                state.lastAppliedNameplateLevel = mobLevel;
                state.lastAppliedShowLevelInNameplate = showLevelInNameplate;
                state.lastAppliedShowNameInNameplate = showNameInNameplate;
                state.lastAppliedShowHealthInNameplate = showHealthInNameplate;
                state.lastNameplateHealthValue = hasFiniteHealthForNameplate ? currentHpForNameplate : Float.NaN;
                state.lastNameplateMaxHealthValue = hasFiniteHealthForNameplate ? currentMaxHpForNameplate : Float.NaN;
                // Only stamp the health-clock when health drove this update.
                // Non-health triggers (first-time null text, level change, config change)
                // must NOT reset the clock or elapsed will always be 0 on the next sweep.
                if (healthChangedForNameplate && currentWorldTick > 0L) {
                    state.lastHealthUpdateTick = currentWorldTick;
                }
                if (cachedDebugMobLevelFlow) {
                    LOGGER.atInfo().log(
                            "[MOB_LEVEL_FLOW] entity=%d uuidBacked=%s phase=nameplate level=%d applied=%s hp=%.3f max=%.3f refreshedByHealthChange=%s finiteHealth=%s",
                            ref.getIndex(),
                            trackingIdentity.uuidBacked(),
                            mobLevel,
                            applied,
                            currentHpForNameplate,
                            currentMaxHpForNameplate,
                            healthChangedForNameplate,
                            hasFiniteHealthForNameplate);
                }
            }
        } else {
            clearTrackedNameplateIfNeeded(ref, commandBuffer, state);
        }

        if (cachedDebugMobLevelFlow && !state.flowInitializedLogged) {
            state.flowInitializedLogged = true;
            LOGGER.atInfo().log(
                    "[MOB_LEVEL_FLOW] entity=%d uuidBacked=%s initialized=true level=%d",
                    ref.getIndex(),
                    trackingIdentity.uuidBacked(),
                    mobLevel);
        }
    }

    private void pruneStaleEntities(long currentTimeMillis) {
        if (entityStates.isEmpty()) {
            return;
        }

        long expiryTimeMillis = currentTimeMillis - STALE_ENTITY_TTL_MILLIS;
        Iterator<Map.Entry<Long, EntityRuntimeState>> iterator = entityStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, EntityRuntimeState> entry = iterator.next();
            Long entityKey = entry.getKey();
            EntityRuntimeState state = entry.getValue();
            if (entityKey == null || state == null || state.lastSeenTimeMillis >= expiryTimeMillis) {
                continue;
            }

            iterator.remove();

            if (state.trackedEntityId >= 0) {
                summonHealthAnomalyLogTimes.remove(state.trackedEntityId);
                mobLevelingManager.clearEntityLevelOverride(state.trackedStore, state.trackedEntityId);
            }
            mobLevelingManager.forgetEntityByKey(entityKey);
        }
    }

    private void ensureDeadComponentWhenZeroHp(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp == null || !Float.isFinite(hp.get()) || hp.get() > 0.0001f) {
            return;
        }

        DeathComponent.tryAddComponent(
                commandBuffer,
                ref,
                new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, 0.0f));
    }

    private boolean applyHealthModifier(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            int appliedLevel,
            long entityKey) {
        if (ref == null || commandBuffer == null || mobLevelingManager == null) {
            return false;
        }

        int entityId = ref.getIndex();
        long fallbackEntityKey = toEntityKey(ref.getStore(), entityId);
        boolean hasFallbackAlias = fallbackEntityKey != -1L && fallbackEntityKey != entityKey;

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.isValid()) {
            clearMobHealthScaleModifierForPlayer(ref, commandBuffer);
            return false;
        }

        if (ArmyOfTheDeadPassive.isManagedSummon(ref, ref.getStore(), commandBuffer)) {
            EntityStatMap summonStatMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
            if (summonStatMap == null) {
                return false;
            }

            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue before = summonStatMap.get(healthIndex);
            float beforeCurrent = before != null ? before.get() : -1.0f;
            float beforeMax = before != null ? before.getMax() : -1.0f;

            summonStatMap.removeModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY);
            summonStatMap.removeModifier(healthIndex, MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY);
            EntityStatValue summonHp = summonStatMap.get(healthIndex);
            if (summonHp != null && Float.isFinite(summonHp.getMax()) && summonHp.getMax() > 0.0f) {
                summonStatMap.setStatValue(healthIndex, summonHp.getMax());
            }
            summonStatMap.update();

            EntityStatValue updatedSummonHp = summonStatMap.get(healthIndex);
            if (updatedSummonHp != null && Float.isFinite(updatedSummonHp.getMax())
                    && updatedSummonHp.getMax() > 0.0f) {
                mobLevelingManager.recordEntityMaxHealth(entityKey, updatedSummonHp.getMax());
                if (hasFallbackAlias) {
                    mobLevelingManager.recordEntityMaxHealth(fallbackEntityKey, updatedSummonHp.getMax());
                }

                float afterCurrent = updatedSummonHp.get();
                float afterMax = updatedSummonHp.getMax();
                if (Float.isFinite(afterCurrent)
                        && Float.isFinite(afterMax)
                        && afterMax > 0.0f
                        && afterCurrent < afterMax - 0.5f
                        && shouldLogSummonHealthAnomaly(entityId)) {
                        LOGGER.atInfo().log(
                            "[ARMY_OF_THE_DEAD][DEBUG-HP][LEVELING] Managed summon still below max after normalize entity=%d before=%.3f/%.3f after=%.3f/%.3f",
                            entityId,
                            beforeCurrent,
                            beforeMax,
                            afterCurrent,
                            afterMax);
                }
            }
            return true;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return false;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return false;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();

        float currentValue = healthStat.get();
        float currentMax = healthStat.getMax();
        if (!Float.isFinite(currentValue) || !Float.isFinite(currentMax) || currentMax <= 0.0f) {
            return false;
        }

        statMap.removeModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY);
        statMap.removeModifier(healthIndex, MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY);
        EntityStatValue baselineHealth = statMap.get(healthIndex);
        float baselineRead = baselineHealth != null ? baselineHealth.getMax() : currentMax;
        if (!Float.isFinite(baselineRead) || baselineRead <= 0.0f) {
            baselineRead = Math.max(1.0f, currentMax);
        }
        // Anchor baseMax to the value captured on first application (before any augment percent-health
        // passives have run). Augment passives such as goliath (+20%) and raid_boss (+50%) add their own
        // modifier keys that we intentionally leave in the map so they remain effective. Without this
        // anchor, those augment modifiers would be included in baseMax on every subsequent tick, causing
        // the level-scaling multiplier to compound on an ever-growing base and produce billions of HP.
        mobLevelingManager.cacheTrueBaseHealth(entityKey, baselineRead);
        if (hasFallbackAlias) {
            mobLevelingManager.cacheTrueBaseHealth(fallbackEntityKey, baselineRead);
        }
        Float cachedBase = mobLevelingManager.getTrueBaseHealth(entityKey);
        float baseMax = (cachedBase != null && Float.isFinite(cachedBase) && cachedBase > 0.0f)
                ? cachedBase
                : baselineRead;

        float lifeForceBonus = resolveMobLifeForceHealthBonus(ref, commandBuffer);

        if (!mobLevelingManager.isMobHealthScalingEnabled(ref.getStore())) {
            float targetMax = Math.max(1.0f, baseMax + lifeForceBonus);
            if (lifeForceBonus > 0.0001f) {
                try {
                    StaticModifier lifeForceModifier = new StaticModifier(
                            ModifierTarget.MAX,
                            CalculationType.ADDITIVE,
                            lifeForceBonus);
                    statMap.putModifier(healthIndex, MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY, lifeForceModifier);
                } catch (Exception e) {
                    return false;
                }
            }

            float ratio = currentMax > 0.0f ? currentValue / currentMax : 1.0f;
            float restoredValue = Math.max(0.0f, Math.min(targetMax, ratio * targetMax));
            if (currentValue <= 0.0f) {
                restoredValue = 0.0f;
            }
            statMap.setStatValue(healthIndex, restoredValue);
            statMap.update();
            EntityStatValue restoredHealth = statMap.get(healthIndex);
            if (restoredHealth != null && Float.isFinite(restoredHealth.getMax()) && restoredHealth.getMax() > 0.0f) {
                mobLevelingManager.recordEntityMaxHealth(entityKey, restoredHealth.getMax());
                if (hasFallbackAlias) {
                    mobLevelingManager.recordEntityMaxHealth(fallbackEntityKey, restoredHealth.getMax());
                }
                mobLevelingManager.recordEntityHealthComposition(
                    entityKey,
                        baseMax,
                        baseMax,
                        lifeForceBonus,
                        restoredHealth.getMax());
                if (hasFallbackAlias) {
                    mobLevelingManager.recordEntityHealthComposition(
                        fallbackEntityKey,
                        baseMax,
                        baseMax,
                        lifeForceBonus,
                        restoredHealth.getMax());
                }
                if (cachedDebugMobCommonDefense) {
                    LOGGER.atInfo().log(
                            "[MOB_COMMON_DEFENSE][HEALTH_AUDIT] entity=%d level=%d scalingEnabled=false baseMax=%.3f scaledMax=%.3f lifeForceBonus=%.3f expectedCombinedMax=%.3f current=%.3f actualMax=%.3f",
                            entityId,
                            appliedLevel,
                            baseMax,
                            baseMax,
                            lifeForceBonus,
                            targetMax,
                            restoredHealth.get(),
                            restoredHealth.getMax());
                }
                return true;
            }
            // statMap.update() did not produce valid health yet; signal failure so the
            // retry path re-attempts on the next cycle instead of permanently settling.
            return false;
        }

        MobLevelingManager.MobHealthScalingResult scaled = mobLevelingManager.computeMobHealthScaling(
                ref,
                ref.getStore(),
                commandBuffer,
                appliedLevel,
                baseMax,
                currentMax,
                currentValue);

        float targetMax = scaled.targetMax();
        float targetMaxWithAugments = Math.max(1.0f, targetMax + lifeForceBonus);
        float ratio = currentMax > 0.0f ? currentValue / currentMax : 1.0f;
        float targetValue = Math.max(0.0f, Math.min(targetMaxWithAugments, ratio * targetMaxWithAugments));
        if (currentValue <= 0.0f) {
            targetValue = 0.0f;
        }
        if (!Float.isFinite(targetMax) || targetMax <= 0.0f || !Float.isFinite(targetValue)) {
            return false;
        }

        float additive = scaled.additive();
        if (Math.abs(additive) > 0.0001f) {
            try {
                StaticModifier modifier = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, additive);
                statMap.putModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY, modifier);
            } catch (Exception e) {
                return false;
            }
        }

        if (lifeForceBonus > 0.0001f) {
            try {
                StaticModifier lifeForceModifier = new StaticModifier(
                        ModifierTarget.MAX,
                        CalculationType.ADDITIVE,
                        lifeForceBonus);
                statMap.putModifier(healthIndex, MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY, lifeForceModifier);
            } catch (Exception e) {
                return false;
            }
        }

        statMap.setStatValue(healthIndex, targetValue);
        statMap.update();
        EntityStatValue updatedHealth = statMap.get(healthIndex);
        if (updatedHealth == null || !Float.isFinite(updatedHealth.getMax()) || updatedHealth.getMax() <= 0.0f) {
            // statMap.update() did not produce valid health yet; signal failure so the
            // retry path re-attempts on the next cycle instead of permanently settling.
            return false;
        }
        mobLevelingManager.recordEntityMaxHealth(entityKey, updatedHealth.getMax());
        if (hasFallbackAlias) {
            mobLevelingManager.recordEntityMaxHealth(fallbackEntityKey, updatedHealth.getMax());
        }
        mobLevelingManager.recordEntityHealthComposition(
            entityKey,
            baseMax,
            targetMax,
            lifeForceBonus,
            updatedHealth.getMax());
        if (hasFallbackAlias) {
            mobLevelingManager.recordEntityHealthComposition(
                fallbackEntityKey,
                baseMax,
                targetMax,
                lifeForceBonus,
                updatedHealth.getMax());
        }
        if (cachedDebugMobCommonDefense) {
            LOGGER.atInfo().log(
                    "[MOB_HEALTH_LAYER_DEBUG] entity=%d level=%d baseMax=%.3f scaledMax=%.3f lifeForceBonus=%.3f finalMax=%.3f current=%.3f ratio=%.4f",
                    entityId,
                    appliedLevel,
                    baseMax,
                    targetMax,
                    lifeForceBonus,
                    updatedHealth.getMax(),
                    updatedHealth.get(),
                    ratio);
        }
        if (cachedDebugMobCommonDefense) {
            LOGGER.atInfo().log(
                    "[MOB_COMMON_DEFENSE][HEALTH_AUDIT] entity=%d level=%d scalingEnabled=true baseMax=%.3f scaledMax=%.3f lifeForceBonus=%.3f expectedCombinedMax=%.3f current=%.3f actualMax=%.3f",
                    entityId,
                    appliedLevel,
                    baseMax,
                    targetMax,
                    lifeForceBonus,
                    targetMaxWithAugments,
                    updatedHealth.get(),
                    updatedHealth.getMax());
        }
        return true;
    }

    private void clearMobHealthScaleModifierForPlayer(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue before = statMap.get(healthIndex);
        if (before == null) {
            return;
        }

        float previousValue = before.get();
        float previousMax = before.getMax();

        statMap.removeModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY);
        statMap.removeModifier(healthIndex, MOB_AUGMENT_LIFE_FORCE_MODIFIER_KEY);

        EntityStatValue baseline = statMap.get(healthIndex);
        float baselineMax = baseline != null ? baseline.getMax() : previousMax;
        if (!Float.isFinite(previousMax) || previousMax <= 0.0f
                || !Float.isFinite(baselineMax) || baselineMax <= 0.0f) {
            statMap.update();
            return;
        }

        if (Math.abs(previousMax - baselineMax) <= 0.0001f) {
            return;
        }

        float ratio = previousValue / previousMax;
        float restoredValue = Math.max(0.0f, Math.min(baselineMax, ratio * baselineMax));
        if (!Float.isFinite(restoredValue)) {
            restoredValue = Math.max(0.0f, Math.min(baselineMax, previousValue));
        }
        if (previousValue <= 0.0f) {
            restoredValue = 0.0f;
        }

        statMap.setStatValue(healthIndex, restoredValue);
        statMap.update();
    }

    private float resolveMobLifeForceHealthBonus(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return 0.0f;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null || uuidComponent.getUuid() == null) {
            return 0.0f;
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return 0.0f;
        }

        MobAugmentExecutor mobAugmentExecutor = plugin.getMobAugmentExecutor();
        if (mobAugmentExecutor == null) {
            return 0.0f;
        }

        double bonus = mobAugmentExecutor.getAttributeBonus(uuidComponent.getUuid(), SkillAttributeType.LIFE_FORCE);
        if (!Double.isFinite(bonus) || bonus <= 0.0D) {
            return 0.0f;
        }
        return (float) bonus;
    }

    private void tickMobPassiveAugmentStats(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            UUID entityUuid,
            MobAugmentExecutor executor) {
        if (ref == null || commandBuffer == null || entityUuid == null || executor == null) {
            return;
        }

        if (!executor.hasMobAugments(entityUuid)) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        executor.tickPassiveStats(entityUuid, ref, commandBuffer, statMap);
    }

    private boolean ensureMobAugmentsRegistered(Ref<EntityStore> ref,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            UUID mobUuid) {
        if (ref == null || store == null || commandBuffer == null || mobLevelingManager == null) {
            return false;
        }

        if (mobUuid == null) {
            return false;
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null || plugin.getAugmentManager() == null || plugin.getAugmentRuntimeManager() == null) {
            return false;
        }

        MobAugmentExecutor executor = plugin.getMobAugmentExecutor();
        if (executor == null) {
            return false;
        }

        if (executor.hasMobAugments(mobUuid)) {
            return false;
        }

        List<String> augmentIds = mobLevelingManager.getMobOverrideAugmentIds(ref, store, commandBuffer);
        if (augmentIds == null || augmentIds.isEmpty()) {
            return false;
        }

        executor.registerMobAugments(
                mobUuid,
                augmentIds,
                plugin.getAugmentManager(),
                plugin.getAugmentRuntimeManager());
        return true;
    }

    private void applyMobAugments(Ref<EntityStore> ref,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            long currentTimeMillis,
            TrackingIdentity trackingIdentity,
            EntityRuntimeState state) {
        if (ref == null || store == null || commandBuffer == null) {
            return;
        }
        if (trackingIdentity == null || state == null) {
            return;
        }

        if (state.nextMobAugmentRegistrationCheckMillis <= 0L
                || currentTimeMillis >= state.nextMobAugmentRegistrationCheckMillis) {
            boolean alreadyInitialized = state.mobAugmentsInitialized;
            boolean registeredNow = ensureMobAugmentsRegistered(ref, store, commandBuffer, trackingIdentity.uuid());
            if (registeredNow) {
                state.mobAugmentsInitialized = true;
                state.mobHasAugments = true;
                if (!alreadyInitialized) {
                    state.augmentHealthReconcilePending = true;
                }
                state.nextMobAugmentRegistrationCheckMillis = 0L;
                // Defer the first passive tick by just enough time for applyHealthModifier
                // to run once and cache trueBaseHealth (one system cycle = ~100ms).
                // 200ms covers two cycles as a safe buffer — far less than the old 1000ms
                // which was the main reason life_force applied so slowly on fresh spawns.
                // The ongoing passive tick rate (PASSIVE_STAT_TICK_INTERVAL_MILLIS) is
                // unchanged; only the initial post-registration defer is shortened.
                state.lastPassiveStatTickMillis = currentTimeMillis - (PASSIVE_STAT_TICK_INTERVAL_MILLIS - 200L);
                state.augmentPassiveDeferActive = true;
                if (cachedDebugMobLevelFlow) {
                    LOGGER.atInfo().log(
                            "[MOB_LEVEL_FLOW] entity=%d uuidBacked=%s phase=augments registered=%s",
                            ref.getIndex(),
                            trackingIdentity.uuidBacked(),
                            true);
                }
            } else {
                // Two cases both return false from ensureMobAugmentsRegistered:
                //   (a) mob already has augments registered (hasMobAugments UUID guard)
                //   (b) mob genuinely has no augments to assign
                // Only clear mobHasAugments for case (b). For case (a) the mob is
                // initialized and the passive-tick path must keep running so the
                // reconciler can maintain goliath/raidBoss % modifiers.
                if (!state.mobAugmentsInitialized) {
                    state.mobHasAugments = false;
                }
                // For uninitialized mobs, use a short retry — registration often fails on
                // the first tick because the UUID component isn't attached to a fresh spawn
                // yet.  A 3-second delay here is the main cause of visible slowness.
                // For already-initialized mobs (case a), the longer interval keeps the
                // periodic recheck cheap.
                state.nextMobAugmentRegistrationCheckMillis = currentTimeMillis
                        + (alreadyInitialized ? 3000L : 200L);
            }
        }

        if (!state.mobHasAugments) {
            return;
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null || plugin.getMobAugmentExecutor() == null) {
            return;
        }

        // Use the UUID already resolved by resolveTrackingIdentity at the top
        // of processEntity — avoids a redundant UUIDComponent lookup per tick.
        UUID mobUuid = trackingIdentity.uuid();
        if (mobUuid == null) {
            return; // Skip passive ticking if we can't resolve UUID
        }

        if (state.lastPassiveStatTickMillis > 0L
                && currentTimeMillis - state.lastPassiveStatTickMillis < PASSIVE_STAT_TICK_INTERVAL_MILLIS) {
            return;
        }

        tickMobPassiveAugmentStats(ref, commandBuffer, mobUuid, plugin.getMobAugmentExecutor());
        state.lastPassiveStatTickMillis = currentTimeMillis;
        state.augmentPassiveDeferActive = false;
        // Passive just fired for the first time (or is re-ticking). Force an immediate
        // health re-settle so augment bonuses (life_force, etc.) that were just written
        // to runtimeState are picked up by applyHealthModifier in this same processEntity
        // cycle rather than waiting a full tick for the re-settle trigger.
        if (state.augmentHealthReconcilePending) {
            state.settledHealthLevel = 0;
            state.nextHealthApplyAttemptMillis = currentTimeMillis;
        }
    }

        private boolean applyNameplate(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            boolean showLevelInNameplate,
            boolean showNameInNameplate,
            boolean showHealthInNameplate,
            int mobLevel,
            EntityRuntimeState state) {
        if (ref == null || commandBuffer == null) {
            return false;
        }

        NPCEntity npcEntity = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) {
            clearOrRemoveNameplate(ref, commandBuffer);
            return false;
        }

        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
            clearOrRemoveNameplate(ref, commandBuffer);
            return false;
        }

        String baseName = "Mob";
        DisplayNameComponent display = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType());
        if (display != null) {
            Message displayNameMsg = display.getDisplayName();
            if (displayNameMsg != null) {
                baseName = resolveEntityDisplayName(displayNameMsg);
            }
        }

        StringBuilder labelBuilder = new StringBuilder();
        if (showLevelInNameplate) {
            labelBuilder.append("[Lv.").append(mobLevel).append("] ");
        }
        if (showNameInNameplate) {
            labelBuilder.append(baseName);
        }
        
        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (showHealthInNameplate && statMap != null) {
            EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
            float hpValue = hp != null ? hp.get() : Float.NaN;
            float hpMax = hp != null ? hp.getMax() : Float.NaN;
            if (Float.isFinite(hpValue) && Float.isFinite(hpMax) && hpMax > 0.0f) {
                if (labelBuilder.length() > 0) {
                    labelBuilder.append(" ");
                }
                labelBuilder.append(" [").append(Math.round(hpValue)).append("/").append(Math.round(hpMax)).append("]");
            }
        }

        String label = labelBuilder.toString();
        if (label.isBlank()) {
            clearOrRemoveNameplate(ref, commandBuffer);
            return false;
        }

        if (state != null) {
            state.lastAppliedNameplateText = label;
        }

        if (NameplateBuilderCompatibility.isAvailable()) {
            if (showLevelInNameplate) {
                NameplateBuilderCompatibility.registerELShowLevel(ref.getStore(), ref, mobLevel);
            } else {
                NameplateBuilderCompatibility.removeELShowLevel(ref.getStore(), ref);
            }

            if (showNameInNameplate) {
                NameplateBuilderCompatibility.registerELShowName(ref.getStore(), ref, baseName);
            } else {
                NameplateBuilderCompatibility.removeELShowName(ref.getStore(), ref);
            }

            if (showHealthInNameplate && statMap != null) {
                EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
                float hpValue = hp != null ? hp.get() : Float.NaN;
                float hpMax = hp != null ? hp.getMax() : Float.NaN;
                if (Float.isFinite(hpValue) && Float.isFinite(hpMax) && hpMax > 0.0f) {
                    NameplateBuilderCompatibility.registerELShowHealth(ref.getStore(), ref, hpValue, hpMax);
                } else {
                    NameplateBuilderCompatibility.removeELShowHealth(ref.getStore(), ref);
                }
            } else {
                NameplateBuilderCompatibility.removeELShowHealth(ref.getStore(), ref);
            }
        }

        Nameplate nameplate = commandBuffer.getComponent(ref, Nameplate.getComponentType());
        if (nameplate != null) {
            nameplate.setText(label);
        } else {
            commandBuffer.run(store -> {
                if (ref.isValid()) {
                    store.ensureAndGetComponent(ref, Nameplate.getComponentType()).setText(label);
                }
            });
        }
        return true;
    }

    private boolean shouldLogSummonHealthAnomaly(int entityId) {
        long now = System.currentTimeMillis();
        Long last = summonHealthAnomalyLogTimes.get(entityId);
        if (last != null && now - last < SUMMON_HEALTH_ANOMALY_LOG_COOLDOWN_MS) {
            return false;
        }
        summonHealthAnomalyLogTimes.put(entityId, now);
        return true;
    }

    private void clearOrRemoveNameplate(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        if (NameplateBuilderCompatibility.isAvailable()) {
            NameplateBuilderCompatibility.removeELShowLevel(ref.getStore(), ref);
            NameplateBuilderCompatibility.removeELShowName(ref.getStore(), ref);
            NameplateBuilderCompatibility.removeELShowHealth(ref.getStore(), ref);
            NameplateBuilderCompatibility.removeSummonText(ref.getStore(), ref);
        }

        Nameplate nameplate = commandBuffer.getComponent(ref, Nameplate.getComponentType());
        if (nameplate != null) {
            commandBuffer.removeComponent(ref, Nameplate.getComponentType());
                LOGGER.atInfo().log("[MOB_SHUTDOWN_DEBUG] entity=%d uuid=%s removedNameplateComponent=true",
                    ref.getIndex(),
                    resolveUuid(ref, commandBuffer));
        }
    }

    private void clearTrackedNameplateIfNeeded(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            EntityRuntimeState state) {
        if (state == null || !state.hasNameplateState()) {
            return;
        }
        clearOrRemoveNameplate(ref, commandBuffer);
        resetNameplateState(state);
    }

    private void clearMobLevelingStateForBlockedEntity(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            EntityRuntimeState state) {
        if (ref == null) {
            return;
        }

        stripMobHealthModifiers(ref, commandBuffer);
        clearTrackedNameplateIfNeeded(ref, commandBuffer, state);
        // Do NOT call clearMobLevelRuntimeStateForEntity here — that would wipe the
        // UUID-keyed level snapshot that XpEventSystem needs at death time.  A mob can
        // die in the same tick it becomes "blocked" (world unidentifiable for one tick,
        // world‑XP blacklist transient flip, etc.) and we must not orphan that snapshot.
        // Instead, clean up only health/augment state and let EntityRuntimeState age out
        // through the normal TTL prune path.
        clearHealthStateForBlockedEntity(ref, commandBuffer);
        markEntityChunkDirty(ref, commandBuffer);
    }

    private void clearHealthStateForBlockedEntity(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || mobLevelingManager == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        int entityId = ref.getIndex();
        if (entityId >= 0) {
            mobLevelingManager.clearEntityLevelOverride(store, entityId);
            summonHealthAnomalyLogTimes.remove(entityId);
        }
        long fallbackEntityKey = toEntityKey(store, entityId);
        mobLevelingManager.forgetEntityHealthByKey(fallbackEntityKey);
        TrackingIdentity identity = resolveTrackingIdentity(ref, commandBuffer);
        if (identity != null && identity.key() != fallbackEntityKey) {
            mobLevelingManager.forgetEntityHealthByKey(identity.key());
        }
    }

    /**
     * Copies manager-side snapshots (level, health composition, true base health) from
     * {@code sourceKey} to {@code targetKey}. Used during entity key migration when the
     * tracking key changes (e.g., UUID component becomes available after index-based tracking).
     */
    private void migrateEntityKeySnapshots(long sourceKey, long targetKey) {
        if (sourceKey == targetKey || sourceKey == -1L || targetKey == -1L || mobLevelingManager == null) {
            return;
        }

        Integer level = mobLevelingManager.getEntityResolvedLevelSnapshot(sourceKey);
        if (level != null && level > 0) {
            mobLevelingManager.recordEntityResolvedLevel(targetKey, level);
        }

        MobLevelingManager.MobHealthCompositionSnapshot healthSnapshot =
                mobLevelingManager.getEntityHealthCompositionSnapshot(sourceKey);
        if (healthSnapshot != null && Float.isFinite(healthSnapshot.combinedMax())
                && healthSnapshot.combinedMax() > 0f) {
            mobLevelingManager.recordEntityHealthComposition(targetKey,
                    healthSnapshot.baseMax(),
                    healthSnapshot.scaledMax(),
                    healthSnapshot.lifeForceBonus(),
                    healthSnapshot.combinedMax());
        }

        Float trueBase = mobLevelingManager.getTrueBaseHealth(sourceKey);
        if (trueBase != null && Float.isFinite(trueBase) && trueBase > 0f) {
            mobLevelingManager.cacheTrueBaseHealth(targetKey, trueBase);
        }
    }

    /**
     * Resolves a translated display name from a Message, falling back to extracting
     * a readable name from the translation key when I18nModule cannot resolve it.
     */
    private static String resolveEntityDisplayName(Message msg) {
        String messageId = msg.getMessageId();
        if (messageId != null) {
            String translated = I18nModule.get().getMessage("en-US", messageId);
            if (translated != null && !translated.isBlank()) {
                return translated;
            }
            return extractNameFromTranslationKey(messageId);
        }
        String rawText = msg.getRawText();
        if (rawText != null && !rawText.isBlank()) {
            return rawText;
        }
        return "Mob";
    }

    /**
     * Extracts a human-readable name from a translation key.
     * e.g. "server.npcRoles.Skrythe_Role.name" -> "Skrythe"
     */
    private static String extractNameFromTranslationKey(String key) {
        String[] parts = key.split("\\.");
        if (parts.length >= 3) {
            // Take the segment before ".name" (typically the role identifier)
            String segment = parts[parts.length - 2];
            if (segment.endsWith("_Role")) {
                segment = segment.substring(0, segment.length() - "_Role".length());
            }
            return segment.replace("_", " ");
        }
        return "Mob";
    }

    private void resetNameplateState(EntityRuntimeState state) {
        if (state == null) {
            return;
        }
        state.lastAppliedNameplateLevel = Integer.MIN_VALUE;
        state.lastAppliedShowLevelInNameplate = false;
        state.lastAppliedShowNameInNameplate = false;
        state.lastAppliedShowHealthInNameplate = false;
        state.lastAppliedNameplateText = null;
        state.lastNameplateHealthValue = Float.NaN;
        state.lastNameplateMaxHealthValue = Float.NaN;
        state.lastKnownAtFullHealth = false;
        state.lastHealthUpdateTick = 0L;
    }

    private EntityRuntimeState getOrCreateEntityState(long entityKey, int entityId, Store<EntityStore> store) {
        EntityRuntimeState state = entityStates.computeIfAbsent(entityKey, ignored -> {
            EntityRuntimeState newState = new EntityRuntimeState();
            // Passive tick bucketing: stagger entity ticks across the 1000ms interval instead of syncing
            // Set lastPassiveStatTickMillis to a random offset in [-1000, 0] so initial tick happens spread out
            newState.lastPassiveStatTickMillis = -(PASSIVE_STAT_TICK_INTERVAL_MILLIS - (long)(random.nextDouble() * PASSIVE_STAT_TICK_INTERVAL_MILLIS));
            return newState;
        });
        if (entityId >= 0) {
            state.trackedEntityId = entityId;
        }
        if (store != null) {
            state.trackedStore = store;
        }
        return state;
    }

    private long toEntityKey(Store<EntityStore> store, int entityId) {
        long storePart = store == null ? 0L : Integer.toUnsignedLong(System.identityHashCode(store));
        long entityPart = Integer.toUnsignedLong(entityId);
        return (storePart << 32) | entityPart;
    }

    private UUID resolveUuid(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null) {
            return null;
        }

        UUIDComponent uuidComponent = null;
        if (commandBuffer != null) {
            uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        }
        if (uuidComponent == null) {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            }
        }

        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    private TrackingIdentity resolveTrackingIdentity(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null) {
            return new TrackingIdentity(-1L, false, null);
        }

        Store<EntityStore> store = ref.getStore();
        int entityId = ref.getIndex();

        UUIDComponent uuidComponent = null;
        if (commandBuffer != null) {
            uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        }
        if (uuidComponent == null && store != null) {
            uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        }

        if (uuidComponent != null) {
            try {
                UUID uuid = uuidComponent.getUuid();
                if (uuid != null) {
                    // For UUID-backed entities the UUID is globally unique; do NOT
                    // include storePart because ref.getStore() may be a different
                    // Java object than the store parameter in other callbacks.
                    long uuidPart = uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 1);
                    return new TrackingIdentity(uuidPart, true, uuid);
                }
            } catch (Throwable ignored) {
            }
        }

        return new TrackingIdentity(toEntityKey(store, entityId), false, null);
    }

    private record TrackingIdentity(long key, boolean uuidBacked, UUID uuid) {
    }

    private boolean isDebugSectionEnabled(String sectionKey) {
        return LoggingManager.isDebugSectionEnabled(sectionKey);
    }

    private record PlayerChunkViewport(int chunkX, int chunkZ, int radiusChunks) {
    }

    private List<PlayerChunkViewport> snapshotPlayerChunkViewports(Store<EntityStore> currentStore) {
        if (currentStore == null || currentStore.isShutdown()) {
            return List.of();
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return List.of();
        }

        List<PlayerChunkViewport> viewports = new ArrayList<>();

        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                continue;
            }

            Store<EntityStore> playerStore = playerEntityRef.getStore();
            // Only read components from the store currently ticking on this thread.
            if (playerStore != currentStore) {
                continue;
            }

            TransformComponent transform = playerStore != null
                    ? playerStore.getComponent(playerEntityRef, TransformComponent.getComponentType())
                    : null;
            Vector3d playerPosition = transform != null ? transform.getPosition() : null;
            if (playerPosition == null) {
                continue;
            }

            int viewRadiusChunks = resolvePlayerViewRadiusChunks(playerStore, playerEntityRef);
            if (viewRadiusChunks <= 0) {
                continue;
            }

            int chunkX = blockToChunk(playerPosition.getX());
            int chunkZ = blockToChunk(playerPosition.getZ());
            int viewportRadius = viewRadiusChunks + PLAYER_VIEW_RADIUS_BUFFER_CHUNKS;
            viewports.add(new PlayerChunkViewport(chunkX, chunkZ, viewportRadius));
        }

        return viewports;
    }

    private int resolvePlayerViewRadiusChunks(Store<EntityStore> playerStore, Ref<EntityStore> playerRef) {
        if (playerStore == null || playerRef == null) {
            return MIN_PLAYER_VIEW_RADIUS_CHUNKS;
        }

        Player player = playerStore.getComponent(playerRef, Player.getComponentType());
        int configuredRadius = player != null ? player.getViewRadius() : 0;
        return Math.max(MIN_PLAYER_VIEW_RADIUS_CHUNKS, configuredRadius);
    }

    private boolean isWithinActivePlayerChunk(Ref<EntityStore> entityRef,
            CommandBuffer<EntityStore> commandBuffer,
            List<PlayerChunkViewport> playerChunkViewports) {
        if (entityRef == null || playerChunkViewports == null || playerChunkViewports.isEmpty()) {
            return false;
        }

        Vector3d mobPosition = resolvePosition(entityRef, commandBuffer);
        if (mobPosition == null) {
            return false;
        }

        int mobChunkX = blockToChunk(mobPosition.getX());
        int mobChunkZ = blockToChunk(mobPosition.getZ());
        for (PlayerChunkViewport viewport : playerChunkViewports) {
            if (viewport == null) {
                continue;
            }
            int radiusChunks = Math.max(MIN_PLAYER_VIEW_RADIUS_CHUNKS, viewport.radiusChunks());
            int dx = Math.abs(mobChunkX - viewport.chunkX());
            int dz = Math.abs(mobChunkZ - viewport.chunkZ());
            if (dx <= radiusChunks && dz <= radiusChunks) {
                return true;
            }
        }
        return false;
    }

    private Vector3d resolvePosition(Ref<EntityStore> entityRef, CommandBuffer<EntityStore> commandBuffer) {
        if (entityRef == null) {
            return null;
        }

        TransformComponent transform = null;
        if (commandBuffer != null) {
            transform = commandBuffer.getComponent(entityRef, TransformComponent.getComponentType());
        }
        if (transform == null) {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            }
        }

        return transform != null ? transform.getPosition() : null;
    }

    private int blockToChunk(double blockCoordinate) {
        return ((int) Math.floor(blockCoordinate)) >> CHUNK_BIT_SHIFT;
    }

    private Integer resolveAndAssignLevelOnce(Ref<EntityStore> ref,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            int entityId,
            EntityRuntimeState state) {
        if (ref == null || commandBuffer == null || mobLevelingManager == null || entityId < 0 || state == null) {
            return null;
        }

        int resolveAttempts = state.resolveAttempts + 1;
        state.resolveAttempts = resolveAttempts;

        Integer resolvedLevel = mobLevelingManager.resolveMobLevelForEntity(
                ref,
                store,
                commandBuffer,
                resolveAttempts);
        if (resolvedLevel == null || resolvedLevel <= 0) {
            return null;
        }

        state.appliedLevel = resolvedLevel;
        int resolveAssignments = state.resolveAssignments;
        boolean levelSet = mobLevelingManager.setEntityLevelOverrideIfChanged(ref.getStore(), entityId, resolvedLevel);
        if (levelSet) {
            resolveAssignments = resolveAssignments + 1;
            state.resolveAssignments = resolveAssignments;
        } else if (resolveAssignments <= 0) {
            state.resolveAssignments = 1;
        }

        return resolvedLevel;
    }

    private static final class EntityRuntimeState {
        private int appliedLevel;
        private int lastRecordedResolvedLevel;
        private int settledHealthLevel;
        private long lastSeenTimeMillis;
        private int resolveAttempts;
        private int resolveAssignments;
        private int trackedEntityId = -1;
        private Store<EntityStore> trackedStore;
        private long lastPassiveStatTickMillis;
        private long nextMobAugmentRegistrationCheckMillis;
        private long nextHealthApplyAttemptMillis;
        private long lastHealthFlowLogMillis;
        private float lastNameplateHealthValue = Float.NaN;
        private float lastNameplateMaxHealthValue = Float.NaN;
        private boolean lastKnownAtFullHealth = false;
        private long lastHealthUpdateTick = 0L;
        private int lastAppliedNameplateLevel = Integer.MIN_VALUE;
        private boolean lastAppliedShowLevelInNameplate;
        private boolean lastAppliedShowNameInNameplate;
        private boolean lastAppliedShowHealthInNameplate;
        private String lastAppliedNameplateText;
        private boolean augmentHealthReconcilePending;
        private boolean augmentPassiveDeferActive;
        private boolean mobAugmentsInitialized;
        private boolean mobHasAugments;
        private boolean flowInitializedLogged;
        private long lastTrackingKey = -1L;
        private long lastLevelRevalidationMillis;
        // Blacklist cache: 0 = unchecked, 1 = allowed, 2 = blacklisted.
        private byte cachedBlacklistStatus;
        private long nextBlacklistCheckMillis;
        private boolean blockedStateCleared;

        private void copyFrom(EntityRuntimeState other) {
            this.appliedLevel = other.appliedLevel;
            this.lastRecordedResolvedLevel = other.lastRecordedResolvedLevel;
            this.settledHealthLevel = other.settledHealthLevel;
            this.resolveAttempts = other.resolveAttempts;
            this.resolveAssignments = other.resolveAssignments;
            this.mobAugmentsInitialized = other.mobAugmentsInitialized;
            this.mobHasAugments = other.mobHasAugments;
            this.augmentHealthReconcilePending = other.augmentHealthReconcilePending;
            this.augmentPassiveDeferActive = other.augmentPassiveDeferActive;
            this.lastPassiveStatTickMillis = other.lastPassiveStatTickMillis;
            this.nextMobAugmentRegistrationCheckMillis = other.nextMobAugmentRegistrationCheckMillis;
            this.nextHealthApplyAttemptMillis = other.nextHealthApplyAttemptMillis;
            this.lastHealthFlowLogMillis = other.lastHealthFlowLogMillis;
            this.lastAppliedNameplateLevel = other.lastAppliedNameplateLevel;
            this.lastAppliedNameplateText = other.lastAppliedNameplateText;
            this.lastAppliedShowLevelInNameplate = other.lastAppliedShowLevelInNameplate;
            this.lastAppliedShowNameInNameplate = other.lastAppliedShowNameInNameplate;
            this.lastAppliedShowHealthInNameplate = other.lastAppliedShowHealthInNameplate;
            this.lastNameplateHealthValue = other.lastNameplateHealthValue;
            this.lastNameplateMaxHealthValue = other.lastNameplateMaxHealthValue;
            this.lastHealthUpdateTick = other.lastHealthUpdateTick;
            this.lastKnownAtFullHealth = other.lastKnownAtFullHealth;
            this.flowInitializedLogged = other.flowInitializedLogged;
            this.lastLevelRevalidationMillis = other.lastLevelRevalidationMillis;
            this.cachedBlacklistStatus = other.cachedBlacklistStatus;
            this.nextBlacklistCheckMillis = other.nextBlacklistCheckMillis;
            this.blockedStateCleared = other.blockedStateCleared;
        }

        private boolean hasNameplateState() {
            return lastAppliedNameplateText != null
                    || lastAppliedNameplateLevel != Integer.MIN_VALUE
                    || lastAppliedShowLevelInNameplate
                    || lastAppliedShowNameInNameplate
                    || lastAppliedShowHealthInNameplate
                    || Float.isFinite(lastNameplateHealthValue)
                    || Float.isFinite(lastNameplateMaxHealthValue);
        }

    }
}

