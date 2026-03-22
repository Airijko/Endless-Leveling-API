package com.airijko.endlessleveling.commands.augments;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.augments.types.CommonAugment;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /el augments reroll &lt;augment_id&gt;
 *
 * <p>Un-selects a previously chosen augment and restores pending offers for
 * that tier slot so the player can pick again. Consumes one reroll token for
 * the tier unless the sender is an operator (operators bypass the check and
 * consume no token).
 */
public class AugmentRerollSelectedCommand extends AbstractPlayerCommand {

    private final PlayerDataManager playerDataManager;
    private final AugmentUnlockManager augmentUnlockManager;
    private final AugmentManager augmentManager;

    private final RequiredArg<String> augmentArg = this.withRequiredArg(
            "augment_id", "ID of a selected augment to reroll", ArgTypes.STRING);

    public AugmentRerollSelectedCommand() {
        super("reroll", "Un-select a chosen augment and receive new offers for that tier");
        this.addUsageVariant(new RerollListVariant());

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.playerDataManager = plugin != null ? plugin.getPlayerDataManager() : null;
        this.augmentUnlockManager = plugin != null ? plugin.getAugmentUnlockManager() : null;
        this.augmentManager = plugin != null ? plugin.getAugmentManager() : null;
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
        executeInternal(commandContext, playerRef, augmentArg.get(commandContext));
    }

    private void executeInternal(@Nonnull CommandContext commandContext,
            @Nonnull PlayerRef playerRef,
            String explicitTargetAugmentId) {
        if (playerDataManager == null || augmentUnlockManager == null) {
            playerRef.sendMessage(Message.raw("Augment system is not initialised.").color("#ff6666"));
            return;
        }

        PlayerData playerData = playerDataManager.get(playerRef.getUuid());
        if (playerData == null) {
            playerRef.sendMessage(Message.raw("No saved data found. Try rejoining.").color("#ff6666"));
            return;
        }

        Map<String, String> selected = playerData.getSelectedAugmentsSnapshot();
        if (selected.isEmpty()) {
            playerRef.sendMessage(Message.raw("You have no selected augments to reroll.").color("#ff9900"));
            return;
        }

        String targetAugmentId = explicitTargetAugmentId == null ? "" : explicitTargetAugmentId.trim();
        if (targetAugmentId.isBlank()) {
            playerRef.sendMessage(Message.raw(
                "Usage: /el augments reroll <augment_id>\nSelected augments:").color("#4fd7f7"));
            sendSelectedAugmentList(playerRef, selected);
            return;
        }

        // Find the slot key whose value matches the supplied augment id
        String foundKey = null;
        for (Map.Entry<String, String> entry : selected.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(targetAugmentId)) {
                foundKey = entry.getKey();
                break;
            }
        }

        if (foundKey == null) {
            playerRef.sendMessage(Message.raw(
                    "No selected augment found with id: " + targetAugmentId
                    + ". Selected augments:").color("#ff9900"));
            sendSelectedAugmentList(playerRef, selected);
            return;
        }

        // Derive the tier from the slot key (e.g. "MYTHIC" or "MYTHIC#2" → MYTHIC)
        PassiveTier tier = resolveTier(foundKey, targetAugmentId);
        if (tier == null) {
            playerRef.sendMessage(
                    Message.raw("Could not determine augment tier for slot: " + foundKey).color("#ff6666"));
            return;
        }

        boolean isOperator = OperatorHelper.isOperator(playerRef);

        if (!isOperator) {
            int remaining = augmentUnlockManager.getRemainingRerolls(playerData, tier);
            if (remaining <= 0) {
                playerRef.sendMessage(Message.raw(
                        "No " + tier.name() + " rerolls remaining. Earn more through prestige.").color("#ff9900"));
                return;
            }
        }

        String tierKey = tier.name();
        int offersBefore = playerData.getAugmentOffersForTier(tierKey).size();

        // Remove the selected augment so ensureUnlocks can repopulate the slot
        playerData.setSelectedAugmentForTier(foundKey, null);

        // Consume one reroll token (operators are exempt)
        if (!isOperator) {
            playerData.incrementAugmentRerollsUsedForTier(tierKey);
        }

        // Roll a fresh offer set for the freed slot.
        augmentUnlockManager.ensureUnlocks(playerData);

        // Fallback: if no offers were added for that tier, force-generate a fresh
        // bundle for that tier.
        int offersAfter = playerData.getAugmentOffersForTier(tierKey).size();
        if (offersAfter <= offersBefore) {
            boolean forced = augmentUnlockManager.forceOfferBundleForTier(playerData, tier);
            if (!forced) {
                playerDataManager.save(playerData);
                playerRef.sendMessage(Message.raw(
                        "Rerolled " + targetAugmentId + " (" + tier.name() + "), but no new offers could be generated "
                                + "for this tier right now. This usually means your current class/tier pool has no valid options.")
                        .color("#ff9900"));
                return;
            }
        }

        playerDataManager.save(playerData);

        int remainingAfter = isOperator ? -1 : augmentUnlockManager.getRemainingRerolls(playerData, tier);
        String suffix = isOperator
                ? "(operator bypass — no reroll consumed)"
                : remainingAfter + " " + tier.name() + " reroll(s) remaining";

        playerRef.sendMessage(Message.raw(
                "Rerolled " + targetAugmentId + " (" + tier.name() + "). "
                + "New offers are available. " + suffix).color("#4fd7f7"));
    }

    private void sendSelectedAugmentList(@Nonnull PlayerRef playerRef, @Nonnull Map<String, String> selected) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(selected.entrySet());
        entries.removeIf(entry -> entry == null || entry.getValue() == null || entry.getValue().isBlank());

        if (entries.isEmpty()) {
            playerRef.sendMessage(Message.raw("- (none)").color("#ff9900"));
            return;
        }

        entries.sort(Comparator.comparing(entry -> entry.getKey() == null ? "" : entry.getKey()));
        for (Map.Entry<String, String> entry : entries) {
            String selectionKey = entry.getKey() == null ? "UNKNOWN" : entry.getKey();
            PassiveTier tier = resolveTier(selectionKey, entry.getValue());
            String tierName = tier != null ? tier.name() : "UNKNOWN";
            playerRef.sendMessage(Message.raw(
                    "- " + entry.getValue() + " [" + tierName.toUpperCase(Locale.ROOT) + "]")
                    .color("#4fd7f7"));
        }
    }

    private PassiveTier resolveTier(String selectionKey, String selectedAugmentId) {
        if (selectionKey != null && !selectionKey.isBlank()) {
            String key = selectionKey.trim();
            int hashIndex = key.indexOf('#');
            if (hashIndex >= 0) {
                key = key.substring(0, hashIndex);
            }
            int colonIndex = key.indexOf(':');
            if (colonIndex >= 0) {
                key = key.substring(0, colonIndex);
            }

            try {
                return PassiveTier.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to id-based resolution for legacy/nonstandard keys.
            }
        }

        if (selectedAugmentId == null || selectedAugmentId.isBlank()) {
            return null;
        }

        if (CommonAugment.parseStatOfferId(selectedAugmentId) != null) {
            return PassiveTier.COMMON;
        }

        if (augmentManager == null) {
            return null;
        }

        AugmentDefinition definition = augmentManager.getAugment(selectedAugmentId);
        return definition != null ? definition.getTier() : null;
    }

    private final class RerollListVariant extends AbstractPlayerCommand {

        private RerollListVariant() {
            super("List selected augments that can be rerolled");
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
            executeInternal(commandContext, playerRef, null);
        }
    }
}
