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
 * /bard root command. Bard-class players use this to perform songs.
 */
public class BardCommand extends AbstractPlayerCommand {

    public BardCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("bard", "Bard class commands");
        this.addSubCommand(new BardPlayCommand(classManager, playerDataManager));
        this.addSubCommand(new BardStopCommand(classManager, playerDataManager));
        BardMusicCommand musicCommand = new BardMusicCommand(classManager, playerDataManager);
        this.addSubCommand(musicCommand);
        this.addSubCommand(new BardSongCommand(musicCommand));
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
        senderRef.sendMessage(Message.raw("Usage: /bard play <song> | /bard stop | /bard music").color("#4fd7f7"));
        senderRef.sendMessage(Message.raw("Available songs: " + BardSongRegistry.listSongNames()).color("#9fb6d3"));
    }
}
