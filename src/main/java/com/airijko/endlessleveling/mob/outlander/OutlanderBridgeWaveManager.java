package com.airijko.endlessleveling.mob.outlander;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.util.NPCPhysicsMath;
import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.ui.OutlanderBridgeRewardsPage;
import com.airijko.endlessleveling.ui.OutlanderBridgeWaveHud;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ECS-driven wave manager for the Outlander Bridge game mode.
 * <p>
 * Extends {@link TickingSystem} so wave sessions are tied to the instance
 * world's entity store lifecycle — when the instance is removed the store
 * shuts down and the system stops ticking automatically for that store.
 * No {@code ScheduledExecutorService} needed; all timing uses
 * {@code deltaSeconds} accumulation.
 */
public final class OutlanderBridgeWaveManager extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final OutlanderBridgeWaveManager INSTANCE = new OutlanderBridgeWaveManager();

    // Instance worlds auto-named by Hytale from PortalType InstanceId `Endless_Outlander_Bridge`
    // yield `instance-Endless_Outlander_Bridge-<uuid>` (safeName only replaces `/`). Lowered
    // check matches `instance-endless_outlander_bridge-`.
    public static final String WORLD_PREFIX = "instance-endless_outlander_bridge-";
    public static final String WORLD_BASE_NAME = "endless";
    public static final String WAVES_RESOURCE = "/waves/outlander_bridge_waves.json";

    // ---- Outlander Bridge geometry ----
    private static final Vector3d CENTER = new Vector3d(0.0, 80.0, 0.0);
    private static final Vector3d SPAWN_LEFT = new Vector3d(-50.0, 80.0, 0.0);
    private static final Vector3d SPAWN_RIGHT = new Vector3d(50.0, 80.0, 0.0);
    private static final double SPAWN_Z_HALF_RANGE = 8.0;

    // ---- Tuning ----
    private static final float COUNTDOWN_SECONDS = 15.0f;
    private static final long TIER_INFO_RETRY_DELAY_MS = 5000L;
    private static final String WAVE_10_SECOND_COUNTDOWN_SOUND_ID = "SFX_EL_DungeonBreak_10_Second_Countdown";
    private static final float INTER_WAVE_DELAY_SECONDS = 5.0f;
    /** Grace delay after final wave clear before the rewards panel opens. */
    private static final float VICTORY_GRACE_SECONDS = 5.0f;
    /** Duration of the rewards-open window (also the dungeon-close countdown). */
    private static final float REWARDS_WINDOW_SECONDS = 60.0f;
    /** Post-timeout buffer before safeRemoveInstance so teleports drain cleanly. */
    private static final long INSTANCE_REMOVE_DELAY_MS = 500L;
    private static final float WAVE_TICK_INTERVAL = 1.0f;
    private static final int DEFAULT_BATCH_KILL_PERCENT = 70;
    private static final int DEFAULT_BATCH_FALLBACK_SECONDS = 25;
    private static final double DEFAULT_SPAWN_RADIUS = 18.0;
    private static final double LEASH_RADIUS = 150.0;
    private static final double AGGRO_RADIUS = 500.0;
    private static final double MIN_SPAWN_Y = 80.0;
    // Bridge combat zone: 30-block radius around (0,0). Mobs are pulled toward
    // X = ±15 (their own side) rather than CENTER so two opposing fronts form
    // on the deck. After PULL_GRACE_SECONDS any mob still outside the 30-block
    // combat zone is snapped back onto its side line at X = ±15, Z scattered
    // in [-5, +5] to avoid pile-up at a single point.
    private static final double PULL_X_ABS = 15.0;
    private static final double COMBAT_ZONE_RADIUS = 30.0;
    private static final double COMBAT_ZONE_RADIUS_SQ = COMBAT_ZONE_RADIUS * COMBAT_ZONE_RADIUS;
    private static final double SNAPBACK_Z_HALF_RANGE = 5.0;
    private static final float PULL_GRACE_SECONDS = 10.0f;
    private static final double AGGRO_RADIUS_SQ = AGGRO_RADIUS * AGGRO_RADIUS;
    private static final double BOUNDS_HORIZONTAL_MULTIPLIER = 1.6;
    private static final double BOUNDS_VERTICAL_BELOW = 12.0;
    private static final double BOUNDS_VERTICAL_ABOVE = 48.0;
    private static final int DRY_LAND_ATTEMPTS = 12;
    private static final int BOUNDS_RECOVERY_ATTEMPTS = 16;
    private static final int AIR_BLOCK_ID = 0;
    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y = 319;
    private static final float BOSS_SIZE_SCALE = 1.8f;

    // ---- No-kill hint ----
    private static final float NO_KILL_HINT_INTERVAL_SECONDS = 10.0f;
    private static final int NO_KILL_HINT_MAX_LISTED = 12;

    // ---- SFX ----
    private static final String HORN_SOUND_ID = "SFX_EL_Medieval_Horn";
    private static final String COUNTDOWN_10S_SOUND_ID = "SFX_EL_DungeonBreak_10_Second_Countdown";

    // ---- Combat music via SoundUtil.playSoundEvent2d (SoundCategory.Music).
    // Played directly to each player as a 2D SFX routed to the Music audio
    // category. Per-player cooldown prevents duplicate plays when players
    // teleport back/forth within the track duration (~260s).
    private static final String COMBAT_MUSIC_SOUND_ID = "SFX_EL_OutlanderBridge_Combat_Music";
    private static final long COMBAT_MUSIC_COOLDOWN_MS = 260_000L; // ~4m 20s (ogg is 259.3s)
    private final ConcurrentHashMap<UUID, Long> musicCooldowns = new ConcurrentHashMap<>();

    // ---- Return portal hide/restore during active waves ----
    private static final List<String> RETURN_PORTAL_BLOCK_IDS = List.of(
            "Portal_Return", "Return_Portal");
    private static final int PORTAL_SCAN_RADIUS = 8;
    private static final int PORTAL_SCAN_Y_MIN = 76;
    private static final int PORTAL_SCAN_Y_MAX = 86;
    private static volatile int[] returnPortalBlockIntIdsCache;

    // ---- Aggro target-memory slots ----
    private static final String[] AGGRO_TARGET_SLOTS = {
            "LockedTarget", "Target", "EnemyTarget", "Enemy",
            "CombatTarget", "PrimaryTarget", "Attacker", "Hostile"
    };

    // ---- Config / wave definitions (loaded once) ----
    private final List<WaveDef> waves = new ArrayList<>();
    private int configSpawnRadius = (int) DEFAULT_SPAWN_RADIUS;
    private int configBatchKillPercent = DEFAULT_BATCH_KILL_PERCENT;
    private int configBatchFallbackSeconds = DEFAULT_BATCH_FALLBACK_SECONDS;
    private final Random random = new Random();

    // ---- Per-world sessions keyed by world UUID ----
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    // ---- Completed worlds — prevents session re-creation after victory ----
    private final Set<UUID> completedWorldIds = ConcurrentHashMap.newKeySet();

    // ---- Force-start requests from command thread (picked up by next tick) ----
    private final Set<UUID> pendingForceStarts = ConcurrentHashMap.newKeySet();

    private OutlanderBridgeWaveManager() {}

    public static OutlanderBridgeWaveManager get() { return INSTANCE; }

    // ========================================================================
    // ECS tick — called every frame for every active store
    // ========================================================================

    @Override
    public void tick(float deltaSeconds, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (store.isShutdown()) return;
        if (waves.isEmpty()) return;

        EntityStore ext = store.getExternalData();
        if (ext == null) return;
        World world = ext.getWorld();
        if (world == null || !isOutlanderBridgeWorld(world)) return;

        UUID worldId = world.getWorldConfig().getUuid();
        Session s = sessions.get(worldId);

        // ---- Session creation ----
        // Every outlander-bridge world is a unique instance-endless-<uuid> (see
        // isOutlanderBridgeWorld). Sessions are keyed by worldId so each instance
        // gets its own wave state with no overlap. Auto-create on first
        // player entry; force-start skips countdown.
        if (s == null) {
            if (completedWorldIds.contains(worldId)) return;
            boolean hasForceStart = pendingForceStarts.contains(worldId);
            if (!hasPlayersInWorld(world) && !hasForceStart) return;

            s = new Session(world, store);
            sessions.put(worldId, s);
            if (pendingForceStarts.remove(worldId)) {
                lockTierOffsetForSession(s);
                openHudForWorldPlayers(world);
                beginWave(s, 1);
                return;
            }
            onSessionCreated(s);
            return;
        }

        // Guard: store identity match
        if (s.store != store) return;

        // Check pending force-start on existing countdown session
        if (s.phase == Phase.COUNTDOWN && pendingForceStarts.remove(worldId)) {
            beginWave(s, 1);
            return;
        }

        s.tickAccumulator += deltaSeconds;
        s.sessionElapsedSeconds += deltaSeconds;

        // Per-frame forced motion toward CENTER. Runs every tick (not throttled)
        // so forceVelocity is refreshed before steering decays it. Guarantees
        // mobs walk toward 0,79,0 even with no valid aggro target.
        if (s.phase == Phase.WAVE_ACTIVE) {
            driveMobsToCenter(s, store, deltaSeconds);
        }

        switch (s.phase) {
            case COUNTDOWN   -> tickCountdown(s);
            case WAVE_ACTIVE -> tickWaveActive(s, store);
            case WAVE_CLEARED -> tickWaveCleared(s);
            case COMPLETED   -> tickCompleted(s);
        }
    }

    // ========================================================================
    // Config loading
    // ========================================================================

    public void load() {
        waves.clear();
        try (InputStream in = OutlanderBridgeWaveManager.class.getResourceAsStream(WAVES_RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("Outlander Bridge waves resource missing: %s", WAVES_RESOURCE);
                return;
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            JsonObject rootObj = root.getAsJsonObject();

            if (rootObj.has("spawn_radius"))
                configSpawnRadius = rootObj.get("spawn_radius").getAsInt();
            if (rootObj.has("batch_kill_percent"))
                configBatchKillPercent = rootObj.get("batch_kill_percent").getAsInt();
            if (rootObj.has("batch_fallback_seconds"))
                configBatchFallbackSeconds = rootObj.get("batch_fallback_seconds").getAsInt();

            JsonArray wavesArr = rootObj.getAsJsonArray("waves");
            for (JsonElement we : wavesArr) {
                JsonObject wo = we.getAsJsonObject();
                int num = wo.has("wave") ? wo.get("wave").getAsInt() : (waves.size() + 1);
                WaveDef def = new WaveDef(num);

                if (wo.has("pools")) {
                    for (JsonElement pe : wo.getAsJsonArray("pools")) {
                        JsonObject po = pe.getAsJsonObject();
                        JsonArray batchArr = po.getAsJsonArray("mobs");
                        if (batchArr == null || batchArr.isEmpty()) continue;
                        JsonObject batchVariant = batchArr.get(random.nextInt(batchArr.size()))
                                .getAsJsonObject();
                        SpawnBatch batch = new SpawnBatch();
                        for (JsonElement me : batchVariant.getAsJsonArray("mobs")) {
                            JsonObject mo = me.getAsJsonObject();
                            batch.entries.add(new SpawnEntry(
                                    mo.get("id").getAsString(), mo.get("count").getAsInt()));
                        }
                        def.batches.add(batch);
                    }
                }

                if (wo.has("bosses")) {
                    for (JsonElement be : wo.getAsJsonArray("bosses"))
                        def.bossIds.add(be.getAsJsonObject().get("id").getAsString());
                }
                waves.add(def);
            }
            LOGGER.atInfo().log(
                    "Outlander Bridge: loaded %d waves (radius=%d, batchKill=%d%%, fallback=%ds)",
                    waves.size(), configSpawnRadius, configBatchKillPercent,
                    configBatchFallbackSeconds);
        } catch (Exception ex) {
            LOGGER.atSevere().withCause(ex).log("Failed to load Outlander Bridge wave config");
        }
    }

    // ========================================================================
    // World detection
    // ========================================================================

    public boolean isOutlanderBridgeWorld(@Nullable World world) {
        if (world == null) return false;
        String n = world.getName();
        if (n == null) return false;
        // Only ephemeral instance worlds run waves. Base "endless" world
        // must never host a session — otherwise a session spawns in the
        // lobby world while the portal teleports players to a different
        // instance-endless-<uuid>, producing ghost waves in wrong world.
        return n.toLowerCase(Locale.ROOT).startsWith(WORLD_PREFIX);
    }

    // ========================================================================
    // Session creation & HUD
    // ========================================================================

    private void onSessionCreated(@Nonnull Session s) {
        LOGGER.atInfo().log("Outlander Bridge: session created world=%s", s.world.getName());
        s.phase = Phase.COUNTDOWN;
        s.countdownRemaining = COUNTDOWN_SECONDS;

        lockTierOffsetForSession(s);
        registerAllPlayersForBanking(s);

        // Rich formatted join message with tier/level info
        sendOutlanderJoinNotification(s.world, s.store);

        // Countdown announcement
        broadcastRich(s.world, Message.join(
                Message.raw("Waves begin in ").color("#f3c27a"),
                Message.raw(String.valueOf((int) COUNTDOWN_SECONDS) + "s").color("#4fd7f7"),
                Message.raw("  |  Use ").color("#9e9e9e"),
                Message.raw("/lvl start").color("#8effb6"),
                Message.raw(" to skip").color("#9e9e9e")));

        showTitleToWorld(s.world,
                Message.raw("OUTLANDER BRIDGE").color("#f8d66d"),
                Message.raw(String.format(Locale.ROOT,
                        "Waves begin in %ds", (int) COUNTDOWN_SECONDS)).color("#8fd3ff"));

        playCombatMusicToWorld(s.world, s.store);
        openHudForWorldPlayers(s.world);
    }

    /** Called from AddPlayerToWorldEvent — open HUD + tiered join notification for late joiners. */
    public void onPlayerEntered(@Nonnull World world, @Nullable Holder<EntityStore> holder) {
        UUID key = world.getWorldConfig().getUuid();
        Session s = isOutlanderBridgeWorld(world) ? sessions.get(key) : null;

        if (s == null) {
            // Non-outlander world OR outlander instance that hasn't had its first tick
            // yet — force-close any stale HUD the client may still render
            // (post-restart ghosts). Session will open its own HUD on tick.
            closeHudForWorldPlayers(world);
            return;
        }

        openHudForWorldPlayers(world);

        UUID sessionWorldId = world.getWorldConfig() != null
                ? world.getWorldConfig().getUuid() : null;

        // Open HUD directly for the joining player via Holder — they may not
        // yet appear in world.getPlayerRefs() at event fire time.
        if (holder != null) {
            PlayerRef joiningRef = holder.getComponent(PlayerRef.getComponentType());
            if (joiningRef != null && joiningRef.isValid()) {
                UUID joiningUuid = joiningRef.getUuid();

                // Re-entry guard: if this player was already locked out of this
                // session (claimed/cancelled/timed out), immediately kick them.
                // TP-exploit defense — they cannot resume banking. Wrapped in
                // try/catch so any failure inside the banking subsystem cannot
                // block the world-join handshake.
                try {
                    if (joiningUuid != null && sessionWorldId != null
                            && OutlanderBridgeXpBank.get()
                                    .isLockedFromSession(joiningUuid, sessionWorldId)) {
                        LOGGER.atInfo().log(
                                "Outlander Bridge: locked player=%s re-entered instance=%s, re-kicking",
                                joiningUuid, world.getName());
                        kickPlayerFromInstance(joiningUuid);
                        return;
                    }

                    if (joiningUuid != null && sessionWorldId != null) {
                        OutlanderBridgeXpBank.get()
                                .registerPlayerForSession(joiningUuid, sessionWorldId);
                    }
                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log(
                            "Outlander Bridge: banking hook failed for player=%s; continuing world-join",
                            joiningUuid);
                }

                Ref<EntityStore> ref = joiningRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> st = ref.getStore();
                    if (st != null) {
                        Player player = st.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            OutlanderBridgeWaveHud.open(player, joiningRef);
                        }
                    }
                }
                sendTierInfoToPlayer(joiningRef, s.store);
            }
        }

        playCombatMusicToWorld(world, s.store);

        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            sendTierInfoToPlayer(pr, s.store);
        }
    }

    /** Called from DrainPlayerFromWorldEvent — unregister HUD + banking. */
    public void onPlayerDrain(@Nullable UUID playerUuid) {
        if (playerUuid != null) {
            OutlanderBridgeWaveHud.unregister(playerUuid);
            OutlanderBridgeXpBank.get().onPlayerDrain(playerUuid);
        }
    }

    /**
     * Returns true if the given world has an active wave session bound
     * to its UUID. Used by HUD refresh/sweep systems to detect ghost HUDs.
     */
    public boolean hasActiveSession(@Nullable World world) {
        if (world == null) return false;
        return sessions.containsKey(world.getWorldConfig().getUuid());
    }

    /**
     * Force-start from /lvl start command. Thread-safe: stores a flag
     * that the next store tick picks up on the correct thread.
     */
    public boolean forceStart(@Nonnull World world) {
        if (!isOutlanderBridgeWorld(world)) return false;
        UUID worldId = world.getWorldConfig().getUuid();
        Session s = sessions.get(worldId);
        if (s != null && s.phase != Phase.COUNTDOWN) return false;
        pendingForceStarts.add(worldId);
        return true;
    }

    // ========================================================================
    // Phase: COUNTDOWN
    // ========================================================================

    private void tickCountdown(@Nonnull Session s) {
        float before = s.countdownRemaining;
        s.countdownRemaining -= s.consumeAccumulator();
        // Play 10s countdown sound when crossing the 10s mark
        if (before > 10.0f && s.countdownRemaining <= 10.0f) {
            playSoundToWorld(s.world, WAVE_10_SECOND_COUNTDOWN_SOUND_ID);
        }
        if (s.countdownRemaining <= 0.0f) {
            beginWave(s, 1);
        }
    }

    // ========================================================================
    // Phase: WAVE_ACTIVE
    // ========================================================================

    private void tickWaveActive(@Nonnull Session s, @Nonnull Store<EntityStore> store) {
        float consumed = s.consumeAccumulatorThrottled(WAVE_TICK_INTERVAL);
        if (consumed <= 0.0f) return;

        if (store.isShutdown()) return;

        // --- Death detection ---
        int aliveBefore = s.lastAliveMobCount;
        pruneDeadMobs(s, store);
        int aliveAfter = s.activeMobs.size();

        if (aliveAfter < aliveBefore) {
            s.secondsSinceLastKill = 0.0f;
            s.secondsSinceLastHint = 0.0f;
        } else {
            s.secondsSinceLastKill += consumed;
        }
        s.lastAliveMobCount = aliveAfter;

        // --- Mob bounds + aggro ---
        enforceMobBounds(s, store);

        // --- Batch-advance trigger ---
        if (!s.allBatchesSpawned && s.batchAge >= 0.0f) {
            s.batchAge += consumed;
            int totalKills = s.totalSpawnedInWave - aliveAfter;
            int killsSinceBatch = totalKills - s.killsAtBatchStart;
            int killThreshold = (int) Math.ceil(
                    s.currentBatchSpawnedCount * configBatchKillPercent / 100.0);

            boolean allCurrentDead = s.activeMobs.isEmpty();
            if (allCurrentDead
                    || killsSinceBatch >= killThreshold
                    || s.batchAge >= configBatchFallbackSeconds) {
                spawnNextBatch(s, s.currentWave, store);
            }

            maybeSendNoKillHint(s, consumed);
            return;
        }

        // --- Wave clear detection ---
        if (!s.activeMobs.isEmpty()) {
            maybeSendNoKillHint(s, consumed);
            return;
        }

        // All mobs dead + all batches spawned → wave cleared. Checkpoint
        // the XP bank: every player's pending XP is now locked into saved.
        OutlanderBridgeXpBank.get().checkpointSession(s.world.getWorldConfig().getUuid());

        s.phase = Phase.WAVE_CLEARED;
        s.waveClearedElapsed = 0.0f;
        s.nextWaveCountdownFired = false;

        if (s.currentWave >= waves.size()) {
            showTitleToWorld(s.world,
                    Message.raw("WAVE COMPLETE").color("#6cff78"),
                    Message.raw(String.format(Locale.ROOT,
                            "%d waves finished", waves.size())).color("#d5f7db"));
            broadcast(s.world, "[Outlander Bridge] All waves cleared. Victory!", "#8effb6");
            playSoundToWorld(s.world, HORN_SOUND_ID);
            restoreReturnPortals(s);
            s.phase = Phase.COMPLETED;
            s.completedTimer = 0.0f;
            s.rewardsOpened = false;
            s.lastCountdownSecond = -1;
            return;
        }

        broadcast(s.world, String.format(Locale.ROOT,
                "[Outlander Bridge] Wave %d cleared. Next in %ds...",
                s.currentWave, (int) INTER_WAVE_DELAY_SECONDS), "#8effb6");
    }

    // ========================================================================
    // Phase: COMPLETED — victory grace + rewards countdown + timeout
    // ========================================================================

    private void tickCompleted(@Nonnull Session s) {
        s.completedTimer += s.consumeAccumulator();

        // Victory grace: wait 5s, then open rewards panel for every player
        // with saved XP > 0.
        if (!s.rewardsOpened && s.completedTimer >= VICTORY_GRACE_SECONDS) {
            s.rewardsOpened = true;
            openVictoryRewardsPanels(s);
        }

        if (!s.rewardsOpened) return;

        // After grace, the 60-second dungeon-close countdown runs.
        float windowElapsed = s.completedTimer - VICTORY_GRACE_SECONDS;
        int secondsLeft = Math.max(0,
                (int) Math.ceil(REWARDS_WINDOW_SECONDS - windowElapsed));

        if (secondsLeft != s.lastCountdownSecond) {
            s.lastCountdownSecond = secondsLeft;
            for (PlayerRef pr : s.world.getPlayerRefs()) {
                if (pr == null || !pr.isValid()) continue;
                UUID uuid = pr.getUuid();
                if (uuid == null) continue;
                OutlanderBridgeRewardsPage.tickCountdown(uuid, secondsLeft);
            }
        }

        if (windowElapsed >= REWARDS_WINDOW_SECONDS) {
            finalizeRewardsTimeout(s);
        }
    }

    private void openVictoryRewardsPanels(@Nonnull Session s) {
        UUID worldId = s.world.getWorldConfig().getUuid();
        int countdown = (int) REWARDS_WINDOW_SECONDS;
        for (PlayerRef pr : s.world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            UUID uuid = pr.getUuid();
            if (uuid == null) continue;
            double saved = OutlanderBridgeXpBank.get().getSavedXp(worldId, uuid);
            if (saved <= 0.0) continue;
            OutlanderBridgeRewardsPage.openFor(pr, worldId, saved, countdown, true);
        }
    }

    // ========================================================================
    // Phase: WAVE_CLEARED — inter-wave delay
    // ========================================================================

    private void tickWaveCleared(@Nonnull Session s) {
        s.waveClearedElapsed += s.consumeAccumulator();

        if (!s.nextWaveCountdownFired
                && INTER_WAVE_DELAY_SECONDS > 10.0f
                && s.waveClearedElapsed >= INTER_WAVE_DELAY_SECONDS - 10.0f
                && s.currentWave < waves.size()) {
            s.nextWaveCountdownFired = true;
            showTitleToWorld(s.world,
                    Message.raw("NEXT WAVE IN 10s").color("#ffd27a"),
                    Message.raw(String.format(Locale.ROOT, "Wave %d/%d incoming",
                            s.currentWave + 1, waves.size())).color("#f0d6a8"));
            playSoundToWorld(s.world, COUNTDOWN_10S_SOUND_ID);
        }

        if (s.waveClearedElapsed >= INTER_WAVE_DELAY_SECONDS) {
            beginWave(s, s.currentWave + 1);
        }
    }

    // ========================================================================
    // Wave start
    // ========================================================================

    private void beginWave(@Nonnull Session s, int waveNumber) {
        if (waveNumber > waves.size()) {
            showTitleToWorld(s.world,
                    Message.raw("WAVE COMPLETE").color("#6cff78"),
                    Message.raw(String.format(Locale.ROOT,
                            "%d waves finished", waves.size())).color("#d5f7db"));
            broadcast(s.world, "[Outlander Bridge] All waves cleared. Victory!", "#8effb6");
            playSoundToWorld(s.world, HORN_SOUND_ID);
            restoreReturnPortals(s);
            s.phase = Phase.COMPLETED;
            s.completedTimer = 0.0f;
            return;
        }

        if (waveNumber == 1) {
            playCombatMusicToWorld(s.world, s.store);
            hideReturnPortals(s);
        }

        s.phase = Phase.WAVE_ACTIVE;
        s.currentWave = waveNumber;
        s.activeMobs.clear();
        s.activeMobNames.clear();
        s.mobSpawnTimes.clear();
        s.waveClearedElapsed = 0.0f;
        s.nextWaveCountdownFired = false;
        s.secondsSinceLastKill = 0.0f;
        s.secondsSinceLastHint = 0.0f;
        s.totalSpawnedInWave = 0;
        s.lastAliveMobCount = 0;
        s.waveTickRemainder = 0.0f;
        s.tickAccumulator = 0.0f;

        WaveDef def = waves.get(waveNumber - 1);
        s.partyBonusPerEntry = resolvePartyBonus(s.world);
        int totalEntries = 0;
        for (SpawnBatch b : def.batches) totalEntries += b.entries.size();
        int totalMobs = def.totalMobCount() + s.partyBonusPerEntry * totalEntries;
        int bossCount = def.bossIds.size();
        int batchCount = Math.max(1, def.batches.size());

        showTitleToWorld(s.world,
                Message.raw(String.format(Locale.ROOT,
                        "WAVE %d / %d", waveNumber, waves.size())).color("#f8d66d"),
                Message.raw(String.format(Locale.ROOT,
                        "%d mobs + %d boss%s in %d batch%s",
                        totalMobs, bossCount,
                        bossCount == 1 ? "" : "es",
                        batchCount,
                        batchCount == 1 ? "" : "es")).color("#8fd3ff"));
        broadcast(s.world, String.format(Locale.ROOT,
                "[Outlander Bridge] Wave %d/%d: %d mobs + %d boss(es)",
                waveNumber, waves.size(), totalMobs, bossCount), "#f3b37a");
        playSoundToWorld(s.world, HORN_SOUND_ID);

        // Boss indices — random slots in the mob spawn order
        Set<Integer> bossIndices = new HashSet<>();
        if (totalMobs > 0) {
            while (bossIndices.size() < Math.min(bossCount, totalMobs))
                bossIndices.add(random.nextInt(totalMobs));
        }

        s.currentWaveBatches = def.batches.isEmpty()
                ? buildLegacyBatches(def) : def.batches;
        s.bossIndices = bossIndices;
        s.bossIds = def.bossIds;
        s.pendingBatchIndex = 0;
        s.allBatchesSpawned = false;
        s.currentBatchSpawnedCount = 0;
        s.batchAge = -1.0f;
        s.killsAtBatchStart = 0;
        s.currentWaveMobIndexOffset = 0;

        if (batchCount > 1) {
            broadcast(s.world, String.format(Locale.ROOT,
                    "[Outlander Bridge] %d mobs in %d batches | next batch after %d%% kills or %ds",
                    totalMobs, batchCount, configBatchKillPercent,
                    configBatchFallbackSeconds), "#d9c88d");
        }

        // Spawn first batch — already on store thread
        spawnNextBatch(s, waveNumber, s.store);
    }

    // ========================================================================
    // Batch spawning (runs on store thread via tick)
    // ========================================================================

    private void spawnNextBatch(@Nonnull Session s, int waveNumber,
                                @Nonnull Store<EntityStore> store) {
        if (s.allBatchesSpawned || s.currentWave != waveNumber) return;

        List<SpawnBatch> batches = s.currentWaveBatches;
        int batchIdx = s.pendingBatchIndex;
        if (batches == null || batchIdx >= batches.size()) {
            s.allBatchesSpawned = true;
            return;
        }

        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) { LOGGER.atWarning().log("NPCPlugin null"); return; }

        SpawnBatch batch = batches.get(batchIdx);
        int globalOffset = s.currentWaveMobIndexOffset;
        int batchSpawned = 0;
        int localIndex = 0;
        StringBuilder batchDesc = new StringBuilder();

        int bonus = s.partyBonusPerEntry;
        int batchTotalCount = 0;
        for (SpawnEntry entry : batch.entries) {
            int effectiveCount = entry.count + bonus;
            batchTotalCount += effectiveCount;
            if (!batchDesc.isEmpty()) batchDesc.append(" + ");
            batchDesc.append(effectiveCount).append("x ").append(entry.id);

            for (int i = 0; i < effectiveCount; i++) {
                Vector3d spawnOrigin = (localIndex % 2 == 0) ? SPAWN_LEFT : SPAWN_RIGHT;
                Vector3d spawnPos = findFlankSpawnPosition(
                        s.world, spawnOrigin, SPAWN_Z_HALF_RANGE, DRY_LAND_ATTEMPTS);
                if (spawnPos == null) {
                    double fallbackZ = spawnOrigin.z
                            + (random.nextDouble() * 2.0 - 1.0) * SPAWN_Z_HALF_RANGE;
                    spawnPos = new Vector3d(spawnOrigin.x, spawnOrigin.y, fallbackZ);
                }

                float yaw = (float) Math.atan2(
                        CENTER.x - spawnPos.x, CENTER.z - spawnPos.z);
                Vector3f rot = new Vector3f(0f, yaw, 0f);

                int globalMobIndex = globalOffset + localIndex;
                boolean spawnAsBoss = s.bossIndices.contains(globalMobIndex);

                String roleName;
                if (spawnAsBoss && !s.bossIds.isEmpty()) {
                    roleName = s.bossIds.get(random.nextInt(s.bossIds.size()));
                } else {
                    roleName = entry.id;
                }

                Object result = null;
                try {
                    result = npc.spawnNPC(store, roleName, null, spawnPos, rot);
                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log(
                            "Outlander Bridge: spawn failed id=%s", roleName);
                }

                Ref<EntityStore> ref = extractRef(result);
                if (ref != null) {
                    s.activeMobs.add(ref);
                    s.activeMobNames.put(ref, roleName);
                    s.mobSpawnTimes.put(ref, s.sessionElapsedSeconds);
                    batchSpawned++;

                    TransformComponent mobTr = store.getComponent(ref,
                            Objects.requireNonNull(TransformComponent.getComponentType()));
                    Vector3d mobPos = (mobTr != null && mobTr.getPosition() != null)
                            ? mobTr.getPosition() : spawnPos;
                    activateWaveMobAggro(s.world, ref, mobPos, store);

                    if (spawnAsBoss) {
                        applyBossSizeScale(store, ref);
                        broadcast(s.world, String.format(Locale.ROOT,
                                "[Outlander Bridge] Boss spawned: %s", roleName), "#f3b37a");
                    }
                }
                localIndex++;
            }
        }

        s.currentWaveMobIndexOffset = globalOffset + batchTotalCount;
        s.totalSpawnedInWave += batchSpawned;
        s.currentBatchSpawnedCount = batchSpawned;
        s.batchAge = 0.0f;
        s.killsAtBatchStart = s.totalSpawnedInWave - s.activeMobs.size();
        s.pendingBatchIndex++;

        if (s.pendingBatchIndex >= batches.size()) s.allBatchesSpawned = true;

        broadcast(s.world, String.format(Locale.ROOT,
                "[Outlander Bridge] Wave %d/%d | Batch %d/%d | %s",
                waveNumber, waves.size(),
                batchIdx + 1, batches.size(), batchDesc), "#8fd3ff");

        if (batchIdx > 0 && batchSpawned > 0) {
            playSoundToWorld(s.world, HORN_SOUND_ID);
            showTitleToWorld(s.world,
                    Message.raw("REINFORCEMENTS INCOMING").color("#ff9a5c"),
                    Message.raw(String.format(Locale.ROOT,
                            "Batch %d/%d | +%d mobs",
                            batchIdx + 1, batches.size(), batchSpawned)).color("#f0d6a8"));
        }

        s.lastAliveMobCount = s.activeMobs.size();
        LOGGER.atInfo().log("Outlander Bridge: wave %d batch %d/%d spawned %d mobs",
                s.currentWave, batchIdx + 1, batches.size(), batchSpawned);
    }

    // ========================================================================
    // Death detection (DeathComponent check — on store thread)
    // ========================================================================

    private static void pruneDeadMobs(@Nonnull Session s,
                                      @Nonnull Store<EntityStore> store) {
        List<Ref<EntityStore>> alive = new ArrayList<>();
        Map<Ref<EntityStore>, String> aliveNames = new IdentityHashMap<>();

        for (Ref<EntityStore> ref : s.activeMobs) {
            if (ref == null || !ref.isValid()) continue;
            DeathComponent death = store.getComponent(ref, DeathComponent.getComponentType());
            if (death != null) continue;
            alive.add(ref);
            String name = s.activeMobNames.get(ref);
            if (name != null) aliveNames.put(ref, name);
        }

        s.activeMobs.clear();
        s.activeMobs.addAll(alive);
        s.activeMobNames.clear();
        s.activeMobNames.putAll(aliveNames);
        s.mobSpawnTimes.keySet().retainAll(aliveNames.keySet());
    }

    // ========================================================================
    // Mob bounds enforcement + aggro refresh
    // ========================================================================

    private void enforceMobBounds(@Nonnull Session s,
                                  @Nonnull Store<EntityStore> store) {
        if (s.activeMobs.isEmpty()) return;

        double maxH = LEASH_RADIUS * BOUNDS_HORIZONTAL_MULTIPLIER;
        double maxHSq = maxH * maxH;
        double minY = CENTER.y - BOUNDS_VERTICAL_BELOW;
        double maxY = CENTER.y + BOUNDS_VERTICAL_ABOVE;

        for (Ref<EntityStore> mobRef : s.activeMobs) {
            if (mobRef == null || !mobRef.isValid()) continue;

            TransformComponent tr = store.getComponent(mobRef,
                    Objects.requireNonNull(TransformComponent.getComponentType()));
            if (tr == null || tr.getPosition() == null) continue;
            Vector3d pos = tr.getPosition();

            // Refresh aggro every tick. If a player is within our extended
            // AGGRO_RADIUS (500m), drive the mob toward them — the native role
            // sensor range is fixed at build time and shorter than 500m, so
            // relying on native chase beyond that would stall the mob.
            Vector3d targetPos = activateWaveMobAggro(s.world, mobRef, pos, store);
            if (targetPos != null) {
                chasePlayer(mobRef, pos, targetPos, store);
            } else {
                pullToward(mobRef, pos, store);
            }

            double dx = pos.x - CENTER.x;
            double dz = pos.z - CENTER.z;
            double hSq = dx * dx + dz * dz;
            if (hSq > maxHSq || pos.y < minY || pos.y > maxY) {
                Vector3d flank = (pos.x < 0) ? SPAWN_LEFT : SPAWN_RIGHT;
                Vector3d safe = findFlankSpawnPosition(
                        s.world, flank, SPAWN_Z_HALF_RANGE, BOUNDS_RECOVERY_ATTEMPTS);
                if (safe == null) {
                    safe = new Vector3d(flank.x, flank.y,
                            flank.z + (Math.random() * 2.0 - 1.0) * SPAWN_Z_HALF_RANGE);
                }
                tr.teleportPosition(safe);
            }
        }
    }

    // ========================================================================
    // Aggro: TargetMemory + marked-entity + ghost-hit
    // ========================================================================

    /**
     * Applies aggro to any player within {@link #AGGRO_RADIUS} of the mob.
     * <p>Returns the closest player's position so callers can drive the mob
     * toward that point — native sensor range is fixed per-role at build time
     * ({@code SensorEntityBase.range}, loaded from the role YAML), so beyond
     * that native cap we have to steer the mob ourselves to honor our custom
     * 500m aggro radius.
     * @return closest player's position if any within aggro range, else null.
     */
    @Nullable
    private static Vector3d activateWaveMobAggro(@Nonnull World world,
                                                 @Nonnull Ref<EntityStore> mobRef,
                                                 @Nonnull Vector3d mobPos,
                                                 @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> closest = null;
        Vector3d closestPos = null;
        double closestD2 = Double.MAX_VALUE;

        for (PlayerRef p : world.getPlayerRefs()) {
            if (p == null || !p.isValid()) continue;
            Ref<EntityStore> pRef = p.getReference();
            if (pRef == null || !pRef.isValid()) continue;
            Store<EntityStore> pStore = pRef.getStore();
            if (pStore == null) continue;

            TransformComponent pt = pStore.getComponent(pRef,
                    Objects.requireNonNull(TransformComponent.getComponentType()));
            if (pt == null || pt.getPosition() == null) continue;
            Vector3d pp = pt.getPosition();
            double dx = pp.x - mobPos.x;
            double dy = pp.y - mobPos.y;
            double dz = pp.z - mobPos.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > AGGRO_RADIUS_SQ) continue;

            forceAggroOnPlayer(mobRef, pRef, store);

            if (d2 < closestD2) {
                closestD2 = d2;
                closest = pRef;
                closestPos = pp;
            }
        }

        if (closest != null) {
            TargetMemory tm = store.getComponent(mobRef,
                    Objects.requireNonNull(TargetMemory.getComponentType()));
            if (tm != null) tm.setClosestHostile(closest);
            return closestPos;
        }
        return null;
    }

    /** Closest player position within AGGRO_RADIUS, or null. Does not mutate aggro state. */
    @Nullable
    private static Vector3d findClosestPlayerInAggro(@Nonnull World world, @Nonnull Vector3d mobPos) {
        Vector3d best = null;
        double bestD2 = Double.MAX_VALUE;
        for (PlayerRef p : world.getPlayerRefs()) {
            if (p == null || !p.isValid()) continue;
            Ref<EntityStore> pRef = p.getReference();
            if (pRef == null || !pRef.isValid()) continue;
            Store<EntityStore> pStore = pRef.getStore();
            if (pStore == null) continue;
            TransformComponent pt = pStore.getComponent(pRef,
                    Objects.requireNonNull(TransformComponent.getComponentType()));
            if (pt == null || pt.getPosition() == null) continue;
            Vector3d pp = pt.getPosition();
            double dx = pp.x - mobPos.x;
            double dy = pp.y - mobPos.y;
            double dz = pp.z - mobPos.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > AGGRO_RADIUS_SQ) continue;
            if (d2 < bestD2) { bestD2 = d2; best = pp; }
        }
        return best;
    }

    private static void pullToward(@Nonnull Ref<EntityStore> mobRef,
                                   @Nonnull Vector3d mobPos,
                                   @Nonnull Store<EntityStore> store) {
        // Side line pull: left-spawn mobs walk toward X=-15, right-spawn
        // toward X=+15, forming two fronts on the bridge. Z target is the
        // deck centerline (0), so mobs converge onto the combat strip.
        double targetX = mobPos.x < 0.0 ? -PULL_X_ABS : PULL_X_ABS;
        double dx = targetX - mobPos.x;
        double dz = -mobPos.z;
        double distH = Math.sqrt(dx * dx + dz * dz);
        if (distH < 3.0) return;

        NPCEntity npcE = store.getComponent(mobRef,
                Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcE == null) return;
        com.hypixel.hytale.server.npc.role.Role role = npcE.getRole();
        if (role == null) return;

        double inv = 1.0 / distH;
        double speed = 6.0; // m/s — forced stride toward side line
        Vector3d vel = new Vector3d(dx * inv * speed, 0.0, dz * inv * speed);
        // ignoreDamping=false so velocity decays and native steering can take
        // over once the mob settles — sticky overrides leave mobs frozen.
        role.forceVelocity(vel, null, false);
    }

    /**
     * Distance (m) at which we fully release and let native AI (chase +
     * combat) take over. Sized generously above typical role sensor ranges
     * (~16-24m) so native sensors have plenty of overlap to see the player
     * and engage on their own.
     */
    private static final double NATIVE_HANDOFF_DIST = 24.0;
    private static final double CHASE_SPEED = 6.0;

    /**
     * Long-range chase bridge: NPC role's vanilla sensor range is fixed at
     * build time ({@code SensorEntityBase.range}, sourced from YAML via
     * {@code PositionCache.requirePlayerDistanceSorted} during a
     * configuring-only phase — no runtime override). We extend effective
     * aggro to {@link #AGGRO_RADIUS} by pushing the mob toward the player
     * until it's within native sensor reach, then completely releasing so
     * native chase + combat AI engages.
     * <p>Inside {@link #NATIVE_HANDOFF_DIST} we do NOT touch velocity, leash,
     * or path. Continuing to push every frame was locking mobs in forced
     * motion next to the player.
     */
    private static void chasePlayer(@Nonnull Ref<EntityStore> mobRef,
                                    @Nonnull Vector3d mobPos,
                                    @Nonnull Vector3d playerPos,
                                    @Nonnull Store<EntityStore> store) {
        NPCEntity npcE = store.getComponent(mobRef,
                Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcE == null) return;
        com.hypixel.hytale.server.npc.role.Role role = npcE.getRole();
        if (role == null) return;

        double dx = playerPos.x - mobPos.x;
        double dz = playerPos.z - mobPos.z;
        double distH = Math.sqrt(dx * dx + dz * dz);
        if (distH < NATIVE_HANDOFF_DIST) {
            // Full handoff — native AI takes everything from here.
            return;
        }

        double inv = 1.0 / distH;
        Vector3d vel = new Vector3d(dx * inv * CHASE_SPEED, 0.0, dz * inv * CHASE_SPEED);
        // ignoreDamping=false → force decays between pushes so steering can
        // still operate. Prevents the "locked in forced motion" state.
        role.forceVelocity(vel, null, false);
    }

    // ========================================================================
    // Per-frame forced movement toward CENTER + stuck-nudge fallback
    // ========================================================================

    // Stuck-detection tuning.
    private static final float STUCK_TIMEOUT_SECONDS = 3.0f;
    private static final double STUCK_MIN_PROGRESS = 0.25; // m over 1s window
    private static final double TELEPORT_NUDGE_DISTANCE = 2.0; // m/nudge
    private static final float STUCK_SAMPLE_INTERVAL = 1.0f;

    private static void driveMobsToCenter(@Nonnull Session s,
                                          @Nonnull Store<EntityStore> store,
                                          float deltaSeconds) {
        if (s.activeMobs.isEmpty()) return;

        // Cheap global closest-player lookup — nulls out to no-op if empty world.
        Ref<EntityStore> globalClosestPlayer = findAnyPlayerRef(s.world);

        s.stuckSampleAccumulator += deltaSeconds;
        boolean sample = s.stuckSampleAccumulator >= STUCK_SAMPLE_INTERVAL;
        if (sample) s.stuckSampleAccumulator = 0.0f;

        for (Ref<EntityStore> mobRef : s.activeMobs) {
            if (mobRef == null || !mobRef.isValid()) continue;
            DeathComponent death = store.getComponent(mobRef, DeathComponent.getComponentType());
            if (death != null) continue;

            TransformComponent tr = store.getComponent(mobRef,
                    Objects.requireNonNull(TransformComponent.getComponentType()));
            if (tr == null || tr.getPosition() == null) continue;
            Vector3d pos = tr.getPosition();

            // If a player is within our extended AGGRO_RADIUS (500m), chase
            // the player directly — the native sensor range is shorter, so
            // leaving this to native chase would stall mobs mid-map.
            Vector3d chaseTarget = findClosestPlayerInAggro(s.world, pos);
            boolean hasAggro = chaseTarget != null;

            if (hasAggro) {
                chasePlayer(mobRef, pos, chaseTarget, store);
                if (globalClosestPlayer != null) {
                    lockAggro(mobRef, globalClosestPlayer, store);
                }
            } else {
                pullToward(mobRef, pos, store);
            }

            // Grace-period snapback: the 10s grace lets mobs walk in from
            // their spawn flank (outside the combat zone). After that, any
            // mob outside the 30-block combat zone around (0,0) is teleported
            // to its side line at X=±15, Z randomized in [-5, +5] so repeated
            // snapbacks don't pile every mob on the same point.
            Float spawnTime = s.mobSpawnTimes.get(mobRef);
            if (spawnTime != null
                    && s.sessionElapsedSeconds - spawnTime >= PULL_GRACE_SECONDS) {
                double originD2 = pos.x * pos.x + pos.z * pos.z;
                if (originD2 > COMBAT_ZONE_RADIUS_SQ) {
                    double side = pos.x < 0.0 ? -PULL_X_ABS : PULL_X_ABS;
                    double tpZ = (Math.random() * 2.0 - 1.0) * SNAPBACK_Z_HALF_RANGE;
                    tr.teleportPosition(new Vector3d(side, CENTER.y, tpZ));
                }
            }

            if (sample) {
                StuckState st = s.mobStuckStates.computeIfAbsent(mobRef, k -> new StuckState());

                // Aggro'd mobs are free — reset stuck tracking, no teleport.
                if (hasAggro) {
                    st.stuckSeconds = 0.0f;
                    st.lastDistToCenter = Double.MAX_VALUE;
                    continue;
                }

                double dx = CENTER.x - pos.x;
                double dz = CENTER.z - pos.z;
                double distH = Math.sqrt(dx * dx + dz * dz);

                if (distH < 3.0) {
                    st.stuckSeconds = 0.0f;
                    st.lastDistToCenter = distH;
                    continue;
                }

                if (st.lastDistToCenter - distH < STUCK_MIN_PROGRESS) {
                    st.stuckSeconds += STUCK_SAMPLE_INTERVAL;
                } else {
                    st.stuckSeconds = 0.0f;
                }
                st.lastDistToCenter = distH;

                if (st.stuckSeconds >= STUCK_TIMEOUT_SECONDS) {
                    double step = Math.min(TELEPORT_NUDGE_DISTANCE, distH);
                    double inv = 1.0 / distH;
                    Vector3d nudge = new Vector3d(
                            pos.x + dx * inv * step,
                            CENTER.y,
                            pos.z + dz * inv * step);
                    tr.teleportPosition(nudge);
                    st.stuckSeconds = 0.0f;
                    st.lastDistToCenter = Math.max(0.0, distH - step);
                }
            }
        }

        // GC stuck state for dead/removed mobs.
        if (sample) {
            s.mobStuckStates.keySet().removeIf(r -> r == null || !r.isValid()
                    || store.getComponent(r, DeathComponent.getComponentType()) != null);
        }
    }

    @Nullable
    private static Ref<EntityStore> findAnyPlayerRef(@Nonnull World world) {
        Ref<EntityStore> best = null;
        for (PlayerRef p : world.getPlayerRefs()) {
            if (p == null || !p.isValid()) continue;
            Ref<EntityStore> pRef = p.getReference();
            if (pRef == null || !pRef.isValid()) continue;
            best = pRef;
            break;
        }
        return best;
    }

    private static void lockAggro(@Nonnull Ref<EntityStore> mobRef,
                                  @Nonnull Ref<EntityStore> playerRef,
                                  @Nonnull Store<EntityStore> store) {
        if (!mobRef.isValid() || !playerRef.isValid()) return;
        TargetMemory tm = store.getComponent(mobRef,
                Objects.requireNonNull(TargetMemory.getComponentType()));
        if (tm != null) {
            Int2FloatOpenHashMap hostiles = tm.getKnownHostiles();
            int pIdx = playerRef.getIndex();
            float remember = tm.getRememberFor();
            if (hostiles.put(pIdx, remember) <= 0.0F)
                tm.getKnownHostilesList().add(playerRef);
            tm.setClosestHostile(playerRef);
        }
        NPCEntity npcE = store.getComponent(mobRef,
                Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcE != null) {
            com.hypixel.hytale.server.npc.role.Role role = npcE.getRole();
            if (role != null) {
                for (String slot : AGGRO_TARGET_SLOTS) role.setMarkedTarget(slot, playerRef);
            }
        }
    }

    private static void forceAggroOnPlayer(@Nonnull Ref<EntityStore> mobRef,
                                           @Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store) {
        if (!mobRef.isValid() || !playerRef.isValid()) return;

        TargetMemory tm = store.getComponent(mobRef,
                Objects.requireNonNull(TargetMemory.getComponentType()));
        if (tm != null) {
            Int2FloatOpenHashMap hostiles = tm.getKnownHostiles();
            int pIdx = playerRef.getIndex();
            float remember = tm.getRememberFor();
            if (hostiles.put(pIdx, remember) <= 0.0F)
                tm.getKnownHostilesList().add(playerRef);
            tm.setClosestHostile(playerRef);
        }

        NPCEntity npcE = store.getComponent(mobRef,
                Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcE != null) {
            com.hypixel.hytale.server.npc.role.Role role = npcE.getRole();
            if (role != null) {
                for (String slot : AGGRO_TARGET_SLOTS) role.setMarkedTarget(slot, playerRef);
            }
        }

        try {
            Damage ghost = new Damage(
                    new Damage.EntitySource(playerRef), DamageCause.PHYSICAL, 0.0F);
            DamageSystems.executeDamage(mobRef, store, ghost);
        } catch (Throwable ignored) {}
    }

    // ========================================================================
    // No-kill hint system
    // ========================================================================

    private void maybeSendNoKillHint(@Nonnull Session s, float consumed) {
        // Chat coord hints removed — HUD panel displays remaining mob coords.
        if (s.activeMobs.isEmpty()) return;
        if (s.secondsSinceLastKill < NO_KILL_HINT_INTERVAL_SECONDS) return;
        s.secondsSinceLastHint += consumed;
    }

    @Nonnull
    private static List<MobHintLine> buildMobCoordinateHints(@Nonnull Session s,
                                                             int maxLines) {
        List<MobHintLine> lines = new ArrayList<>();
        for (Ref<EntityStore> mobRef : s.activeMobs) {
            if (mobRef == null || !mobRef.isValid()) continue;
            Store<EntityStore> st = mobRef.getStore();
            if (st == null) continue;
            TransformComponent tr = st.getComponent(mobRef,
                    Objects.requireNonNull(TransformComponent.getComponentType()));
            if (tr == null || tr.getPosition() == null) continue;
            Vector3d pos = tr.getPosition();
            String name = s.activeMobNames.getOrDefault(mobRef, "Unknown");
            lines.add(new MobHintLine(name,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.y),
                    (int) Math.floor(pos.z)));
            if (lines.size() >= maxLines) break;
        }
        return lines;
    }

    // ========================================================================
    // Public API — used by OutlanderBridgeWaveHud
    // ========================================================================

    @Nonnull
    public List<String> getTrackerHintLines(@Nonnull World world) {
        Session s = sessions.get(world.getWorldConfig().getUuid());
        if (s == null || s.activeMobs.isEmpty()) return Collections.emptyList();
        if (s.secondsSinceLastKill < NO_KILL_HINT_INTERVAL_SECONDS)
            return Collections.emptyList();

        List<MobHintLine> hints = buildMobCoordinateHints(s, 5);
        List<String> result = new ArrayList<>(hints.size());
        for (MobHintLine h : hints) {
            result.add(String.format(Locale.ROOT,
                    "%s @ (%d, %d, %d)", h.name, h.x, h.y, h.z));
        }
        return result;
    }

    @Nullable
    public WaveStatus getWaveStatus(@Nonnull World world) {
        Session s = sessions.get(world.getWorldConfig().getUuid());
        if (s == null || s.phase == Phase.COUNTDOWN) return null;
        return new WaveStatus(s.currentWave, waves.size(),
                s.activeMobs.size(), s.totalSpawnedInWave);
    }

    // ========================================================================
    // Spawn position helpers
    // ========================================================================

    @Nullable
    private static Vector3d findFlankSpawnPosition(@Nonnull World world,
                                                   @Nonnull Vector3d origin,
                                                   double zHalfRange,
                                                   int attempts) {
        for (int a = 0; a < attempts; a++) {
            double x = origin.x + (Math.random() * 6.0 - 3.0);
            double z = origin.z + (Math.random() * 2.0 - 1.0) * zHalfRange;
            double y = NPCPhysicsMath.heightOverGround(world, x, z);
            if (y < 0.0) continue;
            // Reject subterranean ground — mobs were spawning inside caves/holes
            // below the bridge deck and clipping into terrain.
            if (y < MIN_SPAWN_Y) continue;
            if (!isDryLandSpawnLocation(world, x, y, z)) continue;
            return new Vector3d(x, y, z);
        }
        return null;
    }

    private static boolean isDryLandSpawnLocation(@Nonnull World world,
                                                  double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = Math.max(WORLD_MIN_Y + 1,
                Math.min(WORLD_MAX_Y - 1, (int) Math.floor(y)));

        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (chunk == null) return false;

        int support = chunk.getBlock(bx, by - 1, bz);
        int feet    = chunk.getBlock(bx, by, bz);
        int head    = chunk.getBlock(bx, by + 1, bz);

        if (support == AIR_BLOCK_ID) return false;
        if (isUnstableBlock(support)) return false;

        return !isLiquidBlock(support)
                && !isLiquidBlock(feet) && !isLiquidBlock(head)
                && !isObstructingFoliage(feet) && !isObstructingFoliage(head);
    }

    private static boolean isLiquidBlock(int id) {
        BlockType bt = BlockType.getAssetMap().getAsset(id);
        if (bt == null || bt.getId() == null) return false;
        String n = bt.getId().toLowerCase(Locale.ROOT);
        return n.contains("water") || n.contains("ocean") || n.contains("river")
                || n.contains("lava") || n.contains("liquid");
    }

    private static boolean isUnstableBlock(int id) {
        BlockType bt = BlockType.getAssetMap().getAsset(id);
        if (bt == null || bt.getId() == null) return false;
        String n = bt.getId().toLowerCase(Locale.ROOT);
        return n.contains("leaves") || n.contains("leaf") || n.contains("foliage")
                || n.contains("sapling") || n.contains("vine")
                || n.contains("fire") || n.contains("torch");
    }

    private static boolean isObstructingFoliage(int id) {
        BlockType bt = BlockType.getAssetMap().getAsset(id);
        if (bt == null || bt.getId() == null) return false;
        String n = bt.getId().toLowerCase(Locale.ROOT);
        return n.contains("leaves") || n.contains("leaf") || n.contains("foliage");
    }

    // ========================================================================
    // Boss size scaling
    // ========================================================================

    private static void applyBossSizeScale(@Nonnull Store<EntityStore> store,
                                           @Nonnull Ref<EntityStore> bossRef) {
        try {
            EntityScaleComponent existing = store.getComponent(
                    bossRef, EntityScaleComponent.getComponentType());
            if (existing != null) {
                existing.setScale(BOSS_SIZE_SCALE);
            } else {
                store.addComponent(bossRef, EntityScaleComponent.getComponentType(),
                        new EntityScaleComponent(BOSS_SIZE_SCALE));
            }
        } catch (Throwable ignored) {}
    }

    // ========================================================================
    // SFX
    // ========================================================================

    private static void playSoundToWorld(@Nonnull World world, @Nonnull String soundId) {
        int idx = resolveSoundIndex(soundId);
        if (idx == 0) return;
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) continue;
            try {
                SoundUtil.playSoundEvent2d(ref, idx, SoundCategory.SFX, ref.getStore());
            } catch (Exception ignored) {}
        }
    }

    private static int resolveSoundIndex(@Nullable String id) {
        if (id == null || id.isBlank()) return 0;
        int index = SoundEvent.getAssetMap().getIndex(id);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    // ========================================================================
    // Title announcements
    // ========================================================================

    private static void showTitleToWorld(@Nonnull World world,
                                        @Nonnull Message primary,
                                        @Nonnull Message secondary) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            EventTitleUtil.showEventTitleToPlayer(pr, primary, secondary, true);
        }
    }

    // ========================================================================
    // Messaging
    // ========================================================================

    private static void broadcast(@Nonnull World world,
                                  @Nonnull String text,
                                  @Nonnull String color) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            pr.sendMessage(Message.raw(text).color(color));
        }
    }

    private static void broadcastRich(@Nonnull World world, @Nonnull Message msg) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            pr.sendMessage(msg);
        }
    }

    // ========================================================================
    // Outlander Bridge tiered join notification
    // ========================================================================

    private int resolvePartyBonus(@Nonnull World world) {
        com.airijko.endlessleveling.leveling.PartyManager pm =
                EndlessLeveling.getInstance().getPartyManager();
        if (pm == null) return 0;
        int maxSize = 1;
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            UUID uuid = pr.getUuid();
            if (uuid == null) continue;
            try {
                Set<UUID> members = pm.getPartyMembers(uuid);
                int size = members != null ? members.size() : 1;
                if (size > maxSize) maxSize = size;
            } catch (Throwable ignored) {}
        }
        return Math.max(0, maxSize - 1);
    }

    private void lockTierOffsetForSession(@Nonnull Session s) {
        MobLevelingManager mlm = EndlessLeveling.getInstance().getMobLevelingManager();
        if (mlm == null) return;
        for (PlayerRef pr : s.world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            UUID uuid = pr.getUuid();
            if (uuid == null) continue;
            mlm.syncTierLevelOverridesForDungeon(s.store, uuid);
            return;
        }
    }

    private void sendOutlanderJoinNotification(@Nonnull World world,
                                          @Nonnull Store<EntityStore> store) {
        MobLevelingManager mlm = EndlessLeveling.getInstance().getMobLevelingManager();

        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            sendTierInfoToPlayer(pr, store);
        }
    }

    private void sendTierInfoToPlayer(@Nonnull PlayerRef pr,
                                      @Nonnull Store<EntityStore> store) {
        MobLevelingManager mlm = EndlessLeveling.getInstance().getMobLevelingManager();
        if (mlm == null) return;

        MobLevelingManager.TieredWorldSummary summary = mlm.resolveTieredWorldSummary(store, pr);
        if (summary != null) {
            PlayerChatNotifier.send(pr, buildTierChat(summary));
            return;
        }

        // World-override mapping may not yet be resolvable at session-creation
        // time (Universe store->world lookup can miss on a freshly-spawned
        // instance). Retry after a short delay using the world executor, same
        // pattern as DungeonTierJoinNotificationListener.
        World world = findWorldForStore(store);
        java.util.concurrent.Executor delayed = (world != null)
                ? CompletableFuture.delayedExecutor(TIER_INFO_RETRY_DELAY_MS, TimeUnit.MILLISECONDS, world)
                : CompletableFuture.delayedExecutor(TIER_INFO_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> {
            if (!pr.isValid()) return;
            MobLevelingManager.TieredWorldSummary retry = mlm.resolveTieredWorldSummary(store, pr);
            Message chat = (retry != null)
                    ? buildTierChat(retry)
                    : Message.join(
                            Message.raw("OUTLANDER BRIDGE").color("#f8d66d"),
                            Message.raw("  |  ").color("#555555"),
                            Message.raw(waves.size() + " waves").color("#8fd3ff"));
            PlayerChatNotifier.send(pr, chat);
        }, delayed);
    }

    private Message buildTierChat(@Nonnull MobLevelingManager.TieredWorldSummary summary) {
        int tierNumber = summary.tierOffset() + 1;
        return Message.join(
                Message.raw("OUTLANDER BRIDGE").color("#f8d66d"),
                Message.raw("  |  ").color("#555555"),
                Message.raw("Tier ").color("#ffcf66"),
                Message.raw(String.valueOf(tierNumber)).color("#4fd7f7"),
                Message.raw("  |  ").color("#555555"),
                Message.raw("Mob Lv ").color("#ffcf66"),
                Message.raw(summary.tierMinLevel() + "-" + summary.tierMaxLevel()).color("#4fd7f7"),
                Message.raw("  |  ").color("#555555"),
                Message.raw("Boss Lv ").color("#ffcf66"),
                Message.raw(String.valueOf(summary.bossLevel())).color("#ff6b6b"),
                Message.raw("  |  ").color("#555555"),
                Message.raw(waves.size() + " waves").color("#8fd3ff"));
    }

    @Nullable
    private World findWorldForStore(@Nonnull Store<EntityStore> store) {
        EntityStore ext = store.getExternalData();
        return ext == null ? null : ext.getWorld();
    }

    // ========================================================================
    // HUD helpers
    // ========================================================================

    private static void openHudForWorldPlayers(@Nonnull World world) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) continue;
            Store<EntityStore> st = ref.getStore();
            if (st == null) continue;
            Player player = st.getComponent(ref, Player.getComponentType());
            if (player == null) continue;
            OutlanderBridgeWaveHud.open(player, pr);
        }
    }

    private static void closeHudForWorldPlayers(@Nonnull World world) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) continue;
            Store<EntityStore> st = ref.getStore();
            if (st == null) continue;
            Player player = st.getComponent(ref, Player.getComponentType());
            if (player == null) continue;
            OutlanderBridgeWaveHud.close(player, pr);
        }
    }

    // ========================================================================
    // Misc helpers
    // ========================================================================

    private static boolean hasPlayersInWorld(@Nonnull World world) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr != null && pr.isValid()) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Ref<EntityStore> extractRef(@Nullable Object res) {
        if (res == null) return null;
        if (res instanceof Ref<?> r) return (Ref<EntityStore>) r;
        for (String m : new String[]{
                "getLeft", "getFirst", "getKey", "left", "first"}) {
            try {
                Object v = res.getClass().getMethod(m).invoke(res);
                if (v instanceof Ref<?> r) return (Ref<EntityStore>) r;
            } catch (Exception ignored) {}
        }
        for (String f : new String[]{"left", "first", "key"}) {
            try {
                Field field = res.getClass().getField(f);
                Object v = field.get(res);
                if (v instanceof Ref<?> r) return (Ref<EntityStore>) r;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void cleanupSession(@Nonnull Session s) {
        UUID worldId = s.world.getWorldConfig().getUuid();
        sessions.remove(worldId);
        completedWorldIds.add(worldId);
        s.activeMobs.clear();
        s.activeMobNames.clear();
        s.mobSpawnTimes.clear();
        restoreReturnPortals(s);
        closeHudForWorldPlayers(s.world);
        OutlanderBridgeXpBank.get().clearSession(worldId);
    }

    // ========================================================================
    // XP banking registration
    // ========================================================================

    private void registerAllPlayersForBanking(@Nonnull Session s) {
        UUID worldId = s.world.getWorldConfig().getUuid();
        OutlanderBridgeXpBank bank = OutlanderBridgeXpBank.get();
        for (PlayerRef pr : s.world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            UUID uuid = pr.getUuid();
            if (uuid == null) continue;
            bank.registerPlayerForSession(uuid, worldId);
        }
    }

    /**
     * Called by death system when a player dies anywhere. If the player is
     * in an active outlander-bridge session, pending XP is zeroed and a
     * rewards-on-respawn pending is queued if saved XP > 0.
     */
    public void handlePlayerDeath(@Nonnull UUID playerUuid, @Nullable World deathWorld) {
        try {
            if (deathWorld == null || !isOutlanderBridgeWorld(deathWorld)) return;
            if (deathWorld.getWorldConfig() == null) return;
            UUID worldId = deathWorld.getWorldConfig().getUuid();
            if (worldId == null) return;
            Session s = sessions.get(worldId);
            if (s == null) return;
            double saved = OutlanderBridgeXpBank.get().onPlayerDied(playerUuid, worldId);
            if (saved > 0.0) {
                LOGGER.atInfo().log(
                        "Outlander Bridge: player=%s died with saved=%.1f xp, queued rewards on respawn",
                        playerUuid, saved);
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log(
                    "Outlander Bridge: handlePlayerDeath failed player=%s", playerUuid);
        }
    }

    /**
     * Called by PlayerReadyEvent hook in EndlessLeveling — if the respawning
     * player has a pending reward, open the rewards panel now. Never throws
     * to the event dispatcher; a failure must never block world-join.
     */
    public void handlePlayerReady(@Nonnull PlayerRef playerRef) {
        try {
            UUID uuid = playerRef.getUuid();
            if (uuid == null) return;
            OutlanderBridgeXpBank.PendingReward pending =
                    OutlanderBridgeXpBank.get().consumePendingOnRespawn(uuid);
            if (pending == null) return;
            if (pending.savedXp() <= 0.0) return;
            // Death-respawn panel has no dungeon-close timer — player already
            // left the instance via the death route, so the 60s close countdown
            // is irrelevant. They decide to claim or cancel at their own pace.
            OutlanderBridgeRewardsPage.openFor(playerRef, pending.sessionWorldId(),
                    pending.savedXp(), 0, false);
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log(
                    "Outlander Bridge: handlePlayerReady failed");
        }
    }

    // ========================================================================
    // Re-entry guard & kicks
    // ========================================================================

    /** True if this player is blocked from banking in this session (claim/cancel/timeout done). */
    public boolean isPlayerLockedFromSession(@Nonnull UUID playerUuid, @Nonnull UUID sessionWorldId) {
        return OutlanderBridgeXpBank.get().isLockedFromSession(playerUuid, sessionWorldId);
    }

    /**
     * Teleport a player out of the current outlander-bridge instance back to
     * the default world spawn. Safe to call from any thread.
     */
    public void kickPlayerFromInstance(@Nonnull UUID playerUuid) {
        Universe universe = Universe.get();
        if (universe == null) return;
        PlayerRef playerRef = universe.getPlayer(playerUuid);
        if (playerRef == null || !playerRef.isValid()) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;
        World defaultWorld = universe.getDefaultWorld();
        if (defaultWorld == null) return;

        Transform spawn = defaultWorld.getWorldConfig() != null
                && defaultWorld.getWorldConfig().getSpawnProvider() != null
                ? defaultWorld.getWorldConfig().getSpawnProvider().getSpawnPoint(defaultWorld, playerUuid)
                : null;
        if (spawn == null) spawn = new Transform(0.0, 64.0, 0.0);

        try {
            store.addComponent(ref, Teleport.getComponentType(),
                    Teleport.createForPlayer(defaultWorld, spawn));
            OutlanderBridgeRewardsPage.forceClose(playerUuid);
            LOGGER.atInfo().log("Outlander Bridge: kicked player=%s to default world", playerUuid);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log(
                    "Outlander Bridge: failed to teleport player=%s out", playerUuid);
        }
    }

    /**
     * Timeout finalizer — forfeits any still-banked XP for all players in
     * the session, kicks them out, then schedules instance removal.
     */
    private void finalizeRewardsTimeout(@Nonnull Session s) {
        UUID worldId = s.world.getWorldConfig().getUuid();
        OutlanderBridgeXpBank bank = OutlanderBridgeXpBank.get();
        // Snapshot player UUIDs before mutating bank state.
        List<UUID> uuids = new ArrayList<>();
        for (PlayerRef pr : s.world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            UUID uuid = pr.getUuid();
            if (uuid != null) uuids.add(uuid);
        }
        for (UUID uuid : uuids) {
            bank.lockAndClear(uuid, worldId);
            OutlanderBridgeRewardsPage.forceClose(uuid);
            kickPlayerFromInstance(uuid);
        }
        String instanceName = s.world.getName();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                if (InstancesPlugin.get() != null && instanceName != null) {
                    InstancesPlugin.safeRemoveInstance(instanceName);
                    LOGGER.atInfo().log("Outlander Bridge: safeRemoveInstance %s", instanceName);
                }
            } catch (Exception ex) {
                LOGGER.atWarning().withCause(ex).log(
                        "Outlander Bridge: safeRemoveInstance failed %s", instanceName);
            }
        }, INSTANCE_REMOVE_DELAY_MS, TimeUnit.MILLISECONDS);
        cleanupSession(s);
    }

    /**
     * Play combat music to all players in the world who haven't heard it
     * within the cooldown window. Uses {@code SoundUtil.playSoundEvent2d}
     * with {@code SoundCategory.Music} so the track routes through the
     * client's music volume slider and doesn't loop or persist across
     * world transfers.
     */
    private void playCombatMusicToWorld(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        int idx = resolveSoundIndex(COMBAT_MUSIC_SOUND_ID);
        if (idx == 0) return;
        long now = System.currentTimeMillis();
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr == null || !pr.isValid()) continue;
            UUID playerId = pr.getUuid();
            if (playerId == null) continue;
            Long lastPlay = musicCooldowns.get(playerId);
            if (lastPlay != null && (now - lastPlay) < COMBAT_MUSIC_COOLDOWN_MS) continue;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) continue;
            try {
                SoundUtil.playSoundEvent2d(ref, idx, SoundCategory.Music, store);
                musicCooldowns.put(playerId, now);
            } catch (Exception ignored) {}
        }
    }

    // ========================================================================
    // Return portal hide/restore
    // ========================================================================

    private static int[] getReturnPortalBlockIntIds() {
        int[] c = returnPortalBlockIntIdsCache;
        if (c != null) return c;
        List<Integer> ids = new ArrayList<>();
        for (String name : RETURN_PORTAL_BLOCK_IDS) {
            int idx = BlockType.getAssetMap().getIndex(name);
            if (idx > 0) ids.add(idx);
        }
        c = new int[ids.size()];
        for (int i = 0; i < c.length; i++) c[i] = ids.get(i);
        returnPortalBlockIntIdsCache = c;
        return c;
    }

    private static boolean isReturnPortalBlock(int blockIntId) {
        if (blockIntId == AIR_BLOCK_ID) return false;
        for (int id : getReturnPortalBlockIntIds()) if (id == blockIntId) return true;
        return false;
    }

    /** Scan a fixed box around instance spawn, save portal blocks, replace with air. */
    private static void hideReturnPortals(@Nonnull Session s) {
        if (!s.savedPortalBlocks.isEmpty()) return;
        int[] portalIds = getReturnPortalBlockIntIds();
        if (portalIds.length == 0) {
            LOGGER.atWarning().log(
                    "Outlander Bridge: no return portal block ids registered — skipping hide");
            return;
        }
        World world = s.world;
        UUID instanceId = world.getWorldConfig().getUuid();
        for (int x = -PORTAL_SCAN_RADIUS; x <= PORTAL_SCAN_RADIUS; x++) {
            for (int z = -PORTAL_SCAN_RADIUS; z <= PORTAL_SCAN_RADIUS; z++) {
                WorldChunk chunk = world.getChunkIfLoaded(
                        ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) continue;
                for (int y = PORTAL_SCAN_Y_MIN; y <= PORTAL_SCAN_Y_MAX; y++) {
                    int id = chunk.getBlock(x, y, z);
                    if (!isReturnPortalBlock(id)) continue;
                    s.savedPortalBlocks.add(new SavedBlock(x, y, z, id));
                    chunk.setBlock(x, y, z, AIR_BLOCK_ID);
                }
            }
        }
        LOGGER.atInfo().log(
                "Outlander Bridge: hid %d return portal block(s) instance=%s",
                s.savedPortalBlocks.size(), instanceId);
    }

    /** Restore all portal blocks saved by {@link #hideReturnPortals}. Idempotent. */
    private static void restoreReturnPortals(@Nonnull Session s) {
        if (s.savedPortalBlocks.isEmpty()) return;
        World world = s.world;
        UUID instanceId = world.getWorldConfig().getUuid();
        int restored = 0;
        for (SavedBlock b : s.savedPortalBlocks) {
            try {
                WorldChunk chunk = world.getChunkIfLoaded(
                        ChunkUtil.indexChunkFromBlock(b.x, b.z));
                if (chunk == null) continue;
                chunk.setBlock(b.x, b.y, b.z, b.blockIntId);
                restored++;
            } catch (Exception ignored) {}
        }
        LOGGER.atInfo().log(
                "Outlander Bridge: restored %d/%d return portal block(s) instance=%s",
                restored, s.savedPortalBlocks.size(), instanceId);
        s.savedPortalBlocks.clear();
    }

    private static final class SavedBlock {
        final int x, y, z, blockIntId;
        SavedBlock(int x, int y, int z, int blockIntId) {
            this.x = x; this.y = y; this.z = z; this.blockIntId = blockIntId;
        }
    }

    @Nonnull
    private static List<SpawnBatch> buildLegacyBatches(@Nonnull WaveDef def) {
        SpawnBatch batch = new SpawnBatch();
        for (String id : def.legacyMobIds) {
            boolean merged = false;
            for (SpawnEntry e : batch.entries) {
                if (e.id.equals(id)) { e.count++; merged = true; break; }
            }
            if (!merged) batch.entries.add(new SpawnEntry(id, 1));
        }
        return batch.entries.isEmpty() ? Collections.emptyList() : List.of(batch);
    }

    // ========================================================================
    // Data classes
    // ========================================================================

    enum Phase { COUNTDOWN, WAVE_ACTIVE, WAVE_CLEARED, COMPLETED }

    private static final class WaveDef {
        final int number;
        final List<SpawnBatch> batches = new ArrayList<>();
        final List<String> bossIds = new ArrayList<>();
        final List<String> legacyMobIds = new ArrayList<>();
        WaveDef(int n) { this.number = n; }
        int totalMobCount() {
            if (!batches.isEmpty()) {
                int t = 0;
                for (SpawnBatch b : batches) t += b.totalCount();
                return t;
            }
            return legacyMobIds.size();
        }
    }

    private static final class SpawnBatch {
        final List<SpawnEntry> entries = new ArrayList<>();
        int totalCount() {
            int t = 0;
            for (SpawnEntry e : entries) t += e.count;
            return t;
        }
    }

    private static final class SpawnEntry {
        final String id;
        int count;
        SpawnEntry(String id, int count) { this.id = id; this.count = count; }
    }

    private static final class MobHintLine {
        final String name;
        final int x, y, z;
        MobHintLine(String n, int x, int y, int z) {
            this.name = n; this.x = x; this.y = y; this.z = z;
        }
    }

    public record WaveStatus(int currentWave, int totalWaves,
                              int remainingMobs, int totalSpawnedInWave) {}

    private static final class StuckState {
        double lastDistToCenter = Double.MAX_VALUE;
        float stuckSeconds = 0.0f;
    }

    private static final class Session {
        final World world;
        final Store<EntityStore> store;

        Phase phase = Phase.COUNTDOWN;
        int currentWave = 0;
        volatile boolean forceStartRequested = false;

        // ---- Timing accumulators ----
        float tickAccumulator = 0.0f;
        float waveTickRemainder = 0.0f;
        float countdownRemaining = 0.0f;
        float waveClearedElapsed = 0.0f;
        boolean nextWaveCountdownFired = false;
        float completedTimer = 0.0f;

        // ---- Mob tracking ----
        final List<Ref<EntityStore>> activeMobs = new ArrayList<>();
        final Map<Ref<EntityStore>, String> activeMobNames = new IdentityHashMap<>();
        final Map<Ref<EntityStore>, StuckState> mobStuckStates = new IdentityHashMap<>();
        final Map<Ref<EntityStore>, Float> mobSpawnTimes = new IdentityHashMap<>();
        float sessionElapsedSeconds = 0.0f;
        float stuckSampleAccumulator = 0.0f;
        int lastAliveMobCount = 0;

        // ---- Kill tracking ----
        float secondsSinceLastKill = 0.0f;
        float secondsSinceLastHint = 0.0f;

        // ---- Batch state ----
        int pendingBatchIndex = 0;
        boolean allBatchesSpawned = false;
        int currentBatchSpawnedCount = 0;
        float batchAge = -1.0f;
        int totalSpawnedInWave = 0;
        int killsAtBatchStart = 0;
        int currentWaveMobIndexOffset = 0;
        @Nullable List<SpawnBatch> currentWaveBatches;
        @Nonnull Set<Integer> bossIndices = new HashSet<>();
        @Nonnull List<String> bossIds = new ArrayList<>();
        int partyBonusPerEntry = 0;

        // ---- Return portal hide/restore ----
        final List<SavedBlock> savedPortalBlocks = new ArrayList<>();

        // ---- Post-victory rewards flow ----
        boolean rewardsOpened = false;
        int lastCountdownSecond = -1;

        Session(@Nonnull World world, @Nonnull Store<EntityStore> store) {
            this.world = world;
            this.store = store;
        }

        float consumeAccumulator() {
            float v = tickAccumulator;
            tickAccumulator = 0.0f;
            return v;
        }

        float consumeAccumulatorThrottled(float interval) {
            waveTickRemainder += tickAccumulator;
            tickAccumulator = 0.0f;
            if (waveTickRemainder >= interval) {
                float consumed = waveTickRemainder;
                waveTickRemainder = 0.0f;
                return consumed;
            }
            return 0.0f;
        }
    }
}
