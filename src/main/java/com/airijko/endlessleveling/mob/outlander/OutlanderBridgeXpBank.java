package com.airijko.endlessleveling.mob.outlander;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player XP banking state for Outlander Bridge sessions.
 * <p>
 * XP pipeline keeps every bonus (party, marriage, passive, level-diff, etc.)
 * intact — after all modifiers have been applied, {@link #tryDivertXp} in
 * {@code LevelingManager.addXp} redirects the final adjusted amount into the
 * player's pending bank instead of crediting profile XP.
 * <p>
 * Banking rules:
 * <ul>
 *   <li>XP earned mid-wave → {@code pendingXp} (wipes on player death).</li>
 *   <li>Wave clear → {@code pendingXp} is added to {@code savedXp} (checkpoint).</li>
 *   <li>Player death in session → {@code pendingXp} zeroed, rewards panel
 *       queued on respawn if {@code savedXp > 0}.</li>
 *   <li>Claim/cancel/timeout → bank cleared and player locked for that
 *       session. Re-entering via TP exploit triggers immediate kick (see
 *       {@link #isLockedFromSession}).</li>
 * </ul>
 */
public final class OutlanderBridgeXpBank {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final OutlanderBridgeXpBank INSTANCE = new OutlanderBridgeXpBank();

    /** Player UUID → active outlander session world UUID (banking-eligible). */
    private final ConcurrentHashMap<UUID, UUID> activeBanking = new ConcurrentHashMap<>();

    /** Session world UUID → map of player UUID → BankState. */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, BankState>> sessionBanks =
            new ConcurrentHashMap<>();

    /** Session world UUID → locked-out player UUIDs (cannot re-bank or re-enter). */
    private final ConcurrentHashMap<UUID, Set<UUID>> sessionLockedPlayers =
            new ConcurrentHashMap<>();

    /**
     * Player UUID → PendingReward queued for their next respawn.
     * Populated when a player dies in-session with savedXp > 0; consumed by
     * PlayerReady hook which opens the rewards panel.
     */
    private final ConcurrentHashMap<UUID, PendingReward> pendingOnRespawn = new ConcurrentHashMap<>();

    private OutlanderBridgeXpBank() {}

    public static OutlanderBridgeXpBank get() { return INSTANCE; }

    // ========================================================================
    // Banking registration (called by OutlanderBridgeWaveManager)
    // ========================================================================

    /** Marks player as banking-active in this session. Clears prior state. */
    public void registerPlayerForSession(@Nonnull UUID playerUuid, @Nonnull UUID sessionWorldId) {
        // Locked players cannot rejoin banking even if they re-enter the world.
        Set<UUID> locked = sessionLockedPlayers.get(sessionWorldId);
        if (locked != null && locked.contains(playerUuid)) return;

        activeBanking.put(playerUuid, sessionWorldId);
        sessionBanks.computeIfAbsent(sessionWorldId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerUuid, k -> new BankState());
    }

    /** Called on DrainPlayer — stops XP diversion but keeps bank state so
     *  rewards panel on respawn can see it. */
    public void onPlayerDrain(@Nullable UUID playerUuid) {
        if (playerUuid == null) return;
        activeBanking.remove(playerUuid);
    }

    /** Full cleanup on session end (cleanupSession). */
    public void clearSession(@Nonnull UUID sessionWorldId) {
        sessionBanks.remove(sessionWorldId);
        sessionLockedPlayers.remove(sessionWorldId);
        activeBanking.values().removeIf(sessionWorldId::equals);
    }

    // ========================================================================
    // XP divert — called from LevelingManager.addXp AFTER all bonuses applied
    // ========================================================================

    /**
     * @return true if the XP was diverted into the player's bank. Caller
     *         MUST NOT credit profile XP in that case.
     * <p>
     * Hardened: verifies the player's current world id equals the banking
     * session world id. Closes leaks where {@code activeBanking} drifts from
     * reality (death gap, missed drain, disconnect without cleanup). If the
     * player is not currently inside the session world, banking is auto-drained
     * and the XP falls through to profile credit.
     */
    public boolean tryDivertXp(@Nonnull UUID playerUuid, double adjustedXp) {
        if (adjustedXp <= 0) return false;
        UUID sessionWorldId = activeBanking.get(playerUuid);
        if (sessionWorldId == null) return false;

        // World-location guard: player must physically be inside the session
        // world for divert to succeed. Prevents stale activeBanking entries
        // from silently swallowing XP earned outside Outlander Bridge.
        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef ref = universe.getPlayer(playerUuid);
            UUID currentWorldId = ref != null ? ref.getWorldUuid() : null;
            if (currentWorldId == null || !sessionWorldId.equals(currentWorldId)) {
                activeBanking.remove(playerUuid);
                return false;
            }
        }

        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sessionWorldId);
        if (bank == null) return false;
        BankState state = bank.get(playerUuid);
        if (state == null) return false;
        state.pendingXp += adjustedXp;
        return true;
    }

    // ========================================================================
    // Checkpoint / death
    // ========================================================================

    /** Called on wave clear — moves all players' pending into saved. */
    public void checkpointSession(@Nonnull UUID sessionWorldId) {
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sessionWorldId);
        if (bank == null) return;
        for (BankState state : bank.values()) {
            state.savedXp += state.pendingXp;
            state.pendingXp = 0.0;
        }
    }

    /**
     * Called when a player dies in-session. Zeros pending and — if saved
     * XP is non-zero — queues a rewards-panel pending on respawn.
     * <p>
     * Also drains {@code activeBanking} so any XP that lands in the
     * death-to-drain gap (DoT tick kill credit, queued AoE resolution) is
     * routed to profile XP instead of a stale session bank.
     * @return savedXp (snapshot) so caller can log.
     */
    public double onPlayerDied(@Nonnull UUID playerUuid, @Nonnull UUID sessionWorldId) {
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sessionWorldId);
        // Always drain activeBanking on death — do this even if bank/state is
        // missing, to guarantee no stale divert after death.
        activeBanking.remove(playerUuid);
        if (bank == null) return 0.0;
        BankState state = bank.get(playerUuid);
        if (state == null) return 0.0;
        state.pendingXp = 0.0;
        double saved = state.savedXp;
        if (saved > 0.0) {
            pendingOnRespawn.put(playerUuid, new PendingReward(sessionWorldId, saved));
        }
        return saved;
    }

    // ========================================================================
    // Queries
    // ========================================================================

    public double getSavedXp(@Nonnull UUID playerUuid) {
        UUID sid = activeBanking.get(playerUuid);
        if (sid == null) return 0.0;
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sid);
        if (bank == null) return 0.0;
        BankState state = bank.get(playerUuid);
        return state == null ? 0.0 : state.savedXp;
    }

    public double getPendingXp(@Nonnull UUID playerUuid) {
        UUID sid = activeBanking.get(playerUuid);
        if (sid == null) return 0.0;
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sid);
        if (bank == null) return 0.0;
        BankState state = bank.get(playerUuid);
        return state == null ? 0.0 : state.pendingXp;
    }

    /** Total claimable (saved) across a whole session (for victory panel). */
    public double getSavedXp(@Nonnull UUID sessionWorldId, @Nonnull UUID playerUuid) {
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sessionWorldId);
        if (bank == null) return 0.0;
        BankState state = bank.get(playerUuid);
        return state == null ? 0.0 : state.savedXp;
    }

    public boolean isBankingActive(@Nonnull UUID playerUuid) {
        return activeBanking.containsKey(playerUuid);
    }

    /** True if player is locked out of this specific session (claimed, cancelled, timed out). */
    public boolean isLockedFromSession(@Nonnull UUID playerUuid, @Nonnull UUID sessionWorldId) {
        Set<UUID> locked = sessionLockedPlayers.get(sessionWorldId);
        return locked != null && locked.contains(playerUuid);
    }

    // ========================================================================
    // Resolution (claim/cancel/timeout)
    // ========================================================================

    /**
     * Lock player out of this session permanently. Clears their bank state
     * so any re-entry via TP cannot resume banking.
     */
    public void lockAndClear(@Nonnull UUID playerUuid, @Nonnull UUID sessionWorldId) {
        sessionLockedPlayers.computeIfAbsent(sessionWorldId,
                k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
        activeBanking.remove(playerUuid);
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sessionWorldId);
        if (bank != null) bank.remove(playerUuid);
        pendingOnRespawn.remove(playerUuid);
    }

    // ========================================================================
    // Pending-on-respawn (consumed by PlayerReadyEvent hook)
    // ========================================================================

    @Nullable
    public PendingReward consumePendingOnRespawn(@Nonnull UUID playerUuid) {
        return pendingOnRespawn.remove(playerUuid);
    }

    @Nullable
    public PendingReward peekPendingOnRespawn(@Nonnull UUID playerUuid) {
        return pendingOnRespawn.get(playerUuid);
    }

    // ========================================================================
    // Data types
    // ========================================================================

    private static final class BankState {
        double savedXp = 0.0;
        double pendingXp = 0.0;
    }

    /** Queued reward awaiting a respawn — sessionWorldId identifies the
     *  instance even if it no longer exists when the panel opens. */
    public record PendingReward(@Nonnull UUID sessionWorldId, double savedXp) {}

    /** Read-only view of a player's bank. */
    public record BankView(double savedXp, double pendingXp) {}

    @Nonnull
    public BankView viewBank(@Nonnull UUID playerUuid) {
        UUID sid = activeBanking.get(playerUuid);
        if (sid == null) return new BankView(0.0, 0.0);
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sid);
        if (bank == null) return new BankView(0.0, 0.0);
        BankState state = bank.get(playerUuid);
        return state == null ? new BankView(0.0, 0.0)
                : new BankView(state.savedXp, state.pendingXp);
    }

    /** Iterate all players in a session with saved XP > 0 — used to open
     *  victory rewards panels. */
    @Nonnull
    public Map<UUID, Double> snapshotSessionSavedXp(@Nonnull UUID sessionWorldId) {
        ConcurrentHashMap<UUID, BankState> bank = sessionBanks.get(sessionWorldId);
        if (bank == null) return Map.of();
        Map<UUID, Double> out = new java.util.HashMap<>();
        for (Map.Entry<UUID, BankState> e : bank.entrySet()) {
            out.put(e.getKey(), e.getValue().savedXp);
        }
        return out;
    }
}
