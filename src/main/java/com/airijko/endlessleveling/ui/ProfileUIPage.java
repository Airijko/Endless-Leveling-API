package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.data.PlayerData.PlayerProfile;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

public class ProfileUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final PlayerDataManager playerDataManager;

    public ProfileUIPage(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Profile/ProfilePage.ui");
        NavUIHelper.bindNavEvents(events);

        PlayerData playerData = resolvePlayerData();
        if (playerData == null) {
            ui.set("#ProfilesSummary.Text", "Player data unavailable.");
            ui.set("#EmptyStateLabel.Text", "Unable to load profiles right now.");
            return;
        }

        events.addEventBinding(Activating, "#NewProfileButton", of("Action", "profile:new"), false);

        updateSummary(ui, playerData);
        buildProfileList(ui, events, playerData);
    }

    private PlayerData resolvePlayerData() {
        if (playerDataManager == null) {
            LOGGER.atSevere().log("ProfileUIPage: PlayerDataManager is not available");
            return null;
        }

        PlayerData data = playerDataManager.get(playerRef.getUuid());
        if (data == null) {
            LOGGER.atWarning().log("ProfileUIPage: PlayerData missing for %s", playerRef.getUuid());
            return null;
        }
        return data;
    }

    private void updateSummary(@Nonnull UICommandBuilder ui, @Nonnull PlayerData data) {
        ui.set("#ProfilesSummary.Text",
                "Profiles " + data.getProfileCount() + "/" + PlayerData.MAX_PROFILES);
    }

    private void buildProfileList(@Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull PlayerData data) {
        ui.clear("#ProfileCards");

        List<Map.Entry<Integer, PlayerProfile>> profiles = data.getProfiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        if (profiles.isEmpty()) {
            ui.set("#EmptyStateLabel.Text", "No profiles yet. Use NEW PROFILE to create one.");
            return;
        }

        ui.set("#EmptyStateLabel.Text", "");

        int index = 0;
        for (Map.Entry<Integer, PlayerProfile> entry : profiles) {
            int slot = entry.getKey();
            PlayerProfile profile = entry.getValue();
            boolean active = data.isProfileActive(slot);
            boolean canDelete = data.getProfileCount() > 1 && !active;

            ui.append("#ProfileCards", "Pages/Profile/ProfileRow.ui");
            String base = "#ProfileCards[" + index + "]";

            ui.set(base + " #SlotLabel.Text", "Slot " + slot);
            ui.set(base + " #ProfileName.Text", profile.getName());
            ui.set(base + " #LevelValue.Text", "Level " + profile.getLevel());
            ui.set(base + " #XpValue.Text", formatNumber(profile.getXp()) + " XP");
            ui.set(base + " #RaceValue.Text", profile.getRaceId());
            ui.set(base + " #StatusBadge.Text", active ? "ACTIVE" : "");

            ui.set(base + " #SelectButton.Text", active ? "ACTIVE" : "SELECT");
            if (!active) {
                events.addEventBinding(Activating, base + " #SelectButton",
                        of("Action", "profile:select:" + slot), false);
            }
            if (canDelete) {
                events.addEventBinding(Activating, base + " #DeleteButton",
                        of("Action", "profile:delete:" + slot), false);
            }

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action != null && !data.action.isEmpty()) {
            if (NavUIHelper.handleNavAction(data.action, ref, store, playerRef)) {
                return;
            }
        }

        if (data.action == null || data.action.isEmpty() || !data.action.startsWith("profile:")) {
            return;
        }

        PlayerData playerData = resolvePlayerData();
        if (playerData == null) {
            playerRef.sendMessage(Message.raw("Unable to load your profiles right now.").color("#ff0000"));
            return;
        }

        ProfileActionOutcome outcome = handleProfileAction(data.action, playerData);
        if (outcome.requiresSave() && playerDataManager != null) {
            playerDataManager.save(playerData);
        }
        if (outcome.requiresRebuild()) {
            rebuild();
        }
    }

    private ProfileActionOutcome handleProfileAction(@Nonnull String action,
            @Nonnull PlayerData playerData) {
        String payload = action.substring("profile:".length());

        try {
            if ("new".equalsIgnoreCase(payload)) {
                return handleNewProfile(playerData);
            }
            if (payload.startsWith("select:")) {
                return handleSelectProfile(playerData, payload);
            }
            if (payload.startsWith("delete:")) {
                return handleDeleteRequest(playerData, payload);
            }
        } catch (Exception ex) {
            LOGGER.atSevere().withCause(ex).log("ProfileUIPage: error handling action %s", action);
            playerRef.sendMessage(Message.raw("Something went wrong handling that request.").color("#ff0000"));
        }

        return new ProfileActionOutcome(false, false);
    }

    private ProfileActionOutcome handleNewProfile(@Nonnull PlayerData playerData) {
        if (playerData.getProfileCount() >= PlayerData.MAX_PROFILES) {
            playerRef.sendMessage(Message.raw("All profile slots are already in use. Delete one first.")
                    .color("#ff9900"));
            return new ProfileActionOutcome(false, false);
        }

        int nextSlot = playerData.findNextAvailableProfileSlot();
        if (!PlayerData.isValidProfileIndex(nextSlot)) {
            playerRef.sendMessage(Message.raw("Unable to find an open slot right now.").color("#ff0000"));
            return new ProfileActionOutcome(false, false);
        }

        boolean created = playerData.createProfile(nextSlot, PlayerData.defaultProfileName(nextSlot), false, true);
        if (!created) {
            playerRef.sendMessage(Message.raw("Could not create that profile slot.").color("#ff0000"));
            return new ProfileActionOutcome(false, false);
        }

        playerRef.sendMessage(Message.raw("Created and activated profile slot " + nextSlot + ".")
                .color("#4fd7f7"));
        return new ProfileActionOutcome(true, true);
    }

    private ProfileActionOutcome handleSelectProfile(@Nonnull PlayerData playerData, @Nonnull String payload) {
        int slot = parseSlot(payload, "select:");
        if (!PlayerData.isValidProfileIndex(slot)) {
            playerRef.sendMessage(Message.raw("Profile slot must be between 1 and " + PlayerData.MAX_PROFILES + ".")
                    .color("#ff0000"));
            return new ProfileActionOutcome(false, false);
        }

        if (!playerData.hasProfile(slot)) {
            playerRef.sendMessage(Message.raw("Profile slot " + slot + " has not been created yet.")
                    .color("#ff9900"));
            return new ProfileActionOutcome(false, false);
        }

        if (playerData.isProfileActive(slot)) {
            playerRef.sendMessage(Message.raw("Profile slot " + slot + " is already active.").color("#4fd7f7"));
            return new ProfileActionOutcome(false, false);
        }

        PlayerData.ProfileSwitchResult result = playerData.switchProfile(slot);
        if (result == PlayerData.ProfileSwitchResult.SWITCHED_EXISTING) {
            playerRef.sendMessage(Message.raw(
                    "Switched to profile slot " + slot + " (" + playerData.getProfileName(slot) + ").")
                    .color("#00ff00"));
            return new ProfileActionOutcome(true, true);
        }

        playerRef.sendMessage(Message.raw("Unable to switch to that slot right now.").color("#ff0000"));
        return new ProfileActionOutcome(false, false);
    }

    private ProfileActionOutcome handleDeleteRequest(@Nonnull PlayerData playerData,
            @Nonnull String payload) {
        int slot = parseSlot(payload, "delete:");
        if (!PlayerData.isValidProfileIndex(slot)) {
            playerRef.sendMessage(Message.raw("Profile slot must be between 1 and " + PlayerData.MAX_PROFILES + ".")
                    .color("#ff0000"));
            return new ProfileActionOutcome(false, false);
        }
        if (!playerData.hasProfile(slot)) {
            playerRef.sendMessage(Message.raw("Profile slot " + slot + " is already empty.").color("#ff9900"));
            return new ProfileActionOutcome(false, false);
        }
        if (playerData.isProfileActive(slot)) {
            playerRef.sendMessage(Message.raw("Switch to a different profile before deleting slot " + slot + ".")
                    .color("#ff9900"));
            return new ProfileActionOutcome(false, false);
        }
        if (playerData.getProfileCount() <= 1) {
            playerRef.sendMessage(Message.raw("You must keep at least one profile slot.").color("#ff0000"));
            return new ProfileActionOutcome(false, false);
        }

        boolean deleted = playerData.deleteProfile(slot);
        if (!deleted) {
            playerRef.sendMessage(Message.raw("Could not delete that profile slot right now.").color("#ff0000"));
            return new ProfileActionOutcome(false, false);
        }

        playerRef.sendMessage(Message.raw("Deleted profile slot " + slot + ".").color("#4fd7f7"));
        return new ProfileActionOutcome(true, true);
    }

    private int parseSlot(@Nonnull String payload, @Nonnull String prefix) {
        try {
            return Integer.parseInt(payload.substring(prefix.length()));
        } catch (Exception ex) {
            LOGGER.atWarning().log("ProfileUIPage: invalid slot payload %s", payload);
            return -1;
        }
    }

    private String formatNumber(double value) {
        String formatted = String.format("%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    private record ProfileActionOutcome(boolean requiresSave, boolean requiresRebuild) {
    }
}
