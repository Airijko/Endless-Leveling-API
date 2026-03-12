package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.airijko.endlessleveling.ui.PlayerHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class ToggleHudCommand extends AbstractPlayerCommand {

    public ToggleHudCommand() {
        super("togglehud", "Toggle the EndlessLeveling HUD on or off");
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

        CompletableFuture.runAsync(() -> {
            PlayerDataManager playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
            if (playerDataManager == null) {
                playerRef.sendMessage(Message.raw("Player data is unavailable right now.").color("#ff6666"));
                return;
            }

            PlayerData playerData = playerDataManager.get(playerRef.getUuid());
            if (playerData == null) {
                playerData = playerDataManager.loadOrCreate(playerRef.getUuid(), playerRef.getUsername());
            }
            if (playerData == null) {
                playerRef.sendMessage(Message.raw("Unable to load your player data.").color("#ff6666"));
                return;
            }

            playerData.setPlayerHudEnabled(!playerData.isPlayerHudEnabled());
            playerDataManager.save(playerData);
            PlayerHud.openPreferred(player, playerRef);

            String text = playerData.isPlayerHudEnabled() ? "HUD enabled." : "HUD hidden.";
            playerRef.sendMessage(Message.raw(text).color("#4fd7f7"));
        }, world);
    }
}