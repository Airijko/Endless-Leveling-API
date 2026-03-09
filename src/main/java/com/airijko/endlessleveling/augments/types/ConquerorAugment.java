package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentRuntimeState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;

public final class ConquerorAugment extends YamlAugment implements AugmentHooks.OnHitAugment {
    public static final String ID = "conqueror";

    private final double bonusDamagePerStack;
    private final int maxStacks;
    private final double maxStackTrueDamage;

    public ConquerorAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");
        Map<String, Object> bonusDamage = AugmentValueReader.getMap(buffs, "bonus_damage");
        Map<String, Object> maxStackBonus = AugmentValueReader.getMap(passives, "max_stack_bonus");
        Map<String, Object> trueDamage = AugmentValueReader.getMap(maxStackBonus, "bonus_true_damage");

        this.bonusDamagePerStack = Math.max(0.0D, AugmentValueReader.getDouble(bonusDamage, "value", 0.0D));
        this.maxStacks = Math.max(1, AugmentValueReader.getInt(buffs, "max_stacks", 1));
        this.maxStackTrueDamage = Math.max(0.0D, AugmentValueReader.getDouble(trueDamage, "value", 0.0D));
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        AugmentRuntimeState runtime = context != null ? context.getRuntimeState() : null;
        if (context == null || runtime == null) {
            return context != null ? context.getDamage() : 0f;
        }

        PlayerRef playerRef = AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getAttackerRef());
        int stacks = AugmentUtils.setStacksWithNotify(runtime,
                ID,
                runtime.getState(ID).getStacks() + 1,
                maxStacks,
                playerRef,
                getName());

        float preMitigatedDamage = context.getDamage();
        float updatedDamage = AugmentUtils.applyMultiplier(preMitigatedDamage, stacks * bonusDamagePerStack);
        if (stacks >= maxStacks && maxStackTrueDamage > 0.0D) {
            updatedDamage += (float) (preMitigatedDamage * maxStackTrueDamage);
        }
        return updatedDamage;
    }
}
