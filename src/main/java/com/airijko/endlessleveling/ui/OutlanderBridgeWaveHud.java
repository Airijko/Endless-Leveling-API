package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.compatibility.MultipleHudCompatibility;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeWaveManager.WaveStatus;
import com.airijko.endlessleveling.mob.outlander.OutlanderBridgeXpBank;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HUD overlay for the Outlander Bridge wave tracker — shows wave count,
 * remaining mobs, and mob coordinate hints.
 */
public final class OutlanderBridgeWaveHud extends CustomUIHud {

    public static final String MULTI_HUD_SLOT = "OutlanderBridgeWaveHud";
    private static final Map<UUID, OutlanderBridgeWaveHud> ACTIVE_HUDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> HUD_LOCKS = new ConcurrentHashMap<>();

    private static final String[] HINT_SELECTORS = {
            "#MobHint1", "#MobHint2", "#MobHint3", "#MobHint4", "#MobHint5"
    };

    private final PlayerRef targetPlayerRef;
    private final Map<String, Object> lastUiState = new HashMap<>();
    private final AtomicBoolean built = new AtomicBoolean(false);

    public OutlanderBridgeWaveHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
        this.targetPlayerRef = playerRef;
    }

    // ========================================================================
    // Build
    // ========================================================================

    @Override
    protected void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        UUID uuid = targetPlayerRef.getUuid();
        if (uuid == null || ACTIVE_HUDS.get(uuid) != this) return;
        if (!built.compareAndSet(false, true)) return;
        uiCommandBuilder.append("Hud/OutlanderBridgeWaveHud.ui");
        computeHudLabels(uiCommandBuilder);
    }

    // ========================================================================
    // Refresh
    // ========================================================================

    public void refreshHud(@Nonnull Store<EntityStore> store) {
        if (!built.get()) return;
        UUID uuid = targetPlayerRef.getUuid();
        if (uuid == null || !targetPlayerRef.isValid()) {
            unregister(uuid);
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        boolean changed = computeHudLabels(builder);
        if (changed) {
            update(false, builder);
        }
    }

    private boolean computeHudLabels(@Nonnull UICommandBuilder b) {
        Ref<EntityStore> ref = targetPlayerRef.getReference();
        if (ref == null || !ref.isValid()) return false;
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) return false;
        var world = store.getExternalData().getWorld();
        if (world == null) return false;

        OutlanderBridgeWaveManager mgr = OutlanderBridgeWaveManager.get();
        WaveStatus status = mgr.getWaveStatus(world);
        boolean changed = false;

        UUID playerUuid = targetPlayerRef.getUuid();
        if (playerUuid != null) {
            OutlanderBridgeXpBank.BankView bank = OutlanderBridgeXpBank.get().viewBank(playerUuid);
            // Banked = locked-in from cleared waves (safe on death).
            // Unsecured = current wave's running total (wipes on death until wave clears).
            changed |= setText(b, "#BankedXp.Text",
                    String.format("Banked XP: %s", formatXpShort(bank.savedXp())));
            changed |= setText(b, "#UnsecuredXp.Text",
                    String.format("Unsecured XP: %s", formatXpShort(bank.pendingXp())));
        }

        if (status == null) {
            changed |= setText(b, "#WaveCount.Text", "Wave: --/--");
            changed |= setText(b, "#RemainingMobs.Text", "Remaining Mobs: --");
            changed |= hideHints(b);
            return changed;
        }

        changed |= setText(b, "#WaveCount.Text",
                String.format("Wave: %d/%d", status.currentWave(), status.totalWaves()));
        changed |= setText(b, "#RemainingMobs.Text",
                String.format("Remaining Mobs: %d / %d", status.remainingMobs(), status.totalSpawnedInWave()));

        List<String> hints = mgr.getTrackerHintLines(world);
        boolean showHints = !hints.isEmpty();
        changed |= setVisible(b, "#CoordsHeading.Visible", showHints);

        for (int i = 0; i < HINT_SELECTORS.length; i++) {
            String sel = HINT_SELECTORS[i];
            boolean visible = showHints && i < hints.size();
            changed |= setVisible(b, sel + ".Visible", visible);
            if (visible) {
                changed |= setText(b, sel + ".Text", String.format("%d. %s", i + 1, hints.get(i)));
            } else {
                changed |= setText(b, sel + ".Text", "");
            }
        }
        return changed;
    }

    private boolean hideHints(@Nonnull UICommandBuilder b) {
        boolean changed = false;
        changed |= setVisible(b, "#CoordsHeading.Visible", false);
        for (String sel : HINT_SELECTORS) {
            changed |= setVisible(b, sel + ".Visible", false);
            changed |= setText(b, sel + ".Text", "");
        }
        return changed;
    }

    // ========================================================================
    // Open / Close / Unregister
    // ========================================================================

    public static OpenStatus open(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null || !playerRef.isValid()) return OpenStatus.PLAYER_INVALID;

        synchronized (getHudLock(uuid)) {
            OutlanderBridgeWaveHud newHud = new OutlanderBridgeWaveHud(playerRef);
            ACTIVE_HUDS.put(uuid, newHud);

            if (MultipleHudCompatibility.showHud(player, playerRef, MULTI_HUD_SLOT, newHud)) {
                return OpenStatus.OPENED;
            }

            var hudManager = player.getHudManager();
            var existing = hudManager.getCustomHud();
            // Don't override another mod's HUD (but do override our own hide/wave hud)
            if (existing != null
                    && !(existing instanceof OutlanderBridgeWaveHud)
                    && !(existing instanceof OutlanderBridgeWaveHudHide)) {
                ACTIVE_HUDS.remove(uuid);
                return OpenStatus.BLOCKED_BY_EXISTING_HUD;
            }

            hudManager.setCustomHud(playerRef, null);
            hudManager.setCustomHud(playerRef, newHud);
            return OpenStatus.OPENED;
        }
    }

    public static void close(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) return;

        synchronized (getHudLock(uuid)) {
            ACTIVE_HUDS.remove(uuid);

            if (MultipleHudCompatibility.showHud(player, playerRef, MULTI_HUD_SLOT,
                    new OutlanderBridgeWaveHudHide(playerRef))) {
                return;
            }

            var hudManager = player.getHudManager();
            var existing = hudManager.getCustomHud();
            if (existing instanceof OutlanderBridgeWaveHud || existing instanceof OutlanderBridgeWaveHudHide) {
                hudManager.setCustomHud(playerRef, null);
            }
        }
    }

    public static void unregister(@Nullable UUID uuid) {
        if (uuid == null) return;
        synchronized (getHudLock(uuid)) {
            OutlanderBridgeWaveHud removed = ACTIVE_HUDS.remove(uuid);
            if (removed != null) removed.built.set(false);
        }
    }

    // ========================================================================
    // Static queries
    // ========================================================================

    public static boolean hasActiveHuds() { return !ACTIVE_HUDS.isEmpty(); }

    @Nonnull
    public static Set<UUID> getActiveHudUuids() { return Set.copyOf(ACTIVE_HUDS.keySet()); }

    public static boolean isHudInStore(@Nullable UUID uuid, @Nullable Store<EntityStore> store) {
        if (uuid == null || store == null) return false;
        OutlanderBridgeWaveHud hud = ACTIVE_HUDS.get(uuid);
        if (hud == null || !hud.built.get()) return false;
        Ref<EntityStore> ref = hud.targetPlayerRef.getReference();
        return ref != null && ref.getStore() == store;
    }

    public static void refreshHudNow(@Nullable UUID uuid, @Nonnull Store<EntityStore> store) {
        if (uuid == null) return;
        OutlanderBridgeWaveHud hud = ACTIVE_HUDS.get(uuid);
        if (hud == null || !hud.built.get()) return;
        if (!hud.targetPlayerRef.isValid()) { unregister(uuid); return; }
        hud.refreshHud(store);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private boolean setText(@Nonnull UICommandBuilder b, @Nonnull String sel, String val) {
        String norm = val == null ? "" : val;
        Object prev = lastUiState.get(sel);
        if (Objects.equals(prev, norm)) return false;
        lastUiState.put(sel, norm);
        b.set(sel, norm);
        return true;
    }

    private boolean setVisible(@Nonnull UICommandBuilder b, @Nonnull String sel, boolean val) {
        Object prev = lastUiState.get(sel);
        if (Objects.equals(prev, val)) return false;
        lastUiState.put(sel, val);
        b.set(sel, val);
        return true;
    }

    private static Object getHudLock(@Nonnull UUID uuid) {
        return HUD_LOCKS.computeIfAbsent(uuid, k -> new Object());
    }

    @Nonnull
    private static String formatXpShort(double xp) {
        long rounded = Math.max(0L, Math.round(xp));
        if (rounded < 1_000L) return Long.toString(rounded);
        if (rounded < 1_000_000L)
            return String.format(java.util.Locale.ROOT, "%.1fk", rounded / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%.2fM", rounded / 1_000_000.0);
    }

    public enum OpenStatus {
        OPENED,
        BLOCKED_BY_EXISTING_HUD,
        PLAYER_INVALID
    }
}
