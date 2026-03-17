package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ResetAllCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = HytalePermissions.fromCommand("endlessleveling.resetall");

    private final PlayerDataManager playerDataManager;
    private final LevelingManager levelingManager;
    private final SkillManager skillManager;
    private final AugmentUnlockManager augmentUnlockManager;

    private final RequiredArg<String> targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);

    public ResetAllCommand() {
        super("resetall", "Reset a player's level to 1 and prestige to 0");

        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
        this.levelingManager = EndlessLeveling.getInstance().getLevelingManager();
        this.skillManager = EndlessLeveling.getInstance().getSkillManager();
        this.augmentUnlockManager = EndlessLeveling.getInstance().getAugmentUnlockManager();
        this.addAliases("fullreset");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        CommandUtil.requirePermission(commandContext.sender(), PERMISSION_NODE);

        String targetName = targetArg.get(commandContext);
        PlayerData targetData = playerDataManager.getByName(targetName);
        if (targetData == null) {
            senderRef.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }

        PlayerRef targetRef = Universe.get().getPlayer(targetData.getUuid());

        targetData.setPrestigeLevel(0);
        levelingManager.setPlayerLevel(targetData, 1);
        applySkillModifiers(targetData, targetRef);
        if (augmentUnlockManager != null) {
            augmentUnlockManager.trimExcessUnlocks(targetData);
        }

        senderRef.sendMessage(Message.raw("Reset level and prestige of " + targetName + " (level 1, prestige 0)."));

        if (targetRef != null) {
            targetRef.sendMessage(Message.raw("An admin reset your level to 1 and your prestige to 0."));
        }
    }

    private void applySkillModifiers(PlayerData targetData, PlayerRef targetRef) {
        if (targetRef == null) {
            return;
        }

        Ref<EntityStore> targetEntity = targetRef.getReference();
        if (targetEntity == null) {
            return;
        }

        Store<EntityStore> targetStore = targetEntity.getStore();

        boolean applied = skillManager.applyAllSkillModifiers(targetEntity, targetStore, targetData);
        if (!applied) {
            var retrySystem = EndlessLeveling.getInstance().getPlayerRaceStatSystem();
            if (retrySystem != null) {
                retrySystem.scheduleRetry(targetData.getUuid());
            }
        }
    }
}
