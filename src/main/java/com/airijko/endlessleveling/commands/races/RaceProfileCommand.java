package com.airijko.endlessleveling.commands.races;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.airijko.endlessleveling.managers.RaceManager;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /race profile: display the player's current EndlessLeveling race.
 */
public class RaceProfileCommand extends AbstractPlayerCommand {

    private final RaceManager raceManager;
    private final PlayerDataManager playerDataManager;

    public RaceProfileCommand(RaceManager raceManager, PlayerDataManager playerDataManager) {
        super("profile", "Show your current EndlessLeveling race");
        this.raceManager = raceManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        if (raceManager == null || !raceManager.isEnabled()) {
            senderRef.sendMessage(Message.raw("Races are currently disabled.").color("#ff6666"));
            return;
        }

        if (playerDataManager == null) {
            senderRef.sendMessage(Message.raw("Player data is unavailable right now.").color("#ff6666"));
            return;
        }

        PlayerData data = playerDataManager.get(senderRef.getUuid());
        if (data == null) {
            data = playerDataManager.loadOrCreate(senderRef.getUuid(), senderRef.getUsername());
        }

        if (data == null) {
            senderRef.sendMessage(Message.raw("Unable to load your player data.").color("#ff6666"));
            return;
        }

        RaceDefinition race = raceManager.getPlayerRace(data);
        String raceName = race != null ? race.getDisplayName() : data.getRaceId();
        if (raceName == null || raceName.isBlank()) {
            raceName = PlayerData.DEFAULT_RACE_ID;
        }

        senderRef.sendMessage(Message.join(
                Message.raw("[Races] ").color("#4fd7f7"),
                Message.raw("Your current race is ").color("#ffffff"),
                Message.raw(raceName).color("#ffc300")));
    }
}
