package com.airijko.endlessleveling.commands.xpstats;

import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /xpstats admin — opens the admin panel (operator-only).
 */
public class XpStatsAdminSubCommand extends AbstractPlayerCommand {

    private final XpStatsLeaderboardService leaderboardService;

    public XpStatsAdminSubCommand(XpStatsLeaderboardService leaderboardService) {
        super("admin", "Open the XP Stats admin panel");
        this.leaderboardService = leaderboardService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return true; // require operator permission
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        Player player = commandContext.senderAs(Player.class);
        if (player == null) return;

        player.getPageManager().openCustomPage(ref, store,
                new com.airijko.endlessleveling.ui.XpStatsAdminUIPage(playerRef,
                        CustomPageLifetime.CanDismiss, leaderboardService));
    }
}
