package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.ExecutionerSettings;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.BiConsumer;

/**
 * Applies Final Incantation passive bonus damage against low-health targets.
 */
public final class FinalIncantationPassive {

    private static final double TRIGGER_VFX_Y_OFFSET = 1.0D;
    private static final double TRIGGER_VFX_OVERHEAD_OFFSET = 0.65D;
    private static final String[] TRIGGER_VFX_CORE_IDS = new String[] {
        "Impact_Critical",
        "Impact_Blade_01"
    };
    private static final String[] TRIGGER_VFX_ACCENT_IDS = new String[] {
        "Impact_Sword_Basic",
        "Explosion_Small"
    };
    private static final int TRIGGER_VFX_CORE_BURST_COUNT = 2;
    private static final int TRIGGER_VFX_ACCENT_BURST_COUNT = 1;
    private static final String[] TRIGGER_SFX_PRIMARY_IDS = new String[] {
        "SFX_Sword_T2_Signature_Part_2",
        "SFX_Sword_T2_Impact",
    };
    private static final String[] TRIGGER_SFX_LAYER_IDS = new String[] {
        "SFX_Daggers_T2_Slash_Impact"
    };
    private static final int TRIGGER_SFX_BASE_PLAY_COUNT = 2;
    private static final int TRIGGER_SFX_VOLUME_MULTIPLIER = 3;
    private static final int TRIGGER_SFX_PLAY_COUNT = TRIGGER_SFX_BASE_PLAY_COUNT * TRIGGER_SFX_VOLUME_MULTIPLIER;

    private final ExecutionerSettings settings;

    private FinalIncantationPassive(ExecutionerSettings settings) {
        this.settings = settings == null ? new ExecutionerSettings(java.util.List.of(), 0.0D, 0.0D) : settings;
    }

    public static FinalIncantationPassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new FinalIncantationPassive(ExecutionerSettings.fromSnapshot(snapshot));
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
        double percentBonusDamage = 0.0D;
        for (ExecutionerSettings.Entry entry : settings.entries()) {
            double threshold = entry.thresholdPercent();
            if (threshold <= 0.0D) {
                continue;
            }
            float thresholdHealth = (float) (max * threshold);
            if (current <= thresholdHealth || predicted <= thresholdHealth) {
                flatBonusDamage += Math.max(0.0D, entry.flatBonusDamage());
                percentBonusDamage += Math.max(0.0D, entry.bonusDamagePercent());
            }
        }

        if (flatBonusDamage <= 0.0D && percentBonusDamage <= 0.0D) {
            return 0f;
        }

        long cooldownMillis = settings.cooldownMillis();
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0L && now < runtimeState.getExecutionerCooldownExpiresAt()) {
            return 0f;
        }

        double scaledBonus = Math.max(0.0D, currentDamage) * Math.max(0.0D, percentBonusDamage);
        float bonusDamage = (float) (Math.max(0.0D, flatBonusDamage) + Math.max(0.0D, scaledBonus));
        if (bonusDamage <= 0f) {
            return 0f;
        }

        if (cooldownMillis > 0L) {
            runtimeState.setExecutionerCooldownExpiresAt(now + cooldownMillis);
            runtimeState.setExecutionerReadyNotified(false);
        }

        playTriggerSound(commandBuffer, targetRef);
        playTriggerVfx(commandBuffer, targetRef);

        if (messenger != null) {
            float totalDamage = Math.max(0f, currentDamage + bonusDamage);
            messenger.accept(playerRef,
                    String.format("Final Incantation triggered! %.0f total damage dealt.", totalDamage));
        }

        return bonusDamage;
    }

    public void reduceCooldownOnKill(PassiveRuntimeState runtimeState) {
        if (runtimeState == null || !settings.enabled()) {
            return;
        }

        long reduction = settings.cooldownReductionOnKillMillis();
        if (reduction <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        long previousExpiresAt = runtimeState.getExecutionerCooldownExpiresAt();
        if (previousExpiresAt <= now) {
            return;
        }

        long updatedExpiresAt = Math.max(now, previousExpiresAt - reduction);
        if (updatedExpiresAt >= previousExpiresAt) {
            return;
        }

        runtimeState.setExecutionerCooldownExpiresAt(updatedExpiresAt);
        runtimeState.setExecutionerReadyNotified(false);
    }

    private void playTriggerVfx(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        if (commandBuffer == null || !EntityRefUtil.isUsable(targetRef)) {
            return;
        }

        Vector3d targetPosition = resolveTargetEffectPosition(commandBuffer, targetRef);
        if (targetPosition == null) {
            return;
        }

        Vector3d overheadPosition = new Vector3d(
                targetPosition.getX(),
                targetPosition.getY() + TRIGGER_VFX_OVERHEAD_OFFSET,
                targetPosition.getZ());

        spawnVfxBursts(targetRef, targetPosition, TRIGGER_VFX_CORE_IDS, TRIGGER_VFX_CORE_BURST_COUNT);
        spawnVfxBursts(targetRef, overheadPosition, TRIGGER_VFX_ACCENT_IDS, TRIGGER_VFX_ACCENT_BURST_COUNT);
    }

    private void spawnVfxBursts(Ref<EntityStore> targetRef,
            Vector3d position,
            String[] vfxIds,
            int burstCount) {
        if (!EntityRefUtil.isUsable(targetRef) || position == null || vfxIds == null || vfxIds.length == 0) {
            return;
        }

        int safeBurstCount = Math.max(1, burstCount);
        for (int burst = 0; burst < safeBurstCount; burst++) {
            for (String vfxId : vfxIds) {
                if (vfxId == null || vfxId.isBlank()) {
                    continue;
                }
                try {
                    ParticleUtil.spawnParticleEffect(vfxId, position, targetRef.getStore());
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void playTriggerSound(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        if (commandBuffer == null || !EntityRefUtil.isUsable(targetRef)) {
            return;
        }

        Vector3d targetPosition = resolveTargetEffectPosition(commandBuffer, targetRef);
        if (targetPosition == null) {
            return;
        }

        int primaryIndex = resolveFirstAvailableSoundIndex(TRIGGER_SFX_PRIMARY_IDS, 0);
        if (primaryIndex == 0) {
            return;
        }

        int layerIndex = resolveFirstAvailableSoundIndex(TRIGGER_SFX_LAYER_IDS, primaryIndex);
        for (int i = 0; i < TRIGGER_SFX_PLAY_COUNT; i++) {
            SoundUtil.playSoundEvent3d(null, primaryIndex, targetPosition, targetRef.getStore());
            if (layerIndex != 0) {
                SoundUtil.playSoundEvent3d(null, layerIndex, targetPosition, targetRef.getStore());
            }
        }
    }

    private static int resolveFirstAvailableSoundIndex(String[] ids, int excludedIndex) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (String id : ids) {
            int index = resolveSoundIndex(id);
            if (index == 0 || index == excludedIndex) {
                continue;
            }
            return index;
        }
        return 0;
    }

    private static int resolveSoundIndex(String id) {
        int index = SoundEvent.getAssetMap().getIndex(id);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    private Vector3d resolveTargetEffectPosition(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        TransformComponent targetTransform = EntityRefUtil.tryGetComponent(
                commandBuffer,
                targetRef,
                TransformComponent.getComponentType());
        if (targetTransform == null || targetTransform.getPosition() == null) {
            return null;
        }

        Vector3d baseTargetPosition = targetTransform.getPosition();
        return new Vector3d(
                baseTargetPosition.getX(),
                baseTargetPosition.getY() + TRIGGER_VFX_Y_OFFSET,
                baseTargetPosition.getZ());
    }
}
