package com.airijko.endlessleveling.commands.gate;

import com.airijko.endlessleveling.managers.WavePortalPreviewManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Display current wave gate portal status.
 */
public final class WaveStatusCommand extends AbstractCommand {

    public WaveStatusCommand() {
        super("status", "View active wave gate portal count");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        List<WavePortalPreviewManager.WavePreviewSnapshot> previews = WavePortalPreviewManager.listActivePreviews();
        if (previews.isEmpty()) {
            context.sendMessage(Message.raw("No active wave gate previews.").color("#8be7ff"));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("Active wave gate previews: " + previews.size()).color("#8be7ff"));
        for (WavePortalPreviewManager.WavePreviewSnapshot preview : previews) {
            String timeLabel = preview.secondsRemaining() <= 0
                    ? "opening now"
                    : String.format("opens in 00:%02d", Math.max(0, preview.secondsRemaining()));
            context.sendMessage(Message.raw(String.format(
                            "%s-rank at (%d, %d, %d) - %s",
                            preview.rankTier().letter(),
                            preview.x(),
                            preview.y(),
                            preview.z(),
                            timeLabel))
                    .color(preview.rankTier().color().hex()));
        }
        return CompletableFuture.completedFuture(null);
    }
}
