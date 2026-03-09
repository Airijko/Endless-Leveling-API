package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.managers.SkillManager;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class PhantomBoltsAugment extends YamlAugment implements AugmentHooks.OnHitAugment {
    public static final String ID = "phantom_bolts";

    private final double flatDamage;
    private final double strengthScaling;
    private final double sorceryScaling;
    private final boolean canCrit;

    public PhantomBoltsAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> phantomDamage = AugmentValueReader.getMap(passives, "phantom_damage");

        this.flatDamage = Math.max(0.0D, AugmentValueReader.getDouble(phantomDamage, "flat_damage", 0.0D));
        this.strengthScaling = Math.max(0.0D, AugmentValueReader.getDouble(phantomDamage, "strength_scaling", 0.0D));
        this.sorceryScaling = Math.max(0.0D, AugmentValueReader.getDouble(phantomDamage, "sorcery_scaling", 0.0D));
        this.canCrit = AugmentValueReader.getBoolean(phantomDamage, "can_crit", false);
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }

        SkillManager skillManager = context.getSkillManager();
        if (skillManager == null || context.getPlayerData() == null) {
            return context.getDamage();
        }

        double strength = skillManager.calculatePlayerStrength(context.getPlayerData());
        double sorcery = skillManager.calculatePlayerSorcery(context.getPlayerData());
        double phantomDamage = flatDamage + (strength * strengthScaling) + (sorcery * sorceryScaling);

        if (phantomDamage <= 0.0D) {
            return context.getDamage();
        }

        if (canCrit) {
            double critChance = Math.max(0.0D,
                    Math.min(1.0D, skillManager.calculatePlayerPrecision(context.getPlayerData())));
            if (ThreadLocalRandom.current().nextDouble() <= critChance) {
                double ferocity = skillManager.calculatePlayerFerocity(context.getPlayerData());
                phantomDamage *= 1.0D + (ferocity / 100.0D);
            }
        }

        return context.getDamage() + (float) phantomDamage;
    }
}
