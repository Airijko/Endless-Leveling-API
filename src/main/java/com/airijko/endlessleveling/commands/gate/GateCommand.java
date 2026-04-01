package com.airijko.endlessleveling.commands.gate;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Root command for gate-related admin tools on core sneak-peek.
 */
public final class GateCommand extends AbstractCommand {

    public GateCommand() {
        super("gate", "Root command for EL gate tools (core sneak-peek)");
        this.addAliases("g", "elgate");
        this.addSubCommand(new GateWaveCommand());
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Usage: /gate wave <spawn [rank]|remove [all|rank]|status>").color("#ffcc66"));
        return CompletableFuture.completedFuture(null);
    }
}
