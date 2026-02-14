package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.PartyManager;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class PartyUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final PartyManager partyManager;
    private final PlayerDataManager playerDataManager;

    public PartyUIPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        this.partyManager = EndlessLeveling.getInstance().getPartyManager();
        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Party/PartyPage.ui");
        NavUIHelper.applyNavVersion(ui);
        NavUIHelper.bindNavEvents(events);

        if (partyManager == null || !partyManager.isAvailable()) {
            ui.set("#PartyTitleLabel.Text", "Party (PartyPro required)");
            ui.set("#PartyStatus.Text", "PartyPro is not installed or is disabled.");
            ui.set("#CreatePartyButton.Visible", false);
            ui.set("#LeavePartyButton.Visible", false);
            ui.set("#DisbandPartyButton.Visible", false);
            return;
        }

        // PartyPro manages creation/invites; hide unused controls in this UI
        ui.set("#CreatePartyButton.Visible", false);
        ui.set("#LeavePartyButton.Visible", false);
        ui.set("#DisbandPartyButton.Visible", false);

        updatePartyStatus(ui);

        // Build initial member list
        buildMemberList(ui);
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

            if (partyManager == null || !partyManager.isAvailable()) {
                return;
            }

            if (data.action.startsWith("party:")) {
                handlePartyAction(data.action);
                rebuild();
            }
        }
    }

    private void handlePartyAction(@Nonnull String action) {
        playerRef.sendMessage(Message.raw(
                "Party management is handled by PartyPro. Use PartyPro commands to create, invite, or leave parties.")
                .color("#ff9900"));
    }

    private void updatePartyStatus(@Nonnull UICommandBuilder ui) {
        PlayerRef ref = playerRef;

        String title = "Party";
        ui.set("#PartyTitleLabel.Text", title);

        var leaderUuid = partyManager.getPartyLeader(ref.getUuid());
        if (leaderUuid == null) {
            ui.set("#PartyStatus.Text", "You are not in a PartyPro party.");
            return;
        }

        String leaderName = resolveName(leaderUuid);
        String partyName = partyManager.getPartyName(ref.getUuid());
        int memberCount = partyManager.getPartyMembers(ref.getUuid()).size();

        StringBuilder status = new StringBuilder();
        if (partyName != null && !partyName.isBlank()) {
            status.append(partyName).append(" - ");
        }
        status.append("Leader: ").append(leaderName).append(" (" + memberCount + " players)");
        ui.set("#PartyStatus.Text", status.toString());
    }

    private void buildMemberList(@Nonnull UICommandBuilder ui) {
        ui.clear("#MemberCards");

        java.util.UUID leaderUuid = partyManager != null ? partyManager.getPartyLeader(playerRef.getUuid()) : null;
        if (leaderUuid == null) {
            return;
        }

        java.util.Set<java.util.UUID> memberIds = partyManager.getPartyMembers(playerRef.getUuid());
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }

        int index = 0;
        for (java.util.UUID memberId : memberIds) {
            ui.append("#MemberCards", "Pages/Party/MembersRow.ui");
            String base = "#MemberCards[" + index + "]";

            String displayName;
            PlayerRef memberRef = Universe.get().getPlayer(memberId);
            if (memberRef != null && memberRef.getUsername() != null && !memberRef.getUsername().isEmpty()) {
                displayName = memberRef.getUsername();
            } else {
                displayName = resolveName(memberId);
            }

            if (leaderUuid.equals(memberId)) {
                displayName = displayName + " (Leader)";
            }
            ui.set(base + " #MemberPlayerName.Text", displayName);
            index++;
        }
    }

    private String resolveName(@Nonnull java.util.UUID uuid) {
        PlayerData data = playerDataManager.get(uuid);
        if (data != null && data.getPlayerName() != null) {
            return data.getPlayerName();
        }
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null && ref.getUsername() != null) {
            return ref.getUsername();
        }
        return uuid.toString();
    }
}
