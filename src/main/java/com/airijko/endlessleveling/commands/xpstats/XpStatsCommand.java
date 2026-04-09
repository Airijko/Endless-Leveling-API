package com.airijko.endlessleveling.commands.xpstats;

import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService;
import com.airijko.endlessleveling.xpstats.XpStatsManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Root command: /xpstats (alias: xps)
 * Opens the player's personal XP stats page by default.
 * Subcommands: top, profiles, profile, admin
 */
public class XpStatsCommand extends AbstractCommand {

    private final XpStatsManager xpStatsManager;
    private final XpStatsLeaderboardService leaderboardService;

    public XpStatsCommand(XpStatsManager xpStatsManager, XpStatsLeaderboardService leaderboardService) {
        super("xpstats", "XP Stats and Leaderboards");
        this.addAliases("xps");
        this.xpStatsManager = xpStatsManager;
        this.leaderboardService = leaderboardService;

        this.addSubCommand(new XpStatsTopSubCommand(leaderboardService));
        this.addSubCommand(new XpStatsProfilesSubCommand(xpStatsManager, leaderboardService));
        this.addSubCommand(new XpStatsProfileSubCommand(xpStatsManager));
        this.addSubCommand(new XpStatsAdminSubCommand(leaderboardService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext commandContext) {
        Player player = commandContext.sender() instanceof Player p ? p : null;
        if (player == null) {
            commandContext.sendMessage(Message.raw("This command is player-only.").color("#ff9900"));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null) {
            commandContext.sendMessage(Message.raw("Unable to open XP stats page right now.").color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        @SuppressWarnings("deprecation")
        PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
        if (playerRef == null) {
            commandContext.sendMessage(Message.raw("Unable to open XP stats page right now.").color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        player.getPageManager().openCustomPage(ref, store,
                new com.airijko.endlessleveling.ui.XpStatsUIPage(playerRef, CustomPageLifetime.CanDismiss,
                        xpStatsManager));
        return CompletableFuture.completedFuture(null);
    }
}
