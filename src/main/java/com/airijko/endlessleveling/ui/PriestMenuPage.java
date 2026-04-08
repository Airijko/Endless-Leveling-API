package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.commands.classes.ChurchManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Priest church menu GUI opened by /priest menu.
 * Displays current church status (location / world / placement time / cooldown)
 * and exposes Place, Remove, and Refresh actions. Mirrors the visual style of
 * {@link BardMusicPage} so the two class menus stay consistent.
 */
public class PriestMenuPage extends InteractiveCustomUIPage<PriestMenuPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final String ACTION_PLACE = "priest_menu:place";
    private static final String ACTION_REMOVE = "priest_menu:remove";
    private static final String ACTION_REFRESH = "priest_menu:refresh";

    public PriestMenuPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/Priest/PriestMenuPage.ui");

        applyStatus(ui, playerRef.getUuid());

        events.addEventBinding(Activating, "#PriestPlaceButton", of("Action", ACTION_PLACE), false);
        events.addEventBinding(Activating, "#PriestRemoveButton", of("Action", ACTION_REMOVE), false);
        events.addEventBinding(Activating, "#PriestRefreshButton", of("Action", ACTION_REFRESH), false);
    }

    private void applyStatus(@Nonnull SafeUICommandBuilder ui, @Nonnull UUID uuid) {
        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            ui.set("#PriestStatusLabel.Text", "Church system unavailable");
            ui.set("#PriestStatusLabel.Style.TextColor", "#ff6666");
            ui.set("#PriestStatusLine1.Text", "");
            ui.set("#PriestStatusLine2.Text", "");
            ui.set("#PriestStatusLine3.Text", "");
            ui.set("#PriestCooldownLabel.Text", "");
            return;
        }

        if (!manager.hasChurch(uuid)) {
            ui.set("#PriestStatusLabel.Text", "No church placed");
            ui.set("#PriestStatusLabel.Style.TextColor", "#ffffff");
            ui.set("#PriestStatusLine1.Text", "Use the PLACE action to build one at your location.");
            ui.set("#PriestStatusLine2.Text", "");
            ui.set("#PriestStatusLine3.Text", "");
        } else {
            ChurchManager.ChurchPlacement placement = manager.getChurch(uuid);
            ui.set("#PriestStatusLabel.Text", "Church Active");
            ui.set("#PriestStatusLabel.Style.TextColor", "#66ff66");
            ui.set("#PriestStatusLine1.Text",
                    "World: " + placement.worldName);
            ui.set("#PriestStatusLine2.Text",
                    "Position: (" + placement.posX + ", " + placement.posY + ", " + placement.posZ + ")");
            ui.set("#PriestStatusLine3.Text",
                    "Placed: " + TIME_FMT.format(Instant.ofEpochMilli(placement.placedAtMs)));
        }

        if (manager.isOnCooldown(uuid)) {
            ui.set("#PriestCooldownLabel.Text",
                    "Cooldown: " + manager.getRemainingCooldownSeconds(uuid) + "s remaining");
        } else {
            ui.set("#PriestCooldownLabel.Text", "");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        switch (data.action) {
            case ACTION_PLACE -> handlePlace(ref, store);
            case ACTION_REMOVE -> handleRemove(ref, store);
            case ACTION_REFRESH -> {
                // fall through to rebuild
            }
            default -> {
                return;
            }
        }

        rebuild();
    }

    private void handlePlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            sendError("Church system is not initialized.");
            return;
        }
        if (!manager.isPrefabAvailable()) {
            sendError("Church prefab file is not available on this server.");
            return;
        }
        if (manager.hasChurch(playerRef.getUuid())) {
            sendInfo("You already have a church placed. Use REMOVE first.");
            return;
        }
        if (manager.isOnCooldown(playerRef.getUuid())) {
            sendError("Please wait " + manager.getRemainingCooldownSeconds(playerRef.getUuid())
                    + "s before placing a church.");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendError("Could not resolve player entity.");
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            sendError("Could not determine your world.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            sendError("Could not determine your position.");
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3i blockPos = new Vector3i(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z));

        sendInfo("Placing church...");

        try {
            world.execute(() -> {
                String error = manager.placeChurch(playerRef.getUuid(), world, blockPos, store);
                if (error != null) {
                    sendError(error);
                } else {
                    playerRef.sendMessage(Message.join(
                            Message.raw("[Priest] ").color("#ffd700"),
                            Message.raw("Your church has been placed at ").color("#ffffff"),
                            Message.raw("(" + blockPos.x + ", " + blockPos.y + ", " + blockPos.z + ")").color("#4fd7f7"),
                            Message.raw(".").color("#ffffff")));
                }
            });
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to enqueue church placement for %s",
                    playerRef.getUsername());
            sendError("Failed to schedule church placement.");
        }
    }

    private void handleRemove(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ChurchManager manager = ChurchManager.get();
        if (manager == null) {
            sendError("Church system is not initialized.");
            return;
        }
        if (!manager.hasChurch(playerRef.getUuid())) {
            sendInfo("You don't have a church placed.");
            return;
        }
        if (manager.isOnCooldown(playerRef.getUuid())) {
            sendError("Please wait " + manager.getRemainingCooldownSeconds(playerRef.getUuid())
                    + "s before modifying your church.");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendError("Could not resolve player entity.");
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            sendError("Could not determine your world.");
            return;
        }

        ChurchManager.ChurchPlacement placement = manager.getChurch(playerRef.getUuid());
        if (placement != null && !placement.worldName.equals(world.getName())) {
            sendInfo("Your church is in world '" + placement.worldName
                    + "'. You must be in that world to remove it.");
            return;
        }

        sendInfo("Removing church...");

        try {
            world.execute(() -> {
                String error = manager.undoChurch(playerRef.getUuid(), world);
                if (error != null) {
                    sendError(error);
                } else {
                    playerRef.sendMessage(Message.join(
                            Message.raw("[Priest] ").color("#ffd700"),
                            Message.raw("Your church has been removed and the terrain restored.")
                                    .color("#ffffff")));
                }
            });
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to enqueue church removal for %s",
                    playerRef.getUsername());
            sendError("Failed to schedule church removal.");
        }
    }

    private void sendInfo(@Nonnull String text) {
        playerRef.sendMessage(Message.join(
                Message.raw("[Priest] ").color("#ffd700"),
                Message.raw(text).color("#ffffff")));
    }

    private void sendError(@Nonnull String text) {
        playerRef.sendMessage(Message.join(
                Message.raw("[Priest] ").color("#ff6666"),
                Message.raw(text).color("#ffffff")));
    }

    public static final class Data {
        public String action;

        public Data() {
            this.action = "";
        }

        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (d, v) -> d.action = v,
                        d -> d.action)
                .add()
                .build();
    }
}
