package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import java.util.Map;

public final class BurnAugment extends YamlAugment implements AugmentHooks.OnHitAugment {
    public static final String ID = "burn";

    private final double basePercentPerSecond;
    private final double bonusScalingPer100Health;
    private final double baseRadius;
    private final double healthPerRadiusBlock;

    public BurnAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> auraBurn = AugmentValueReader.getMap(passives, "aura_burn");
        Map<String, Object> radiusScaling = AugmentValueReader.getMap(auraBurn, "radius_health_scaling");

        this.basePercentPerSecond = Math.max(0.0D,
                AugmentValueReader.getDouble(auraBurn, "base_max_hp_percent_per_second", 0.0D));
        this.bonusScalingPer100Health = Math.max(0.0D,
                AugmentValueReader.getDouble(auraBurn, "bonus_max_hp_scaling", 0.0D));
        this.baseRadius = Math.max(0.0D, AugmentValueReader.getDouble(auraBurn, "radius", 0.0D));
        this.healthPerRadiusBlock = Math.max(0.0D,
                AugmentValueReader.getDouble(radiusScaling, "health_per_block", 0.0D));
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null || context.getAttackerStats() == null || context.getTargetStats() == null) {
            return context != null ? context.getDamage() : 0f;
        }

        EntityStatValue attackerHp = context.getAttackerStats().get(DefaultEntityStatTypes.getHealth());
        EntityStatValue targetHp = context.getTargetStats().get(DefaultEntityStatTypes.getHealth());
        if (attackerHp == null || targetHp == null || targetHp.getMax() <= 0f) {
            return context.getDamage();
        }

        double maxHealth = Math.max(0.0D, attackerHp.getMax());
        double radius = resolveRadius(maxHealth);
        if (radius > 0.0D && resolveDistance(context) > radius) {
            return context.getDamage();
        }

        double percent = basePercentPerSecond + ((maxHealth / 100.0D) * bonusScalingPer100Health);
        if (percent <= 0.0D) {
            return context.getDamage();
        }

        float extraDamage = (float) (targetHp.getMax() * percent);
        return context.getDamage() + Math.max(0f, extraDamage);
    }

    private double resolveRadius(double attackerMaxHealth) {
        if (healthPerRadiusBlock <= 0.0D) {
            return baseRadius;
        }
        return baseRadius + Math.floor(attackerMaxHealth / healthPerRadiusBlock);
    }

    private double resolveDistance(AugmentHooks.HitContext context) {
        if (context.getCommandBuffer() == null || context.getAttackerRef() == null || context.getTargetRef() == null) {
            return 0.0D;
        }
        TransformComponent attackerTransform = context.getCommandBuffer().getComponent(
                context.getAttackerRef(),
                TransformComponent.getComponentType());
        TransformComponent targetTransform = context.getCommandBuffer().getComponent(
                context.getTargetRef(),
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
}
