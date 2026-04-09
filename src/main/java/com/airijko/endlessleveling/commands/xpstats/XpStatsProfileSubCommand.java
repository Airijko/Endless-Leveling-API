package com.airijko.endlessleveling.commands.xpstats;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.xpstats.XpStatsData;
import com.airijko.endlessleveling.xpstats.XpStatsManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * /xpstats profile &lt;id&gt; — deep dive into a specific profile's XP stats.
 */
public class XpStatsProfileSubCommand extends AbstractPlayerCommand {

    private final XpStatsManager xpStatsManager;
    private final RequiredArg<Integer> profileIndexArg = this.withRequiredArg("slot", "Profile slot number",
            ArgTypes.INTEGER);

    public XpStatsProfileSubCommand(XpStatsManager xpStatsManager) {
        super("profile", "View detailed XP stats for a specific profile");
        this.xpStatsManager = xpStatsManager;
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
        int profileIndex = profileIndexArg.get(commandContext);
        if (!PlayerData.isValidProfileIndex(profileIndex)) {
            playerRef.sendMessage(Message.raw("Profile slot must be between 1 and " + PlayerData.MAX_PROFILES + ".")
                    .color("#ff6666"));
            return;
        }

        XpStatsData data = xpStatsManager.getOrLoad(playerRef.getUuid(), profileIndex);
        if (data == null) {
            playerRef.sendMessage(Message.raw("No XP stats for profile " + profileIndex + ".").color("#ff6666"));
            return;
        }

        data.rotateBuckets();

        var plugin = EndlessLeveling.getInstance();
        String profileName = "Profile " + profileIndex;
        if (plugin != null && plugin.getPlayerDataManager() != null) {
            PlayerData pd = plugin.getPlayerDataManager().get(playerRef.getUuid());
            if (pd != null) {
                profileName = pd.getProfileName(profileIndex);
            }
        }

        playerRef.sendMessage(Message.raw("--- XP Stats: " + profileName + " (slot " + profileIndex + ") ---")
                .color("#4fd7f7"));
        playerRef.sendMessage(Message.raw(String.format(Locale.ROOT, "Total XP: %,.0f", data.getTotalXp()))
                .color("#c0cee5"));
        playerRef.sendMessage(Message.raw(String.format(Locale.ROOT, "XP (24h): %,.0f", data.getXp24h()))
                .color("#c0cee5"));
        playerRef.sendMessage(Message.raw(String.format(Locale.ROOT, "XP (7d):  %,.0f", data.getXp7d()))
                .color("#c0cee5"));
        playerRef.sendMessage(Message.raw(String.format(Locale.ROOT, "Momentum: %.2f", data.getMomentum()))
                .color("#c0cee5"));

        // Hourly breakdown
        StringBuilder hourly = new StringBuilder("Hourly: ");
        double[] h = data.getHourly();
        for (int i = 0; i < h.length; i++) {
            if (h[i] > 0) {
                hourly.append(String.format(Locale.ROOT, "[%02d]=%,.0f ", i, h[i]));
            }
        }
        playerRef.sendMessage(Message.raw(hourly.toString().trim()).color("#8899aa"));

        // Prestige history
        if (!data.getPrestigeHistory().isEmpty()) {
            playerRef.sendMessage(Message.raw("Prestige History:").color("#ffd79a"));
            for (var event : data.getPrestigeHistory()) {
                String ts = java.time.Instant.ofEpochSecond(event.timestamp())
                        .atZone(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                playerRef.sendMessage(Message.raw("  P" + event.prestige() + " at " + ts + " UTC")
                        .color("#ffd79a"));
            }
        }
    }
}
