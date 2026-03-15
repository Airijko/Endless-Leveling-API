package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.BerzerkerSettings;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Applies Berzerker passive scaling based on missing health.
 */
public final class BerzerkerPassive {

    private final BerzerkerSettings settings;

    private BerzerkerPassive(BerzerkerSettings settings) {
        this.settings = settings == null ? new BerzerkerSettings(java.util.List.of()) : settings;
    }

    public static BerzerkerPassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new BerzerkerPassive(BerzerkerSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public float computeBonus(Ref<EntityStore> attackerRef,
            CommandBuffer<EntityStore> commandBuffer,
            float baseDamage) {
        if (!settings.enabled() || attackerRef == null || commandBuffer == null || baseDamage <= 0f) {
            return 0f;
        }

        EntityStatMap statMap = EntityRefUtil.tryGetComponent(commandBuffer, attackerRef,
                EntityStatMap.getComponentType());
        if (statMap == null) {
            return 0f;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return 0f;
        }

        float max = healthStat.getMax();
        float current = healthStat.get();
        if (max <= 0f || current <= 0f) {
            return 0f;
        }

        float ratio = current / max;
        double totalBonus = 0.0D;
        for (BerzerkerSettings.Entry entry : settings.entries()) {
            double maxBonus = Math.max(0.0D, entry.bonusPercent());
            if (maxBonus <= 0.0D) {
                continue;
            }

            double threshold = Math.min(Math.max(0.0D, entry.thresholdPercent()), 0.999D);
            double scale;
            if (ratio <= threshold) {
                scale = 1.0D;
            } else if (ratio >= 1.0D) {
                scale = 0.0D;
            } else {
                double denominator = 1.0D - threshold;
                scale = denominator <= 0.0D ? 0.0D : (1.0D - ratio) / denominator;
            }

            scale = Math.max(0.0D, Math.min(1.0D, scale));
            totalBonus += maxBonus * scale;
        }

        if (totalBonus <= 0.0D) {
            return 0f;
        }

        float bonusDamage = (float) (baseDamage * totalBonus);
        return bonusDamage > 0f ? bonusDamage : 0f;
    }
}
