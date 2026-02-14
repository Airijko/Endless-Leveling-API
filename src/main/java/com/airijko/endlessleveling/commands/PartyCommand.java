package com.airijko.endlessleveling.commands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.managers.PartyManager;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

public class PartyCommand extends AbstractPlayerCommand {

    private final PartyManager partyManager;
    private final PlayerDataManager playerDataManager;

    public PartyCommand() {
        super("party", "Show your PartyPro party info");
        this.addAliases("eparty");
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.partyManager = plugin.getPartyManager();
        this.playerDataManager = plugin.getPlayerDataManager();
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

        if (partyManager == null || !partyManager.isAvailable()) {
            senderRef.sendMessage(Message.raw("PartyPro is not available on this server.").color("#ff6666"));
            return;
        }

        UUID senderUuid = senderRef.getUuid();
        if (!partyManager.isInParty(senderUuid)) {
            senderRef.sendMessage(Message.raw("You are not in a PartyPro party.").color("#ff9900"));
            return;
        }

        UUID leader = partyManager.getPartyLeader(senderUuid);
        Set<UUID> members = partyManager.getPartyMembers(senderUuid);
        String partyName = partyManager.getPartyName(senderUuid);

        StringBuilder sb = new StringBuilder();
        if (partyName != null && !partyName.isBlank()) {
            sb.append(partyName).append(" - ");
        }
        sb.append("Members: ");

        boolean first = true;
        for (UUID member : members) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(resolveName(member));
            if (leader != null && leader.equals(member)) {
                sb.append(" (Leader)");
            }
            first = false;
        }

        senderRef.sendMessage(Message.raw(sb.toString()).color("#4fd7f7"));
    }

    private String resolveName(@Nonnull UUID uuid) {
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
