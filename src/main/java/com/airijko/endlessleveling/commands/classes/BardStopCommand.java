package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
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
 * /bard stop — stop performing the active bard song.
 * Restricted to players whose primary class is any tier of Bard.
 */
public class BardStopCommand extends AbstractPlayerCommand {

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;

    public BardStopCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("stop", "Stop performing the current bard song");
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
            senderRef.sendMessage(Message.raw("Only Bards may perform songs.").color("#ff6666"));
            return;
        }

        BardSongRegistry.ActiveSong cleared = BardSongRegistry.clearActiveSong(senderRef.getUuid());
        if (cleared == null) {
            senderRef.sendMessage(Message.raw("You are not currently performing a song.").color("#ff9900"));
            return;
        }

        senderRef.sendMessage(Message.join(
                Message.raw("[Bard] ").color("#4fd7f7"),
                Message.raw("Stopped playing ").color("#ffffff"),
                Message.raw(BardSongRegistry.getDisplayName(cleared.getSongId())).color("#ffc300"),
                Message.raw(".").color("#ffffff")));
    }
}
