package com.airijko.endlessleveling.commands.races;

import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.airijko.endlessleveling.ui.RacePathsUIPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class RacePathsCommand extends AbstractPlayerCommand {

    private final RaceManager raceManager;
    private final OptionalArg<String> raceArg = this.withOptionalArg("race", "Race identifier", ArgTypes.STRING);

    public RacePathsCommand(RaceManager raceManager) {
        super("paths", "Open the EndlessLeveling Race Paths page");
        this.raceManager = raceManager;
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
        Player player = commandContext.senderAs(Player.class);
        if (player == null) {
            return;
        }

        String requestedRaceId = null;
        if (raceArg.provided(commandContext)) {
            String requestedInput = raceArg.get(commandContext);
            if (raceManager == null || !raceManager.isEnabled()) {
                playerRef.sendMessage(Message.raw("Races are currently disabled.").color("#ff6666"));
                return;
            }

            RaceDefinition definition = raceManager.findRaceByUserInput(requestedInput);
            if (definition == null) {
                playerRef.sendMessage(Message.raw("Unknown race: " + requestedInput).color("#ff6666"));
                return;
            }
            requestedRaceId = definition.getId();
        }

        String finalRequestedRaceId = requestedRaceId;
        CompletableFuture.runAsync(() -> player.getPageManager().openCustomPage(ref, store,
                new RacePathsUIPage(playerRef, CustomPageLifetime.CanDismiss, finalRequestedRaceId)), world);
    }
}
