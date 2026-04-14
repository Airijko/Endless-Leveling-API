package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentRuntimeState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.component.spatial.SpatialResource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FrozenDomainAugment extends Augment
        implements AugmentHooks.OnDamageTakenAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "frozen_domain";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final long SLOW_DURATION_MILLIS = 2000L;
    private static final float MIN_MOVEMENT_MULTIPLIER = 0.0001F;
    private static final String[] SLOW_EFFECT_IDS = new String[] { "slowness", "slow" };
    private static final String ZONE_PARTICLE_ID = "EL_FrozenDomain_Zone";
    private static final long ZONE_VISUAL_INTERVAL_MILLIS = 2500L;
    private static final double VANILLA_PARTICLE_RADIUS = 4.0D;
    private static final String[] TRIGGER_PULSE_SFX_IDS = new String[] {
            "SFX_Arrow_Frost_Miss",
            "SFX_Arrow_Frost_Hit",
            "SFX_Ice_Ball_Death"
    };
    private static final double AURA_VFX_Y_OFFSET = 0.3D;
    private static final String PLAYER_HASTE_DEBUFF_SOURCE_PREFIX = ID + "_target_haste_debuff_";
    private static final Map<String, ActiveFrozen> ACTIVE_FROST = new ConcurrentHashMap<>();
    private static final Map<String, ZoneState> ZONE_STATES = new ConcurrentHashMap<>();

    private final double slowPercent;
    private final double stolenSlowRatio;
    private final double baseRadius;
    private final double healthPerRadiusBlock;
    private final long activeDurationMillis;
    private final long cooldownMillis;
    private final long slowTickIntervalMillis;
    private final double lifeForceFlatBonus;
    private final double slowPercentCap;

    private static final class MovementSnapshot {
        final float forwardWalk;
        final float backwardWalk;
        final float strafeWalk;
        final float forwardRun;
        final float backwardRun;
        final float strafeRun;
        final float forwardCrouch;
        final float backwardCrouch;
        final float strafeCrouch;
        final float forwardSprint;

        MovementSnapshot(MovementSettings source) {
            this.forwardWalk = source.forwardWalkSpeedMultiplier;
            this.backwardWalk = source.backwardWalkSpeedMultiplier;
            this.strafeWalk = source.strafeWalkSpeedMultiplier;
            this.forwardRun = source.forwardRunSpeedMultiplier;
            this.backwardRun = source.backwardRunSpeedMultiplier;
            this.strafeRun = source.strafeRunSpeedMultiplier;
            this.forwardCrouch = source.forwardCrouchSpeedMultiplier;
            this.backwardCrouch = source.backwardCrouchSpeedMultiplier;
            this.strafeCrouch = source.strafeCrouchSpeedMultiplier;
            this.forwardSprint = source.forwardSprintSpeedMultiplier;
        }

        void apply(MovementSettings target, float multiplier) {
            target.forwardWalkSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, forwardWalk * multiplier);
            target.backwardWalkSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, backwardWalk * multiplier);
            target.strafeWalkSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, strafeWalk * multiplier);
            target.forwardRunSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, forwardRun * multiplier);
            target.backwardRunSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, backwardRun * multiplier);
            target.strafeRunSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, strafeRun * multiplier);
            target.forwardCrouchSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, forwardCrouch * multiplier);
            target.backwardCrouchSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, backwardCrouch * multiplier);
            target.strafeCrouchSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, strafeCrouch * multiplier);
            target.forwardSprintSpeedMultiplier = Math.max(MIN_MOVEMENT_MULTIPLIER, forwardSprint * multiplier);
        }

        void restore(MovementSettings target) {
            target.forwardWalkSpeedMultiplier = forwardWalk;
            target.backwardWalkSpeedMultiplier = backwardWalk;
            target.strafeWalkSpeedMultiplier = strafeWalk;
            target.forwardRunSpeedMultiplier = forwardRun;
            target.backwardRunSpeedMultiplier = backwardRun;
            target.strafeRunSpeedMultiplier = strafeRun;
            target.forwardCrouchSpeedMultiplier = forwardCrouch;
            target.backwardCrouchSpeedMultiplier = backwardCrouch;
            target.strafeCrouchSpeedMultiplier = strafeCrouch;
            target.forwardSprintSpeedMultiplier = forwardSprint;
        }
    }

    private static final class ActiveFrozen {
        Ref<EntityStore> targetRef;
        UUID targetUuid;
        long expiresAt;
        double slowPercent;
        MovementSnapshot movementSnapshot;
        MovementSnapshot defaultMovementSnapshot;
        boolean fallbackSlowEffectApplied;
        String fallbackSlowEffectId;
        boolean loggedMissingMovementManager;
        boolean loggedMissingMovementSettings;
        boolean loggedMissingEffectController;
        boolean loggedMissingSlowEffectAsset;
        boolean loggedEffectApplyFailure;
    }

    private static final class ZoneState {
        Vector3d position;
        long lastVisualAt;
    }

    public FrozenDomainAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> aura = AugmentValueReader.getMap(passives, "aura_frozen_domain");
        Map<String, Object> radiusScaling = AugmentValueReader.getMap(aura, "radius_health_scaling");
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");
        Map<String, Object> lifeForce = AugmentValueReader.getMap(buffs, "life_force");

        this.slowPercentCap = Math.max(0.0D, AugmentValueReader.getDouble(aura, "slow_percent_cap", 0.0D));

        this.slowPercent = normalizeSlowPercent(AugmentValueReader.getDouble(aura, "slow_percent", 0.0D),
            slowPercentCap);
        this.stolenSlowRatio = clampRatio(AugmentValueReader.getDouble(aura, "stolen_slow_ratio", 0.0D));
        this.baseRadius = Math.max(0.0D, AugmentValueReader.getDouble(aura, "radius", 0.0D));
        this.healthPerRadiusBlock = Math.max(0.0D,
                AugmentValueReader.getDouble(radiusScaling, "health_per_block", 0.0D));
        this.activeDurationMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(aura, "duration", 0.0D));
        this.cooldownMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(aura, "cooldown", 0.0D));
        this.slowTickIntervalMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(aura, "slow_interval", 2.5D));
        this.lifeForceFlatBonus = Math.max(0.0D, AugmentValueReader.getDouble(lifeForce, "value", 0.0D));
    }

    @Override
    public float onDamageTaken(AugmentHooks.DamageTakenContext context) {
        if (context == null || context.getRuntimeState() == null || activeDurationMillis <= 0L) {
            return context != null ? context.getIncomingDamage() : 0f;
        }

        AugmentRuntimeState runtimeState = context.getRuntimeState();
        var state = runtimeState.getState(ID);
        long now = System.currentTimeMillis();
        if (state.getExpiresAt() > now) {
            return context.getIncomingDamage();
        }

        long cooldownEndsAt = (long) state.getStoredValue();
        if (cooldownEndsAt > now) {
            return context.getIncomingDamage();
        }

        long activeUntil = now + activeDurationMillis;
        long combinedCooldownEnd = cooldownMillis > 0L ? activeUntil + cooldownMillis : activeUntil;
        state.setExpiresAt(activeUntil);
        state.setStoredValue(combinedCooldownEnd);
        state.setStacks(1);
        state.setLastProc(0L);
        if (combinedCooldownEnd > now) {
            runtimeState.setCooldown(ID, getName(), combinedCooldownEnd);
        }

        // Capture zone position at activation — zone stays in place, does not follow player
        Ref<EntityStore> defenderRef = context.getDefenderRef();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (defenderRef != null && commandBuffer != null) {
            TransformComponent transform = EntityRefUtil.tryGetComponent(commandBuffer,
                    defenderRef, TransformComponent.getComponentType());
            if (transform != null && transform.getPosition() != null) {
                Vector3d pos = transform.getPosition();
                String key = keyFor(defenderRef, commandBuffer);
                ZoneState zone = ZONE_STATES.computeIfAbsent(key, unused -> new ZoneState());
                zone.position = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                zone.lastVisualAt = 0L;


                // Resolve radius for visual
                double activationRadius = baseRadius;
                EntityStatMap defenderStats = EntityRefUtil.tryGetComponent(commandBuffer,
                        defenderRef, EntityStatMap.getComponentType());
                if (defenderStats != null) {
                    EntityStatValue hp = defenderStats.get(DefaultEntityStatTypes.getHealth());
                    if (hp != null) {
                        activationRadius = resolveRadius(Math.max(0.0D, hp.getMax()));
                    }
                }

                // Spawn initial zone particle at fixed position
                spawnZoneParticle(zone.position, activationRadius, commandBuffer);
                playTriggerPulseSound(defenderRef, new Vector3d(
                        zone.position.getX(),
                        zone.position.getY() + AURA_VFX_Y_OFFSET,
                        zone.position.getZ()));
            }
        }

        PlayerRef playerRef = AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getDefenderRef());
        if (playerRef != null && playerRef.isValid()) {
            AugmentUtils.sendAugmentMessage(playerRef,
                    String.format("%s activated for %.1fs.", getName(), activeDurationMillis / 1000.0D));
        }

        return context.getIncomingDamage();
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null || context.getCommandBuffer() == null
                || context.getPlayerRef() == null || context.getStatMap() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        var augmentState = context.getRuntimeState().getState(ID);
        boolean active = augmentState.getExpiresAt() > now;
        String playerKey = keyFor(context.getPlayerRef(), context.getCommandBuffer());

        if (!active) {
            boolean hadZoneState = ZONE_STATES.remove(playerKey) != null;
            boolean hadActiveState = augmentState.getStacks() > 0;
            boolean hasLingeringFrozenTargets = !ACTIVE_FROST.isEmpty();
            if (!hadZoneState && !hadActiveState && !hasLingeringFrozenTargets) {
                return;
            }

            if (hadActiveState) {
                PlayerRef playerRef = AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getPlayerRef());
                if (playerRef != null && playerRef.isValid()) {
                    AugmentUtils.sendAugmentMessage(playerRef,
                            String.format("%s expired.", getName()));
                }
                augmentState.setStacks(0);
            }
            AugmentUtils.setAttributeBonus(context.getRuntimeState(), ID + "_life_force", SkillAttributeType.LIFE_FORCE,
                    0.0D, 0L);
            AugmentUtils.setAttributeBonus(context.getRuntimeState(), ID + "_stolen_haste", SkillAttributeType.HASTE,
                    0.0D, 0L);
            if (hasLingeringFrozenTargets) {
                cleanupExpired(context.getCommandBuffer(), now);
            }
            return;
        }

        AugmentUtils.setAttributeBonus(context.getRuntimeState(), ID + "_life_force", SkillAttributeType.LIFE_FORCE,
                lifeForceFlatBonus, 0L);

        EntityStatMap sourceStats = context.getStatMap();
        EntityStatValue sourceHp = sourceStats.get(DefaultEntityStatTypes.getHealth());
        if (sourceHp == null || sourceHp.getMax() <= 0f || sourceHp.get() <= 0f) {
            cleanupExpired(context.getCommandBuffer(), now);
            return;
        }

        double maxHealth = Math.max(0.0D, sourceHp.getMax());
        double radius = resolveRadius(maxHealth);

        // Resolve zone position — use stored position (stationary zone)
        ZoneState zone = ZONE_STATES.get(playerKey);
        if (zone == null || zone.position == null) {
            // Fallback: if zone state was lost, re-capture from current position
            TransformComponent sourceTransform = EntityRefUtil.tryGetComponent(context.getCommandBuffer(),
                    context.getPlayerRef(), TransformComponent.getComponentType());
            if (sourceTransform == null || sourceTransform.getPosition() == null) {
                cleanupExpired(context.getCommandBuffer(), now);
                return;
            }
            Vector3d pos = sourceTransform.getPosition();
            zone = new ZoneState();
            zone.position = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
            zone.lastVisualAt = 0L;
            ZONE_STATES.put(playerKey, zone);
        }

        Vector3d zonePos = zone.position;

        // Zone ground particle (spawned at fixed position, re-spawned periodically)
        if (zone.lastVisualAt <= 0L || now - zone.lastVisualAt >= ZONE_VISUAL_INTERVAL_MILLIS) {
            zone.lastVisualAt = now;
            spawnZoneParticle(zonePos, radius, context.getCommandBuffer());
        }

        long effectiveTickIntervalMillis = Math.max(1L, Math.min(1000L, slowTickIntervalMillis));
        if (augmentState.getLastProc() > 0L && now - augmentState.getLastProc() < effectiveTickIntervalMillis) {
            cleanupExpired(context.getCommandBuffer(), now);
            return;
        }

        if (radius <= 0.0D || slowPercent <= 0.0D) {
            cleanupExpired(context.getCommandBuffer(), now);
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        augmentState.setLastProc(now);

        UUID sourceUuid = context.getPlayerData() != null ? context.getPlayerData().getUuid() : null;
        PartyManager partyManager = resolvePartyManager();
        UUID sourcePartyLeader = resolvePartyLeader(partyManager, sourceUuid);

        int affectedTargets = 0;
        HashSet<Integer> visitedEntityIds = new HashSet<>();
        // Use zone position for sphere check — zone is stationary
        for (Ref<EntityStore> targetRef : TargetUtil.getAllEntitiesInSphere(
                zonePos,
                radius,
                commandBuffer)) {
            if (targetRef == null || !targetRef.isValid()) {
                continue;
            }
            if (!visitedEntityIds.add(targetRef.getIndex())) {
                continue;
            }
            if (targetRef.equals(context.getPlayerRef())) {
                continue;
            }
            if (isFriendlyPetOrSummon(sourceUuid, sourcePartyLeader, targetRef, commandBuffer, partyManager)) {
                continue;
            }
            if (isSamePartyTarget(sourceUuid, sourcePartyLeader, targetRef, commandBuffer, partyManager)) {
                continue;
            }
            // Skip dead entities to avoid "Entity can't be removed and also
            // receive an update" tracker errors.
            if (!EntityRefUtil.isAliveAndUsable(targetRef, commandBuffer)) {
                continue;
            }

            EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    EntityStatMap.getComponentType());
            EntityStatValue targetHp = targetStats == null ? null : targetStats.get(DefaultEntityStatTypes.getHealth());
            if (targetHp == null || targetHp.getMax() <= 0f || targetHp.get() <= 0f) {
                continue;
            }

            String key = keyFor(targetRef, commandBuffer);
            ActiveFrozen state = ACTIVE_FROST.computeIfAbsent(key, unused -> new ActiveFrozen());
            state.targetRef = targetRef;
            state.targetUuid = resolveEntityUuid(targetRef, commandBuffer);
            state.expiresAt = now + SLOW_DURATION_MILLIS;
            state.slowPercent = slowPercent;
            applySlowIfPossible(state, commandBuffer, targetRef);
            affectedTargets++;
        }

        cleanupExpired(commandBuffer, now);

        double stolenHastePercent = affectedTargets * slowPercent * stolenSlowRatio * 100.0D;
        AugmentUtils.setAttributeBonus(context.getRuntimeState(), ID + "_stolen_haste", SkillAttributeType.HASTE,
                stolenHastePercent, 0L);
    }

    // ── Zone particle spawning ─────────────────────────────────────────────

    private static void spawnZoneParticle(Vector3d zonePos, double radius,
            CommandBuffer<EntityStore> commandBuffer) {
        if (zonePos == null || commandBuffer == null || radius <= 0.0D) {
            return;
        }
        try {
            // Scale particle system to match computed radius
            float scale = (float) (radius / VANILLA_PARTICLE_RADIUS);

            // Collect nearby players for particle broadcast
            @SuppressWarnings("unchecked")
            SpatialResource<Ref<EntityStore>, EntityStore> playerSpatial =
                    (SpatialResource<Ref<EntityStore>, EntityStore>)
                    commandBuffer.getResource(EntityModule.get().getPlayerSpatialResourceType());
            List<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
            playerSpatial.getSpatialStructure().collect(zonePos, 75.0D, playerRefs);

            ParticleUtil.spawnParticleEffect(
                    ZONE_PARTICLE_ID,
                    zonePos.getX(), zonePos.getY(), zonePos.getZ(),
                    0f, 0f, 0f,
                    scale,
                    null,
                    null,
                    playerRefs,
                    commandBuffer);
        } catch (Exception ignored) {
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private void cleanupExpired(CommandBuffer<EntityStore> commandBuffer, long now) {
        if (commandBuffer == null) {
            return;
        }
        for (var entry : ACTIVE_FROST.entrySet()) {
            ActiveFrozen state = entry.getValue();
            if (state == null || state.expiresAt <= 0L || now < state.expiresAt) {
                continue;
            }

            Ref<EntityStore> targetRef = state.targetRef;
            if (targetRef != null && targetRef.isValid()
                    && matchesExpectedUuid(state.targetUuid, resolveEntityUuid(targetRef, commandBuffer))) {
                clearSlowIfPossible(state, commandBuffer, targetRef);
            }
            ACTIVE_FROST.remove(entry.getKey());
        }
    }

    private static String keyFor(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (commandBuffer != null) {
            PlayerRef playerRef = AugmentUtils.getPlayerRef(commandBuffer, ref);
            if (playerRef != null && playerRef.isValid() && playerRef.getUuid() != null) {
                return playerRef.getUuid().toString();
            }

            UUIDComponent uuidComponent = EntityRefUtil.tryGetComponent(commandBuffer,
                    ref,
                    UUIDComponent.getComponentType());
            if (uuidComponent != null && uuidComponent.getUuid() != null) {
                return uuidComponent.getUuid().toString();
            }
        }
        Object store = ref.getStore();
        if (store != null) {
            return System.identityHashCode(store) + ":" + ref.getIndex();
        }
        return String.valueOf(ref.getIndex());
    }

    // ── Slow application ─────────────────────────────────────────────────────

    private static void applySlowIfPossible(ActiveFrozen state,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref) {
        if (state == null || commandBuffer == null || ref == null || state.slowPercent <= 0.0D) {
            return;
        }
        if (!EntityRefUtil.isAliveAndUsable(ref, commandBuffer)) {
            return;
        }
        if (!matchesExpectedUuid(state.targetUuid, resolveEntityUuid(ref, commandBuffer))) {
            return;
        }

        String key = keyFor(ref, commandBuffer);
        MovementManager movementManager = EntityRefUtil.tryGetComponent(commandBuffer,
                ref,
                MovementManager.getComponentType());
        if (movementManager == null) {
            boolean fallbackApplied = applySlowEffectFallback(state, commandBuffer, ref, key);
            if (!fallbackApplied && !state.loggedMissingMovementManager) {
                LOGGER.atWarning().log(
                        "Frozen Domain slow unavailable: MovementManager missing and fallback failed key=%s target=%s",
                        key,
                        ref);
                state.loggedMissingMovementManager = true;
            }
            return;
        }

        MovementSettings settings = movementManager.getSettings();
        MovementSettings defaultSettings = movementManager.getDefaultSettings();
        PlayerRef playerRef = AugmentUtils.getPlayerRef(commandBuffer, ref);
        if (settings == null && defaultSettings == null) {
            boolean fallbackApplied = applySlowEffectFallback(state, commandBuffer, ref, key);
            if (!fallbackApplied && !state.loggedMissingMovementSettings) {
                LOGGER.atWarning().log(
                        "Frozen Domain slow unavailable: movement settings missing and fallback failed key=%s target=%s",
                        key,
                        ref);
                state.loggedMissingMovementSettings = true;
            }
            return;
        }

        if (state.movementSnapshot == null && settings != null) {
            state.movementSnapshot = new MovementSnapshot(settings);
        }
        if (state.defaultMovementSnapshot == null && defaultSettings != null) {
            state.defaultMovementSnapshot = new MovementSnapshot(defaultSettings);
        }

        float multiplier = (float) Math.max(MIN_MOVEMENT_MULTIPLIER, 1.0D - state.slowPercent);
        if (settings != null && state.movementSnapshot != null) {
            state.movementSnapshot.apply(settings, multiplier);
        }
        if (defaultSettings != null && state.defaultMovementSnapshot != null) {
            state.defaultMovementSnapshot.apply(defaultSettings, multiplier);
        }

        if (playerRef != null && playerRef.isValid()) {
            movementManager.update(playerRef.getPacketHandler());
            applyPlayerHasteDebuff(state, playerRef, key);
        }

        // Always apply the visual slow effect in addition to the movement manager slow.
        applySlowEffectFallback(state, commandBuffer, ref, key);
    }

    private static void clearSlowIfPossible(ActiveFrozen state,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref) {
        if (state == null || commandBuffer == null || ref == null) {
            return;
        }
        if (!matchesExpectedUuid(state.targetUuid, resolveEntityUuid(ref, commandBuffer))) {
            return;
        }

        String key = keyFor(ref, commandBuffer);
        clearSlowEffectFallback(state, commandBuffer, ref, key);

        MovementManager movementManager = EntityRefUtil.tryGetComponent(commandBuffer,
                ref,
                MovementManager.getComponentType());
        if (movementManager == null) {
            return;
        }

        MovementSettings settings = movementManager.getSettings();
        MovementSettings defaultSettings = movementManager.getDefaultSettings();
        PlayerRef playerRef = AugmentUtils.getPlayerRef(commandBuffer, ref);
        if (settings != null && state.movementSnapshot != null) {
            state.movementSnapshot.restore(settings);
        }
        if (defaultSettings != null && state.defaultMovementSnapshot != null) {
            state.defaultMovementSnapshot.restore(defaultSettings);
        }

        if (playerRef != null && playerRef.isValid()) {
            movementManager.update(playerRef.getPacketHandler());
            clearPlayerHasteDebuff(playerRef, key);
        }
    }

    // ── Haste debuff on frozen targets ───────────────────────────────────────

    private static void applyPlayerHasteDebuff(ActiveFrozen state, PlayerRef playerRef, String key) {
        if (state == null || playerRef == null || !playerRef.isValid() || playerRef.getUuid() == null || key == null) {
            return;
        }
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null || plugin.getAugmentRuntimeManager() == null) {
            return;
        }

        double debuffPercent = Math.max(0.0D, state.slowPercent) * 100.0D;
        plugin.getAugmentRuntimeManager().getRuntimeState(playerRef.getUuid()).setAttributeBonus(
                SkillAttributeType.HASTE,
                PLAYER_HASTE_DEBUFF_SOURCE_PREFIX + key,
                -debuffPercent,
                state.expiresAt);
    }

    private static void clearPlayerHasteDebuff(PlayerRef playerRef, String key) {
        if (playerRef == null || !playerRef.isValid() || playerRef.getUuid() == null || key == null) {
            return;
        }
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null || plugin.getAugmentRuntimeManager() == null) {
            return;
        }

        plugin.getAugmentRuntimeManager().getRuntimeState(playerRef.getUuid()).setAttributeBonus(
                SkillAttributeType.HASTE,
                PLAYER_HASTE_DEBUFF_SOURCE_PREFIX + key,
                0.0D,
                0L);
    }

    // ── Slow effect fallback (entity effect system) ──────────────────────────

    private static boolean applySlowEffectFallback(ActiveFrozen state,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            String key) {
        EffectControllerComponent controller = EntityRefUtil.tryGetComponent(commandBuffer,
                ref,
                EffectControllerComponent.getComponentType());
        if (controller == null) {
            if (!state.loggedMissingEffectController) {
                LOGGER.atWarning().log("Frozen Domain fallback unavailable: EffectController missing key=%s target=%s",
                        key,
                        ref);
                state.loggedMissingEffectController = true;
            }
            return false;
        }

        EntityEffect slowEffect = resolveSlowEffect();
        if (slowEffect == null) {
            if (!state.loggedMissingSlowEffectAsset) {
                LOGGER.atWarning().log(
                        "Frozen Domain fallback unavailable: no slow effect asset found key=%s target=%s",
                        key,
                        ref);
                state.loggedMissingSlowEffectAsset = true;
            }
            return false;
        }

        float remainingSeconds = Math.max(0.1F, (float) ((state.expiresAt - System.currentTimeMillis()) / 1000.0D));
        boolean applied = controller.addEffect(ref, slowEffect, remainingSeconds, OverlapBehavior.OVERWRITE,
                commandBuffer);
        if (applied) {
            state.fallbackSlowEffectApplied = true;
            state.fallbackSlowEffectId = slowEffect.getId();
            return true;
        } else if (!state.loggedEffectApplyFailure) {
            LOGGER.atWarning().log("Frozen Domain fallback slow failed to apply key=%s target=%s effect=%s",
                    key,
                    ref,
                    slowEffect.getId());
            state.loggedEffectApplyFailure = true;
        }
        return false;
    }

    private static void clearSlowEffectFallback(ActiveFrozen state,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            String key) {
        if (!state.fallbackSlowEffectApplied || state.fallbackSlowEffectId == null) {
            return;
        }

        EffectControllerComponent controller = EntityRefUtil.tryGetComponent(commandBuffer,
                ref,
                EffectControllerComponent.getComponentType());
        if (controller == null) {
            return;
        }

        int idx = EntityEffect.getAssetMap().getIndex(state.fallbackSlowEffectId);
        if (idx != Integer.MIN_VALUE) {
            controller.removeEffect(ref, idx, commandBuffer);
        }

        state.fallbackSlowEffectApplied = false;
        state.fallbackSlowEffectId = null;
    }

    private static EntityEffect resolveSlowEffect() {
        for (String candidate : SLOW_EFFECT_IDS) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(candidate);
            if (effect != null) {
                return effect;
            }
            effect = EntityEffect.getAssetMap().getAsset(candidate.toLowerCase());
            if (effect != null) {
                return effect;
            }
            effect = EntityEffect.getAssetMap().getAsset(candidate.toUpperCase());
            if (effect != null) {
                return effect;
            }
        }
        return null;
    }

    // ── Sound ────────────────────────────────────────────────────────────────

    private static void playTriggerPulseSound(Ref<EntityStore> sourceRef, Vector3d position) {
        for (String soundId : TRIGGER_PULSE_SFX_IDS) {
            int soundIndex = resolveSoundIndex(soundId);
            if (soundIndex == 0) {
                continue;
            }
            SoundUtil.playSoundEvent3d(null, soundIndex, position, sourceRef.getStore());
            return;
        }
    }

    private static int resolveSoundIndex(String id) {
        int index = SoundEvent.getAssetMap().getIndex(id);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    // ── Target filtering ─────────────────────────────────────────────────────

    /**
     * Skip ONLY pets/summons owned by source player or party member.
     * Enemy pets/summons (owned by non-party players) still get slowed.
     * Ownerless mounts/summons (no valid owner ref) are treated as friendly to avoid edge-case grief.
     */
    private boolean isFriendlyPetOrSummon(UUID sourceUuid,
            UUID sourcePartyLeader,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            PartyManager partyManager) {
        NPCMountComponent mountComponent = EntityRefUtil.tryGetComponent(commandBuffer,
                targetRef,
                NPCMountComponent.getComponentType());
        if (mountComponent == null) {
            return false;
        }

        PlayerRef ownerPlayer = mountComponent.getOwnerPlayerRef();
        if (ownerPlayer == null || !ownerPlayer.isValid() || ownerPlayer.getUuid() == null) {
            return true;
        }

        UUID ownerUuid = ownerPlayer.getUuid();
        if (sourceUuid != null && sourceUuid.equals(ownerUuid)) {
            return true;
        }

        return isSamePartyUuid(sourceUuid, sourcePartyLeader, ownerUuid, partyManager);
    }

    private boolean isSamePartyTarget(UUID sourceUuid,
            UUID sourcePartyLeader,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            PartyManager partyManager) {
        if (sourceUuid == null || partyManager == null || !partyManager.isAvailable()) {
            return false;
        }

        PlayerRef targetPlayer = EntityRefUtil.tryGetComponent(commandBuffer,
                targetRef,
                PlayerRef.getComponentType());
        if (targetPlayer == null || !targetPlayer.isValid()) {
            return false;
        }

        UUID targetUuid = targetPlayer.getUuid();
        if (targetUuid == null) {
            return false;
        }

        return isSamePartyUuid(sourceUuid, sourcePartyLeader, targetUuid, partyManager);
    }

    private boolean isSamePartyUuid(UUID sourceUuid,
            UUID sourcePartyLeader,
            UUID targetUuid,
            PartyManager partyManager) {
        if (sourceUuid == null || targetUuid == null || partyManager == null || !partyManager.isAvailable()) {
            return false;
        }

        UUID effectiveSourceLeader = sourcePartyLeader != null
                ? sourcePartyLeader
                : resolvePartyLeader(partyManager, sourceUuid);
        if (effectiveSourceLeader == null) {
            return false;
        }

        UUID targetLeader = resolvePartyLeader(partyManager, targetUuid);
        return targetLeader != null && targetLeader.equals(effectiveSourceLeader);
    }

    private UUID resolvePartyLeader(PartyManager partyManager, UUID playerUuid) {
        if (partyManager == null || !partyManager.isAvailable() || playerUuid == null) {
            return null;
        }
        if (!partyManager.isInParty(playerUuid)) {
            return null;
        }
        return partyManager.getPartyLeader(playerUuid);
    }

    private PartyManager resolvePartyManager() {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        return plugin != null ? plugin.getPartyManager() : null;
    }

    // ── Radius resolution ────────────────────────────────────────────────────

    private double resolveRadius(double attackerMaxHealth) {
        if (healthPerRadiusBlock <= 0.0D) {
            return baseRadius;
        }
        return baseRadius + Math.floor(attackerMaxHealth / healthPerRadiusBlock);
    }

    private static double normalizeSlowPercent(double rawValue, double configuredCap) {
        double magnitude = Math.abs(rawValue);
        if (magnitude > 1.0D) {
            magnitude /= 100.0D;
        }
        magnitude = Math.max(0.0D, magnitude);
        return configuredCap > 0.0D ? Math.min(configuredCap, magnitude) : magnitude;
    }

    private static double clampRatio(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static UUID resolveEntityUuid(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (!EntityRefUtil.isUsable(ref) || commandBuffer == null) {
            return null;
        }

        UUIDComponent uuidComponent = EntityRefUtil.tryGetComponent(commandBuffer,
                ref,
                UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    private static boolean matchesExpectedUuid(UUID expected, UUID actual) {
        if (expected == null || actual == null) {
            return true;
        }
        return expected.equals(actual);
    }

    public static int clearAllRuntimeState() {
        int cleared = ACTIVE_FROST.size() + ZONE_STATES.size();
        ACTIVE_FROST.clear();
        ZONE_STATES.clear();
        return cleared;
    }
}
