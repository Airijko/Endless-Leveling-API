package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
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
 * /priest root command. Priest-class players use this to manage their church.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>/priest menu — open the priest church menu GUI</li>
 *   <li>/priest setup church — place your church at your location</li>
 *   <li>/priest undo church — remove your church and restore terrain</li>
 *   <li>/priest info — view your church placement status</li>
 *   <li>/priest admin remove &lt;uuid&gt; — (admin) force-remove a player's church</li>
 * </ul>
 */
public class PriestCommand extends AbstractPlayerCommand {

    public PriestCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("priest", "Priest class commands");
        this.addSubCommand(new PriestMenuCommand(classManager, playerDataManager));
        this.addSubCommand(new PriestSetupCommand(classManager, playerDataManager));
        this.addSubCommand(new PriestUndoCommand(classManager, playerDataManager));
        this.addSubCommand(new PriestInfoCommand(classManager, playerDataManager));
        this.addSubCommand(new PriestAdminCommand());
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
        senderRef.sendMessage(Message.raw("=== Priest Commands ===").color("#ffd700"));
        senderRef.sendMessage(Message.join(
                Message.raw("  /priest menu").color("#ffc300"),
                Message.raw(" — Open the church menu").color("#9fb6d3")));
        senderRef.sendMessage(Message.join(
                Message.raw("  /priest setup church").color("#ffc300"),
                Message.raw(" — Place your church").color("#9fb6d3")));
        senderRef.sendMessage(Message.join(
                Message.raw("  /priest undo church").color("#ffc300"),
                Message.raw(" — Remove your church").color("#9fb6d3")));
        senderRef.sendMessage(Message.join(
                Message.raw("  /priest info").color("#ffc300"),
                Message.raw(" — Check church status").color("#9fb6d3")));
    }
}
