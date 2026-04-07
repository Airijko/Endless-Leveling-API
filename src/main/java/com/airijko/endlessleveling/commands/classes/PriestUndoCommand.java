package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /priest undo church — remove the priest's placed church, restoring original blocks.
 * Requires confirmation (run twice within 15 seconds).
 */
public class PriestUndoCommand extends AbstractPlayerCommand {

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;
    private final RequiredArg<String> typeArg = this.withRequiredArg("type", "Structure type (church)", ArgTypes.STRING);

    public PriestUndoCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("undo", "Remove your church structure");
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
            senderRef.sendMessage(Message.raw("Usage: /priest undo church").color("#ff6666"));
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
            senderRef.sendMessage(Message.raw("Only Priests may manage churches.").color("#ff6666"));
            return;
        }

        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            senderRef.sendMessage(Message.raw("Church system is not initialized.").color("#ff6666"));
            return;
        }

        if (!manager.hasChurch(senderRef.getUuid())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("You don't have a church placed.").color("#ffffff")));
            return;
        }

        if (manager.isOnCooldown(senderRef.getUuid())) {
            long remaining = manager.getRemainingCooldownSeconds(senderRef.getUuid());
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Please wait " + remaining + "s before modifying your church.").color("#ff6666")));
            return;
        }

        ChurchManager.ChurchPlacement placement = manager.getChurch(senderRef.getUuid());
        if (!placement.worldName.equals(world.getName())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Your church is in world '").color("#ffffff"),
                    Message.raw(placement.worldName).color("#4fd7f7"),
                    Message.raw("'. You must be in that world to undo it.").color("#ffffff")));
            return;
        }

        // Confirmation step
        if (manager.requestUndoConfirmation(senderRef.getUuid())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Are you sure you want to remove your church? ").color("#ffffff"),
                    Message.raw("This will restore the original terrain. ").color("#9fb6d3"),
                    Message.raw("Run the command again within 15 seconds to confirm.").color("#ffc300")));
            return;
        }

        senderRef.sendMessage(Message.join(
                Message.raw("[Priest] ").color("#ffd700"),
                Message.raw("Removing church...").color("#ffffff")));

        String error = manager.undoChurch(senderRef.getUuid(), world);
        if (error != null) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ff6666"),
                    Message.raw(error).color("#ffffff")));
        } else {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("Your church has been removed and the terrain restored.").color("#ffffff")));
        }
    }
}
