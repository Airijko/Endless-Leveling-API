package com.airijko.endlessleveling.commands.xpstats;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.xpstats.XpStatsLeaderboardService;
import com.airijko.endlessleveling.xpstats.XpStatsManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * /xpstats profiles — lists the sender's profiles ranked by xp24h.
 */
public class XpStatsProfilesSubCommand extends AbstractPlayerCommand {

    private final XpStatsManager xpStatsManager;
    private final XpStatsLeaderboardService leaderboardService;

    public XpStatsProfilesSubCommand(XpStatsManager xpStatsManager, XpStatsLeaderboardService leaderboardService) {
        super("profiles", "List your profiles ranked by recent XP gain");
        this.xpStatsManager = xpStatsManager;
        this.leaderboardService = leaderboardService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        var plugin = EndlessLeveling.getInstance();
        if (plugin == null) return;

        var playerDataManager = plugin.getPlayerDataManager();
        PlayerData playerData = playerDataManager != null ? playerDataManager.get(playerRef.getUuid()) : null;
        if (playerData == null) {
            playerRef.sendMessage(Message.raw("No data found.").color("#ff6666"));
            return;
        }

        var entries = leaderboardService.getPlayerProfiles(playerRef.getUuid());
        if (entries.isEmpty()) {
            playerRef.sendMessage(Message.raw("No XP stats data yet. Earn some XP first!").color("#ffcc66"));
            return;
        }

        playerRef.sendMessage(Message.raw("--- Your Profiles (by XP/24h) ---").color("#4fd7f7"));
        int rank = 1;
        for (var entry : entries) {
            String line = String.format(Locale.ROOT, "#%d %s — XP/24h: %,.0f | XP/7d: %,.0f | Total: %,.0f",
                    rank++, entry.profileName(), entry.xp24h(), entry.xp7d(), entry.totalXp());
            playerRef.sendMessage(Message.raw(line).color("#c0cee5"));
        }
    }
}
