package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /priest setup church — place the church prefab at the priest's current location.
 * Requires confirmation (run twice within 15 seconds).
 */
public class PriestSetupCommand extends AbstractPlayerCommand {

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;
    private final RequiredArg<String> typeArg = this.withRequiredArg("type", "Structure type (church)", ArgTypes.STRING);

    public PriestSetupCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("setup", "Place your church structure");
        this.classManager = classManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef senderRef,
                           @Nonnull World world) {
        String type = typeArg.get(context);
        if (type == null || !type.equalsIgnoreCase("church")) {
            senderRef.sendMessage(Message.raw("Usage: /priest setup church").color("#ff6666"));
            return;
        }

        if (classManager == null || !classManager.isEnabled()) {
            senderRef.sendMessage(Message.raw("Classes are currently disabled.").color("#ff6666"));
            return;
        }
        if (playerDataManager == null) {
            senderRef.sendMessage(Message.raw("Player data is unavailable right now.").color("#ff6666"));
            return;
        }

        PlayerData data = playerDataManager.get(senderRef.getUuid());
        if (data == null) {
            data = playerDataManager.loadOrCreate(senderRef.getUuid(), senderRef.getUsername());
        }
        if (data == null) {
            senderRef.sendMessage(Message.raw("Unable to load your player data.").color("#ff6666"));
            return;
        }

        if (!PriestClassCheck.isPriest(classManager, data)) {
            senderRef.sendMessage(Message.raw("Only Priests may place a church.").color("#ff6666"));
            return;
        }

        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            senderRef.sendMessage(Message.raw("Church system is not initialized.").color("#ff6666"));
            return;
        }

        if (!manager.isPrefabAvailable()) {
            senderRef.sendMessage(Message.raw("Church prefab file is not available on this server.").color("#ff6666"));
            return;
        }

        if (manager.hasChurch(senderRef.getUuid())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("You already have a church placed. Use ").color("#ffffff"),
                    Message.raw("/priest undo church").color("#ffc300"),
                    Message.raw(" to remove it first.").color("#ffffff")));
            return;
        }

        if (manager.isOnCooldown(senderRef.getUuid())) {
            long remaining = manager.getRemainingCooldownSeconds(senderRef.getUuid());
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Please wait " + remaining + "s before placing a church.").color("#ff6666")));
            return;
        }

        // Confirmation step
        if (manager.requestPlacementConfirmation(senderRef.getUuid())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Are you sure you want to place your church here? ").color("#ffffff"),
                    Message.raw("Run the command again within 15 seconds to confirm.").color("#ffc300")));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            senderRef.sendMessage(Message.raw("Could not determine your position.").color("#ff6666"));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3i blockPos = new Vector3i((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));

        senderRef.sendMessage(Message.join(
                Message.raw("[Priest] ").color("#ffd700"),
                Message.raw("Placing church...").color("#ffffff")));

        String error = manager.placeChurch(senderRef.getUuid(), world, blockPos, store);
        if (error != null) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ff6666"),
                    Message.raw(error).color("#ffffff")));
        } else {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Your church has been placed at ").color("#ffffff"),
                    Message.raw("(" + blockPos.x + ", " + blockPos.y + ", " + blockPos.z + ")").color("#4fd7f7"),
                    Message.raw(".").color("#ffffff")));
        }
    }
}
