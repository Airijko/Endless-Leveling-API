package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.util.ChatMessageStrings;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MobAugmentAnnouncer {
    private static final double ANNOUNCE_RADIUS = 48.0D;

    private MobAugmentAnnouncer() {
    }

    public static void announceTrigger(CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> mobRef,
            String augmentName,
            String detail) {
        if (commandBuffer == null || !EntityRefUtil.isUsable(mobRef) || augmentName == null || augmentName.isBlank()) {
            return;
        }

        Vector3d position = resolvePosition(commandBuffer, mobRef);
        if (position == null) {
            return;
        }

        String mobLabel = resolveMobLabel(commandBuffer, mobRef);
        String suffix = detail == null || detail.isBlank() ? "" : " (" + detail.trim() + ")";
        String message = String.format("%s triggered %s%s.", mobLabel, augmentName.trim(), suffix);

        Set<UUID> sent = new HashSet<>();
        for (Ref<EntityStore> entityRef : TargetUtil.getAllEntitiesInSphere(position, ANNOUNCE_RADIUS, commandBuffer)) {
            if (!EntityRefUtil.isUsable(entityRef)) {
                continue;
            }
            PlayerRef playerRef = EntityRefUtil.tryGetComponent(commandBuffer, entityRef, PlayerRef.getComponentType());
            if (playerRef == null || !playerRef.isValid() || playerRef.getUuid() == null) {
                continue;
            }
            if (!sent.add(playerRef.getUuid())) {
                continue;
            }
            PlayerChatNotifier.send(playerRef, message, ChatMessageStrings.Color.WARNING_ORANGE);
        }
    }

    private static Vector3d resolvePosition(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref) {
        TransformComponent transform = EntityRefUtil.tryGetComponent(commandBuffer, ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d p = transform.getPosition();
        return new Vector3d(p.getX(), p.getY(), p.getZ());
    }

    private static String resolveMobLabel(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> mobRef) {
        String baseName = "Mob";
        DisplayNameComponent display = EntityRefUtil.tryGetComponent(commandBuffer,
                mobRef,
                DisplayNameComponent.getComponentType());
        if (display != null
                && display.getDisplayName() != null
                && display.getDisplayName().getAnsiMessage() != null
                && !display.getDisplayName().getAnsiMessage().isBlank()) {
            baseName = display.getDisplayName().getAnsiMessage().trim();
        }

        int level = resolveMobLevel(commandBuffer, mobRef);
        if (level > 0) {
            return "[Lv." + level + "] " + baseName;
        }
        return baseName;
    }

    private static int resolveMobLevel(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> mobRef) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        MobLevelingManager levelingManager = plugin == null ? null : plugin.getMobLevelingManager();
        if (levelingManager == null || mobRef == null) {
            return -1;
        }
        try {
            return levelingManager.resolveMobLevel(mobRef, commandBuffer);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }
}
