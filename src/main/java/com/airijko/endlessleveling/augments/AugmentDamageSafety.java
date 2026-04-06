package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared guard for augment-driven ECS damage applications.
 */
public final class AugmentDamageSafety {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /**
     * Re-entrancy guard to prevent infinite recursion when augment-driven damage
     * triggers another damage event that re-invokes augment on-hit hooks.
     * ThreadLocal is appropriate since each world ticks on its own thread.
     */
    private static final ThreadLocal<Boolean> REENTRANCY_GUARD = ThreadLocal.withInitial(() -> Boolean.FALSE);

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
        // Prevent updating entities already marked for removal — triggers the
        // "Entity can't be removed and also receive an update" tracker error.
        if (EntityRefUtil.tryGetComponent(commandBuffer, targetRef, DeathComponent.getComponentType()) != null) {
            LOGGER.atFine().log("Skipped augment damage for dying entity source=%s target=%s", sourceTag, targetRef);
            return false;
        }
        if (REENTRANCY_GUARD.get()) {
            LOGGER.atFine().log("Blocked re-entrant augment damage source=%s target=%s", sourceTag, targetRef);
            return false;
        }

        REENTRANCY_GUARD.set(true);
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
        } finally {
            REENTRANCY_GUARD.set(false);
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