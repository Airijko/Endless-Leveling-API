package com.airijko.endlessleveling.commands.augments;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * /el augments resetallplayers
 *
 * Resets every loaded player's selected augments and offers to zero, then
 * re-grants the correct number of augment choices based on their current
 * level and prestige according to the configured milestone rules.
 */
public class ResetAugmentsAllPlayersCommand extends AbstractPlayerCommand {

    private final PlayerDataManager playerDataManager;
    private final AugmentUnlockManager augmentUnlockManager;

    public ResetAugmentsAllPlayersCommand() {
        super("resetallplayers", "Reset augments for all loaded players and regrant correct offers");
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.playerDataManager = plugin != null ? plugin.getPlayerDataManager() : null;
        this.augmentUnlockManager = plugin != null ? plugin.getAugmentUnlockManager() : null;
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        if (OperatorHelper.denyNonAdmin(senderRef)) return;

        if (playerDataManager == null || augmentUnlockManager == null) {
            senderRef.sendMessage(Message.raw("Augment system is not initialised.").color("#ff6666"));
            return;
        }

        // getAllPlayersSortedByLevel() loads all on-disk data files into the cache,
        // so offline players are included as well as online ones.
        Collection<PlayerData> allPlayers = playerDataManager.getAllPlayersSortedByLevel();
        if (allPlayers.isEmpty()) {
            senderRef.sendMessage(Message.raw("No player data found.").color("#ff6666"));
            return;
        }

        int count = 0;
        for (PlayerData data : allPlayers) {
            augmentUnlockManager.resetAllAugmentsForAllProfiles(data);

            PlayerRef targetRef = Universe.get().getPlayer(data.getUuid());
            if (targetRef != null && !targetRef.getUuid().equals(senderRef.getUuid())) {
                targetRef.sendMessage(Message.raw(
                        "An admin reset your augments. Your augment offers have been refreshed based on your level.")
                        .color("#4fd7f7"));
            }
            count++;
        }

        senderRef.sendMessage(Message.raw(
                "Reset augments and rebuilt eligible offers for " + count + " player(s).").color("#4fd7f7"));
    }
}
