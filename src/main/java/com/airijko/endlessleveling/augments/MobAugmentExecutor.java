package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.augments.types.CommonAugment;
import com.airijko.endlessleveling.augments.types.GoliathAugment;
import com.airijko.endlessleveling.augments.types.GraspOfTheUndyingAugment;
import com.airijko.endlessleveling.augments.types.RaidBossAugment;
import com.airijko.endlessleveling.augments.types.TankEngineAugment;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.managers.LoggingManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.SkillManager;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.airijko.endlessleveling.augments.types.DeathBombAugment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages and executes augments for mobs/bosses loaded from world
 * configuration.
 * Stores mob augment instances keyed by entity UUID.
 */
public final class MobAugmentExecutor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    // Map: Entity UUID -> List of active augments for that entity
    private final Map<UUID, MobAugmentInstance> mobAugments = new HashMap<>();
    private final Map<UUID, Long> mobPassiveAppliedAtMillis = new HashMap<>();

    private static final String CATEGORY_ON_HIT = "ON_HIT";
    private static final String CATEGORY_ON_CRIT = "ON_CRIT";
    private static final String CATEGORY_ON_MISS = "ON_MISS";
    private static final String CATEGORY_ON_TARGET_CONDITION = "ON_TARGET_CONDITION";
    private static final String CATEGORY_ON_DAMAGE_TAKEN = "ON_DAMAGE_TAKEN";
    private static final String CATEGORY_ON_LOW_HP = "ON_LOW_HP";
    private static final String CATEGORY_ON_KILL = "ON_KILL";
    private static final String CATEGORY_PASSIVE_STAT = "PASSIVE_STAT";

    // Debug-section name recognized by config.yml logging.debug_sections
    private static final String DEBUG_SECTION = "mob_augments";
    private static final String SUMMON_DEBUG_SECTION = "necromancer_summons";
    private static final long DEBUG_CACHE_TTL_MS = 5000L;
    private volatile boolean debugEnabled = false;
    private volatile long debugCacheExpiresAt = 0L;
    private volatile boolean summonDebugEnabled = false;
    private volatile long summonDebugCacheExpiresAt = 0L;

    public MobAugmentExecutor() {
    }

    /**
     * Returns true when the 'mob_augments' debug section is enabled in config.yml.
     * Result is cached for {@value DEBUG_CACHE_TTL_MS}ms to keep per-hit overhead negligible.
     */
    private boolean isDebugEnabled() {
        long now = System.currentTimeMillis();
        if (now < debugCacheExpiresAt) {
            return debugEnabled;
        }
        debugEnabled = LoggingManager.isDebugSectionEnabled(DEBUG_SECTION);
        debugCacheExpiresAt = now + DEBUG_CACHE_TTL_MS;
        return debugEnabled;
    }

    /**
     * Returns true when the 'necromancer_summons' OR 'mob_augments' debug section is enabled.
     */
    private boolean isSummonDebugEnabled() {
        if (isDebugEnabled()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < summonDebugCacheExpiresAt) {
            return summonDebugEnabled;
        }
        summonDebugEnabled = LoggingManager.isDebugSectionEnabled(SUMMON_DEBUG_SECTION);
        summonDebugCacheExpiresAt = now + DEBUG_CACHE_TTL_MS;
        return summonDebugEnabled;
    }


    /**
     * Register augments for a mob entity.
     * Called when a mob with configured augments is initialized.
     */
    public void registerMobAugments(
            UUID entityId,
            List<String> augmentIds,
            AugmentManager augmentManager,
            AugmentRuntimeManager runtimeManager) {
        registerAugments(entityId, augmentIds, augmentManager, runtimeManager, false);
    }

    /**
     * Register augments for a necromancer summon, mirroring its summoner's
     * full augment loadout 1:1. Unlike {@link #registerMobAugments}, this
     * bypasses the {@link MobAugmentDiagnostics#isAllowedMobCommonStatKey}
     * filter so common-stat offers for stats not used by world-configured
     * mobs (haste, discipline, flow, stamina) still pass through. The
     * underlying augment instance is replaced atomically, preserving the
     * runtime state keyed by entity UUID.
     */
    public void registerSummonAugments(
            UUID entityId,
            List<String> augmentIds,
            AugmentManager augmentManager,
            AugmentRuntimeManager runtimeManager) {
        registerAugments(entityId, augmentIds, augmentManager, runtimeManager, true);
    }

    private void registerAugments(
            UUID entityId,
            List<String> augmentIds,
            AugmentManager augmentManager,
            AugmentRuntimeManager runtimeManager,
            boolean bypassCommonStatFilter) {
        if (entityId == null || augmentIds == null || augmentIds.isEmpty()) {
            return;
        }

        List<Augment> augments = new ArrayList<>();
        List<String> appliedAugmentIds = new ArrayList<>();
        int skippedDisallowedCommonOffers = 0;
        for (String augmentId : augmentIds) {
            if (augmentId == null || augmentId.isBlank()) {
                continue;
            }

            if (!bypassCommonStatFilter) {
                CommonAugment.CommonStatOffer commonOffer = CommonAugment.parseStatOfferId(augmentId);
                if (commonOffer != null
                        && !MobAugmentDiagnostics.isAllowedMobCommonStatKey(commonOffer.attributeKey())) {
                    skippedDisallowedCommonOffers++;
                    continue;
                }
            }

            try {
                Augment augment = augmentManager.createAugment(augmentId.trim());
                if (augment == null) {
                    LOGGER.atWarning().log("Failed to create augment '%s' for mob %s", augmentId, entityId);
                    continue;
                }
                augments.add(augment);
                appliedAugmentIds.add(augmentId.trim());
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e)
                        .log("Error creating augment '%s' for mob %s: %s", augmentId, entityId, e.getMessage());
            }
        }

        if (!augments.isEmpty()) {
            // Create a runtime state for the mob using its UUID
            var runtimeState = runtimeManager.getRuntimeState(entityId);
            mobAugments.put(entityId, new MobAugmentInstance(augments, appliedAugmentIds, runtimeState));
            if (isDebugEnabled()) {
                String tag = bypassCommonStatFilter ? "[SUMMON_MIRRORED_AUGMENTS]" : "[MOB_OVERRIDE_AUGMENTS]";
                LOGGER.atInfo().log("%s Bound %d augments to mob %s: %s",
                        tag, augments.size(), entityId, formatAugmentBindSummary(appliedAugmentIds));
                if (skippedDisallowedCommonOffers > 0) {
                    LOGGER.atInfo().log(
                        "%s Skipped %d disallowed COMMON stat offers for mob %s (allowed=%s)",
                        tag,
                        skippedDisallowedCommonOffers,
                        entityId,
                        MobAugmentDiagnostics.getAllowedMobCommonStatKeys());
                }
                LOGGER.atInfo().log("%s[RAW] mob=%s ids=%s",
                        tag, entityId, appliedAugmentIds);
                LOGGER.atInfo().log("[MOB_AUGMENT_CATEGORIES] mob=%s categories=%s",
                        entityId, summarizeCategories(augments));
            }
        }
    }

    private String formatAugmentBindSummary(List<String> augmentIds) {
        if (augmentIds == null || augmentIds.isEmpty()) {
            return "commonTotals=[] nonCommonCount=0";
        }

        Map<String, Double> commonTotals = new LinkedHashMap<>();
        int nonCommonCount = 0;

        for (String augmentId : augmentIds) {
            if (augmentId == null || augmentId.isBlank()) {
                continue;
            }

            String trimmed = augmentId.trim();
            String[] parts = trimmed.split("::");
            if (parts.length >= 3 && "common".equalsIgnoreCase(parts[0])) {
                String stat = parts[1].trim().toLowerCase(Locale.ROOT);
                double amount = parseCommonAmount(parts[2]);
                commonTotals.merge(stat, amount, Double::sum);
                continue;
            }

            nonCommonCount++;
        }

        StringBuilder sb = new StringBuilder("commonTotals=[");
        commonTotals.forEach((k, v) -> sb.append(k).append("=").append(v).append(","));
        if (!commonTotals.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        sb.append("] nonCommonCount=").append(nonCommonCount);
        return sb.toString();
    }

    private static double parseCommonAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    /**
     * Apply on-hit effects for a mob attacker.
     */
    public AugmentDispatch.OnHitResult applyOnHit(
            UUID entityId,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap attackerStats,
            EntityStatMap targetStats,
            float startingDamage) {
        return applyOnHit(entityId,
                attackerRef,
                targetRef,
                commandBuffer,
                attackerStats,
                targetStats,
                startingDamage,
                false);
    }

    public AugmentDispatch.OnHitResult applyOnHit(
            UUID entityId,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap attackerStats,
            EntityStatMap targetStats,
            float startingDamage,
            boolean isCritical) {

        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.augments.isEmpty()) {
            return new AugmentDispatch.OnHitResult(startingDamage, 0.0D);
        }

        applyPassiveHooks(entityId, instance, attackerRef, commandBuffer, attackerStats);

        AugmentHooks.HitContext context = new AugmentHooks.HitContext(
                null,
                instance.runtimeState,
                null,
                attackerRef,
                targetRef,
                commandBuffer,
                attackerStats,
                targetStats,
                startingDamage,
                isCritical,
                false,
                null,
                1.0f);

        if (AugmentDispatch.isMiss(context)) {
            for (Augment augment : instance.augments) {
                if (augment instanceof AugmentHooks.OnMissAugment onMiss) {
                    try {
                        float beforeDamage = context.getDamage();
                        double beforeTrueDamage = context.getTrueDamageBonus();
                        onMiss.onMiss(context);
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e)
                                .log("[AUGMENT] Error executing OnMiss %s for mob %s: %s", augment.getId(),
                                        entityId, e.getMessage());
                    }
                }
            }
            return new AugmentDispatch.OnHitResult(Math.max(0.0f, context.getDamage()), Math.max(0.0D, context.getTrueDamageBonus()));
        }

        for (Augment augment : instance.augments) {
            if (augment instanceof AugmentHooks.OnHitAugment onHit) {
                try {
                    float beforeDamage = context.getDamage();
                    double beforeTrueDamage = context.getTrueDamageBonus();
                    float updated = onHit.onHit(context);
                    context.setDamage(updated);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnHit %s for mob %s: %s", augment.getId(),
                                    entityId, e.getMessage());
                }
            }
            if (augment instanceof AugmentHooks.OnCritAugment onCrit && context.isCritical()) {
                try {
                    float beforeDamage = context.getDamage();
                    double beforeTrueDamage = context.getTrueDamageBonus();
                    onCrit.onCrit(context);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnCrit %s for mob %s: %s", augment.getId(),
                                    entityId, e.getMessage());
                }
            }
            if (augment instanceof AugmentHooks.OnTargetConditionAugment onTargetCondition) {
                try {
                    float beforeDamage = context.getDamage();
                    double beforeTrueDamage = context.getTrueDamageBonus();
                    float updated = onTargetCondition.onTargetCondition(context);
                    context.setDamage(updated);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnTargetCondition %s for mob %s: %s",
                                    augment.getId(), entityId, e.getMessage());
                }
            }
        }

        return new AugmentDispatch.OnHitResult(Math.max(0.0f, context.getDamage()), Math.max(0.0D, context.getTrueDamageBonus()));
    }

    /**
     * Apply onLowHp augment effects when a mob takes damage and reaches low HP.
     * Returns the modified damage (may be 0 if rebirth/etc. activate).
     */
    public float applyOnLowHp(
            UUID entityId,
            Ref<EntityStore> mobRef,
            Ref<EntityStore> attackerRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap statMap,
            float incomingDamage) {
        return applyOnLowHp(entityId,
                mobRef,
                attackerRef,
                commandBuffer,
                statMap,
                incomingDamage,
                null,
                null);
    }

    /**
     * onLowHp dispatch with optional owner PlayerData/SkillManager for summon
     * mirroring. See {@link #applyOnHitSummon} for the rationale.
     */
    public float applyOnLowHp(
            UUID entityId,
            Ref<EntityStore> mobRef,
            Ref<EntityStore> attackerRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap statMap,
            float incomingDamage,
            PlayerData ownerPlayerData,
            SkillManager ownerSkillManager) {

        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.augments.isEmpty()) {
            return incomingDamage;
        }

        applyPassiveHooks(entityId, instance, mobRef, commandBuffer, statMap);

        AugmentHooks.DamageTakenContext context = new AugmentHooks.DamageTakenContext(
                ownerPlayerData,
                instance.runtimeState,
                ownerSkillManager,
                mobRef,
                attackerRef,
                commandBuffer,
                statMap,
                incomingDamage);

        float damage = incomingDamage;

        // Execute low-HP augments in priority order
        for (Augment augment : AugmentDispatch.resolveLowHpTriggerOrder(instance.augments)) {
            AugmentHooks.OnLowHpAugment lowHpHandler = (AugmentHooks.OnLowHpAugment) augment;
            try {
                float beforeDamage = context.getIncomingDamage();
                float result = lowHpHandler.onLowHp(context);
                context.setIncomingDamage(result);
                damage = result;

                if (result <= 0f) {
                    if (isDebugEnabled()) {
                        LOGGER.atInfo().log("[AUGMENT] Mob %s %s activated! Blocked damage.", entityId,
                                augment.getId());
                    }
                    return 0f;
                } else if (result != incomingDamage && isDebugEnabled()) {
                    LOGGER.atInfo().log("[AUGMENT] Mob %s %s reduced damage from %.3f to %.3f",
                            entityId, augment.getId(), incomingDamage, result);
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[AUGMENT] Error executing %s for mob %s: %s", augment.getId(),
                        entityId, e.getMessage());
            }
        }

        return damage;
    }

    /**
     * Apply on-hit effects for a summon attacker (no crit context).
     * Backward-compatible overload that defaults isCritical=false.
     */
    public AugmentDispatch.OnHitResult applyOnHitSummon(
            UUID entityId,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap attackerStats,
            EntityStatMap targetStats,
            float startingDamage) {
        return applyOnHitSummon(entityId,
                attackerRef,
                targetRef,
                commandBuffer,
                attackerStats,
                targetStats,
                startingDamage,
                false);
    }

    /**
     * Apply on-hit effects for a summon attacker.
     * Mirrors the player on-hit dispatch (OnMiss / OnHit / OnCrit / OnTargetCondition)
     * so necromancer summons proc the augments mirrored from their owner.
     * All augments are executed with PlayerData/SkillManager set to null;
     * augment implementations use defensive null-checks for player-context operations.
     */
    public AugmentDispatch.OnHitResult applyOnHitSummon(
            UUID entityId,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap attackerStats,
            EntityStatMap targetStats,
            float startingDamage,
            boolean isCritical) {
        return applyOnHitSummon(entityId,
                attackerRef,
                targetRef,
                commandBuffer,
                attackerStats,
                targetStats,
                startingDamage,
                isCritical,
                null,
                null);
    }

    /**
     * Summon on-hit dispatch that receives the owner's PlayerData and
     * SkillManager. Passing them causes {@link MobSkillAttributeResolver} to
     * resolve strength / sorcery / precision / ferocity from the owner's
     * player stats instead of the summon's empty stat map, so mirrored
     * augments (MagicMissile, BloodEcho, etc.) scale with the summoner's full
     * power. The runtime state is still keyed by the summon's UUID, so
     * cooldowns and stacks remain isolated from the owner's own augment state.
     */
    public AugmentDispatch.OnHitResult applyOnHitSummon(
            UUID entityId,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap attackerStats,
            EntityStatMap targetStats,
            float startingDamage,
            boolean isCritical,
            PlayerData ownerPlayerData,
            SkillManager ownerSkillManager) {
        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.augments.isEmpty()) {
            if (isSummonDebugEnabled()) {
                LOGGER.atInfo().log(
                        "[SUMMON_MIRRORED_AUGMENTS][ON_HIT] No augment instance for summon=%s (instance=%s)",
                        entityId, instance == null ? "null" : "empty");
            }
            return new AugmentDispatch.OnHitResult(startingDamage, 0.0D);
        }

        if (isSummonDebugEnabled()) {
            LOGGER.atInfo().log(
                    "[SUMMON_MIRRORED_AUGMENTS][ON_HIT] Dispatching %d augments for summon=%s startDmg=%.2f crit=%s ownerData=%s ids=%s",
                    instance.augments.size(), entityId, startingDamage, isCritical,
                    ownerPlayerData != null ? ownerPlayerData.getPlayerName() : "null",
                    instance.appliedAugmentIds);
        }

        // Apply passive-stat augment hooks so summons benefit from common-stat
        // offers (strength, precision, ferocity, sorcery, defense, life_force)
        // mirrored from the owner.
        applyPassiveHooks(entityId, instance, attackerRef, commandBuffer, attackerStats);

        AugmentHooks.HitContext context = new AugmentHooks.HitContext(
                ownerPlayerData,
                instance.runtimeState,
                ownerSkillManager,
                attackerRef,
                targetRef,
                commandBuffer,
                attackerStats,
                targetStats,
                startingDamage,
                isCritical,
                false,
                null,
                1.0f);

        if (AugmentDispatch.isMiss(context)) {
            if (isSummonDebugEnabled()) {
                LOGGER.atInfo().log(
                        "[SUMMON_MIRRORED_AUGMENTS][ON_HIT] isMiss=true for summon=%s damage=%.2f targetRef=%s targetStats=%s",
                        entityId, context.getDamage(),
                        targetRef != null && targetRef.isValid() ? "valid" : "invalid",
                        targetStats != null ? "present" : "null");
            }
            for (Augment augment : instance.augments) {
                if (augment instanceof AugmentHooks.OnMissAugment onMiss) {
                    try {
                        onMiss.onMiss(context);
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e)
                                .log("[AUGMENT] Error executing OnMiss %s for summon %s: %s",
                                        augment.getId(), entityId, e.getMessage());
                    }
                }
            }
            return new AugmentDispatch.OnHitResult(
                    Math.max(0.0f, context.getDamage()),
                    Math.max(0.0D, context.getTrueDamageBonus()));
        }

        for (Augment augment : instance.augments) {
            if (augment instanceof AugmentHooks.OnHitAugment onHit) {
                try {
                    float before = context.getDamage();
                    float updated = onHit.onHit(context);
                    context.setDamage(updated);
                    if (isSummonDebugEnabled()) {
                        LOGGER.atInfo().log(
                                "[SUMMON_MIRRORED_AUGMENTS][ON_HIT] %s fired for summon=%s before=%.2f after=%.2f",
                                augment.getId(), entityId, before, updated);
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnHit %s for summon %s: %s",
                                    augment.getId(), entityId, e.getMessage());
                }
            }
            if (isCritical && augment instanceof AugmentHooks.OnCritAugment onCrit) {
                try {
                    onCrit.onCrit(context);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnCrit %s for summon %s: %s",
                                    augment.getId(), entityId, e.getMessage());
                }
            }
            if (augment instanceof AugmentHooks.OnTargetConditionAugment onTargetCondition) {
                try {
                    float updated = onTargetCondition.onTargetCondition(context);
                    context.setDamage(updated);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnTargetCondition %s for summon %s: %s",
                                    augment.getId(), entityId, e.getMessage());
                }
            }
        }
        if (isSummonDebugEnabled()) {
            LOGGER.atInfo().log(
                    "[SUMMON_MIRRORED_AUGMENTS][ON_HIT] Final result for summon=%s: damage=%.2f trueDmg=%.2f (started=%.2f)",
                    entityId, context.getDamage(), context.getTrueDamageBonus(), startingDamage);
        }
        return new AugmentDispatch.OnHitResult(
                Math.max(0.0f, context.getDamage()),
                Math.max(0.0D, context.getTrueDamageBonus()));
    }

    /**
     * Apply kill augment effects for a mob killer.
     */
    public void handleKill(
            UUID entityId,
            Ref<EntityStore> killerRef,
            Ref<EntityStore> victimRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap victimStats) {
        handleKill(entityId, killerRef, victimRef, commandBuffer, victimStats, null, null);
    }

    /**
     * Kill dispatch with optional owner PlayerData/SkillManager for summon
     * mirroring. See {@link #applyOnHitSummon} for the rationale.
     */
    public void handleKill(
            UUID entityId,
            Ref<EntityStore> killerRef,
            Ref<EntityStore> victimRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap victimStats,
            PlayerData ownerPlayerData,
            SkillManager ownerSkillManager) {
        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.augments.isEmpty()) {
            return;
        }

        AugmentHooks.KillContext context = new AugmentHooks.KillContext(
                ownerPlayerData,
                instance.runtimeState,
                ownerSkillManager,
                killerRef,
                victimRef,
                commandBuffer,
                victimStats);

        for (Augment augment : instance.augments) {
            if (augment instanceof AugmentHooks.OnKillAugment onKill) {
                try {
                    onKill.onKill(context);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e)
                            .log("[AUGMENT] Error executing OnKill %s for mob %s: %s", augment.getId(),
                                    entityId, e.getMessage());
                }
            }
        }
    }

    /**
     * Apply onDamageTaken augment effects for mobs.
     */
    public float applyOnDamageTaken(
            UUID entityId,
            Ref<EntityStore> mobRef,
            Ref<EntityStore> attackerRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap statMap,
            float incomingDamage) {
        return applyOnDamageTaken(entityId,
                mobRef,
                attackerRef,
                commandBuffer,
                statMap,
                incomingDamage,
                null,
                null);
    }

    /**
     * onDamageTaken dispatch with optional owner PlayerData/SkillManager for
     * summon mirroring. See {@link #applyOnHitSummon} for the rationale.
     */
    public float applyOnDamageTaken(
            UUID entityId,
            Ref<EntityStore> mobRef,
            Ref<EntityStore> attackerRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap statMap,
            float incomingDamage,
            PlayerData ownerPlayerData,
            SkillManager ownerSkillManager) {

        // Tick pending death bombs for this world even if this mob has no augments.
        DeathBombAugment.tickPendingBombs(commandBuffer, mobRef);

        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.augments.isEmpty()) {
            return incomingDamage;
        }

        AugmentHooks.DamageTakenContext context = new AugmentHooks.DamageTakenContext(
                ownerPlayerData,
                instance.runtimeState,
                ownerSkillManager,
                mobRef,
                attackerRef,
                commandBuffer,
                statMap,
                incomingDamage);

        float damage = incomingDamage;

        for (Augment augment : instance.augments) {
            if (augment instanceof AugmentHooks.OnDamageTakenAugment handler) {
                try {
                    float beforeDamage = context.getIncomingDamage();
                    float result = handler.onDamageTaken(context);
                    context.setIncomingDamage(result);
                    damage = result;

                    if (result != incomingDamage && isDebugEnabled()) {
                        LOGGER.atInfo().log("[AUGMENT] Mob %s %s modified damage from %.3f to %.3f",
                                entityId, augment.getId(), incomingDamage, result);
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("[AUGMENT] Error executing %s for mob %s: %s",
                            augment.getId(), entityId, e.getMessage());
                }
            }
        }

        // Tick again after handlers so newly queued bombs can progress naturally.
        DeathBombAugment.tickPendingBombs(commandBuffer, mobRef);

        return damage;
    }

    /**
     * Clean up augments for a mob when it's removed.
     */
    public void unregisterMob(UUID entityId) {
        mobAugments.remove(entityId);
        mobPassiveAppliedAtMillis.remove(entityId);
    }

    public int clearRuntimeState() {
        int cleared = mobAugments.size() + mobPassiveAppliedAtMillis.size();
        mobAugments.clear();
        mobPassiveAppliedAtMillis.clear();
        return cleared;
    }

    public List<String> clearPersistentHealthModifiers(UUID entityId, EntityStatMap statMap) {
        List<String> clearedModifierKeys = new ArrayList<>();
        if (entityId == null || statMap == null) {
            return clearedModifierKeys;
        }

        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null) {
            return clearedModifierKeys;
        }

        Set<String> modifierKeys = new HashSet<>();
        if (instance.appliedAugmentIds != null) {
            for (String augmentId : instance.appliedAugmentIds) {
                addHealthModifierKeys(modifierKeys, augmentId);
            }
        }
        if (instance.augments != null) {
            for (Augment augment : instance.augments) {
                if (augment != null) {
                    addHealthModifierKeys(modifierKeys, augment.getId());
                }
            }
        }

        if (modifierKeys.isEmpty()) {
            return clearedModifierKeys;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        for (String modifierKey : modifierKeys) {
            statMap.removeModifier(healthIndex, modifierKey);
            clearedModifierKeys.add(modifierKey);
        }
        return clearedModifierKeys;
    }

    /**
     * Check if a mob has any registered augments.
     */
    public boolean hasMobAugments(UUID entityId) {
        return mobAugments.containsKey(entityId);
    }

    private void addHealthModifierKeys(Set<String> modifierKeys, String augmentId) {
        String normalizedAugmentId = normalizeAugmentModifierId(augmentId);
        if (normalizedAugmentId == null || normalizedAugmentId.isBlank()) {
            return;
        }

        modifierKeys.add("EL_" + normalizedAugmentId + "_max_hp_bonus");
        modifierKeys.add(normalizedAugmentId + "_max_hp_bonus");
        modifierKeys.add("EL_" + normalizedAugmentId + "_max_hp_penalty");
        modifierKeys.add(normalizedAugmentId + "_max_hp_penalty");
    }

    private String normalizeAugmentModifierId(String augmentId) {
        if (augmentId == null) {
            return null;
        }
        return augmentId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    public double getAttributeBonus(UUID entityId, SkillAttributeType type) {
        if (entityId == null || type == null) {
            return 0.0D;
        }
        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.runtimeState == null) {
            return 0.0D;
        }
        return Math.max(0.0D, instance.runtimeState.getAttributeBonus(type, System.currentTimeMillis()));
    }

    /**
     * Applies passive-stat augment hooks for a mob outside of combat events.
     */
    public void tickPassiveStats(UUID entityId,
            Ref<EntityStore> mobRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap statMap) {
        if (entityId == null || statMap == null) {
            return;
        }
        MobAugmentInstance instance = mobAugments.get(entityId);
        if (instance == null || instance.passiveAugmentCount == 0) {
            return;
        }
        applyPassiveHooks(entityId, instance, mobRef, commandBuffer, statMap);
    }

    private void applyPassiveHooks(UUID entityId,
            MobAugmentInstance instance,
            Ref<EntityStore> mobRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap statMap) {
        if (instance == null || instance.passiveAugmentCount == 0 || statMap == null) {
            return;
        }

        double deltaSeconds = resolvePassiveDeltaSeconds(entityId);

        // Iterate only over pre-filtered passive augment indices (skips player-only
        // augments without per-tick instanceof + requiresPlayer checks).
        for (int p = 0; p < instance.passiveAugmentCount; p++) {
            int augmentIndex = instance.passiveAugmentIndices[p];
            Augment augment = instance.augments.get(augmentIndex);
            try {
                // Fast path for CommonAugment with a pre-parsed stat offer: skip
                // extractMobAugmentIdFromSelectionKey, parseStatOfferId, and
                // buildSourcePrefix string work entirely.
                if (augment instanceof CommonAugment common
                        && instance.cachedOffers[augmentIndex] != null) {
                    AugmentHooks.PassiveStatContext context = new AugmentHooks.PassiveStatContext(
                            null,
                            instance.runtimeState,
                            null,
                            instance.cachedSelectionKeys[augmentIndex],
                            mobRef,
                            commandBuffer,
                            statMap,
                            deltaSeconds);
                    common.applyMobPassiveFast(context,
                            instance.cachedSourcePrefixes[augmentIndex],
                            instance.cachedOffers[augmentIndex]);
                } else {
                    // Generic path for non-Common augments — still uses pre-computed
                    // selection key to avoid string concatenation.
                    AugmentHooks.PassiveStatContext context = new AugmentHooks.PassiveStatContext(
                            null,
                            instance.runtimeState,
                            null,
                            instance.cachedSelectionKeys[augmentIndex],
                            mobRef,
                            commandBuffer,
                            statMap,
                            deltaSeconds);
                    ((AugmentHooks.PassiveStatAugment) augment).applyPassive(context);
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e)
                        .log("[AUGMENT] Error executing Passive %s for mob %s: %s", augment.getId(),
                                entityId, e.getMessage());
            }
        }

        // Skip the reconciler entirely when the mob has no health-modifying augments
        // (Goliath, RaidBoss, TankEngine, Grasp) — avoids iterating augments + stat
        // map modifier clear/update cycles every tick for the majority of mobs.
        if (instance.hasHealthAugments) {
            AugmentPassiveHealthReconciler.reconcile(statMap, instance.augments, instance.runtimeState);
        }
    }

    private String summarizeCategories(List<Augment> augments) {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put(CATEGORY_ON_HIT, new ArrayList<>());
        categories.put(CATEGORY_ON_CRIT, new ArrayList<>());
        categories.put(CATEGORY_ON_MISS, new ArrayList<>());
        categories.put(CATEGORY_ON_TARGET_CONDITION, new ArrayList<>());
        categories.put(CATEGORY_ON_DAMAGE_TAKEN, new ArrayList<>());
        categories.put(CATEGORY_ON_LOW_HP, new ArrayList<>());
        categories.put(CATEGORY_ON_KILL, new ArrayList<>());
        categories.put(CATEGORY_PASSIVE_STAT, new ArrayList<>());

        for (Augment augment : augments) {
            String augmentId = augment != null ? augment.getId() : "unknown";
            if (augment instanceof AugmentHooks.OnHitAugment) {
                categories.get(CATEGORY_ON_HIT).add(augmentId);
            }
            if (augment instanceof AugmentHooks.OnCritAugment) {
                categories.get(CATEGORY_ON_CRIT).add(augmentId);
            }
            if (augment instanceof AugmentHooks.OnMissAugment) {
                categories.get(CATEGORY_ON_MISS).add(augmentId);
            }
            if (augment instanceof AugmentHooks.OnTargetConditionAugment) {
                categories.get(CATEGORY_ON_TARGET_CONDITION).add(augmentId);
            }
            if (augment instanceof AugmentHooks.OnDamageTakenAugment) {
                categories.get(CATEGORY_ON_DAMAGE_TAKEN).add(augmentId);
            }
            if (augment instanceof AugmentHooks.OnLowHpAugment) {
                categories.get(CATEGORY_ON_LOW_HP).add(augmentId);
            }
            if (augment instanceof AugmentHooks.OnKillAugment) {
                categories.get(CATEGORY_ON_KILL).add(augmentId);
            }
            if (augment instanceof AugmentHooks.PassiveStatAugment) {
                categories.get(CATEGORY_PASSIVE_STAT).add(augmentId);
            }
        }

        StringBuilder builder = new StringBuilder();
        boolean firstCategory = true;
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (!firstCategory) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            firstCategory = false;
        }
        return firstCategory ? "none" : builder.toString();
    }

    /**
     * Accepts a pre-resolved timestamp so the hot path avoids a
     * System.currentTimeMillis() call per mob entity.
     */
    private double resolvePassiveDeltaSeconds(UUID entityId, long nowMillis) {
        Long previous = mobPassiveAppliedAtMillis.put(entityId, nowMillis);
        if (previous == null || previous <= 0L) {
            return 0.1D;
        }
        double deltaSeconds = (nowMillis - previous) / 1000.0D;
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0D) {
            return 0.1D;
        }
        return Math.min(1.0D, deltaSeconds);
    }

    private double resolvePassiveDeltaSeconds(UUID entityId) {
        return resolvePassiveDeltaSeconds(entityId, System.currentTimeMillis());
    }

    /**
     * Represents a mob's active augments and runtime state.
     * Pre-computes per-augment selection keys, source prefixes and parsed
     * stat offers at registration time so the passive-tick hot path avoids
     * repeated string parsing/allocation every second.
     */
    private static class MobAugmentInstance {
        final List<Augment> augments;
        final List<String> appliedAugmentIds;
        final AugmentRuntimeManager.AugmentRuntimeState runtimeState;

        // --- Pre-computed hot-path caches (built once at registration) ---
        /** "mob::<augmentId>::<index>" per augment slot. */
        final String[] cachedSelectionKeys;
        /** CommonAugment.buildSourcePrefix(selectionKey) per slot (null for non-Common). */
        final String[] cachedSourcePrefixes;
        /** CommonAugment.parseStatOfferId(augmentId) per slot (null for non-Common). */
        final CommonAugment.CommonStatOffer[] cachedOffers;
        /** Indices of PassiveStatAugment slots that are NOT player-only. */
        final int[] passiveAugmentIndices;
        final int passiveAugmentCount;
        /** True when at least one augment is a health-modifying type (Goliath/RaidBoss/TankEngine/Grasp). */
        final boolean hasHealthAugments;

        MobAugmentInstance(List<Augment> augments,
                List<String> appliedAugmentIds,
                AugmentRuntimeManager.AugmentRuntimeState runtimeState) {
            this.augments = augments;
            this.appliedAugmentIds = appliedAugmentIds;
            this.runtimeState = runtimeState;

            int size = augments.size();
            this.cachedSelectionKeys = new String[size];
            this.cachedSourcePrefixes = new String[size];
            this.cachedOffers = new CommonAugment.CommonStatOffer[size];

            boolean foundHealthAugment = false;
            int[] tempPassiveIndices = new int[size];
            int passiveCount = 0;

            for (int i = 0; i < size; i++) {
                Augment augment = augments.get(i);
                String originalAugmentId = (appliedAugmentIds != null && i < appliedAugmentIds.size())
                        ? appliedAugmentIds.get(i)
                        : augment.getId();

                String selectionKey = "mob::" + originalAugmentId + "::" + i;
                cachedSelectionKeys[i] = selectionKey;

                if (augment instanceof CommonAugment) {
                    cachedOffers[i] = CommonAugment.parseStatOfferId(originalAugmentId);
                    cachedSourcePrefixes[i] = CommonAugment.buildSourcePrefixStatic(selectionKey);
                }

                if (augment instanceof GoliathAugment || augment instanceof RaidBossAugment
                        || augment instanceof TankEngineAugment || augment instanceof GraspOfTheUndyingAugment) {
                    foundHealthAugment = true;
                }

                if (augment instanceof AugmentHooks.PassiveStatAugment passive && !passive.requiresPlayer()) {
                    tempPassiveIndices[passiveCount++] = i;
                }
            }

            this.hasHealthAugments = foundHealthAugment;
            this.passiveAugmentIndices = new int[passiveCount];
            System.arraycopy(tempPassiveIndices, 0, this.passiveAugmentIndices, 0, passiveCount);
            this.passiveAugmentCount = passiveCount;
        }
    }
}
