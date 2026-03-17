package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.ExecutionerSettings;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.BiConsumer;

/**
 * Applies Executioner passive bonus damage against low-health targets.
 */
public final class ExecutionerPassive {

    private final ExecutionerSettings settings;

    private ExecutionerPassive(ExecutionerSettings settings) {
        this.settings = settings == null ? new ExecutionerSettings(java.util.List.of(), 0.0D) : settings;
    }

    public static ExecutionerPassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new ExecutionerPassive(ExecutionerSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public float apply(PassiveRuntimeState runtimeState,
            PlayerRef playerRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            float currentDamage,
            BiConsumer<PlayerRef, String> messenger) {
        if (runtimeState == null || !settings.enabled() || targetRef == null || commandBuffer == null
                || currentDamage <= 0f) {
            return 0f;
        }

        EntityStatMap statMap = commandBuffer.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return 0f;
        }
        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return 0f;
        }

        float current = healthStat.get();
        float max = healthStat.getMax();
        if (max <= 0f || current <= 0f) {
            return 0f;
        }

        float predicted = Math.max(0f, current - currentDamage);
        double flatBonusDamage = 0.0D;
        for (ExecutionerSettings.Entry entry : settings.entries()) {
            double threshold = entry.thresholdPercent();
            if (threshold <= 0.0D) {
                continue;
            }
            float thresholdHealth = (float) (max * threshold);
            if (current <= thresholdHealth || predicted <= thresholdHealth) {
                flatBonusDamage += Math.max(0.0D, entry.flatBonusDamage());
            }
        }

        if (flatBonusDamage <= 0.0D) {
            return 0f;
        }

        long cooldownMillis = settings.cooldownMillis();
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0L && now < runtimeState.getExecutionerCooldownExpiresAt()) {
            return 0f;
        }

        float bonusDamage = (float) flatBonusDamage;
        if (bonusDamage <= 0f) {
            return 0f;
        }

        if (cooldownMillis > 0L) {
            runtimeState.setExecutionerCooldownExpiresAt(now + cooldownMillis);
            runtimeState.setExecutionerReadyNotified(false);
        }

        if (messenger != null) {
            messenger.accept(playerRef,
                    String.format("Executioner triggered! +%.0f flat damage.", flatBonusDamage));
        }

        return bonusDamage;
    }
}
