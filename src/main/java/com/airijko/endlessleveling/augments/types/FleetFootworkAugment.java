package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Map;

public final class FleetFootworkAugment extends YamlAugment implements AugmentHooks.OnHitAugment {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    public static final String ID = "fleet_footwork";

    private final long cooldownMillis;
    private final double healPercentOfDamage;
    private final double movementSpeedBonus;
    private final long movementDurationMillis;

    public FleetFootworkAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> empoweredHit = AugmentValueReader.getMap(passives, "empowered_hit");
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");
        Map<String, Object> movementSpeed = AugmentValueReader.getMap(buffs, "movement_speed");

        this.cooldownMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(empoweredHit, "cooldown", 0.0D));
        this.healPercentOfDamage = Math.max(0.0D,
                AugmentValueReader.getDouble(empoweredHit, "heal_percent_of_damage", 0.0D));
        this.movementSpeedBonus = AugmentValueReader.getDouble(movementSpeed, "value", 0.0D);
        this.movementDurationMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(movementSpeed, "duration", 0.0D));
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }
        if (!AugmentUtils.consumeCooldown(context.getRuntimeState(), ID, getName(), cooldownMillis)) {
            return context.getDamage();
        }

        if (healPercentOfDamage > 0.0D) {
            AugmentUtils.heal(context.getAttackerStats(), context.getDamage() * healPercentOfDamage);
        }

        if (movementSpeedBonus != 0.0D && context.getRuntimeState() != null) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    ID + "_haste",
                    SkillAttributeType.HASTE,
                    movementSpeedBonus * 100.0D,
                    movementDurationMillis);
        }

        String playerId = context.getPlayerData() != null && context.getPlayerData().getUuid() != null
                ? context.getPlayerData().getUuid().toString()
                : "unknown";
        LOGGER.atInfo().log(
                "Fleet Footwork activated for player=%s damage=%.2f healPct=%.3f hastePct=%.3f durationMs=%d cooldownMs=%d",
                playerId,
                context.getDamage(),
                healPercentOfDamage,
                movementSpeedBonus,
                movementDurationMillis,
                cooldownMillis);

        return context.getDamage();
    }
}
