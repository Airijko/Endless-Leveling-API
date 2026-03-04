package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.compatibility.NameplateBuilderCompatibility;
import com.airijko.endlessleveling.managers.MobLevelingManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.WorldGenId;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies mob-level stat scaling. For now mobs are hard-coded to level 10.
 * Scales Health and Damage (if a "Damage" stat exists) using the multipliers
 * provided by `LevelingManager`.
 */
public class MobLevelingSystem extends TickingSystem<EntityStore> {

    private static final Query<EntityStore> ENTITY_QUERY = Query.any();
    private static final String MOB_HEALTH_SCALE_MODIFIER_KEY = "EL_MOB_HEALTH_SCALE";
    private static final long STALE_ENTITY_TTL_STEPS = 2000L;
    private static final long HEALTH_RESYNC_INTERVAL_STEPS = 200L;

    private final MobLevelingManager mobLevelingManager = EndlessLeveling.getInstance().getMobLevelingManager();
    private final Map<Long, Integer> healthAppliedLevel = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSeenStepByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, Integer> loggedHealthLevelByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, Integer> loggedNameplateLevelByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, Integer> levelResolveAttemptCountByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, Integer> levelResolveAssignmentCountByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, String> lastResetReasonByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastHealthResyncStepByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, String> lastKnownEntitySignatureByEntityKey = new ConcurrentHashMap<>();
    private final Map<Long, String> lastHealthApplySkipReasonByEntityKey = new ConcurrentHashMap<>();
    private final Set<Long> deathHandledEntityKeys = ConcurrentHashMap.newKeySet();
    private final Set<Long> forcedDeathLoggedEntityKeys = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean fullMobRescaleRequested = new AtomicBoolean(false);
    private long systemStepCounter = 0L;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    public MobLevelingSystem() {
    }

    public void requestFullMobRescale() {
        fullMobRescaleRequested.set(true);
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown())
            return;

        if (mobLevelingManager == null || !mobLevelingManager.isMobLevelingEnabled())
            return;

        boolean showMobLevelUi = mobLevelingManager.shouldShowMobLevelUi();
        boolean includeLevelInName = mobLevelingManager.shouldIncludeLevelInNameplate();
        boolean shouldResetAllMobs = fullMobRescaleRequested.getAndSet(false);

        if (shouldResetAllMobs) {
            mobLevelingManager.clearAllEntityLevelOverrides();
            healthAppliedLevel.clear();
            lastSeenStepByEntityKey.clear();
            loggedHealthLevelByEntityKey.clear();
            loggedNameplateLevelByEntityKey.clear();
            levelResolveAttemptCountByEntityKey.clear();
            levelResolveAssignmentCountByEntityKey.clear();
            lastResetReasonByEntityKey.clear();
            lastHealthResyncStepByEntityKey.clear();
            lastKnownEntitySignatureByEntityKey.clear();
            lastHealthApplySkipReasonByEntityKey.clear();
            deathHandledEntityKeys.clear();
            forcedDeathLoggedEntityKeys.clear();
            LOGGER.atInfo().log("MobLeveling: queued full mob rescale pass.");
        }

        long currentStep = ++systemStepCounter;

        store.forEachChunk(ENTITY_QUERY,
                (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        int entityId = ref.getIndex();
                        long entityKey = toEntityKey(ref.getStore(), entityId);
                        lastSeenStepByEntityKey.put(entityKey, currentStep);

                        NPCEntity npcEntity = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
                        if (npcEntity == null) {
                            lastKnownEntitySignatureByEntityKey.remove(entityKey);
                            lastHealthApplySkipReasonByEntityKey.remove(entityKey);
                            continue;
                        }

                        if (shouldResetAllMobs) {
                            clearLevelingStateForEntity(ref, commandBuffer, entityId, "reload-rescale");
                        }

                        ensureDeadComponentWhenZeroHp(ref, commandBuffer);

                        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
                            if (deathHandledEntityKeys.add(entityKey)) {
                                handleDeadEntity(ref, commandBuffer);
                                clearLevelingStateOnDeath(ref, entityId, entityKey);
                                forcedDeathLoggedEntityKeys.remove(entityKey);
                                lastResetReasonByEntityKey.put(entityKey, "death-component");
                            }
                            continue;
                        }

                        if (mobLevelingManager.isEntityBlacklisted(ref, store, commandBuffer)) {
                            lastKnownEntitySignatureByEntityKey.remove(entityKey);
                            lastHealthApplySkipReasonByEntityKey.remove(entityKey);
                            boolean hasTrackedState = healthAppliedLevel.containsKey(entityKey)
                                    || levelResolveAttemptCountByEntityKey.containsKey(entityKey)
                                    || levelResolveAssignmentCountByEntityKey.containsKey(entityKey)
                                    || loggedHealthLevelByEntityKey.containsKey(entityKey)
                                    || loggedNameplateLevelByEntityKey.containsKey(entityKey)
                                    || forcedDeathLoggedEntityKeys.contains(entityKey)
                                    || deathHandledEntityKeys.contains(entityKey);
                            if (hasTrackedState) {
                                clearLevelingStateForEntity(ref, commandBuffer, entityId, "blacklisted");
                            }
                            clearOrRemoveNameplate(ref, commandBuffer);
                            continue;
                        }

                        String currentEntitySignature = buildEntitySignature(ref, commandBuffer, npcEntity);
                        String previousEntitySignature = lastKnownEntitySignatureByEntityKey.put(entityKey,
                                currentEntitySignature);
                        if (previousEntitySignature != null
                                && !previousEntitySignature.equals(currentEntitySignature)) {
                            LOGGER.atInfo().log(
                                    "MobEntitySignatureChanged target=%d previous=%s current=%s step=%d",
                                    entityId,
                                    previousEntitySignature,
                                    currentEntitySignature,
                                    currentStep);
                            clearLevelingStateForEntity(ref, commandBuffer, entityId, "entity-signature-changed");
                            lastKnownEntitySignatureByEntityKey.put(entityKey, currentEntitySignature);
                        }

                        Integer previouslyAppliedLevel = healthAppliedLevel.get(entityKey);
                        Integer appliedLevel = previouslyAppliedLevel;
                        if (appliedLevel == null || appliedLevel <= 0) {
                            int resolveAttempts = levelResolveAttemptCountByEntityKey.getOrDefault(entityKey, 0) + 1;
                            levelResolveAttemptCountByEntityKey.put(entityKey, resolveAttempts);
                            appliedLevel = mobLevelingManager.resolveMobLevelForEntity(
                                    ref,
                                    store,
                                    commandBuffer,
                                    resolveAttempts);
                            if (appliedLevel != null && appliedLevel > 0) {
                                int resolveAssignments = levelResolveAssignmentCountByEntityKey
                                        .getOrDefault(entityKey, 0) + 1;
                                levelResolveAssignmentCountByEntityKey.put(entityKey, resolveAssignments);
                                mobLevelingManager.setEntityLevelOverride(ref.getStore(), entityId, appliedLevel);
                                if (resolveAssignments > 1) {
                                    LOGGER.atWarning().log(
                                            "MobLevelResolveReentry target=%d attempts=%d assignments=%d newLevel=%d previousApplied=%s lastResetReason=%s step=%d",
                                            entityId,
                                            resolveAttempts,
                                            resolveAssignments,
                                            appliedLevel,
                                            previouslyAppliedLevel != null ? previouslyAppliedLevel.toString() : "none",
                                            lastResetReasonByEntityKey.getOrDefault(entityKey, "unknown"),
                                            currentStep);
                                } else if (resolveAttempts > 1) {
                                    LOGGER.atInfo().log(
                                            "MobLevelResolveInitialDelayed target=%d level=%d attempts=%d assignments=%d lastResetReason=%s step=%d",
                                            entityId,
                                            appliedLevel,
                                            resolveAttempts,
                                            resolveAssignments,
                                            lastResetReasonByEntityKey.getOrDefault(entityKey, "unknown"),
                                            currentStep);
                                } else {
                                    LOGGER.atInfo().log(
                                            "MobLevelResolveInitial target=%d level=%d attempts=%d assignments=%d step=%d",
                                            entityId,
                                            appliedLevel,
                                            resolveAttempts,
                                            resolveAssignments,
                                            currentStep);
                                }
                            } else {
                                String pendingReason = diagnoseResolvePendingReason(ref, commandBuffer);
                                if (shouldLogResolvePending(resolveAttempts, pendingReason)) {
                                    String entityDebug = describeEntityForPending(ref, commandBuffer);
                                    String playerContext = "n/a";
                                    if ("manager-returned-no-level".equals(pendingReason)) {
                                        playerContext = mobLevelingManager.describePlayerContextForEntity(
                                                ref,
                                                store,
                                                commandBuffer);
                                    }
                                    LOGGER.atInfo().log(
                                            "MobLevelResolvePending target=%d attempts=%d reason=%s entity=%s playerContext=%s lastResetReason=%s step=%d",
                                            entityId,
                                            resolveAttempts,
                                            pendingReason,
                                            entityDebug,
                                            playerContext,
                                            lastResetReasonByEntityKey.getOrDefault(entityKey, "unknown"),
                                            currentStep);
                                }
                            }
                        }
                        if (appliedLevel == null || appliedLevel <= 0) {
                            if (isAtOrBelowZeroHealth(ref, commandBuffer)) {
                                clearOrRemoveNameplate(ref, commandBuffer);
                                healthAppliedLevel.remove(entityKey);
                                lastResetReasonByEntityKey.put(entityKey, "zero-health-no-level");
                            }
                            continue;
                        }

                        boolean shouldApplyHealthModifier = previouslyAppliedLevel == null
                                || previouslyAppliedLevel <= 0
                                || !previouslyAppliedLevel.equals(appliedLevel);
                        if (!shouldApplyHealthModifier && shouldForceHealthResync(
                                ref,
                                commandBuffer,
                                entityId,
                                appliedLevel,
                                entityKey,
                                currentStep)) {
                            shouldApplyHealthModifier = true;
                        }

                        if (shouldApplyHealthModifier) {
                            lastHealthApplySkipReasonByEntityKey.remove(entityKey);
                            applyHealthModifier(ref, commandBuffer, appliedLevel, entityKey);
                            lastHealthResyncStepByEntityKey.put(entityKey, currentStep);
                        } else {
                            logHealthApplySkipIfChanged(
                                    entityKey,
                                    entityId,
                                    appliedLevel,
                                    "cached-level-unchanged",
                                    summarizeHealthForDebug(ref, commandBuffer));
                        }
                        if (showMobLevelUi) {
                            applyNameplate(ref, commandBuffer, includeLevelInName, entityKey);
                        } else if (NameplateBuilderCompatibility.isAvailable()) {
                            NameplateBuilderCompatibility.removeMobLevel(ref.getStore(), ref);
                        }
                    }
                });

        pruneStaleEntities(currentStep);
    }

    private void pruneStaleEntities(long currentStep) {
        if (lastSeenStepByEntityKey.isEmpty()) {
            return;
        }

        long expiryStep = currentStep - STALE_ENTITY_TTL_STEPS;
        Iterator<Map.Entry<Long, Long>> iterator = lastSeenStepByEntityKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            Long entityKey = entry.getKey();
            Long lastSeenStep = entry.getValue();
            if (entityKey == null || lastSeenStep == null || lastSeenStep >= expiryStep) {
                continue;
            }

            iterator.remove();
            healthAppliedLevel.remove(entityKey);
            loggedHealthLevelByEntityKey.remove(entityKey);
            loggedNameplateLevelByEntityKey.remove(entityKey);
            lastHealthResyncStepByEntityKey.remove(entityKey);
            lastKnownEntitySignatureByEntityKey.remove(entityKey);
            lastHealthApplySkipReasonByEntityKey.remove(entityKey);
            levelResolveAttemptCountByEntityKey.remove(entityKey);
            levelResolveAssignmentCountByEntityKey.remove(entityKey);
            deathHandledEntityKeys.remove(entityKey);
            forcedDeathLoggedEntityKeys.remove(entityKey);
            lastResetReasonByEntityKey.put(entityKey, "stale-prune");
            int entityId = (int) (entityKey & 0xFFFFFFFFL);
            mobLevelingManager.forgetEntityByKey(entityKey);
            mobLevelingManager.forgetEntity(entityId);
        }
    }

    private void ensureDeadComponentWhenZeroHp(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        long entityKey = toEntityKey(ref.getStore(), ref.getIndex());

        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
            forcedDeathLoggedEntityKeys.remove(entityKey);
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp == null || !Float.isFinite(hp.get()) || hp.get() > 0.0001f) {
            forcedDeathLoggedEntityKeys.remove(entityKey);
            return;
        }

        DeathComponent.tryAddComponent(
                commandBuffer,
                ref,
                new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, 0.0f));

        int entityId = ref.getIndex();
        if (forcedDeathLoggedEntityKeys.add(entityKey)) {
            LOGGER.atWarning().log(
                    "ForcedDeathComponent target=%d hp=%.3f max=%.3f",
                    entityId,
                    hp.get(),
                    hp.getMax());
        }
    }

    private void handleDeadEntity(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        int entityId = ref.getIndex();
        long entityKey = toEntityKey(ref.getStore(), entityId);
        Integer appliedLevel = healthAppliedLevel.get(entityKey);
        float hpBefore = Float.NaN;
        float maxBefore = Float.NaN;

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue before = statMap.get(healthIndex);
            if (before != null) {
                hpBefore = before.get();
                maxBefore = before.getMax();
            }
        }

        LOGGER.atInfo().log(
                "MobOnDeath triggered: entity=%d level=%s hpBefore=%.3f maxBefore=%.3f",
                entityId,
                appliedLevel != null ? appliedLevel.toString() : "unknown",
                hpBefore,
                maxBefore);

        clearOrRemoveNameplate(ref, commandBuffer);
    }

    private void clearLevelingStateOnDeath(Ref<EntityStore> ref, int entityId, long entityKey) {
        if (ref == null || entityId < 0) {
            return;
        }

        healthAppliedLevel.remove(entityKey);
        loggedHealthLevelByEntityKey.remove(entityKey);
        loggedNameplateLevelByEntityKey.remove(entityKey);
        lastHealthResyncStepByEntityKey.remove(entityKey);
        lastKnownEntitySignatureByEntityKey.remove(entityKey);
        lastHealthApplySkipReasonByEntityKey.remove(entityKey);
        levelResolveAttemptCountByEntityKey.remove(entityKey);
        levelResolveAssignmentCountByEntityKey.remove(entityKey);

        mobLevelingManager.forgetEntity(ref.getStore(), entityId);
    }

    private boolean isAtOrBelowZeroHealth(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return false;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return false;
        }

        EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
        return hp != null && hp.get() <= 0.0001f;
    }

    private void applyHealthModifier(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            int appliedLevel,
            long entityKey) {
        if (ref == null || commandBuffer == null || mobLevelingManager == null) {
            return;
        }

        int entityId = ref.getIndex();
        Integer lastLoggedHealthLevel = loggedHealthLevelByEntityKey.get(entityKey);
        if (!Integer.valueOf(appliedLevel).equals(lastLoggedHealthLevel)) {
            LOGGER.atInfo().log(
                    "MobLevelingApplyHealth called target=%d level=%d",
                    entityId,
                    appliedLevel);
            loggedHealthLevelByEntityKey.put(entityKey, appliedLevel);
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            logHealthApplySkipIfChanged(entityKey, entityId, appliedLevel, "missing-stat-map", "");
            return;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            logHealthApplySkipIfChanged(entityKey, entityId, appliedLevel, "missing-health-stat", "");
            return;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();

        float currentValue = healthStat.get();
        float currentMax = healthStat.getMax();
        if (!Float.isFinite(currentValue) || !Float.isFinite(currentMax) || currentMax <= 0.0f) {
            logHealthApplySkipIfChanged(
                    entityKey,
                    entityId,
                    appliedLevel,
                    "invalid-current-health",
                    String.format("value=%.3f max=%.3f", currentValue, currentMax));
            healthAppliedLevel.put(entityKey, appliedLevel);
            return;
        }

        statMap.removeModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY);
        EntityStatValue baselineHealth = statMap.get(healthIndex);
        float baseMax = baselineHealth != null ? baselineHealth.getMax() : currentMax;
        if (!Float.isFinite(baseMax) || baseMax <= 0.0f) {
            baseMax = Math.max(1.0f, currentMax);
        }

        if (!mobLevelingManager.isMobHealthScalingEnabled()) {
            float ratio = currentMax > 0.0f ? currentValue / currentMax : 1.0f;
            float restoredValue = Math.max(0.0f, Math.min(baseMax, ratio * baseMax));
            if (currentValue <= 0.0f) {
                restoredValue = 0.0f;
            }
            statMap.setStatValue(healthIndex, restoredValue);
            statMap.update();
            EntityStatValue restoredHealth = statMap.get(healthIndex);
            if (restoredHealth != null && Float.isFinite(restoredHealth.getMax()) && restoredHealth.getMax() > 0.0f) {
                mobLevelingManager.recordEntityMaxHealth(entityId, restoredHealth.getMax());
            }
            lastHealthApplySkipReasonByEntityKey.remove(entityKey);
            healthAppliedLevel.put(entityKey, appliedLevel);
            return;
        }

        MobLevelingManager.MobHealthScalingResult scaled = mobLevelingManager.computeMobHealthScaling(
                appliedLevel,
                baseMax,
                currentMax,
                currentValue);

        float targetMax = scaled.targetMax();
        float targetValue = scaled.newValue();
        if (!Float.isFinite(targetMax) || targetMax <= 0.0f || !Float.isFinite(targetValue)) {
            logHealthApplySkipIfChanged(
                    entityKey,
                    entityId,
                    appliedLevel,
                    "invalid-scaled-health",
                    String.format("targetMax=%.3f targetValue=%.3f baseMax=%.3f current=%.3f/%.3f",
                            targetMax,
                            targetValue,
                            baseMax,
                            currentValue,
                            currentMax));
            healthAppliedLevel.put(entityKey, appliedLevel);
            return;
        }

        float additive = scaled.additive();
        if (Math.abs(additive) > 0.0001f) {
            try {
                StaticModifier modifier = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, additive);
                statMap.putModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY, modifier);
            } catch (Exception e) {
                LOGGER.atWarning().log(
                        "MobHealthScaling modifier apply failed for entity=%d level=%d: %s",
                        entityId,
                        appliedLevel,
                        e.toString());
            }
        }

        statMap.setStatValue(healthIndex, targetValue);
        statMap.update();
        EntityStatValue updatedHealth = statMap.get(healthIndex);
        if (updatedHealth != null && Float.isFinite(updatedHealth.getMax()) && updatedHealth.getMax() > 0.0f) {
            mobLevelingManager.recordEntityMaxHealth(entityId, updatedHealth.getMax());
        }

        lastHealthApplySkipReasonByEntityKey.remove(entityKey);
        healthAppliedLevel.put(entityKey, appliedLevel);

    }

    private void clearLevelingStateForEntity(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            int entityId,
            String reason) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            statMap.removeModifier(healthIndex, MOB_HEALTH_SCALE_MODIFIER_KEY);
            statMap.update();
        }

        long entityKey = toEntityKey(ref.getStore(), entityId);
        Integer previousLevel = healthAppliedLevel.get(entityKey);
        healthAppliedLevel.remove(entityKey);
        lastSeenStepByEntityKey.remove(entityKey);
        loggedHealthLevelByEntityKey.remove(entityKey);
        loggedNameplateLevelByEntityKey.remove(entityKey);
        lastHealthResyncStepByEntityKey.remove(entityKey);
        lastKnownEntitySignatureByEntityKey.remove(entityKey);
        lastHealthApplySkipReasonByEntityKey.remove(entityKey);
        levelResolveAttemptCountByEntityKey.remove(entityKey);
        levelResolveAssignmentCountByEntityKey.remove(entityKey);
        deathHandledEntityKeys.remove(entityKey);
        forcedDeathLoggedEntityKeys.remove(entityKey);
        lastResetReasonByEntityKey.put(entityKey, reason != null ? reason : "unspecified");
        LOGGER.atInfo().log(
                "MobLevelStateReset target=%d reason=%s previousLevel=%s step=%d",
                entityId,
                reason != null ? reason : "unspecified",
                previousLevel != null ? previousLevel.toString() : "none",
                systemStepCounter);
        mobLevelingManager.forgetEntity(ref.getStore(), entityId);
    }

    private void applyNameplate(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            boolean includeLevelInName,
            long entityKey) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        int entityId = ref.getIndex();
        Integer appliedLevel = healthAppliedLevel.get(entityKey);
        if (appliedLevel != null && appliedLevel > 0) {
            Integer lastLoggedNameplateLevel = loggedNameplateLevelByEntityKey.get(entityKey);
            if (!appliedLevel.equals(lastLoggedNameplateLevel)) {
                LOGGER.atInfo().log(
                        "MobLevelingApplyNameplate called target=%d level=%d includeLevelInName=%s",
                        entityId,
                        appliedLevel,
                        includeLevelInName);
                loggedNameplateLevelByEntityKey.put(entityKey, appliedLevel);
            }
        }

        NPCEntity npcEntity = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) {
            clearOrRemoveNameplate(ref, commandBuffer);
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.isValid()) {
            return;
        }

        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
            clearOrRemoveNameplate(ref, commandBuffer);
            return;
        }

        if (appliedLevel == null || appliedLevel <= 0) {
            return;
        }

        if (NameplateBuilderCompatibility.isAvailable()) {
            NameplateBuilderCompatibility.registerMobLevel(ref.getStore(), ref, appliedLevel);
            return;
        }

        Nameplate nameplate = commandBuffer.ensureAndGetComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) {
            return;
        }

        String baseName = "Mob";
        DisplayNameComponent display = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType());
        if (display != null && display.getDisplayName() != null
                && display.getDisplayName().getAnsiMessage() != null
                && !display.getDisplayName().getAnsiMessage().isBlank()) {
            baseName = display.getDisplayName().getAnsiMessage();
        }

        String label;
        if (includeLevelInName) {
            label = "[Lv." + appliedLevel + "] " + baseName;
        } else {
            label = baseName;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue hp = statMap == null ? null : statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp != null && Float.isFinite(hp.get()) && Float.isFinite(hp.getMax()) && hp.getMax() > 0.0f) {
            int currentHp = Math.max(0, Math.round(hp.get()));
            int maxHp = Math.max(1, Math.round(hp.getMax()));
            label = label + " [HP " + currentHp + "/" + maxHp + "]";
        }

        nameplate.setText(label);
    }

    private void clearOrRemoveNameplate(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return;
        }

        if (NameplateBuilderCompatibility.isAvailable()) {
            NameplateBuilderCompatibility.removeMobLevel(ref.getStore(), ref);
            return;
        }

        Nameplate nameplate = commandBuffer.getComponent(ref, Nameplate.getComponentType());
        if (nameplate != null) {
            nameplate.setText("");
        }
    }

    private long toEntityKey(Store<EntityStore> store, int entityId) {
        long storePart = store == null ? 0L : Integer.toUnsignedLong(System.identityHashCode(store));
        long entityPart = Integer.toUnsignedLong(entityId);
        return (storePart << 32) | entityPart;
    }

    private String diagnoseResolvePendingReason(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return "missing-ref-or-commandBuffer";
        }

        NPCEntity npcEntity = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) {
            return "non-npc-entity";
        }

        if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return "death-component-present";
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.isValid()) {
            return "target-is-player";
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return "missing-stat-map";
        }

        EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp == null) {
            return "missing-health-stat";
        }

        if (!Float.isFinite(hp.get())) {
            return "invalid-health-value";
        }

        if (hp.get() <= 0.0f) {
            return "non-positive-health";
        }

        if (mobLevelingManager.isEntityBlacklisted(ref, null, commandBuffer)) {
            return "blacklisted";
        }

        return "manager-returned-no-level";
    }

    private boolean shouldLogResolvePending(int attempts, String reason) {
        if (reason == null) {
            return false;
        }

        boolean meaningful = "manager-returned-no-level".equals(reason)
                || "non-positive-health".equals(reason)
                || "invalid-health-value".equals(reason);
        if (!meaningful) {
            return false;
        }

        return attempts == 1 || attempts % 250 == 0;
    }

    private String describeEntityForPending(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return "unknown";
        }

        String npcType = "unknown";
        NPCEntity npc = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            try {
                String rawType = npc.getNPCTypeId();
                if (rawType != null && !rawType.isBlank()) {
                    npcType = rawType.trim();
                }
            } catch (Throwable ignored) {
            }
        }

        String displayName = "";
        DisplayNameComponent display = commandBuffer.getComponent(ref, DisplayNameComponent.getComponentType());
        if (display != null && display.getDisplayName() != null
                && display.getDisplayName().getAnsiMessage() != null) {
            String rawName = display.getDisplayName().getAnsiMessage().trim();
            if (!rawName.isBlank()) {
                displayName = rawName;
            }
        }

        String worldGen = "";
        WorldGenId worldGenId = commandBuffer.getComponent(ref, WorldGenId.getComponentType());
        if (worldGenId != null) {
            try {
                worldGen = Integer.toString(worldGenId.getWorldGenId());
            } catch (Throwable ignored) {
                try {
                    worldGen = worldGenId.toString();
                } catch (Throwable ignored2) {
                }
            }
        }

        StringBuilder identity = new StringBuilder();
        identity.append("npcType=").append(npcType);
        if (!displayName.isBlank()) {
            identity.append("|name=").append(displayName);
        }
        if (!worldGen.isBlank()) {
            identity.append("|worldGen=").append(worldGen);
        }
        return identity.toString();
    }

    private String summarizeHealthForDebug(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null) {
            return "health=unknown";
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return "health=missing-stat-map";
        }

        EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp == null) {
            return "health=missing-health-stat";
        }

        return String.format("health=%.3f/%.3f", hp.get(), hp.getMax());
    }

    private boolean shouldForceHealthResync(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            int entityId,
            int appliedLevel,
            long entityKey,
            long currentStep) {
        if (ref == null || commandBuffer == null || entityId < 0 || appliedLevel <= 0 || mobLevelingManager == null) {
            return false;
        }

        Long lastResyncStep = lastHealthResyncStepByEntityKey.get(entityKey);
        if (lastResyncStep == null || currentStep - lastResyncStep >= HEALTH_RESYNC_INTERVAL_STEPS) {
            return true;
        }

        if (!mobLevelingManager.isMobHealthScalingEnabled()) {
            return false;
        }

        float expectedMaxSnapshot = mobLevelingManager.getEntityMaxHealthSnapshot(entityId);
        if (!Float.isFinite(expectedMaxSnapshot) || expectedMaxSnapshot <= 0.0f) {
            return true;
        }

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return true;
        }

        EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
        if (hp == null || !Float.isFinite(hp.getMax()) || hp.getMax() <= 0.0f) {
            return true;
        }

        float currentMax = hp.getMax();
        float allowedDrift = Math.max(1.0f, expectedMaxSnapshot * 0.02f);
        return Math.abs(currentMax - expectedMaxSnapshot) > allowedDrift;
    }

    private void logHealthApplySkipIfChanged(long entityKey,
            int entityId,
            int level,
            String reason,
            String details) {
        if (reason == null || reason.isBlank()) {
            return;
        }

        String normalizedDetails = details == null ? "" : details.trim();
        String marker = "cached-level-unchanged".equals(reason)
                ? reason
                : (normalizedDetails.isBlank() ? reason : reason + "|" + normalizedDetails);
        String previousMarker = lastHealthApplySkipReasonByEntityKey.get(entityKey);
        if (marker.equals(previousMarker)) {
            return;
        }

        lastHealthApplySkipReasonByEntityKey.put(entityKey, marker);
        if (normalizedDetails.isBlank()) {
            LOGGER.atInfo().log(
                    "MobHealthApplySkipped target=%d level=%d reason=%s",
                    entityId,
                    level,
                    reason);
        } else {
            LOGGER.atInfo().log(
                    "MobHealthApplySkipped target=%d level=%d reason=%s %s",
                    entityId,
                    level,
                    reason,
                    normalizedDetails);
        }
    }

    private String buildEntitySignature(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            NPCEntity npcEntity) {
        if (ref == null || commandBuffer == null || npcEntity == null) {
            return "unknown";
        }

        String npcType = "unknown";
        try {
            String rawType = npcEntity.getNPCTypeId();
            if (rawType != null && !rawType.isBlank()) {
                npcType = rawType.trim();
            }
        } catch (Throwable ignored) {
        }

        String worldGen = "none";
        WorldGenId worldGenId = commandBuffer.getComponent(ref, WorldGenId.getComponentType());
        if (worldGenId != null) {
            try {
                worldGen = Integer.toString(worldGenId.getWorldGenId());
            } catch (Throwable ignored) {
                try {
                    worldGen = worldGenId.toString();
                } catch (Throwable ignored2) {
                }
            }
        }

        return npcType + "|worldGen=" + worldGen;
    }
}
