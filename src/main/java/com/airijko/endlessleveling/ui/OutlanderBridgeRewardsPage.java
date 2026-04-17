package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeRewardCooldowns;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeXpBank;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Rewards panel shown to a player at end-of-dungeon (full victory) or
 * after death when banked XP is non-zero. Player can CLAIM (grant banked
 * XP + 1hr claim cooldown) or CANCEL (forfeit banked XP, no cooldown).
 * <p>
 * The wave manager ticks {@link #tickCountdown} once per second while the
 * page is open; on timeout the manager forces cleanup/cancel externally.
 */
public final class OutlanderBridgeRewardsPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /** Permission node granting bypass of the 1-hour claim cooldown. Grant via
     *  your permissions plugin to let staff/testers repeatedly claim. Wildcard
     *  {@code endlessleveling.*} and op/admin groups also bypass. */
    public static final String BYPASS_COOLDOWN_NODE =
            "endlessleveling.outlander.bypass_cooldown";

    /** Registry of open pages by player UUID so the manager can push countdown ticks. */
    private static final ConcurrentHashMap<UUID, OutlanderBridgeRewardsPage> OPEN_PAGES =
            new ConcurrentHashMap<>();

    private final UUID sessionWorldId;
    private final double savedXp;
    private final boolean showDungeonCountdown;
    private volatile int countdownSeconds;
    private volatile boolean resolved = false;

    public OutlanderBridgeRewardsPage(@Nonnull PlayerRef playerRef,
                                      @Nonnull CustomPageLifetime lifetime,
                                      @Nonnull UUID sessionWorldId,
                                      double savedXp,
                                      int countdownSeconds,
                                      boolean showDungeonCountdown) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        this.sessionWorldId = sessionWorldId;
        this.savedXp = savedXp;
        this.countdownSeconds = countdownSeconds;
        this.showDungeonCountdown = showDungeonCountdown;
    }

    // ========================================================================
    // Static open / close
    // ========================================================================

    public static void openFor(@Nonnull PlayerRef playerRef,
                               @Nonnull UUID sessionWorldId,
                               double savedXp,
                               int countdownSeconds,
                               boolean showDungeonCountdown) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null || !playerRef.isValid()) return;
        if (savedXp <= 0.0) return;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        OutlanderBridgeRewardsPage existing = OPEN_PAGES.get(playerUuid);
        if (existing != null && !existing.resolved) return;

        OutlanderBridgeRewardsPage page = new OutlanderBridgeRewardsPage(
                playerRef, CustomPageLifetime.CanDismiss,
                sessionWorldId, savedXp, countdownSeconds, showDungeonCountdown);
        OPEN_PAGES.put(playerUuid, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    /** Called once per second by the wave manager. Updates countdown label.
     *  No-op if the page was opened without the dungeon-close countdown
     *  (i.e. death-respawn panels). */
    public static void tickCountdown(@Nonnull UUID playerUuid, int secondsRemaining) {
        OutlanderBridgeRewardsPage page = OPEN_PAGES.get(playerUuid);
        if (page == null || page.resolved) return;
        if (!page.showDungeonCountdown) return;
        page.countdownSeconds = Math.max(0, secondsRemaining);
        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#RewardsCountdownLabel.Text",
                "Dungeon closes in: " + page.countdownSeconds + "s");
        page.sendUpdate(ui, false);
    }

    /** Force-close by manager on timeout or session teardown. */
    public static void forceClose(@Nonnull UUID playerUuid) {
        OutlanderBridgeRewardsPage page = OPEN_PAGES.remove(playerUuid);
        if (page != null) page.resolved = true;
    }

    public static boolean hasOpenPage(@Nonnull UUID playerUuid) {
        OutlanderBridgeRewardsPage page = OPEN_PAGES.get(playerUuid);
        return page != null && !page.resolved;
    }

    // ========================================================================
    // Build
    // ========================================================================

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append("Pages/OutlanderBridge/OutlanderBridgeRewards.ui");

        events.addEventBinding(Activating, "#RewardsClaimButton",
                of("Action", "outlander:rewards:claim"), false);
        events.addEventBinding(Activating, "#RewardsCancelButton",
                of("Action", "outlander:rewards:cancel"), false);

        ui.set("#RewardsXpAmount.Text", formatXp(savedXp));
        if (showDungeonCountdown) {
            ui.set("#RewardsCountdownLabel.Text",
                    "Dungeon closes in: " + countdownSeconds + "s");
            ui.set("#RewardsCountdownLabel.Visible", true);
            ui.set("#RewardsCountdownHint.Visible", true);
        } else {
            ui.set("#RewardsCountdownLabel.Visible", false);
            ui.set("#RewardsCountdownHint.Visible", false);
        }

        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        UUID playerUuid = playerRef.getUuid();
        boolean bypass = OperatorHelper.hasPermission(playerRef, BYPASS_COOLDOWN_NODE);
        if (!bypass && cd != null && playerUuid != null && cd.isOnCooldown(playerUuid)) {
            long remainingMin = (cd.remainingMs(playerUuid) + 59_999L) / 60_000L;
            ui.set("#RewardsCooldownStatus.Text",
                    "Claim on cooldown — " + remainingMin + "m remaining");
            ui.set("#RewardsCooldownStatus.Visible", true);
        } else {
            ui.set("#RewardsCooldownStatus.Visible", false);
        }
    }

    // ========================================================================
    // Event handling
    // ========================================================================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);
        if (resolved) return;
        if (data.action == null || data.action.isEmpty()) return;

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) return;

        switch (data.action) {
            case "outlander:rewards:claim" -> handleClaim(playerUuid);
            case "outlander:rewards:cancel" -> handleCancel(playerUuid);
            default -> {}
        }
    }

    private void handleClaim(@Nonnull UUID playerUuid) {
        OutlanderBridgeRewardCooldowns cd = OutlanderBridgeRewardCooldowns.get();
        boolean bypass = OperatorHelper.hasPermission(playerRef, BYPASS_COOLDOWN_NODE);
        if (!bypass && cd != null && cd.isOnCooldown(playerUuid)) {
            // Show cooldown message — do not grant, do not reset, stay open so
            // player can cancel instead.
            UICommandBuilder ui = new UICommandBuilder();
            long remainingMin = (cd.remainingMs(playerUuid) + 59_999L) / 60_000L;
            ui.set("#RewardsCooldownStatus.Text",
                    "Claim on cooldown — " + remainingMin + "m remaining. Press CANCEL to leave.");
            ui.set("#RewardsCooldownStatus.Visible", true);
            sendUpdate(ui, false);
            return;
        }

        resolved = true;
        OPEN_PAGES.remove(playerUuid);

        // Grant XP (banking inactive for this player now that they're locked).
        // Use adjustRawXp: bank already holds fully bonus-adjusted XP from the
        // addXp pipeline at accumulation time. Re-running addXp here would
        // double-stack bonuses and — worse — clamp the whole session total
        // against the per-kill XP gain cap, wiping most of the claim.
        LevelingManager lm = EndlessLeveling.getInstance().getLevelingManager();
        OutlanderBridgeXpBank bank = OutlanderBridgeXpBank.get();
        bank.lockAndClear(playerUuid, sessionWorldId);
        if (lm != null && savedXp > 0.0) {
            lm.adjustRawXp(playerUuid, savedXp);
        }
        if (!bypass && cd != null) cd.setClaimedNow(playerUuid);

        Message tail = bypass
                ? Message.raw("Cooldown bypassed.").color("#f8d66d")
                : Message.raw("1-hour claim cooldown started.").color("#8fd3ff");
        PlayerChatNotifier.send(playerRef, Message.join(
                Message.raw("Claimed ").color("#6cff78"),
                Message.raw(formatXp(savedXp)).color("#ffe396"),
                Message.raw(" from Outlander Bridge. ").color("#6cff78"),
                tail));
        LOGGER.atInfo().log("Outlander Bridge: player=%s claimed %.1f xp", playerUuid, savedXp);

        kickAndClose(playerUuid);
    }

    private void handleCancel(@Nonnull UUID playerUuid) {
        resolved = true;
        OPEN_PAGES.remove(playerUuid);
        OutlanderBridgeXpBank.get().lockAndClear(playerUuid, sessionWorldId);

        PlayerChatNotifier.send(playerRef, Message.join(
                Message.raw("Outlander Bridge banked XP forfeited. ").color("#ff9900"),
                Message.raw("No cooldown set.").color("#9fb6d3")));
        LOGGER.atInfo().log("Outlander Bridge: player=%s cancelled %.1f xp", playerUuid, savedXp);

        kickAndClose(playerUuid);
    }

    private void kickAndClose(@Nonnull UUID playerUuid) {
        try { close(); } catch (Exception ignored) {}
        OutlanderBridgeWaveManager.get().kickPlayerFromInstance(playerUuid);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    @Nonnull
    private static String formatXp(double xp) {
        long rounded = Math.round(xp);
        if (rounded < 1_000L) return rounded + " XP";
        if (rounded < 1_000_000L)
            return String.format(Locale.ROOT, "%.1fk XP", rounded / 1_000.0);
        return String.format(Locale.ROOT, "%.2fM XP", rounded / 1_000_000.0);
    }

    @SuppressWarnings("unused")
    public UUID getSessionWorldId() { return sessionWorldId; }

    public double getSavedXp() { return savedXp; }
}
