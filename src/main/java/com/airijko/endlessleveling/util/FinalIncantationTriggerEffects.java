package com.airijko.endlessleveling.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared Final Incantation VFX/SFX payload used by damage burst triggers.
 */
public final class FinalIncantationTriggerEffects {

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
            "SFX_Sword_T2_Impact"
    };
    private static final String[] TRIGGER_SFX_LAYER_IDS = new String[] {
            "SFX_Daggers_T2_Slash_Impact"
    };
    private static final int TRIGGER_SFX_BASE_PLAY_COUNT = 2;
    private static final int TRIGGER_SFX_VOLUME_MULTIPLIER = 3;
    private static final int TRIGGER_SFX_PLAY_COUNT = TRIGGER_SFX_BASE_PLAY_COUNT * TRIGGER_SFX_VOLUME_MULTIPLIER;

    private FinalIncantationTriggerEffects() {
    }

    public static void play(Ref<EntityStore> targetRef, CommandBuffer<EntityStore> commandBuffer) {
        if (!EntityRefUtil.isUsable(targetRef) || commandBuffer == null) {
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
        playTriggerSound(targetRef, targetPosition);
    }

    private static void spawnVfxBursts(Ref<EntityStore> targetRef,
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

    private static void playTriggerSound(Ref<EntityStore> targetRef, Vector3d targetPosition) {
        if (!EntityRefUtil.isUsable(targetRef) || targetPosition == null) {
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

    private static Vector3d resolveTargetEffectPosition(CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> targetRef) {
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
