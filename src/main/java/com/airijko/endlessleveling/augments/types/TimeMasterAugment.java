package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentRuntimeState;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.CooldownState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;

import java.util.Map;

public final class TimeMasterAugment extends YamlAugment implements AugmentHooks.OnKillAugment {
    public static final String ID = "time_master";

    private final long flatReductionMillis;
    private final double percentRemainingReduction;

    public TimeMasterAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> cooldownReduction = AugmentValueReader.getMap(passives, "cooldown_reduction");

        this.flatReductionMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(cooldownReduction, "flat_seconds", 0.0D));
        this.percentRemainingReduction = Math.max(0.0D,
                AugmentValueReader.getDouble(cooldownReduction, "percent_remaining", 0.0D));
    }

    @Override
    public void onKill(AugmentHooks.KillContext context) {
        AugmentRuntimeState runtime = context != null ? context.getRuntimeState() : null;
        if (runtime == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (CooldownState cooldown : runtime.getCooldowns()) {
            if (cooldown == null || cooldown.getExpiresAt() <= now) {
                continue;
            }
            long remaining = cooldown.getExpiresAt() - now;
            long reduction = flatReductionMillis + (long) Math.floor(remaining * percentRemainingReduction);
            if (reduction <= 0L) {
                continue;
            }
            long updatedExpiresAt = Math.max(now, cooldown.getExpiresAt() - reduction);
            cooldown.setExpiresAt(updatedExpiresAt);
            cooldown.setReadyNotified(false);
        }
    }
}
