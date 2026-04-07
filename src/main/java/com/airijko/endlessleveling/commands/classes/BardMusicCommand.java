package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.ui.BardMusicPage;
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
 * /bard music — opens the bard music player GUI.
 * Restricted to players whose primary class is any tier of Bard.
 */
public class BardMusicCommand extends AbstractPlayerCommand {

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;

    public BardMusicCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("music", "Open the bard music player");
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

        if (!BardClassCheck.isBard(classManager, data)) {
            senderRef.sendMessage(Message.raw("Only Bards may use the music player.").color("#ff6666"));
            return;
        }

        Player player = context.senderAs(Player.class);
        if (player == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            BardMusicPage page = new BardMusicPage(senderRef, CustomPageLifetime.CanDismiss, ref, store);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }
}
