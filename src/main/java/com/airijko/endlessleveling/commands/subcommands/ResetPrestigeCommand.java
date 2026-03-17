package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.ui.PlayerHud;
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

public class ResetPrestigeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = HytalePermissions.fromCommand("endlessleveling.resetprestige");

    private final PlayerDataManager playerDataManager;
    private final LevelingManager levelingManager;
    private final SkillManager skillManager;
    private final AugmentUnlockManager augmentUnlockManager;

    private final RequiredArg<String> targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);

    public ResetPrestigeCommand() {
        super("resetprestige", "Reset a player's prestige to 0 without resetting level unless capped");

        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
        this.levelingManager = EndlessLeveling.getInstance().getLevelingManager();
        this.skillManager = EndlessLeveling.getInstance().getSkillManager();
        this.augmentUnlockManager = EndlessLeveling.getInstance().getAugmentUnlockManager();
        this.addAliases("prestigereset");
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
        int previousPrestige = Math.max(0, targetData.getPrestigeLevel());
        int previousLevel = Math.max(1, targetData.getLevel());

        targetData.setPrestigeLevel(0);
        int prestigeZeroCap = levelingManager.getLevelCap(targetData);

        boolean clampedToCap = previousLevel > prestigeZeroCap;
        if (clampedToCap) {
            levelingManager.setPlayerLevel(targetData, prestigeZeroCap);
            applySkillModifiers(targetData, targetRef);
        } else {
            if (targetData.getLevel() >= prestigeZeroCap) {
                targetData.setXp(0.0D);
            }
            playerDataManager.save(targetData);
            PlayerHud.refreshHud(targetData.getUuid());
        }

        boolean removedPrestigeAugmentSlots = false;
        if (augmentUnlockManager != null) {
            removedPrestigeAugmentSlots = augmentUnlockManager.trimExcessUnlocks(targetData);
        }

        if (clampedToCap) {
            senderRef.sendMessage(Message.raw(
                    "Reset prestige of " + targetName + " from " + previousPrestige
                            + " to 0. Level exceeded cap and was clamped to " + prestigeZeroCap
                            + (removedPrestigeAugmentSlots ? ". Removed excess prestige augment slots." : ".")));
        } else {
            senderRef.sendMessage(Message.raw(
                    "Reset prestige of " + targetName + " from " + previousPrestige
                            + " to 0. Level remains " + previousLevel
                            + (removedPrestigeAugmentSlots ? ". Removed excess prestige augment slots." : ".")));
        }

        if (targetRef != null) {
            if (clampedToCap) {
                targetRef.sendMessage(Message.raw(
                        "An admin reset your prestige to 0. Your level was clamped to " + prestigeZeroCap
                                + (removedPrestigeAugmentSlots ? ". Extra prestige augment slots were removed."
                                        : ".")));
            } else {
                targetRef.sendMessage(Message.raw(
                        "An admin reset your prestige to 0."
                                + (removedPrestigeAugmentSlots ? " Extra prestige augment slots were removed." : "")));
            }
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
