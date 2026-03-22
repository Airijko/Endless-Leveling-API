package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.util.ChatMessageTemplate;
import com.airijko.endlessleveling.util.FixedValue;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scans player data for augment slot mismatches between what the current
 * leveling config grants and what each player actually has (selected +
 * pending). Mismatches can occur when {@code leveling.yml} milestone rules
 * are changed after players have already earned (or exceeded) their
 * allocations.
 *
 * <p>
 * Unsynced players are reported to all online operators with the exact
 * commands needed to fix them:
 * <ul>
 * <li>{@code /lvl augments reset <name>} — reset one player</li>
 * <li>{@code /lvl augments resetallplayers} — bulk-fix everyone</li>
 * </ul>
 *
 * <p>
 * A full audit is run once on plugin startup (results go to the server
 * console). Subsequent per-player checks fire on each login.
 */
public class AugmentSyncValidator {

    /**
     * Number of offer-ID entries stored per pending bundle.
     * Must match {@code AugmentUnlockManager.DEFAULT_OFFER_COUNT}.
     */
    private static final int OFFER_BUNDLE_SIZE = 3;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final PlayerDataManager playerDataManager;
    private final AugmentUnlockManager augmentUnlockManager;

    // -------------------------------------------------------------------------
    // Public types
    // -------------------------------------------------------------------------

    public enum SyncStatus {
        /** Slot count matches current config. */
        IN_SYNC,
        /** Player has fewer slots than the current config grants. */
        TOO_FEW,
        /** Player has more slots than the current config grants. */
        TOO_MANY
    }

    /** Snapshot describing a single unsynced player. */
    public static final class UnsyncedEntry {
        public final String name;
        public final UUID uuid;
        public final SyncStatus status;
        /** Milestone count the current config grants for this player's level. */
        public final int expected;
        /** Milestone count actually present in the player's active profile. */
        public final int actual;
        /** Tier-level mismatch details (expected vs selected/pending/actual). */
        public final List<TierMismatchDetail> tierMismatches;

        public UnsyncedEntry(String name,
                UUID uuid,
                SyncStatus status,
                int expected,
                int actual,
                List<TierMismatchDetail> tierMismatches) {
            this.name = name;
            this.uuid = uuid;
            this.status = status;
            this.expected = expected;
            this.actual = actual;
            this.tierMismatches = tierMismatches == null ? List.of() : List.copyOf(tierMismatches);
        }
    }

    public static final class TierMismatchDetail {
        public final PassiveTier tier;
        public final int expected;
        public final int selected;
        public final int pending;
        public final int actual;

        public TierMismatchDetail(PassiveTier tier, int expected, int selected, int pending) {
            this.tier = tier;
            this.expected = Math.max(0, expected);
            this.selected = Math.max(0, selected);
            this.pending = Math.max(0, pending);
            this.actual = this.selected + this.pending;
        }

        public int delta() {
            return actual - expected;
        }
    }

    private static final class TierActualBreakdown {
        private final int selected;
        private final int pending;

        private TierActualBreakdown(int selected, int pending) {
            this.selected = Math.max(0, selected);
            this.pending = Math.max(0, pending);
        }

        private int selected() {
            return selected;
        }

        private int pending() {
            return pending;
        }

        private int actual() {
            return selected + pending;
        }
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AugmentSyncValidator(@Nonnull PlayerDataManager playerDataManager,
            @Nonnull AugmentUnlockManager augmentUnlockManager) {
        this.playerDataManager = playerDataManager;
        this.augmentUnlockManager = augmentUnlockManager;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the sync status of {@code playerData}'s active profile against
     * the current config rules.
     *
     * <p>
     * <em>Note:</em> if called after {@link AugmentUnlockManager#ensureUnlocks}
     * has already run for this player, {@link SyncStatus#TOO_FEW} will only appear
     * when the augment offer pool was exhausted and missing slots could not be
     * automatically filled.
     */
    @Nonnull
    public SyncStatus checkPlayer(@Nonnull PlayerData playerData) {
        int eligible = augmentUnlockManager.getExpectedSyncMilestoneCount(playerData, playerData.getLevel());
        int actual = sumActual(countActualMilestonesByTier(playerData));
        if (actual < eligible)
            return SyncStatus.TOO_FEW;
        if (actual > eligible)
            return SyncStatus.TOO_MANY;
        return SyncStatus.IN_SYNC;
    }

    /**
     * Loads every player from disk (offline and online) via
     * {@link PlayerDataManager#getAllPlayersSortedByLevel()} and returns
     * {@link UnsyncedEntry} records for those whose active-profile milestone
     * count does not match the current config.
     */
    @Nonnull
    public List<UnsyncedEntry> checkAllPlayers() {
        List<PlayerData> all = playerDataManager.getAllPlayersSortedByLevel();
        List<UnsyncedEntry> unsynced = new ArrayList<>();
        for (PlayerData data : all) {
            SyncStatus status = checkPlayer(data);
            if (status == SyncStatus.IN_SYNC)
                continue;
            Map<PassiveTier, Integer> expectedByTier =
                    augmentUnlockManager.getExpectedSyncMilestonesByTier(data, data.getLevel());
            Map<PassiveTier, TierActualBreakdown> actualByTier = countActualMilestonesByTier(data);
            int eligible = sumExpected(expectedByTier);
            int actual = sumActual(actualByTier);
            List<TierMismatchDetail> tierMismatches = buildTierMismatchDetails(expectedByTier, actualByTier);
            unsynced.add(new UnsyncedEntry(data.getPlayerName(), data.getUuid(), status, eligible, actual, tierMismatches));
        }
        return unsynced;
    }

    /**
     * Performs a full audit over all player data files (offline + online) and:
     * <ol>
     * <li>Logs any mismatches to the server console.</li>
     * <li>Sends a detailed report to every currently online operator.</li>
     * </ol>
     * Safe to call at plugin startup — when no players are connected yet,
     * findings are still captured in the server log.
     */
    public void auditAndNotify() {
        LOGGER.atInfo().log("AugmentSyncValidator: starting full player audit…");
        List<UnsyncedEntry> unsynced = checkAllPlayers();

        if (unsynced.isEmpty()) {
            LOGGER.atInfo().log("AugmentSyncValidator: all players are in sync with current augment config.");
            return;
        }

        LOGGER.atWarning().log("AugmentSyncValidator: %d player(s) have mismatched augment slot counts:",
                unsynced.size());
        for (UnsyncedEntry e : unsynced) {
            LOGGER.atWarning().log("  [%s] %s — expected %d slot(s), actual %d slot(s)",
                    e.status, e.name, e.expected, e.actual);
            for (TierMismatchDetail detail : e.tierMismatches) {
            String direction = detail.delta() < 0 ? "missing" : "excess";
            int amount = Math.abs(detail.delta());
            LOGGER.atWarning().log(
                "      - %s: expected=%d selected=%d pending=%d actual=%d (%s %d)",
                detail.tier.name(),
                detail.expected,
                detail.selected,
                detail.pending,
                detail.actual,
                direction,
                amount);
            }
        }
        LOGGER.atWarning().log(
                "AugmentSyncValidator: to fix all players run:  %s augments resetallplayers",
                FixedValue.ROOT_COMMAND.value());

        // Notify any operators who are already online (unlikely at startup, but
        // possible
        // if the plugin is reloaded while the server is running).
        for (PlayerRef op : Universe.get().getPlayers()) {
            if (!OperatorHelper.isOperator(op))
                continue;
            broadcastUnsyncedList(op, unsynced);
        }
    }

    /**
     * Checks a single player on login and immediately notifies all online
     * operators if that player's slot count is out of sync with the current
     * config.
     *
     * <p>
     * Call this <em>after</em> {@link AugmentUnlockManager#ensureUnlocks} so
     * that routine TOO_FEW cases (missing offers that ensureUnlocks just filled)
     * do not produce false-positive alerts.
     */
    public void auditOnLogin(@Nonnull PlayerData playerData) {
        SyncStatus status = checkPlayer(playerData);
        if (status == SyncStatus.IN_SYNC)
            return;

        Map<PassiveTier, Integer> expectedByTier =
            augmentUnlockManager.getExpectedSyncMilestonesByTier(playerData, playerData.getLevel());
        Map<PassiveTier, TierActualBreakdown> actualByTier = countActualMilestonesByTier(playerData);
        int eligible = sumExpected(expectedByTier);
        int actual = sumActual(actualByTier);
        List<TierMismatchDetail> tierMismatches = buildTierMismatchDetails(expectedByTier, actualByTier);

        LOGGER.atWarning().log(
                "AugmentSyncValidator: %s joined with mismatched augment slots [%s: expected=%d actual=%d]",
                playerData.getPlayerName(), status, eligible, actual);
        for (TierMismatchDetail detail : tierMismatches) {
            String direction = detail.delta() < 0 ? "missing" : "excess";
            int amount = Math.abs(detail.delta());
            LOGGER.atWarning().log(
                "      - %s: expected=%d selected=%d pending=%d actual=%d (%s %d)",
                detail.tier.name(),
                detail.expected,
                detail.selected,
                detail.pending,
                detail.actual,
                direction,
                amount);
        }

        UnsyncedEntry entry = new UnsyncedEntry(
            playerData.getPlayerName(), playerData.getUuid(), status, eligible, actual, tierMismatches);

        for (PlayerRef op : Universe.get().getPlayers()) {
            if (!OperatorHelper.isOperator(op))
                continue;
            broadcastUnsyncedList(op, List.of(entry));
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of milestone slots actually present in the
     * player's active profile: selected augments + pending offer bundles.
     */
    private Map<PassiveTier, TierActualBreakdown> countActualMilestonesByTier(@Nonnull PlayerData playerData) {
        Map<PassiveTier, Integer> selectedByTier = new EnumMap<>(PassiveTier.class);
        for (String key : playerData.getSelectedAugmentsSnapshot().keySet()) {
            PassiveTier tier = resolveTierFromSelectionKey(key);
            if (tier == null) {
                continue;
            }
            selectedByTier.merge(tier, 1, Integer::sum);
        }

        Map<PassiveTier, TierActualBreakdown> breakdownByTier = new EnumMap<>(PassiveTier.class);
        for (PassiveTier tier : PassiveTier.values()) {
            int selected = Math.max(0, selectedByTier.getOrDefault(tier, 0));
            List<String> tierOffers = playerData.getAugmentOffersForTier(tier.name());
            int pending = tierOffers == null ? 0 : tierOffers.size() / OFFER_BUNDLE_SIZE;
            breakdownByTier.put(tier, new TierActualBreakdown(selected, pending));
        }
        return breakdownByTier;
    }

    private int sumExpected(@Nonnull Map<PassiveTier, Integer> expectedByTier) {
        int total = 0;
        for (int expected : expectedByTier.values()) {
            total += Math.max(0, expected);
        }
        return total;
    }

    private int sumActual(@Nonnull Map<PassiveTier, TierActualBreakdown> actualByTier) {
        int total = 0;
        for (TierActualBreakdown value : actualByTier.values()) {
            total += Math.max(0, value.actual());
        }
        return total;
    }

    @Nonnull
    private List<TierMismatchDetail> buildTierMismatchDetails(
            @Nonnull Map<PassiveTier, Integer> expectedByTier,
            @Nonnull Map<PassiveTier, TierActualBreakdown> actualByTier) {
        List<TierMismatchDetail> details = new ArrayList<>();
        PassiveTier[] priority = { PassiveTier.MYTHIC, PassiveTier.LEGENDARY, PassiveTier.ELITE, PassiveTier.COMMON };
        for (PassiveTier tier : priority) {
            int expected = Math.max(0, expectedByTier.getOrDefault(tier, 0));
            TierActualBreakdown actual = actualByTier.getOrDefault(tier, new TierActualBreakdown(0, 0));
            if (actual.actual() == expected) {
                continue;
            }
            details.add(new TierMismatchDetail(tier, expected, actual.selected(), actual.pending()));
        }
        return details;
    }

    private PassiveTier resolveTierFromSelectionKey(String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return null;
        }
        String tierKey = selectionKey.contains("#")
                ? selectionKey.substring(0, selectionKey.indexOf('#'))
                : selectionKey;
        try {
            return PassiveTier.valueOf(tierKey.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Sends a formatted sync-issue report to a single operator. */
    private void broadcastUnsyncedList(@Nonnull PlayerRef op, @Nonnull List<UnsyncedEntry> unsynced) {
        PlayerChatNotifier.send(op, ChatMessageTemplate.AUGMENT_SYNC_SUMMARY, unsynced.size());
        for (UnsyncedEntry e : unsynced) {
            String direction = e.status == SyncStatus.TOO_FEW ? "too few" : "too many";
            PlayerChatNotifier.send(op, ChatMessageTemplate.AUGMENT_SYNC_ENTRY,
                    e.name, direction, e.actual, e.expected);
            for (TierMismatchDetail detail : e.tierMismatches) {
                String deltaDirection = detail.delta() < 0 ? "missing" : "excess";
                int delta = Math.abs(detail.delta());
                PlayerChatNotifier.send(op, ChatMessageTemplate.AUGMENT_GENERIC,
                        "  - " + detail.tier.name()
                                + ": expected " + detail.expected
                                + ", selected " + detail.selected
                                + ", pending " + detail.pending
                                + ", actual " + detail.actual
                                + " (" + deltaDirection + " " + delta + ")");
            }
        }
        PlayerChatNotifier.send(op, ChatMessageTemplate.AUGMENT_SYNC_FIX_ALL);
    }
}
