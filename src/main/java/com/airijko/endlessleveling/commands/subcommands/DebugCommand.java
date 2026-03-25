package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.mob.MobLevelingSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class DebugCommand extends AbstractPlayerCommand {

    private static final String MOB_CLEANUP_PERMISSION = HytalePermissions.fromCommand("endlessleveling.debug.mobcleanup");
    private final MobLevelingManager mobLevelingManager;
    private final MobLevelingSystem mobLevelingSystem;

    public DebugCommand() {
        super("debug", "Debug tools");
        this.mobLevelingManager = EndlessLeveling.getInstance() != null
                ? EndlessLeveling.getInstance().getMobLevelingManager()
                : null;
        this.mobLevelingSystem = EndlessLeveling.getInstance() != null
                ? EndlessLeveling.getInstance().getMobLevelingSystem()
                : null;
        this.addSubCommand(new StatTestCommand());
        this.addSubCommand(new DistanceCenterSubCommand());
        this.addSubCommand(new MobLevelsSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        senderRef.sendMessage(Message.raw("Usage: /el debug stattest | /el debug distancecenter | /el debug moblevels").color("#ffcc66"));
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

    private final class MobLevelsSubCommand extends AbstractPlayerCommand {

        private MobLevelsSubCommand() {
            super("moblevels", "Toggle mob leveling on or off in this world");
            this.addAliases("mobcleanup", "clearmobs", "mobclear", "mobtoggle");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            CommandUtil.requirePermission(commandContext.sender(), MOB_CLEANUP_PERMISSION);

            if (mobLevelingSystem == null) {
                senderRef.sendMessage(Message.raw("MobLevelingSystem is unavailable.").color("#ff6666"));
                return;
            }

            boolean nowSuppressed = mobLevelingSystem.debugToggleStore(store);
            senderRef.sendMessage(
                    Message.raw(nowSuppressed
                            ? "Mob leveling is now OFF for this world. Existing mob scaling and nameplates were cleared. Run the command again to turn it back ON."
                            : "Mob leveling is now ON for this world. A full rescale has been requested.")
                            .color("#6cff78"));
        }
    }
}
