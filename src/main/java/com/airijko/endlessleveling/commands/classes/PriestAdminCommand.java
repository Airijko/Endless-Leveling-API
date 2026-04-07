package com.airijko.endlessleveling.commands.classes;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /priest admin — parent group for admin-only priest subcommands.
 */
public class PriestAdminCommand extends AbstractPlayerCommand {

    public PriestAdminCommand() {
        super("admin", "Priest admin commands");
        this.addSubCommand(new PriestAdminRemoveCommand());
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
        senderRef.sendMessage(Message.raw("Usage: /priest admin remove <player_uuid>").color("#ffd700"));
    }
}
