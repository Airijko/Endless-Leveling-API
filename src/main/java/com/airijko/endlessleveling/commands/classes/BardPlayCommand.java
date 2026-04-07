package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.CharacterClassDefinition;
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
 * /bard play &lt;song&gt; — start performing one of the bard's songs.
 * Restricted to players whose primary class is any tier of Bard.
 */
public class BardPlayCommand extends AbstractPlayerCommand {

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;

    private final RequiredArg<String> songArg = this.withRequiredArg("song", "Song name", ArgTypes.STRING);

    public BardPlayCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("play", "Start performing a bard song");
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

        String requested = songArg.get(context);
        String songId = BardSongRegistry.resolveSongId(requested);
        if (songId == null) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Bard] ").color("#ff6666"),
                    Message.raw("Unknown song: ").color("#ffffff"),
                    Message.raw(requested == null ? "" : requested).color("#ffc300")));
            senderRef.sendMessage(Message.raw("Available songs: " + BardSongRegistry.listSongNames()).color("#9fb6d3"));
            return;
        }

        BardSongRegistry.setActiveSong(senderRef.getUuid(), songId);

        CharacterClassDefinition primary = classManager.getPlayerPrimaryClass(data);
        String classLabel = primary != null ? primary.getDisplayName() : "Bard";

        senderRef.sendMessage(Message.join(
                Message.raw("[Bard] ").color("#4fd7f7"),
                Message.raw(classLabel).color("#d4b5ff"),
                Message.raw(" begins playing ").color("#ffffff"),
                Message.raw(BardSongRegistry.getDisplayName(songId)).color("#ffc300"),
                Message.raw(".").color("#ffffff")));
    }
}
