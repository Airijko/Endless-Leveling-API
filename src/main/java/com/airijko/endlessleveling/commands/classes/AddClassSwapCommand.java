package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.util.PartnerConsoleGuard;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * /el classes addswap &lt;player&gt; &lt;primary|secondary|both&gt; &lt;count&gt;
 *
 * <p>Grants additional class swaps to a player's active profile for the selected
 * class slot(s). Requires the {@code endlessleveling.classes.addswap} permission
 * node when executed by a player, or an authorized EndlessLevelingPartnerAddon
 * when executed from the console.
 */
public class AddClassSwapCommand extends AbstractCommand {

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;

    public AddClassSwapCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("addswap", "Grant an additional class swap to a player");
        this.classManager = classManager;
        this.playerDataManager = playerDataManager;
        this.addUsageVariant(new AddClassSwapSelfVariant());
        this.addUsageVariant(new AddClassSwapTargetVariant());
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("Usage: /el classes addswap <slot> <count> or /el classes addswap <player> <slot> <count>")
                .color("#9fb6d3"));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> executeInternal(@Nonnull CommandContext context,
            @Nullable String explicitTargetName,
            @Nullable String slotInput,
            int count) {
        Player senderPlayer = context.sender() instanceof Player p ? p : null;
        boolean senderIsPlayer = senderPlayer != null;

        if (!PartnerConsoleGuard.isConsoleAllowed("el classes addswap")) {
            context.sendMessage(Message.raw(
                    "This command requires an authorized EndlessLevelingPartnerAddon.")
                    .color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        if (playerDataManager == null) {
            context.sendMessage(Message.raw("Player data system is not initialised.").color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        if (count <= 0) {
            context.sendMessage(Message.raw("Count must be a positive integer.").color("#ff9900"));
            return CompletableFuture.completedFuture(null);
        }

        SwapTarget target = parseSwapTarget(slotInput);
        if (target == null) {
            context.sendMessage(Message.raw("Unknown slot. Use primary, secondary, or both.").color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        boolean secondaryEnabled = classManager != null && classManager.isSecondaryClassEnabled();
        if ((target == SwapTarget.SECONDARY || target == SwapTarget.BOTH) && !secondaryEnabled) {
            context.sendMessage(Message.raw("Secondary classes are currently disabled.").color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        PlayerData targetData;
        String targetName;

        if (explicitTargetName != null && !explicitTargetName.isBlank()) {
            targetName = explicitTargetName.trim();
            targetData = playerDataManager.getByName(targetName);
            if (targetData == null) {
                context.sendMessage(Message.raw("Player not found: " + targetName).color("#ff6666"));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            if (!senderIsPlayer) {
                context.sendMessage(Message.raw("Console usage requires a target player argument.").color("#ff6666"));
                return CompletableFuture.completedFuture(null);
            }

            targetData = playerDataManager.get(senderPlayer.getUuid());
            if (targetData == null) {
                context.sendMessage(Message.raw("No saved data found. Try rejoining.").color("#ff6666"));
                return CompletableFuture.completedFuture(null);
            }

            PlayerRef selfRef = Universe.get().getPlayer(targetData.getUuid());
            targetName = selfRef != null ? selfRef.getUsername() : targetData.getPlayerName();
        }

        // Settle the anti-exploit flag BEFORE adding swaps so the one-time consume
        // fires on the current state and doesn't eat the newly granted swaps.
        if (classManager != null) {
            classManager.settleAntiExploit(targetData);
        }

        int primaryBefore = targetData.getRemainingPrimaryClassSwitches();
        int primaryAfter = primaryBefore;
        int addedPrimary = 0;
        if (target == SwapTarget.PRIMARY || target == SwapTarget.BOTH) {
            primaryAfter = safeAddNonNegative(primaryBefore, count);
            addedPrimary = Math.max(0, primaryAfter - primaryBefore);
            targetData.setRemainingPrimaryClassSwitches(primaryAfter);
        }

        StringBuilder summary = new StringBuilder();
        if (target == SwapTarget.PRIMARY || target == SwapTarget.BOTH) {
            summary.append("primary: ").append(primaryBefore).append(" -> ").append(primaryAfter);
        }

        int secondaryBefore = 0;
        int secondaryAfter = 0;
        int addedSecondary = 0;
        if (target == SwapTarget.SECONDARY || target == SwapTarget.BOTH) {
            secondaryBefore = targetData.getRemainingSecondaryClassSwitches();
            secondaryAfter = safeAddNonNegative(secondaryBefore, count);
            addedSecondary = Math.max(0, secondaryAfter - secondaryBefore);
            targetData.setRemainingSecondaryClassSwitches(secondaryAfter);
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append("secondary: ").append(secondaryBefore).append(" -> ").append(secondaryAfter);
        }

        playerDataManager.save(targetData);

        context.sendMessage(Message.raw(
            "Added " + count + " class swap(s) to " + targetName + " for " + target.label + " (" + summary + ").")
            .color("#4fd7f7"));

        PlayerRef targetRef = Universe.get().getPlayer(targetData.getUuid());
        if (targetRef != null) {
            String msg = switch (target) {
                case PRIMARY -> "An admin granted you " + addedPrimary + " additional primary class swap(s).";
                case SECONDARY -> "An admin granted you " + addedSecondary + " additional secondary class swap(s).";
                case BOTH -> "An admin granted you " + addedPrimary + " additional primary and "
                        + addedSecondary + " additional secondary class swap(s).";
            };
            targetRef.sendMessage(Message.raw(msg).color("#4fd7f7"));
        }

        return CompletableFuture.completedFuture(null);
    }

    private final class AddClassSwapSelfVariant extends AbstractCommand {
        private final RequiredArg<String> selfSlotArg =
                this.withRequiredArg("slot", "primary|secondary|both", ArgTypes.STRING);
        private final RequiredArg<Integer> selfCountArg =
                this.withRequiredArg("count", "Number of swaps to add", ArgTypes.INTEGER);

        private AddClassSwapSelfVariant() {
            super("Grant yourself additional class swaps");
        }

        @Nullable
        @Override
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            return executeInternal(context, null, selfSlotArg.get(context), selfCountArg.get(context));
        }
    }

    private final class AddClassSwapTargetVariant extends AbstractCommand {
        private final RequiredArg<String> targetPlayerArg =
                this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        private final RequiredArg<String> targetSlotArg =
                this.withRequiredArg("slot", "primary|secondary|both", ArgTypes.STRING);
        private final RequiredArg<Integer> targetCountArg =
                this.withRequiredArg("count", "Number of swaps to add", ArgTypes.INTEGER);

        private AddClassSwapTargetVariant() {
            super("Grant a player additional class swaps");
        }

        @Nullable
        @Override
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            return executeInternal(context, targetPlayerArg.get(context), targetSlotArg.get(context),
                    targetCountArg.get(context));
        }
    }

    private SwapTarget parseSwapTarget(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        return switch (normalized) {
            case "primary", "main", "p" -> SwapTarget.PRIMARY;
            case "secondary", "off", "s" -> SwapTarget.SECONDARY;
            case "both", "all", "b" -> SwapTarget.BOTH;
            default -> null;
        };
    }

    private enum SwapTarget {
        PRIMARY("primary"),
        SECONDARY("secondary"),
        BOTH("both");

        private final String label;

        SwapTarget(String label) {
            this.label = label;
        }
    }

    private int safeAddNonNegative(int base, int delta) {
        int safeBase = Math.max(0, base);
        int safeDelta = Math.max(0, delta);
        long sum = (long) safeBase + safeDelta;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
