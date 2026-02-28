package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Map;

public final class VampiricStrikeAugment extends YamlAugment
        implements AugmentHooks.OnHitAugment, AugmentHooks.OnCritAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "vampiric_strike";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final double healPercent;
    private final long cooldownMillis;
    private final double precisionBuff;

    public VampiricStrikeAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> heal = AugmentValueReader.getMap(passives, "heal_on_crit");
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");
        this.healPercent = AugmentValueReader.getDouble(heal, "value", 0.0D);
        this.cooldownMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(heal, "trigger_cooldown", 0.0D));
        this.precisionBuff = AugmentValueReader.getNestedDouble(buffs, 0.0D, "precision", "value");
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }
        if (context.isCritical()) {
            LOGGER.atFine().log("Vampiric Strike: crit hit detected, checking proc conditions.");
        } else {
            LOGGER.atFine().log("Vampiric Strike: not activated (no crit).");
        }
        return context.getDamage();
    }

    @Override
    public void onCrit(AugmentHooks.HitContext context) {
        if (context == null) {
            return;
        }
        if (!AugmentUtils.consumeCooldown(context.getRuntimeState(), ID, getName(), cooldownMillis)) {
            LOGGER.atFine().log("Vampiric Strike: not activated (cooldown active).");
            return;
        }
        var runtime = context.getRuntimeState();
        if (runtime != null) {
            var cooldown = runtime.getCooldown(ID);
            if (cooldown != null) {
                cooldown.setReadyNotified(true);
            }
        }
        double healAmount = context.getDamage() * healPercent;
        float applied = AugmentUtils.heal(context.getAttackerStats(), healAmount);
        LOGGER.atFine().log("Vampiric Strike: activated on crit, healed=%.2f", applied);
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }
        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_precision",
                SkillAttributeType.PRECISION,
                precisionBuff * 100.0D,
                0L);
    }
}
