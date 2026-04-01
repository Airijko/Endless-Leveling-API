package com.airijko.endlessleveling.commands.gate;

import com.airijko.endlessleveling.managers.WavePortalPreviewManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Spawn a visual wave gate portal near the player.
 */
public final class WaveSpawnCommand extends AbstractCommand {

    public WaveSpawnCommand() {
        super("spawn", "Spawn a wave gate portal near you");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.sendMessage(Message.raw("This command requires being in-world.").color("#ff9900"));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("Attempting to spawn wave gate portal...").color("#ffcc66"));

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            context.sendMessage(Message.raw("Could not resolve your player reference.").color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        return WavePortalPreviewManager.spawnPreviewNearPlayer(playerRef)
                .handle((snapshot, throwable) -> {
                    if (throwable != null || snapshot == null) {
                        context.sendMessage(Message.raw("Failed to spawn wave gate portal. Check requirements.").color("#ff6666"));
                        return null;
                    }

                    context.sendMessage(Message.raw(String.format(
                                    "Wave gate portal spawned: %s-rank at (%d, %d, %d)",
                                    snapshot.rankTier().letter(),
                                    snapshot.x(),
                                    snapshot.y(),
                                    snapshot.z()))
                            .color("#6cff78"));
                    return null;
                });
    }
}
