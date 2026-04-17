package com.airijko.endlessleveling.mob;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.MobAugmentExecutor;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.leveling.XpKillCreditTracker;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive.SummonInheritedStats;
import com.airijko.endlessleveling.passives.type.ArmyOfTheDeadPassive;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.managers.LoggingManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Objects;

/**
 * Scales damage dealt by non-player entities (mobs) using LevelingManager
 * multipliers.
 */
public class MobDamageScalingSystem extends DamageEventSystem {

    private final MobLevelingManager levelingManager;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final long SUMMON_DEBUG_LOG_COOLDOWN_MS = 2000L;
    private static final Map<Integer, Long> OUTGOING_DEBUG_LAST_LOG = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> INCOMING_DEBUG_LAST_LOG = new ConcurrentHashMap<>();

    public MobDamageScalingSystem(MobLevelingManager levelingManager) {
        this.levelingManager = Objects.requireNonNull(levelingManager);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Nonnull
    @Override
    public Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage damage) {
        if (damage.getSource() instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
            if (!EntityRefUtil.isUsable(attackerRef) || !EntityRefUtil.isUsable(targetRef)) {
                return;
            }

            if (ArmyOfTheDeadPassive.shouldPreventFriendlyDamage(attackerRef, targetRef, store, commandBuffer)) {
                damage.setAmount(0.0f);
                return;
            }

            XpKillCreditTracker.recordDamage(targetRef, attackerRef, store, commandBuffer);

            ArmyOfTheDeadPassive.focusSummonsOnSummonAttacker(targetRef, attackerRef, store, commandBuffer);

            boolean managedSummonAttacker = ArmyOfTheDeadPassive.isManagedSummon(attackerRef, store, commandBuffer);
            if (managedSummonAttacker) {
                // Skip re-scaling and re-augmenting damage that was already
                // produced by a summon augment (MagicMissile, GraspOfTheUndying,
                // Burn DoT, etc.). Proc and DoT damage have already been computed
                // from the owner's stats inside the augment; running
                // applySummonOutgoingScaling on them again would multiply them a
                // second time, and applySummonAttackerAugments would cascade
                // (augment-on-augment proc loops).
                boolean isSummonProc = com.airijko.endlessleveling.systems.PlayerCombatSystem.isAugmentProcDamage(damage)
                        || com.airijko.endlessleveling.systems.PlayerCombatSystem.isAugmentDotDamage(damage);
                if (!isSummonProc) {
                    float beforeAugments = damage.getAmount();
                    boolean summonCrit = applySummonOutgoingScaling(damage, attackerRef, store, commandBuffer);
                    double summonTrueDamage = applySummonAttackerAugments(damage, attackerRef, targetRef,
                            store, commandBuffer, summonCrit);

                    // Summons mirror the owner's player→mob pipeline: apply the
                    // mob's level-difference defense, the mob's common-stat
                    // defense (capped 80%), and the mob's onDamageTaken/onLowHp
                    // augments. True damage from summon augments bypasses the
                    // augment-defense pipeline and is only reduced by level diff,
                    // matching how player-true-damage is treated.
                    PlayerRef targetPlayerRef = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                            PlayerRef.getComponentType());
                    boolean targetIsPlayer = targetPlayerRef != null && targetPlayerRef.isValid();

                    double mobLevelDiffReduction = 0.0D;
                    if (!targetIsPlayer) {
                        int ownerLevel = resolveSummonOwnerLevel(attackerRef, store, commandBuffer);
                        if (ownerLevel > 0
                                && levelingManager.isMobLevelingEnabled()
                                && levelingManager.isMobDefenseScalingEnabled(targetRef.getStore())) {
                            int mobLevel = levelingManager.resolveMobLevel(targetRef, commandBuffer);
                            mobLevelDiffReduction = levelingManager.getMobDefenseReductionForLevels(
                                    targetRef, commandBuffer, mobLevel, ownerLevel);
                            if (mobLevelDiffReduction != 0.0D) {
                                damage.setAmount((float) (damage.getAmount() * (1.0D - mobLevelDiffReduction)));
                            }
                        }

                        float afterMobDefense = applyTargetMobDefensePipeline(targetRef, store, attackerRef,
                                commandBuffer, damage.getAmount());
                        damage.setAmount(afterMobDefense);
                    }

                    if (summonTrueDamage > 0.0D) {
                        double reducedTrue = targetIsPlayer
                                ? summonTrueDamage
                                : summonTrueDamage * (1.0D - mobLevelDiffReduction);
                        damage.setAmount(Math.max(0.0f, damage.getAmount() + (float) Math.max(0.0D, reducedTrue)));
                    }

                    if (LoggingManager.isDebugSectionEnabled("necromancer_summons")) {
                        LOGGER.atInfo().log(
                                "[NECROMANCER_SUMMONS][COMBAT] Summon hit: before=%.2f after=%.2f crit=%s proc=%s true=%.2f mobLevelDiffRed=%.3f targetIsPlayer=%s",
                                beforeAugments, damage.getAmount(), summonCrit, false,
                                summonTrueDamage, mobLevelDiffReduction, targetIsPlayer);
                    }
                } else if (LoggingManager.isDebugSectionEnabled("necromancer_summons")) {
                    LOGGER.atInfo().log(
                            "[NECROMANCER_SUMMONS][COMBAT] Skipped summon augments — proc/dot damage=%.2f",
                            damage.getAmount());
                }
            }

            PlayerRef attackerPlayer = EntityRefUtil.tryGetComponent(commandBuffer, attackerRef,
                    PlayerRef.getComponentType());
            if ((attackerPlayer == null || !attackerPlayer.isValid())
                    && !managedSummonAttacker
                    && levelingManager.isMobDamageScalingEnabled(store)) {

                // Treat as mob source
                int mobLevel = levelingManager.resolveMobLevel(attackerRef, commandBuffer);
                PlayerRef defenderPlayer = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                        PlayerRef.getComponentType());

                double mult;
                if (defenderPlayer != null && defenderPlayer.isValid()) {
                    int playerLevel = levelingManager.getPlayerLevel(defenderPlayer);
                    mult = levelingManager.getMobDamageMultiplierForLevels(
                            attackerRef,
                            commandBuffer,
                            mobLevel,
                            playerLevel);
                } else {
                    mult = levelingManager.getMobDamageMultiplierForLevel(attackerRef, commandBuffer, mobLevel);
                }

                float old = damage.getAmount();
                float updated = (float) (old * mult);
                damage.setAmount(updated);
                try {
                    LOGGER.atFiner().log("MobDamageScaling: scaled damage from %f to %f for entity %d", old, updated,
                            attackerRef.getIndex());
                } catch (Throwable ignored) {
                }
            }

            if (ArmyOfTheDeadPassive.isManagedSummon(targetRef, store, commandBuffer)) {
                applySummonIncomingDefenseScaling(damage, targetRef, store, commandBuffer);
                applySummonDefenderAugments(damage, targetRef, attackerRef, store, commandBuffer);
            }
        }
    }

    /**
     * Runs summon on-hit augments and writes the regular (non-true) damage
     * portion back onto the Damage event. Returns the true-damage bonus so
     * the caller can reduce it by level diff and add it back separately —
     * keeping it out of the mob-augment defense/onDamageTaken pipeline, same
     * as the player→mob true-damage path in PlayerCombatSystem.
     */
    private double applySummonAttackerAugments(@Nonnull Damage damage,
            @Nonnull Ref<EntityStore> attackerRef,
            @Nonnull Ref<EntityStore> targetRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            boolean isCritical) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return 0.0D;
        }

        MobAugmentExecutor executor = plugin.getMobAugmentExecutor();
        if (executor == null) {
            return 0.0D;
        }

        UUID attackerUuid = resolveEntityUuid(attackerRef, store, commandBuffer);
        if (attackerUuid == null) {
            if (LoggingManager.isDebugSectionEnabled("necromancer_summons")) {
                LOGGER.atInfo().log("[NECROMANCER_SUMMONS][AUGMENTS] applySummonAttackerAugments: attackerUuid is null");
            }
            return 0.0D;
        }
        // Lazy re-mirror: if the owner's augment selection has drifted from
        // what's currently bound to this summon (or the owner had no augments
        // selected at spawn), re-register here before the on-hit dispatch so
        // necromancer summons stay 1:1 with their summoner's loadout in combat.
        ArmyOfTheDeadPassive.ensureSummonAugmentsInSync(attackerUuid, store);
        if (!executor.hasMobAugments(attackerUuid)) {
            if (LoggingManager.isDebugSectionEnabled("necromancer_summons")) {
                LOGGER.atInfo().log(
                        "[NECROMANCER_SUMMONS][AUGMENTS] No mob augments bound for summon=%s (hasMobAugments=false)",
                        attackerUuid);
            }
            return 0.0D;
        }

        EntityStatMap attackerStats = EntityRefUtil.tryGetComponent(commandBuffer, attackerRef,
                EntityStatMap.getComponentType());
        EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                EntityStatMap.getComponentType());

        float before = Math.max(0.0f, damage.getAmount());

        // Resolve the summoner's PlayerData + SkillManager so MobSkillAttributeResolver
        // can return the owner's real strength/sorcery/precision/ferocity to
        // augment hooks. Without this, AugmentUtils.resolveSorcery(context) and
        // friends fall back to the summon's empty stat map, leaving abilities
        // like MagicMissile doing flat-only damage on summons.
        PlayerData ownerPlayerData = resolveSummonOwnerPlayerData(attackerRef, store, commandBuffer, plugin);
        SkillManager ownerSkillManager = plugin.getSkillManager();

        // Mirrors player on-hit dispatch (OnMiss/OnHit/OnCrit/OnTargetCondition) for summons.
        var onHit = executor.applyOnHitSummon(attackerUuid,
            attackerRef,
            targetRef,
            commandBuffer,
            attackerStats,
            targetStats,
            before,
            isCritical,
            ownerPlayerData,
            ownerSkillManager);

        float regular = Math.max(0.0f, onHit.damage());
        damage.setAmount(regular);
        return Math.max(0.0D, onHit.trueDamageBonus());
    }

    private int resolveSummonOwnerLevel(@Nonnull Ref<EntityStore> summonRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return 0;
        }
        PlayerData ownerData = resolveSummonOwnerPlayerData(summonRef, store, commandBuffer, plugin);
        if (ownerData == null) {
            return 0;
        }
        return Math.max(1, ownerData.getLevel());
    }

    /**
     * Applies the target mob's common-stat defense (capped 80%) and augment
     * onDamageTaken/onLowHp hooks to summon-dealt regular damage — mirroring
     * PlayerCombatSystem.applyMobAugmentsIfPresent so summon hits respect the
     * same mob resistances as player hits.
     */
    private float applyTargetMobDefensePipeline(@Nonnull Ref<EntityStore> targetRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> attackerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            float incomingDamage) {
        if (incomingDamage <= 0.0f) {
            return Math.max(0.0f, incomingDamage);
        }
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return incomingDamage;
        }
        MobAugmentExecutor executor = plugin.getMobAugmentExecutor();
        if (executor == null) {
            return incomingDamage;
        }

        EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                EntityStatMap.getComponentType());
        if (targetStats == null) {
            return incomingDamage;
        }

        List<String> augmentIds = levelingManager.getMobOverrideAugmentIds(targetRef, store, commandBuffer);
        if (augmentIds.isEmpty()) {
            return incomingDamage;
        }

        UUID mobUuid = resolveEntityUuid(targetRef, store, commandBuffer);
        if (mobUuid == null) {
            return incomingDamage;
        }

        if (!executor.hasMobAugments(mobUuid)
                && plugin.getAugmentManager() != null
                && plugin.getAugmentRuntimeManager() != null) {
            executor.registerMobAugments(mobUuid,
                    augmentIds,
                    plugin.getAugmentManager(),
                    plugin.getAugmentRuntimeManager());
        }

        double defensePercent = Math.max(0.0D,
                Math.min(MOB_DEFENSE_MAX_REDUCTION_PERCENT,
                        executor.getAttributeBonus(mobUuid, SkillAttributeType.DEFENSE)));
        float afterDefense = (float) (incomingDamage * (1.0D - (defensePercent / 100.0D)));

        float afterDamageTaken = executor.applyOnDamageTaken(
                mobUuid,
                targetRef,
                attackerRef,
                commandBuffer,
                targetStats,
                afterDefense);
        float afterLowHp = executor.applyOnLowHp(
                mobUuid,
                targetRef,
                attackerRef,
                commandBuffer,
                targetStats,
                afterDamageTaken);
        return Math.max(0.0f, afterLowHp);
    }

    private static final double MOB_DEFENSE_MAX_REDUCTION_PERCENT = 80.0D;

    private void applySummonDefenderAugments(@Nonnull Damage damage,
            @Nonnull Ref<EntityStore> targetRef,
            @Nonnull Ref<EntityStore> attackerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return;
        }

        MobAugmentExecutor executor = plugin.getMobAugmentExecutor();
        if (executor == null) {
            return;
        }

        UUID targetUuid = resolveEntityUuid(targetRef, store, commandBuffer);
        if (targetUuid == null) {
            return;
        }
        // Lazy re-mirror so onDamageTaken / onLowHp augments fired against the
        // summon also reflect the owner's current loadout, not just whatever was
        // selected at the moment the summon spawned.
        ArmyOfTheDeadPassive.ensureSummonAugmentsInSync(targetUuid, store);
        if (!executor.hasMobAugments(targetUuid)) {
            return;
        }

        EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                EntityStatMap.getComponentType());
        if (targetStats == null) {
            return;
        }

        // Resolve owner context for defender-side augments (e.g. Fortress,
        // Overheal, Bailout) so shield/heal values scale with the summoner's
        // stats rather than the summon's empty stat map.
        PlayerData ownerPlayerData = resolveSummonOwnerPlayerData(targetRef, store, commandBuffer, plugin);
        SkillManager ownerSkillManager = plugin.getSkillManager();

        float before = Math.max(0.0f, damage.getAmount());
        float afterDamageTaken = executor.applyOnDamageTaken(
                targetUuid,
                targetRef,
                attackerRef,
                commandBuffer,
                targetStats,
                before,
                ownerPlayerData,
                ownerSkillManager);
        float afterLowHp = executor.applyOnLowHp(
                targetUuid,
                targetRef,
                attackerRef,
                commandBuffer,
                targetStats,
                afterDamageTaken,
                ownerPlayerData,
                ownerSkillManager);

        damage.setAmount(Math.max(0.0f, afterLowHp));
    }

    /**
     * Looks up the PlayerData of the summoner that owns the given summon ref.
     * Returns null if the ref isn't a managed summon or the owner isn't
     * tracked (e.g. player offline).
     */
    private PlayerData resolveSummonOwnerPlayerData(@Nonnull Ref<EntityStore> summonRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull EndlessLeveling plugin) {
        UUID ownerUuid = ArmyOfTheDeadPassive.getManagedSummonOwnerUuid(summonRef, store, commandBuffer);
        if (ownerUuid == null) {
            return null;
        }
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        if (playerDataManager == null) {
            return null;
        }
        return playerDataManager.get(ownerUuid);
    }

    private boolean applySummonOutgoingScaling(@Nonnull Damage damage,
            @Nonnull Ref<EntityStore> summonRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        SummonInheritedStats inherited = ArmyOfTheDeadPassive.resolveManagedSummonInheritedStats(
                summonRef,
                store,
                commandBuffer);

        double inheritanceValue = ArmyOfTheDeadPassive.getManagedSummonStatInheritance(
                summonRef,
                store,
                commandBuffer);
        double baseDamageValue = ArmyOfTheDeadPassive.getManagedSummonBaseDamage(
            summonRef,
            store,
            commandBuffer);
        java.util.UUID ownerUuid = ArmyOfTheDeadPassive.getManagedSummonOwnerUuid(summonRef, store, commandBuffer);

        float before = Math.max(0.0f, damage.getAmount());
        float boostedBase = before + Math.max(0.0f, (float) baseDamageValue);
        float amount = boostedBase * Math.max(0.0f, inherited.damageMultiplier());
        boolean critApplied = false;

        if (amount > 0.0f && inherited.critChance() > 0.0f
                && ThreadLocalRandom.current().nextDouble() < inherited.critChance()) {
            amount *= Math.max(1.0f, inherited.critDamageMultiplier());
            critApplied = true;
        }

        float after = Math.max(0.0f, amount);
        damage.setAmount(after);

        if (shouldLogSummonDebug(OUTGOING_DEBUG_LAST_LOG, summonRef.getIndex())) {
            LOGGER.atInfo().log(
                    "[ARMY_OF_THE_DEAD][DEBUG-HIT][OUT] summonRef=%d owner=%s inheritance=%.3f before=%.3f baseDamage=%.3f boostedBase=%.3f after=%.3f dmgMult=%.3f critChance=%.3f critDmgMult=%.3f critApplied=%s",
                    summonRef.getIndex(),
                    ownerUuid,
                    inheritanceValue,
                    before,
                    baseDamageValue,
                    boostedBase,
                    after,
                    inherited.damageMultiplier(),
                    inherited.critChance(),
                    inherited.critDamageMultiplier(),
                    critApplied);
        }

        return critApplied;
    }

    private void applySummonIncomingDefenseScaling(@Nonnull Damage damage,
            @Nonnull Ref<EntityStore> summonRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        SummonInheritedStats inherited = ArmyOfTheDeadPassive.resolveManagedSummonInheritedStats(
                summonRef,
                store,
                commandBuffer);

        double inheritanceValue = ArmyOfTheDeadPassive.getManagedSummonStatInheritance(
                summonRef,
                store,
                commandBuffer);
        java.util.UUID ownerUuid = ArmyOfTheDeadPassive.getManagedSummonOwnerUuid(summonRef, store, commandBuffer);

        float before = Math.max(0.0f, damage.getAmount());
        float amount = before;
        float reduction = Math.max(0.0f, Math.min(0.95f, inherited.defenseReduction()));
        float after = Math.max(0.0f, amount * (1.0f - reduction));
        damage.setAmount(after);

        if (shouldLogSummonDebug(INCOMING_DEBUG_LAST_LOG, summonRef.getIndex())) {
            LOGGER.atInfo().log(
                    "[ARMY_OF_THE_DEAD][DEBUG-HIT][IN] summonRef=%d owner=%s inheritance=%.3f before=%.3f after=%.3f defenseReduction=%.3f",
                    summonRef.getIndex(),
                    ownerUuid,
                    inheritanceValue,
                    before,
                    after,
                    reduction);
        }
    }

    private boolean shouldLogSummonDebug(Map<Integer, Long> logMap, int entityIndex) {
        long now = System.currentTimeMillis();
        Long last = logMap.get(entityIndex);
        if (last != null && now - last < SUMMON_DEBUG_LOG_COOLDOWN_MS) {
            return false;
        }
        logMap.put(entityIndex, now);
        return true;
    }

    private UUID resolveEntityUuid(Ref<EntityStore> ref,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null) {
            return null;
        }

        UUIDComponent uuidComponent = commandBuffer != null
                ? commandBuffer.getComponent(ref, UUIDComponent.getComponentType())
                : null;
        if (uuidComponent == null && store != null) {
            uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        }
        if (uuidComponent == null) {
            return null;
        }

        UUID uuid = uuidComponent.getUuid();
        return uuid != null ? uuid : null;
    }
}
