package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDamageSafety;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.systems.PlayerCombatSystem;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MissileShotAugment extends Augment implements AugmentHooks.OnHitAugment {
    public static final String ID = "missile_shot";
    private static final String IMPACT_PARTICLE_ID = "Explosion_Small";

    private final double strengthScaling;
    private final double precisionScaling;
    private final double ferocityScaling;
    private final double radius;
    private final double minDistance;
    private final long cooldownMillis;

    public MissileShotAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> missileShot = AugmentValueReader.getMap(passives, "missile_shot");

        this.strengthScaling = AugmentUtils
                .normalizeConfiguredBonusMultiplier(AugmentValueReader.getDouble(missileShot, "strength_scaling", 0.0D));
        this.precisionScaling = AugmentUtils
                .normalizeConfiguredBonusMultiplier(AugmentValueReader.getDouble(missileShot, "precision_scaling", 0.0D));
        this.ferocityScaling = AugmentUtils
                .normalizeConfiguredBonusMultiplier(AugmentValueReader.getDouble(missileShot, "ferocity_scaling", 0.0D));
        this.radius = Math.max(0.0D, AugmentValueReader.getDouble(missileShot, "radius", 3.0D));
        this.minDistance = Math.max(0.0D, AugmentValueReader.getDouble(missileShot, "min_distance", 10.0D));
        this.cooldownMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(missileShot, "cooldown", 5.0D));
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }
        if (!context.isRangedAttack() || context.getDamage() <= 0.0f) {
            return context.getDamage();
        }

        Vector3d impactPosition = resolveTargetEffectPosition(context.getCommandBuffer(), context.getTargetRef());
        double distance = resolveDistance(context.getCommandBuffer(), context.getAttackerRef(), context.getTargetRef());
        if (impactPosition == null || distance < minDistance) {
            return context.getDamage();
        }

        if (!AugmentUtils.consumeCooldown(context.getRuntimeState(), ID, getName(), cooldownMillis)) {
            return context.getDamage();
        }

        double strength = AugmentUtils.resolveStrength(context);
        double precision = AugmentUtils.resolvePrecision(context);
        double ferocity = AugmentUtils.resolveFerocity(context);
        double aoeDamage = (strength * strengthScaling) + (precision * precisionScaling) + (ferocity * ferocityScaling);
        if (aoeDamage <= 0.0D) {
            return context.getDamage();
        }

        trySpawnImpactParticle(context.getAttackerRef(), impactPosition);
        applyExplosionDamage(context.getAttackerRef(), context.getCommandBuffer(), impactPosition, (float) aoeDamage);
        return context.getDamage();
    }

    private double resolveDistance(CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef) {
        if (commandBuffer == null || !EntityRefUtil.isUsable(attackerRef) || !EntityRefUtil.isUsable(targetRef)) {
            return 0.0D;
        }
        TransformComponent attackerTransform = EntityRefUtil.tryGetComponent(
                commandBuffer,
                attackerRef,
                TransformComponent.getComponentType());
        TransformComponent targetTransform = EntityRefUtil.tryGetComponent(
                commandBuffer,
                targetRef,
                TransformComponent.getComponentType());
        if (attackerTransform == null || targetTransform == null
                || attackerTransform.getPosition() == null || targetTransform.getPosition() == null) {
            return 0.0D;
        }

        double dx = attackerTransform.getPosition().getX() - targetTransform.getPosition().getX();
        double dy = attackerTransform.getPosition().getY() - targetTransform.getPosition().getY();
        double dz = attackerTransform.getPosition().getZ() - targetTransform.getPosition().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Vector3d resolveTargetEffectPosition(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        if (commandBuffer == null || !EntityRefUtil.isUsable(targetRef)) {
            return null;
        }

        TransformComponent targetTransform = EntityRefUtil.tryGetComponent(
                commandBuffer,
                targetRef,
                TransformComponent.getComponentType());
        if (targetTransform == null || targetTransform.getPosition() == null) {
            return null;
        }

        Vector3d baseTargetPosition = targetTransform.getPosition();
        return new Vector3d(baseTargetPosition.getX(), baseTargetPosition.getY(), baseTargetPosition.getZ());
    }

    private void applyExplosionDamage(Ref<EntityStore> attackerRef,
            CommandBuffer<EntityStore> commandBuffer,
            Vector3d explosionPosition,
            float explosionDamage) {
        if (commandBuffer == null || explosionPosition == null || explosionDamage <= 0.0f || radius <= 0.0D) {
            return;
        }

        Set<Integer> visitedEntityIds = new HashSet<>();
        for (Ref<EntityStore> targetRef : TargetUtil.getAllEntitiesInSphere(explosionPosition, radius, commandBuffer)) {
            if (targetRef == null || !targetRef.isValid()) {
                continue;
            }
            if (!visitedEntityIds.add(targetRef.getIndex())) {
                continue;
            }
            if (attackerRef != null && targetRef.equals(attackerRef)) {
                continue;
            }
            if (!EntityRefUtil.isUsable(targetRef)) {
                continue;
            }

            Damage proc = PlayerCombatSystem.createAugmentProcDamage(attackerRef, explosionDamage);
            AugmentDamageSafety.tryExecuteDamage(targetRef, commandBuffer, proc, ID);
        }
    }

    private void trySpawnImpactParticle(Ref<EntityStore> attackerRef, Vector3d position) {
        if (attackerRef == null || !attackerRef.isValid() || attackerRef.getStore() == null || position == null) {
            return;
        }
        try {
            ParticleUtil.spawnParticleEffect(IMPACT_PARTICLE_ID, position, attackerRef.getStore());
        } catch (RuntimeException ignored) {
        }
    }
}
