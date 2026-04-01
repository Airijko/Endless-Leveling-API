package com.airijko.endlessleveling.commands.gate;

import com.airijko.endlessleveling.enums.GateRankTier;
import com.airijko.endlessleveling.managers.WavePortalPreviewManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Remove active wave gate previews.
 */
public final class WaveRemoveCommand extends AbstractCommand {

    public WaveRemoveCommand() {
        super("remove", "Remove active wave gate preview(s)");
        this.addAliases("clear", "despawn");
        this.setAllowsExtraArguments(true);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        List<String> args = extractTrailingArgs(context);
        if (args.isEmpty()) {
            if (!(context.sender() instanceof Player player)) {
                context.sendMessage(Message.raw("Usage: /gate wave remove <all|rank>").color("#ffcc66"));
                return CompletableFuture.completedFuture(null);
            }
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) {
                context.sendMessage(Message.raw("Could not resolve your player reference.").color("#ff6666"));
                return CompletableFuture.completedFuture(null);
            }
            boolean removed = WavePortalPreviewManager.removePreviewForPlayer(playerRef);
            context.sendMessage(Message.raw(removed
                            ? "Removed your active wave preview."
                            : "You have no active wave preview to remove.")
                    .color(removed ? "#6cff78" : "#ffcc66"));
            return CompletableFuture.completedFuture(null);
        }

        String mode = args.get(0).toLowerCase(Locale.ROOT);
        if ("all".equals(mode)) {
            int removed = WavePortalPreviewManager.removeAllPreviews();
            context.sendMessage(Message.raw("Removed wave previews: " + removed).color("#6cff78"));
            return CompletableFuture.completedFuture(null);
        }

        GateRankTier rankTier = parseRankTier(mode);
        if (rankTier == null) {
            context.sendMessage(Message.raw("Usage: /gate wave remove <all|s|a|b|c|d|e>").color("#ffcc66"));
            return CompletableFuture.completedFuture(null);
        }

        int removed = WavePortalPreviewManager.removePreviewsByRank(rankTier);
        context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Removed %s-rank wave previews: %d",
                        rankTier.letter(),
                        removed))
                .color(rankTier.color().hex()));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static GateRankTier parseRankTier(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return switch (token) {
            case "s", "rank_s", "ranks" -> GateRankTier.S;
            case "a", "rank_a", "ranka" -> GateRankTier.A;
            case "b", "rank_b", "rankb" -> GateRankTier.B;
            case "c", "rank_c", "rankc" -> GateRankTier.C;
            case "d", "rank_d", "rankd" -> GateRankTier.D;
            case "e", "rank_e", "ranke" -> GateRankTier.E;
            default -> null;
        };
    }

    private static List<String> extractTrailingArgs(CommandContext context) {
        if (context == null || context.getInputString() == null || context.getInputString().isBlank()) {
            return List.of();
        }

        String[] tokens = context.getInputString().trim().split("\\s+");
        if (tokens.length == 0) {
            return List.of();
        }

        String commandPath = context.getCalledCommand() != null
                ? context.getCalledCommand().getFullyQualifiedName()
                : null;
        int commandTokenCount = 1;
        if (commandPath != null && !commandPath.isBlank()) {
            commandTokenCount = Math.max(1, commandPath.trim().split("\\s+").length);
        }

        if (tokens.length <= commandTokenCount) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (int index = commandTokenCount; index < tokens.length; index++) {
            String token = tokens[index];
            if (token == null || token.isBlank()) {
                continue;
            }
            values.add(token);
        }
        return values;
    }
}
