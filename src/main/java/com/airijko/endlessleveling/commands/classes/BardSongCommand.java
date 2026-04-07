package com.airijko.endlessleveling.commands.classes;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /bard song — alias for /bard music. Delegates to {@link BardMusicCommand}.
 */
public class BardSongCommand extends AbstractPlayerCommand {

    private final BardMusicCommand delegate;

    public BardSongCommand(BardMusicCommand delegate) {
        super("song", "Open the bard music player");
        this.delegate = delegate;
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
        delegate.execute(context, store, ref, senderRef, world);
    }
}
