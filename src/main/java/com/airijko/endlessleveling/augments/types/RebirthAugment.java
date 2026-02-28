package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import java.util.Map;

public final class RebirthAugment extends YamlAugment implements AugmentHooks.OnLowHpAugment {
    public static final String ID = "rebirth";

    private final double healPercent;
    private final double minHealthPercent;
    private final long cooldownMillis;

    public RebirthAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> heal = AugmentValueReader.getMap(passives, "heal_on_trigger");
        this.healPercent = AugmentValueReader.getDouble(heal, "value", 0.0D);
        this.minHealthPercent = AugmentValueReader.getDouble(heal, "min_health_percent", 0.0D);
        this.cooldownMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(heal, "cooldown", 0.0D));
    }

    @Override
    public float onLowHp(AugmentHooks.DamageTakenContext context) {
        if (context == null) {
            return 0f;
        }
        var runtime = context.getRuntimeState();
        if (runtime == null) {
            return context.getIncomingDamage();
        }
        var hp = context.getStatMap() == null ? null
                : context.getStatMap().get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.getMax() <= 0f) {
            return context.getIncomingDamage();
        }

        double thresholdHp = hp.getMax() * minHealthPercent;
        double projectedHp = hp.get() - context.getIncomingDamage();
        if (projectedHp > thresholdHp) {
            return context.getIncomingDamage();
        }

        if (!AugmentUtils.consumeCooldown(runtime, ID, getName(), cooldownMillis)) {
            return context.getIncomingDamage();
        }

        double healAmount = hp.getMax() * healPercent;
        context.getStatMap().setStatValue(
                DefaultEntityStatTypes.getHealth(),
                (float) Math.min(hp.getMax(), hp.get() + healAmount));
        var playerRef = AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getDefenderRef());
        AugmentUtils.sendAugmentMessage(playerRef,
                String.format("%s activated! Restored %.0f%% of max health.", getName(), healPercent * 100.0D));
        return 0f;
    }
}
