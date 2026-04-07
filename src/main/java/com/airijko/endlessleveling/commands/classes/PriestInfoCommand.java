package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * /priest info — show the priest's church placement status.
 */
public class PriestInfoCommand extends AbstractPlayerCommand {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;

    public PriestInfoCommand(ClassManager classManager, PlayerDataManager playerDataManager) {
        super("info", "Check your church placement status");
        this.classManager = classManager;
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
        if (classManager == null || !classManager.isEnabled()) {
            senderRef.sendMessage(Message.raw("Classes are currently disabled.").color("#ff6666"));
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

        if (!PriestClassCheck.isPriest(classManager, data)) {
            senderRef.sendMessage(Message.raw("Only Priests may use this command.").color("#ff6666"));
            return;
        }

        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            senderRef.sendMessage(Message.raw("Church system is not initialized.").color("#ff6666"));
            return;
        }

        if (!manager.hasChurch(senderRef.getUuid())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("[Priest] ").color("#ffd700"),
                    Message.raw("You have no church placed.").color("#ffffff")));
            senderRef.sendMessage(Message.join(
                    Message.raw("  Use ").color("#9fb6d3"),
                    Message.raw("/priest setup church").color("#ffc300"),
                    Message.raw(" to place one.").color("#9fb6d3")));
            return;
        }

        ChurchManager.ChurchPlacement placement = manager.getChurch(senderRef.getUuid());
        String placedTime = TIME_FMT.format(Instant.ofEpochMilli(placement.placedAtMs));

        senderRef.sendMessage(Message.join(
                Message.raw("[Priest] ").color("#ffd700"),
                Message.raw("Church Status").color("#ffffff")));
        senderRef.sendMessage(Message.join(
                Message.raw("  World: ").color("#9fb6d3"),
                Message.raw(placement.worldName).color("#4fd7f7")));
        senderRef.sendMessage(Message.join(
                Message.raw("  Position: ").color("#9fb6d3"),
                Message.raw("(" + placement.posX + ", " + placement.posY + ", " + placement.posZ + ")").color("#4fd7f7")));
        senderRef.sendMessage(Message.join(
                Message.raw("  Placed: ").color("#9fb6d3"),
                Message.raw(placedTime).color("#4fd7f7")));

        if (manager.isOnCooldown(senderRef.getUuid())) {
            senderRef.sendMessage(Message.join(
                    Message.raw("  Cooldown: ").color("#9fb6d3"),
                    Message.raw(manager.getRemainingCooldownSeconds(senderRef.getUuid()) + "s remaining").color("#ff6666")));
        }
    }
}
