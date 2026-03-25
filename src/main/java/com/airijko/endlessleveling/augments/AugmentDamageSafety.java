package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared guard for augment-driven ECS damage applications.
 */
public final class AugmentDamageSafety {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private AugmentDamageSafety() {
    }

    public static boolean tryExecuteDamage(Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            Damage damage,
            String sourceTag) {
        if (targetRef == null || commandBuffer == null || damage == null) {
            return false;
        }
        if (!EntityRefUtil.isUsable(targetRef)) {
            return false;
        }

        try {
            DamageSystems.executeDamage(targetRef, commandBuffer, damage);
            return true;
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (isTransientTargetFailure(message)) {
                LOGGER.atFine().log("Skipped augment damage for invisible target source=%s target=%s", sourceTag, targetRef);
                return false;
            }
            throw exception;
        } catch (IllegalStateException exception) {
            LOGGER.atFine().log("Skipped augment damage for stale target source=%s target=%s reason=%s",
                    sourceTag,
                    targetRef,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean isTransientTargetFailure(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("not visible")
                || normalized.contains("removed")
                || normalized.contains("invalid")
                || normalized.contains("dead");
    }
}