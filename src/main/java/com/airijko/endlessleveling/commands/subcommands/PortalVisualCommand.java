package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.managers.PortalVisualManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class PortalVisualCommand extends AbstractPlayerCommand {

    public PortalVisualCommand() {
        super("portal", "Portal visuals (no wave logic)");
        this.addAliases("portals", "gatevisual", "sneakpeek");
        this.addSubCommand(new PortalSneakPeekSubCommand());
        this.addSubCommand(new PortalSpawnSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef senderRef,
                           @Nonnull World world) {
        senderRef.sendMessage(Message.raw("Usage: /lvl portal <spawn|sneakpeek>").color("#ffcc66"));
    }

    private static final class PortalSneakPeekSubCommand extends AbstractPlayerCommand {

        private PortalSneakPeekSubCommand() {
            super("sneakpeek", "Show a sneak peek visual portal nearby");
            this.addAliases("peek", "preview", "sp");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef senderRef,
                               @Nonnull World world) {
            if (ref == null || !ref.isValid() || ref.getStore() == null) {
                senderRef.sendMessage(Message.raw("Player entity not available.").color("#ff6666"));
                return;
            }
            boolean spawned = PortalVisualManager.spawnSneakPeekNearPlayer(world, senderRef, ref);
            if (spawned) {
                senderRef.sendMessage(Message.raw("Sneak peek: a portal rift shimmers nearby.").color("#8be7ff"));
                return;
            }
            senderRef.sendMessage(Message.raw("Could not place a sneak peek portal in loaded chunks.").color("#ff6666"));
        }
    }

    private static final class PortalSpawnSubCommand extends AbstractPlayerCommand {

        private PortalSpawnSubCommand() {
            super("spawn", "Spawn a visual-only portal nearby");
            this.addAliases("vspawn", "show");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef senderRef,
                               @Nonnull World world) {
            if (ref == null || !ref.isValid() || ref.getStore() == null) {
                senderRef.sendMessage(Message.raw("Player entity not available.").color("#ff6666"));
                return;
            }
            boolean spawned = PortalVisualManager.spawnVisualNearPlayer(world, senderRef, ref, false);
            if (spawned) {
                senderRef.sendMessage(Message.raw("Visual portal spawned nearby (wave logic disabled).")
                        .color("#6cff78"));
                return;
            }
            senderRef.sendMessage(Message.raw("Could not place a visual portal in loaded chunks.").color("#ff6666"));
        }
    }
}
