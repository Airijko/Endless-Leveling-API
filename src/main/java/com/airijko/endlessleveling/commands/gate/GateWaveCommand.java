package com.airijko.endlessleveling.commands.gate;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Wave gate spawning commands (sneak-peek version without mob waves).
 */
public final class GateWaveCommand extends AbstractCommand {

    public GateWaveCommand() {
        super("wave", "Wave gate spawning (visual portals only)");
        this.addAliases("waves");
        this.addSubCommand(new WaveSpawnCommand());
        this.addSubCommand(new WaveRemoveCommand());
        this.addSubCommand(new WaveStatusCommand());
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Usage: /gate wave <spawn [rank]|remove [all|rank]|status>").color("#ffcc66"));
        return CompletableFuture.completedFuture(null);
    }
}
