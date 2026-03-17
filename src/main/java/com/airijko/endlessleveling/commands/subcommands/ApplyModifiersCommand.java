package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ApplyModifiersCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = HytalePermissions.fromCommand("endlessleveling.applymodifiers");

    private final PlayerDataManager playerDataManager;
    private final SkillManager skillManager;
    private final ConfigManager configManager;

    public ApplyModifiersCommand() {
        super("applymodifiers", "Apply health modifier for testing");

        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
        this.skillManager = EndlessLeveling.getInstance().getSkillManager();
        this.configManager = EndlessLeveling.getInstance().getConfigManager();
    }

    @Override
    protected void execute(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        CommandUtil.requirePermission(commandContext.sender(), PERMISSION_NODE);

        if (playerDataManager == null) {
            senderRef.sendMessage(Message.raw("PlayerDataManager is null."));
            System.out.println("[ApplyModifiersCommand] PlayerDataManager is null");
            return;
        }
        if (skillManager == null) {
            senderRef.sendMessage(Message.raw("SkillManager is null."));
            System.out.println("[ApplyModifiersCommand] SkillManager is null");
            return;
        }
        if (configManager == null) {
            senderRef.sendMessage(Message.raw("ConfigManager is null."));
            System.out.println("[ApplyModifiersCommand] ConfigManager is null");
            return;
        }

        // Use the existing get(UUID) method on PlayerDataManager
        PlayerData playerData = playerDataManager.get(senderRef.getUuid());
        if (playerData == null) {
            senderRef.sendMessage(Message.raw("Player data not found."));
            return;
        }

        // Apply the health modifier using SkillManager
        boolean applied = skillManager.applyAllSkillModifiers(ref, store, playerData);
        if (!applied) {
            var retrySystem = EndlessLeveling.getInstance().getPlayerRaceStatSystem();
            if (retrySystem != null) {
                retrySystem.scheduleRetry(playerData.getUuid());
            }
            senderRef.sendMessage(Message.raw("Modifiers scheduled for retry."));
        } else {
            senderRef.sendMessage(Message.raw("Applied health modifier using SkillManager."));
        }
    }
}
