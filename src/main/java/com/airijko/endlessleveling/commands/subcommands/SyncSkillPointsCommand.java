package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.util.PartnerConsoleGuard;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /el skillpointssync – resets ALL players' skill points only
 * (not levels or prestige) and recalculates them from the current config.
 * Includes both online and offline players (scans playerdata files on disk).
 * Aliases: spsync
 */
public class SyncSkillPointsCommand extends AbstractCommand {

    private final PlayerDataManager playerDataManager;
    private final SkillManager skillManager;

    public SyncSkillPointsCommand() {
        super("skillpointssync", "Reset and recalculate skill points for all players (online + offline)");
        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
        this.skillManager = EndlessLeveling.getInstance().getSkillManager();
        this.addAliases("spsync");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext commandContext) {
        if (commandContext.sender() instanceof Player player) {
            PlayerRef senderRef = Universe.get().getPlayer(player.getUuid());
            if (OperatorHelper.denyNonAdmin(senderRef)) return CompletableFuture.completedFuture(null);
        } else if (!PartnerConsoleGuard.isConsoleAllowed("el skillpointssync")) {
            commandContext.sendMessage(Message.raw(
                    "Console admin access requires an authorized EndlessLevelingPartnerAddon.")
                    .color("#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        // Load ALL players from disk (online + offline) into cache
        List<PlayerData> allPlayers = playerDataManager.getAllPlayersSortedByLevel();
        if (allPlayers.isEmpty()) {
            commandContext.sendMessage(Message.raw("No player data found to sync."));
            return CompletableFuture.completedFuture(null);
        }

        int syncCount = 0;
        for (PlayerData data : allPlayers) {
            skillManager.resetSkillAttributes(data);
            playerDataManager.save(data);

            PlayerRef targetRef = Universe.get().getPlayer(data.getUuid());
            if (targetRef != null) {
                applySkillModifiers(data, targetRef);
                targetRef.sendMessage(Message.raw(
                        "Your skill points have been synced to the current configuration.")
                        .color("#4fd7f7"));
            }
            syncCount++;
        }

        commandContext.sendMessage(Message.raw(
                "Synced skill points for " + syncCount + " player(s).")
                .color("#4fd7f7"));
        return CompletableFuture.completedFuture(null);
    }

    private void applySkillModifiers(PlayerData data, PlayerRef targetRef) {
        UUID worldUuid = targetRef.getWorldUuid();
        World world = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
        if (world == null) {
            scheduleRetry(data);
            return;
        }
        try {
            world.execute(() -> {
                Ref<EntityStore> targetEntity = targetRef.getReference();
                if (targetEntity == null) {
                    scheduleRetry(data);
                    return;
                }
                Store<EntityStore> targetStore = targetEntity.getStore();
                boolean applied = skillManager.applyAllSkillModifiers(targetEntity, targetStore, data);
                if (!applied) {
                    scheduleRetry(data);
                }
            });
        } catch (Exception ex) {
            scheduleRetry(data);
        }
    }

    private void scheduleRetry(PlayerData data) {
        var retrySystem = EndlessLeveling.getInstance().getPlayerRaceStatSystem();
        if (retrySystem != null) {
            retrySystem.scheduleRetry(data.getUuid());
        }
    }
}
