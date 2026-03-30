package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Admin command to force-select one or more augments for quick testing.
 * Replaces all existing selections with the chosen augments on their tiers.
 */
public class AugmentTestCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = HytalePermissions.fromCommand("endlessleveling.augments.test");

    private final AugmentManager augmentManager;
    private final PlayerDataManager playerDataManager;
    private final OptionalArg<String> targetArg = this.withOptionalArg("player", "Target player name", ArgTypes.STRING);
    private final RequiredArg<List<String>> augmentArg = this.withListRequiredArg("augment", "Comma-separated or space-separated augment id(s) (case-insensitive)",
            ArgTypes.STRING);

    public AugmentTestCommand() {
        super("augmenttest", "Force select an augment for testing");
        this.addAliases("testaugment", "augmentdev", "augtest", "testaug");
        this.setAllowsExtraArguments(true);
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.augmentManager = plugin != null ? plugin.getAugmentManager() : null;
        this.playerDataManager = plugin != null ? plugin.getPlayerDataManager() : null;
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        CommandUtil.requirePermission(commandContext.sender(), PERMISSION_NODE);

        if (augmentManager == null || playerDataManager == null) {
            senderRef.sendMessage(Message.raw("Augment system not initialised.").color("#ff6666"));
            return;
        }

        List<String> augmentInputs = sanitizeAugmentInputs(commandContext, augmentArg.get(commandContext));
        if (augmentInputs.isEmpty()) {
            senderRef.sendMessage(Message.raw("Specify at least one augment id.").color("#ff6666"));
            senderRef.sendMessage(Message
                .raw("Examples: /el augtest raid_boss,goliath,tank_engine or /el augtest raid_boss goliath tank_engine")
                .color("#4fd7f7"));
            return;
        }

        List<AugmentDefinition> requestedAugments = new ArrayList<>();
        List<String> unknownAugments = new ArrayList<>();
        for (String augmentInput : augmentInputs) {
            AugmentDefinition definition = resolveAugment(augmentInput);
            if (definition == null) {
                unknownAugments.add(augmentInput);
                continue;
            }
            requestedAugments.add(definition);
        }

        if (!unknownAugments.isEmpty()) {
            senderRef.sendMessage(Message.raw("Unknown augment(s): " + String.join(", ", unknownAugments)).color("#ff6666"));
            senderRef.sendMessage(Message
                    .raw("Try one of: " + String.join(", ", sampleIds(augmentManager.getAugments()))).color("#4fd7f7"));
            return;
        }

        if (requestedAugments.isEmpty()) {
            senderRef.sendMessage(Message.raw("No valid augments were provided.").color("#ff6666"));
            return;
        }

        PlayerData targetData;
        PlayerRef targetRef;
        String targetName;
        if (targetArg.provided(commandContext)) {
            targetName = targetArg.get(commandContext);
            targetData = playerDataManager.getByName(targetName);
            if (targetData == null) {
                senderRef.sendMessage(Message.raw("Player not found: " + targetName).color("#ff6666"));
                return;
            }
            targetRef = Universe.get().getPlayer(targetData.getUuid());
            if (targetRef == null) {
                senderRef.sendMessage(Message.raw("Player is not online: " + targetName).color("#ff9f43"));
            }
        } else {
            targetData = playerDataManager.get(senderRef.getUuid());
            if (targetData == null) {
                senderRef.sendMessage(Message.raw("No saved data found. Try rejoining.").color("#ff6666"));
                return;
            }
            targetRef = senderRef;
            targetName = senderRef.getUsername();
        }

        Map<String, AugmentDefinition> augmentsByTier = new LinkedHashMap<>();
        List<String> replacedTiers = new ArrayList<>();
        for (AugmentDefinition definition : requestedAugments) {
            if (definition == null || definition.getTier() == null || definition.getId() == null) {
                continue;
            }

            String tierKey = definition.getTier().name();
            AugmentDefinition previous = augmentsByTier.put(tierKey, definition);
            if (previous != null) {
                replacedTiers.add(tierKey);
            }
        }

        if (augmentsByTier.isEmpty()) {
            senderRef.sendMessage(Message.raw("No valid augment tiers could be resolved.").color("#ff6666"));
            return;
        }

        // Replace existing augment selections with the requested augment set.
        targetData.clearSelectedAugments();
        targetData.clearAugmentOffers();
        List<String> appliedSummaries = new ArrayList<>();
        for (Map.Entry<String, AugmentDefinition> entry : augmentsByTier.entrySet()) {
            String tierKey = entry.getKey();
            AugmentDefinition definition = entry.getValue();
            targetData.setSelectedAugmentForTier(tierKey, definition.getId());
            targetData.setAugmentOffersForTier(tierKey, List.of(definition.getId()));
            appliedSummaries.add(definition.getName() + " (" + tierKey + ")");
        }
        playerDataManager.save(targetData);

        senderRef.sendMessage(
                Message.raw(String.format("Applied %d augment(s) to %s: %s",
                        augmentsByTier.size(),
                        targetName,
                        String.join(", ", appliedSummaries)))
                        .color("#6cff78"));
        if (!replacedTiers.isEmpty()) {
            senderRef.sendMessage(Message.raw(
                    "Multiple augments were provided for the same tier; kept the last one for: "
                            + String.join(", ", distinctSorted(replacedTiers)))
                    .color("#ff9f43"));
        }
        if (targetRef != null && !targetRef.getUuid().equals(senderRef.getUuid())) {
            targetRef.sendMessage(Message
                    .raw(String.format("An admin applied %d test augment(s): %s",
                            augmentsByTier.size(),
                            String.join(", ", appliedSummaries)))
                    .color("#6cff78"));
        }
    }

    private List<String> sanitizeAugmentInputs(CommandContext commandContext, List<String> parsedInputs) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addNormalizedAugmentValues(merged, parsedInputs);
        addNormalizedAugmentValues(merged, extractTrailingAugmentTokens(commandContext));
        if (merged.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(merged);
    }

    private void addNormalizedAugmentValues(Set<String> destination, List<String> rawInputs) {
        if (destination == null || rawInputs == null || rawInputs.isEmpty()) {
            return;
        }

        for (String value : rawInputs) {
            if (value == null) {
                continue;
            }

            String[] splitValues = value.split(",");
            for (String splitValue : splitValues) {
                if (splitValue == null) {
                    continue;
                }
                String normalized = splitValue.trim();
                if (!normalized.isBlank()) {
                    destination.add(normalized);
                }
            }
        }
    }

    private List<String> extractTrailingAugmentTokens(CommandContext commandContext) {
        if (commandContext == null || commandContext.getInputString() == null || commandContext.getInputString().isBlank()) {
            return List.of();
        }

        String[] tokens = commandContext.getInputString().trim().split("\\s+");
        if (tokens.length == 0) {
            return List.of();
        }

        String commandPath = commandContext.getCalledCommand() != null
                ? commandContext.getCalledCommand().getFullyQualifiedName()
                : null;
        int commandTokenCount = 1;
        if (commandPath != null && !commandPath.isBlank()) {
            commandTokenCount = Math.max(1, commandPath.trim().split("\\s+").length);
        }

        if (tokens.length <= commandTokenCount) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (int index = commandTokenCount; index < tokens.length; index++) {
            String token = tokens[index];
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("-")) {
                break;
            }
            values.add(token);
        }
        return values;
    }

    private AugmentDefinition resolveAugment(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, AugmentDefinition> entry : augmentManager.getAugments().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
            AugmentDefinition def = entry.getValue();
            if (def != null && def.getName() != null && def.getName().equalsIgnoreCase(normalized)) {
                return def;
            }
        }
        return null;
    }

    private List<String> sampleIds(Map<String, AugmentDefinition> augments) {
        if (augments == null || augments.isEmpty()) {
            return List.of("none loaded");
        }
        List<String> ids = new ArrayList<>(augments.keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        if (ids.size() > 10) {
            ids = ids.subList(0, 10);
            ids.add("...");
        }
        return ids;
    }

    private List<String> distinctSorted(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>(input);
        List<String> values = new ArrayList<>(unique);
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(values);
    }
}