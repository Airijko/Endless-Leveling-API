package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.enums.themes.AttributeTheme;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class SpendSkillPointsCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> attributeArg = this.withRequiredArg("attribute", "Skill attribute", ArgTypes.STRING);
    private final RequiredArg<Integer> amountArg = this.withRequiredArg("amount", "Amount of skill points to spend", ArgTypes.INTEGER);

    public SpendSkillPointsCommand() {
        super("spendskillpoints", "Spend skill points on a specific attribute");
        this.addAliases("spendskill", "skillspend", "spendskills");
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
            String rawAttribute = attributeArg.get(commandContext);
            Integer requestedAmount = amountArg.get(commandContext);

            SkillAttributeType attributeType = SkillAttributeType.fromConfigKey(rawAttribute);
            if (attributeType == null) {
                playerRef.sendMessage(Message.raw("Invalid skill attribute: " + rawAttribute).color("#ff6666"));
                return;
            }

            if (requestedAmount == null || requestedAmount <= 0) {
                playerRef.sendMessage(Message.raw("Amount must be greater than 0.").color("#ff6666"));
                return;
            }

            EndlessLeveling plugin = EndlessLeveling.getInstance();
            PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
            SkillManager skillManager = plugin.getSkillManager();
            if (playerDataManager == null || skillManager == null) {
                playerRef.sendMessage(Message.raw("Skill system is unavailable right now.").color("#ff6666"));
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

            SkillManager.SkillSpendResult result = skillManager.spendSkillPointsOnAttribute(playerData,
                    attributeType,
                    requestedAmount);
            if (!result.applied()) {
                playerRef.sendMessage(Message.raw(resolveBlockedMessage(result, attributeType)).color("#ff6666"));
                return;
            }

            playerDataManager.save(playerData);
            boolean applied = skillManager.applyAllSkillModifiers(ref, store, playerData);
            if (!applied) {
                var retrySystem = plugin.getPlayerRaceStatSystem();
                if (retrySystem != null) {
                    retrySystem.scheduleRetry(playerData.getUuid());
                }
            }

            String attributeName = resolveAttributeName(attributeType);
            if (result.spentPoints() < requestedAmount) {
                playerRef.sendMessage(Message.raw(
                        "Spent " + result.spentPoints() + " point(s) on " + attributeName
                                + " (requested " + requestedAmount + ").")
                        .color("#4fd7f7"));
                return;
            }

            playerRef.sendMessage(Message.raw("Spent " + result.spentPoints() + " point(s) on " + attributeName + ".")
                    .color("#4fd7f7"));
        }, world);
    }

    private String resolveBlockedMessage(@Nonnull SkillManager.SkillSpendResult result,
            @Nonnull SkillAttributeType attributeType) {
        String attributeName = resolveAttributeName(attributeType);
        return switch (result.blockReason()) {
            case "not_enough_points" -> "You do not have any spendable skill points right now.";
            case "crit_locked" -> "This build cannot invest in " + attributeName + ".";
            case "effective_cap_reached" -> attributeName + " is already at its effective cap.";
            default -> "Unable to spend skill points on " + attributeName + ".";
        };
    }

    private String resolveAttributeName(@Nonnull SkillAttributeType attributeType) {
        AttributeTheme theme = AttributeTheme.fromType(attributeType);
        return theme != null ? theme.labelFallback() : attributeType.name();
    }
}