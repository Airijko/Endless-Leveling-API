package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.MobLevelingManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class DebugCommand extends AbstractPlayerCommand {

    private final MobLevelingManager mobLevelingManager;

    public DebugCommand() {
        super("debug", "Debug tools");
        this.mobLevelingManager = EndlessLeveling.getInstance() != null
                ? EndlessLeveling.getInstance().getMobLevelingManager()
                : null;
        this.addSubCommand(new StatTestCommand());
        this.addSubCommand(new DistanceCenterSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        senderRef.sendMessage(Message.raw("Usage: /el debug stattest | /el debug distancecenter").color("#ffcc66"));
    }

    private final class DistanceCenterSubCommand extends AbstractPlayerCommand {

        private DistanceCenterSubCommand() {
            super("distancecenter", "Show resolved distance center for this world");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            if (mobLevelingManager == null) {
                senderRef.sendMessage(Message.raw("MobLevelingManager is unavailable.").color("#ff6666"));
                return;
            }

            String details = mobLevelingManager.describeDistanceCenter(store, world);
            senderRef.sendMessage(Message.raw("Distance center debug: " + details).color("#4fd7f7"));
        }
    }
}
