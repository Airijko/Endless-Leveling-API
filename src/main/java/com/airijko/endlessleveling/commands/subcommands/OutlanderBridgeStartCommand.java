package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public final class OutlanderBridgeStartCommand extends AbstractCommand {

    public OutlanderBridgeStartCommand() {
        super("start", "Immediately start the Outlander Bridge wave countdown");
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        Player player = ctx.sender() instanceof Player p ? p : null;
        if (player == null) {
            ctx.sendMessage(Message.raw("Player-only command.").color("#ff9900"));
            return CompletableFuture.completedFuture(null);
        }
        World world = player.getWorld();
        String worldName = world != null && world.getName() != null ? world.getName() : "<null>";
        if (world == null || !OutlanderBridgeWaveManager.get().isOutlanderBridgeWorld(world)) {
            ctx.sendMessage(Message.raw("Not in an Outlander Bridge instance. Current world: " + worldName).color("#ff9900"));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = OutlanderBridgeWaveManager.get().forceStart(world);
        ctx.sendMessage(Message.raw(ok ? "Outlander Bridge waves started." : "Waves already running.")
                .color(ok ? "#8effb6" : "#f3c27a"));
        return CompletableFuture.completedFuture(null);
    }
}
