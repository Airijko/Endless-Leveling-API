package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.airijko.endlessleveling.managers.PassiveManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies the EndlessLeveling luck passive whenever players gain loot inside
 * their inventory.
 */
public class LuckDoubleDropSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final PlayerDataManager playerDataManager;
    private final PassiveManager passiveManager;
    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> recentOreBreaks = new ConcurrentHashMap<>();
    /**
     * Tracks item IDs released from any player's inventory. This blocks staged-item
     * exploits where players (or their allies) drop loot before a kill and pick it
     * up during the kill window.
     */
    private final ConcurrentMap<String, Long> globallyDroppedItems = new ConcurrentHashMap<>();
    /**
     * Tracks recent container/chest breaks per player for strict anti-container
     * gating.
     */
    private final ConcurrentMap<UUID, Long> recentContainerBreaks = new ConcurrentHashMap<>();
    /** How long to remember a released item ID for staged-item detection. */
    private static final long PLAYER_DROP_TRACK_TTL_MS = 30_000L;
    /**
     * Strict guard window after breaking a container where mob-drop luck is
     * blocked.
     */
    private static final long CONTAINER_BREAK_GUARD_MS = 12_000L;

    public LuckDoubleDropSystem(@Nonnull PlayerDataManager playerDataManager,
            @Nonnull PassiveManager passiveManager) {
        this.playerDataManager = playerDataManager;
        this.passiveManager = passiveManager;
    }

    /**
     * Record that a player recently broke an ore block. The inventory change
     * handler will only treat drops as "ore" when a recent break has been
     * recorded for the player (prevents non-mining inventory additions).
     */
    public void markRecentOreBreak(@Nonnull UUID uuid) {
        recentOreBreaks.put(uuid, System.currentTimeMillis());
    }

    /**
     * Record that a player broke a loot container (chest/barrel/etc.). Mob-drop
     * luck is temporarily blocked to prevent container loot from piggy-backing on a
     * kill window.
     */
    public void markRecentContainerBreak(@Nonnull UUID uuid) {
        recentContainerBreaks.put(uuid, System.currentTimeMillis());
    }

    /**
     * Invoked when a living entity's inventory changes. Only players are eligible
     * for luck procs.
     */
    public void onInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Player player)) {
            return;
        }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        if (suppressedPlayers.contains(uuid)) {
            return;
        }

        Transaction transaction = event.getTransaction();
        if (!(transaction instanceof ItemStackTransaction stackTransaction)) {
            return;
        }

        // Track items the player releases from their inventory so we can detect
        // pre-staged items later. Must run before the addedAmount guard below.
        trackRemovedItems(uuid, stackTransaction);

        ItemStack sourceStack = resolveSourceStack(stackTransaction);
        if (sourceStack == null || ItemStack.isEmpty(sourceStack)) {
            return;
        }

        int addedAmount = resolveAddedAmount(stackTransaction);
        if (addedAmount <= 0) {
            return;
        }

        // Some inventory mutations (e.g. ground loot pickup) report non-ADD actions;
        // rely on
        // the computed positive delta instead so we still trigger for mob drops.

        PlayerData playerData = playerDataManager.get(uuid);
        if (playerData == null) {
            return;
        }

        double luckValue = passiveManager.getLuckValue(playerData);
        if (luckValue <= 0.0D) {
            return;
        }

        Optional<DropType> dropType = resolveDropType(uuid, sourceStack);
        if (dropType.isEmpty()) {
            return;
        }

        if (!shouldTrigger(playerRef, luckValue, dropType.get())) {
            return;
        }

        if (dropType.get() == DropType.MOB) {
            passiveManager.consumeMobDropStack(uuid);
        }

        int bonusAmount = addedAmount;
        if (bonusAmount <= 0) {
            return;
        }

        if (grantBonus(player, playerRef, sourceStack, bonusAmount)) {
            passiveManager.notifyLuckDoubleDrop(playerData, formatDropName(sourceStack), addedAmount, bonusAmount);
        }
    }

    private boolean grantBonus(@Nonnull Player player,
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemStack templateStack,
            int bonusAmount) {
        CombinedItemContainer container = player.getInventory().getCombinedHotbarFirst();
        if (container == null) {
            return false;
        }

        ItemStack bonusStack = templateStack.withQuantity(bonusAmount);
        UUID uuid = playerRef.getUuid();
        suppressedPlayers.add(uuid);
        try {
            ItemStackTransaction transaction = container.addItemStack(bonusStack);
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && !ItemStack.isEmpty(remainder)) {
                LOGGER.atFine().log("Luck bonus overflow for %s on %s (%d remainder)",
                        playerRef.getUsername(), templateStack.getItemId(), remainder.getQuantity());
            }
            LOGGER.atFiner().log("Luck double-drop applied to %s for %s: +%d",
                    playerRef.getUsername(), templateStack.getItemId(), bonusAmount);
            return true;
        } catch (Exception exception) {
            LOGGER.atWarning().withCause(exception).log("Failed to award luck bonus to %s", playerRef.getUsername());
            return false;
        } finally {
            suppressedPlayers.remove(uuid);
        }
    }

    private ItemStack resolveSourceStack(@Nonnull ItemStackTransaction transaction) {
        ItemStack query = transaction.getQuery();
        if (query != null && !ItemStack.isEmpty(query)) {
            return query;
        }
        List<ItemStackSlotTransaction> slotTransactions = transaction.getSlotTransactions();
        for (ItemStackSlotTransaction slotTransaction : slotTransactions) {
            ItemStack slotAfter = slotTransaction.getSlotAfter();
            if (slotAfter != null && !ItemStack.isEmpty(slotAfter)) {
                return slotAfter;
            }
        }
        return null;
    }

    private int resolveAddedAmount(@Nonnull ItemStackTransaction transaction) {
        ItemStack query = transaction.getQuery();
        if (query != null && !ItemStack.isEmpty(query)) {
            int amount = query.getQuantity();
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && !ItemStack.isEmpty(remainder)) {
                amount -= remainder.getQuantity();
            }
            if (amount > 0) {
                return amount;
            }
        }

        int amount = 0;
        for (ItemStackSlotTransaction slotTransaction : transaction.getSlotTransactions()) {
            ItemStack before = slotTransaction.getSlotBefore();
            ItemStack after = slotTransaction.getSlotAfter();
            int beforeQty = before == null ? 0 : before.getQuantity();
            int afterQty = after == null ? 0 : after.getQuantity();
            int delta = afterQty - beforeQty;
            if (delta > 0) {
                amount += delta;
            }
        }
        return amount;
    }

    private boolean shouldTrigger(@Nonnull PlayerRef playerRef,
            double value,
            @Nonnull DropType dropType) {
        double clamped = Math.min(100.0D, Math.max(0.0D, value));
        double roll = ThreadLocalRandom.current().nextDouble(100.0D);
        boolean success = roll < clamped;
        if (success) {
            LOGGER.atFiner().log(
                    "Luck double-drop triggered for %s (chance=%.2f%% roll=%.2f | type=%s)",
                    playerRef.getUsername(), clamped, roll, dropType);
        } else {
            LOGGER.atFiner().log(
                    "Luck double-drop failed for %s (chance=%.2f%% roll=%.2f | type=%s)",
                    playerRef.getUsername(), clamped, roll, dropType);
        }
        return success;
    }

    private Optional<DropType> resolveDropType(@Nonnull UUID uuid, @Nonnull ItemStack stack) {
        if (isRecentOreDrop(uuid, stack)) {
            return Optional.of(DropType.ORE);
        }

        // Ores are only eligible from mining markers, never from mob fallback.
        if (isOreStack(stack)) {
            return Optional.empty();
        }

        if (!passiveManager.hasMobDropStack(uuid)) {
            return Optional.empty();
        }

        // Reject items staged on the ground before the mob kill (from any player).
        long killTimestamp = passiveManager.getLastMobKillMillis(uuid);
        if (isItemStagedByAnyPlayer(stack, killTimestamp)) {
            LOGGER.atFine().log(
                    "Luck double-drop blocked for %s: item '%s' was released before mob kill (staged item exploit)",
                    uuid, stack.getItemId());
            return Optional.empty();
        }

        if (isContainerBreakGuardActive(uuid)) {
            LOGGER.atFine().log(
                    "Luck double-drop blocked for %s: recent container break guard active (item=%s)",
                    uuid, stack.getItemId());
            return Optional.empty();
        }

        return Optional.of(DropType.MOB);
    }

    private boolean isRecentOreDrop(@Nonnull UUID uuid, @Nonnull ItemStack stack) {
        if (!isOreStack(stack)) {
            return false;
        }
        Long t = recentOreBreaks.get(uuid);
        if (t == null || System.currentTimeMillis() - t > 3000L) {
            return false;
        }
        recentOreBreaks.remove(uuid);
        return true;
    }

    private boolean isOreStack(@Nonnull ItemStack stack) {
        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String[] tokens = itemId.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        for (String token : tokens) {
            if (token.equals("ORE")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records item IDs leaving any player's inventory for staged-item detection.
     */
    private void trackRemovedItems(@Nonnull UUID uuid, @Nonnull ItemStackTransaction transaction) {
        long now = System.currentTimeMillis();
        for (ItemStackSlotTransaction slot : transaction.getSlotTransactions()) {
            ItemStack before = slot.getSlotBefore();
            if (before == null || ItemStack.isEmpty(before)) {
                continue;
            }
            String itemId = normalizeItemId(before.getItemId());
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ItemStack after = slot.getSlotAfter();
            int beforeQty = before.getQuantity();
            int afterQty = after == null ? 0 : after.getQuantity();
            if (afterQty < beforeQty) {
                // Items left a player's inventory in this slot.
                globallyDroppedItems.put(itemId, now);
            }
        }
    }

    /**
     * Returns {@code true} when the item ID was removed from any player's
     * inventory before the current mob kill, indicating it may have been
     * pre-staged on the ground to exploit the luck double-drop window.
     */
    private boolean isItemStagedByAnyPlayer(@Nonnull ItemStack stack, long killTimestamp) {
        if (killTimestamp <= 0L) {
            return false;
        }
        String itemId = normalizeItemId(stack.getItemId());
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        Long releaseTime = globallyDroppedItems.get(itemId);
        if (releaseTime == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - releaseTime > PLAYER_DROP_TRACK_TTL_MS) {
            globallyDroppedItems.remove(itemId);
            return false;
        }
        // Block if the item was removed from inventory before the mob was killed.
        return releaseTime < killTimestamp;
    }

    private boolean isContainerBreakGuardActive(@Nonnull UUID uuid) {
        Long breakTime = recentContainerBreaks.get(uuid);
        if (breakTime == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - breakTime > CONTAINER_BREAK_GUARD_MS) {
            recentContainerBreaks.remove(uuid);
            return false;
        }
        return true;
    }

    private String normalizeItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return itemId.trim().toLowerCase(Locale.ROOT);
    }

    private String formatDropName(@Nonnull ItemStack stack) {
        String itemId = stack.getItemId();
        return itemId == null || itemId.isBlank() ? "loot" : itemId;
    }

    private enum DropType {
        ORE,
        MOB
    }
}
