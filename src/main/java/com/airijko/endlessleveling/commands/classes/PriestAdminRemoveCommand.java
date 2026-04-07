package com.airijko.endlessleveling.commands.classes;

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
import java.util.UUID;

/**
 * /priest admin remove &lt;uuid&gt; — admin-only command to force-remove a player's church.
 * This restores blocks if the admin is in the same world as the church.
 */
public class PriestAdminRemoveCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> targetArg = this.withRequiredArg("player_uuid", "Target player UUID", ArgTypes.STRING);

    public PriestAdminRemoveCommand() {
        super("remove", "Force-remove a player's church (admin)");
    }

    @Override
    protected boolean canGeneratePermission() {
        return true;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef senderRef,
                           @Nonnull World world) {
        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            senderRef.sendMessage(Message.raw("Church system is not initialized.").color("#ff6666"));
            return;
        }

        String raw = targetArg.get(context);
        if (raw == null || raw.isBlank()) {
            senderRef.sendMessage(Message.raw("Usage: /priest admin remove <player_uuid>").color("#ff6666"));
            return;
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            senderRef.sendMessage(Message.raw("Invalid UUID format.").color("#ff6666"));
            return;
        }

        String error = manager.adminRemoveChurch(targetUuid, world);
        if (error != null) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest Admin] ").color("#ff6666"),
                    Message.raw(error).color("#ffffff")));
        } else {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest Admin] ").color("#ffd700"),
                    Message.raw("Church for ").color("#ffffff"),
                    Message.raw(targetUuid.toString()).color("#4fd7f7"),
                    Message.raw(" has been removed.").color("#ffffff")));
        }
    }
}
