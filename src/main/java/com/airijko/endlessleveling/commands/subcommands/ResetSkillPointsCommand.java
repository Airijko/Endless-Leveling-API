package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.airijko.endlessleveling.util.Lang;

import javax.annotation.Nonnull;

/**
 * /el resetskillpoints - resets the caller's allocated attributes back to
 * the baseline.
 */
public class ResetSkillPointsCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = HytalePermissions.fromCommand("endlessleveling.resetskillpoints");

    private final PlayerDataManager playerDataManager;
    private final SkillManager skillManager;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final OptionalArg<String> targetArg = this.withOptionalArg("player", "Target player name", ArgTypes.STRING);

    public ResetSkillPointsCommand() {
        super("resetskillpoints", "Reset your EndlessLeveling skill points to their default distribution");
        this.playerDataManager = EndlessLeveling.getInstance().getPlayerDataManager();
        this.skillManager = EndlessLeveling.getInstance().getSkillManager();
        this.addAliases("resetskills");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        if (playerDataManager == null || skillManager == null) {
            playerRef.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                    "command.reset_skillpoints.system_unavailable",
                    "Skill system is not initialised. Please contact an admin."))
                    .color("#ff6666"));
            return;
        }

        boolean hasTarget = targetArg.provided(commandContext);
        if (hasTarget) {
            CommandUtil.requirePermission(commandContext.sender(), PERMISSION_NODE);
        }

        PlayerData targetData;
        PlayerRef targetRef;
        String targetName;

        if (hasTarget) {
            targetName = targetArg.get(commandContext);
            targetData = playerDataManager.getByName(targetName);
            if (targetData == null) {
                playerRef.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                        "command.reset_skillpoints.player_not_found",
                        "Player not found: {0}", targetName))
                        .color("#ff6666"));
                return;
            }
            targetRef = Universe.get().getPlayer(targetData.getUuid());
        } else {
            targetData = playerDataManager.get(playerRef.getUuid());
            if (targetData == null) {
                playerRef.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                        "command.reset_skillpoints.no_data",
                        "No saved data found. Try rejoining."))
                        .color("#ff6666"));
                return;
            }
            targetRef = playerRef;
            targetName = playerRef.getUsername();
        }

        skillManager.resetSkillAttributes(targetData);
        applySkillModifiers(targetData, targetRef, ref, store);
        playerDataManager.save(targetData);

        playerRef.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                "command.reset_skillpoints.success_target",
                "Reset skill points for {0}.", targetName))
                .color("#4fd7f7"));
        if (targetRef != null && !targetRef.getUuid().equals(playerRef.getUuid())) {
            targetRef.sendMessage(Message.raw(Lang.tr(targetRef.getUuid(),
                    "command.reset_skillpoints.notify_target",
                    "An admin reset your skill points to the default layout."))
                    .color("#4fd7f7"));
        } else if (!hasTarget) {
            playerRef.sendMessage(Message.raw(Lang.tr(playerRef.getUuid(),
                    "command.reset_skillpoints.success_self",
                    "Your skill points have been reset to the default layout."))
                    .color("#4fd7f7"));
        }
    }

    private void applySkillModifiers(PlayerData targetData,
            PlayerRef targetRef,
            Ref<EntityStore> callerRef,
            Store<EntityStore> callerStore) {
        Ref<EntityStore> targetEntity;
        Store<EntityStore> targetStore;

        if (targetRef != null && targetRef.getReference() != null) {
            targetEntity = targetRef.getReference();
            targetStore = targetEntity.getStore();
        } else {
            // Fallback to the caller's context if the target is offline; modifiers will
            // retry.
            targetEntity = callerRef;
            targetStore = callerStore;
        }

        boolean applied = skillManager.applyAllSkillModifiers(targetEntity, targetStore, targetData);
        if (!applied) {
            LOGGER.atFine().log("ResetSkillPointsCommand: modifiers deferred for %s", targetData.getUuid());
            var retrySystem = EndlessLeveling.getInstance().getPlayerRaceStatSystem();
            if (retrySystem != null) {
                retrySystem.scheduleRetry(targetData.getUuid());
            }
        }
    }
}
