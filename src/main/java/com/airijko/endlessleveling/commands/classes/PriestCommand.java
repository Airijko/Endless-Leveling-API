package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.ui.PriestMenuPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /priest root command. Priest-class players use this to manage their church.
 * Running /priest with no subcommand opens the church menu GUI.
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

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;

    public PriestCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("priest", "Priest class commands");
        this.classManager = classManager;
        this.playerDataManager = playerDataManager;
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
            senderRef.sendMessage(Message.raw("Only Priests may use the church menu.").color("#ff6666"));
            return;
        }

        Player player = context.senderAs(Player.class);

        CompletableFuture.runAsync(() -> {
            PriestMenuPage page = new PriestMenuPage(senderRef, CustomPageLifetime.CanDismiss);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }
}
