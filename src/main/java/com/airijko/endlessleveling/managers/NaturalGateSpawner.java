package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.enums.GateRankTier;
import com.airijko.endlessleveling.enums.PortalGateColor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Spawns visual gate portals with full rank system, announcements, and displays.
 * Ports addon's NaturalPortalGateManager functionality (without actual mob waves).
 */
public final class NaturalGateSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final long DEFAULT_SPAWN_INTERVAL_MINUTES_MIN = 25L;
    private static final long DEFAULT_SPAWN_INTERVAL_MINUTES_MAX = 40L;
    private static final int DEFAULT_MIN_LEVEL_REQUIRED = 1;
    private static final List<String> DEFAULT_PORTAL_WORLDS = List.of("world", "default", "endless");

    // Rank weight system (defaults)
    private static final int DEFAULT_WEIGHT_S = 1;
    private static final int DEFAULT_WEIGHT_A = 6;
    private static final int DEFAULT_WEIGHT_B = 13;
    private static final int DEFAULT_WEIGHT_C = 30;
    private static final int DEFAULT_WEIGHT_D = 25;
    private static final int DEFAULT_WEIGHT_E = 25;

    // Level scaling (defaults)
    private static final int DEFAULT_DYNAMIC_LEVEL_OFFSET_MIN = 0;
    private static final int DEFAULT_DYNAMIC_LEVEL_OFFSET_MAX = 30;
    private static final int DEFAULT_NORMAL_MOB_LEVEL_RANGE = 20;
    private static final int DEFAULT_BOSS_LEVEL_BONUS = 10;
    private static final int DYNAMIC_MIN_LEVEL = 1;
    private static final int DYNAMIC_MAX_LEVEL = 500;

    // S-Rank special constants
    private static final String S_RANK_DISASTER_TITLE = "WORLD DISASTER";
    private static final String S_RANK_GATE_TITLE = "S-RANK GATE BREACH";
    private static final String S_RANK_GATE_SPAWN_SOUND_ID = "SFX_EL_S_Rank_Gate_Spawn";

    // Active gate tracking
    private static final Map<UUID, GateSnapshot> trackedGates = new ConcurrentHashMap<>();

    private static EndlessLeveling plugin;
    private static ScheduledFuture<?> periodicTask;
    private static volatile int configuredMaxConcurrentPortals = 5;
    private static volatile long configuredSpawnIntervalMin = DEFAULT_SPAWN_INTERVAL_MINUTES_MIN;
    private static volatile long configuredSpawnIntervalMax = DEFAULT_SPAWN_INTERVAL_MINUTES_MAX;
    private static volatile int configuredMinLevelRequired = DEFAULT_MIN_LEVEL_REQUIRED;
    private static volatile List<String> configuredPortalWorlds = DEFAULT_PORTAL_WORLDS;

    private NaturalGateSpawner() {
    }

    public static void initialize(@Nonnull EndlessLeveling owner) {
        plugin = owner;
        scheduleNextSpawnTick();
        LOGGER.atInfo().log("[ELGateSpawner] Initialized with interval %d-%d minutes",
                configuredSpawnIntervalMin,
                configuredSpawnIntervalMax);
    }

    public static void shutdown() {
        if (periodicTask != null) {
            periodicTask.cancel(false);
            periodicTask = null;
        }
        trackedGates.clear();
        plugin = null;
    }

    /**
     * Manually trigger a random gate spawn near the given player.
     */
    public static boolean spawnGateNearPlayer(@Nonnull PlayerRef playerRef) {
        return spawnGateNearPlayerInternal(playerRef, false);
    }

    @Nonnull
    public static GateRank resolveGateRankForPlayer(@Nonnull PlayerRef playerRef) {
        return generateGateRankForPlayer(resolvePlayerLevel(playerRef));
    }

    /**
     * Get a list of all currently tracked active gates.
     */
    @Nonnull
    public static List<GateSnapshot> listTrackedGates() {
        return trackedGates.values().stream()
                .sorted((g1, g2) -> Long.compare(g2.spawnTimeMillis, g1.spawnTimeMillis))
                .collect(Collectors.toList());
    }

    /**
     * Find a tracked gate by ID.
     */
    @Nullable
    public static GateSnapshot findTrackedGate(@Nonnull UUID gateId) {
        return trackedGates.get(gateId);
    }

    /**
     * Manually remove a tracked gate.
     */
    public static void removeTrackedGate(@Nonnull UUID gateId) {
        trackedGates.remove(gateId);
    }

    private static boolean spawnGateNearPlayerInternal(@Nonnull PlayerRef playerRef, boolean isNaturalSpawn) {
        if (!playerRef.isValid()) {
            return false;
        }

        UUID worldUuid = playerRef.getWorldUuid();
        World world = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
        if (world == null || !isPortalWorldAllowed(world)) {
            return false;
        }

        // Check if player meets level requirement
        if (!isPlayerLevelEligible(playerRef, configuredMinLevelRequired)) {
            if (!isNaturalSpawn) {
                playerRef.sendMessage(
                        Message.raw("You must reach level " + configuredMinLevelRequired + " to trigger gate spawns.")
                                .color("#ff6666"));
            }
            return false;
        }

        // Check concurrent portal limit
        int currentPortalCount = PortalVisualManager.countActivePortals();
        if (configuredMaxConcurrentPortals > 0 && currentPortalCount >= configuredMaxConcurrentPortals) {
            if (!isNaturalSpawn) {
                playerRef.sendMessage(
                        Message.raw("Too many active portals (" + currentPortalCount + "). Try again later.")
                                .color("#ff6666"));
            }
            return false;
        }

        // Resolve player level and generate gate rank
        GateRank gateRank = resolveGateRankForPlayer(playerRef);

        // Attempt to spawn visual portal near player
        UUID gateId = UUID.randomUUID();
        PortalVisualManager.SpawnedPortalDetails spawnedPortal = PortalVisualManager.spawnSneakPeekNearPlayerDetailed(
            world,
            playerRef,
            playerRef.getReference());
        boolean spawned = spawnedPortal != null;

        if (spawned) {
            int gateX = spawnedPortal.x();
            int gateY = spawnedPortal.y();
            int gateZ = spawnedPortal.z();

            // Track the gate
            GateSnapshot snapshot = new GateSnapshot(
                    gateId,
                    gateX, gateY, gateZ,
                    gateRank.tier,
                    System.currentTimeMillis()
            );
            trackedGates.put(gateId, snapshot);

            // Announce the gate spawn
            announceGate(
                    snapshot.x, snapshot.y, snapshot.z,
                    gateRank,
                    gateRank.normalLevelMin, gateRank.normalLevelMax, gateRank.bossLevel
            );

            // Optionally announce wave convergence
            announceGateWaveConvergence(
                    snapshot.x, snapshot.y, snapshot.z,
                    gateRank.tier,
                    gateRank.normalLevelMin, gateRank.normalLevelMax, gateRank.bossLevel
            );

            LOGGER.atInfo().log("[ELGateSpawner] Gate spawned: %s-rank at (%d, %d, %d) for player %s",
                    gateRank.tier.letter(), snapshot.x, snapshot.y, snapshot.z, playerRef.getUsername());
        }

        return spawned;
    }

    private static void spawnNaturalGateTick() {
        try {
            if (plugin == null) {
                return;
            }

            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }

            List<PlayerRef> eligiblePlayers = resolveEligiblePlayers(universe);
            if (eligiblePlayers.isEmpty()) {
                LOGGER.atFine().log("[ELGateSpawner] No eligible players for natural spawn");
                return;
            }

            // Pick random player
            PlayerRef target = eligiblePlayers.get((int) (Math.random() * eligiblePlayers.size()));
            boolean spawned = spawnGateNearPlayerInternal(target, true);

            if (spawned) {
                LOGGER.atInfo().log("[ELGateSpawner] Natural gate spawned for player %s", target.getUsername());
            } else {
                LOGGER.atFine().log("[ELGateSpawner] Natural gate spawn attempt failed for player %s",
                        target.getUsername());
            }
        } catch (Exception ex) {
            LOGGER.atWarning().log("[ELGateSpawner] Natural gate tick error: %s", ex.getMessage());
        }
    }

    private static void scheduleNextSpawnTick() {
        long delayMinutes = resolveRandomSpawnIntervalMinutes();
        periodicTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                spawnNaturalGateTick();
            } finally {
                if (plugin != null) {
                    scheduleNextSpawnTick();
                }
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    /**
     * Generate a gate rank based on player level using weighted randomness.
     */
    @Nonnull
    private static GateRank generateGateRankForPlayer(int playerLevel) {
        double roll = Math.random();
        GateRankTier tier = GateRankTier.fromRatio(roll);

        // Calculate level range based on tier
        int offsetMin = DEFAULT_DYNAMIC_LEVEL_OFFSET_MIN;
        int offsetMax = DEFAULT_DYNAMIC_LEVEL_OFFSET_MAX;

        int normalLevelMin = Math.max(DYNAMIC_MIN_LEVEL,
                Math.min(DYNAMIC_MAX_LEVEL, playerLevel + offsetMin));
        int normalLevelMax = Math.max(normalLevelMin,
                Math.min(DYNAMIC_MAX_LEVEL, playerLevel + offsetMax));

        // Boss is even higher
        int bossLevel = Math.min(DYNAMIC_MAX_LEVEL, normalLevelMax + DEFAULT_BOSS_LEVEL_BONUS);

        return new GateRank(tier, normalLevelMin, normalLevelMax, bossLevel, roll);
    }

    /**
     * Resolve player level from API.
     */
    private static int resolvePlayerLevel(@Nonnull PlayerRef playerRef) {
        EndlessLevelingAPI api = EndlessLevelingAPI.get();
        if (api != null) {
            UUID playerUuid = playerRef.getUuid();
            if (playerUuid != null) {
                int level = api.getPlayerLevel(playerUuid);
                if (level >= DYNAMIC_MIN_LEVEL) {
                    return level;
                }
            }
        }
        return DYNAMIC_MIN_LEVEL;
    }

    /**
     * Announce a gate spawn to all players with rank-specific messaging.
     */
    private static void announceGate(int x, int y, int z,
                                     @Nonnull GateRank gateRank,
                                     int normalLevelMin, int normalLevelMax, int bossLevel) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        if (gateRank.tier == GateRankTier.S) {
            // S-Rank special announcement
            Message message = Message.join(
                    Message.raw("[WORLD DISASTER] ").color("#ff5a36"),
                    Message.raw("A Sovereign Gate has ruptured the world veil.").color("#ffd7cf"),
                    Message.raw("\n"),
                    Message.raw("[OMEN] ").color("#ff9a3c"),
                    Message.raw(String.format(Locale.ROOT, "Catastrophic breach detected at (%d, %d, %d)", x, y, z)).color("#ffe3a8"),
                    Message.raw("\n"),
                    Message.raw("[THREAT] ").color("#ff5a36"),
                    Message.raw(String.format(Locale.ROOT, "Hostile level range %d-%d | Boss level %d", normalLevelMin, normalLevelMax, bossLevel)).color("#fff0cf"),
                    Message.raw("\n"),
                    Message.raw("[DECREE] ").color("#ff9a3c"),
                    Message.raw("All adventurers must mobilize at once.").color("#ffd7cf")
            );
            universe.sendMessage(message);

            // Show title to all players
            showTitleToAllPlayers(
                    Message.raw(S_RANK_DISASTER_TITLE).color("#ff5a36"),
                    Message.raw(String.format("%s at (%d, %d, %d)", S_RANK_GATE_TITLE, x, y, z)).color("#ffd7cf")
            );

            // Play sound to all players
            playSoundToAllPlayers(S_RANK_GATE_SPAWN_SOUND_ID);
            return;
        }

        // A-E Rank standard announcement
        Message message = Message.join(
                Message.raw("[RIFT BREACH] ").color(gateRank.tier.color().hex()),
                Message.raw(String.format(Locale.ROOT, "%s-Rank gate has emerged.", gateRank.tier.letter())).color("#ffffff"),
                Message.raw("\n"),
                Message.raw("[WAYPOINT] ").color(PortalGateColor.PREFIX.hex()),
                Message.raw(String.format(Locale.ROOT, "Coordinates (%d, %d, %d)", x, y, z)).color(PortalGateColor.POSITION.hex()),
                Message.raw("\n"),
                Message.raw("[THREAT] ").color(PortalGateColor.PREFIX.hex()),
                Message.raw(String.format(Locale.ROOT, "Mob level range %d-%d", normalLevelMin, normalLevelMax)).color(PortalGateColor.LEVEL.hex()),
                Message.raw("\n"),
                Message.raw("[VANGUARD] ").color(PortalGateColor.PREFIX.hex()),
                Message.raw(String.format(Locale.ROOT, "Boss level %d", bossLevel)).color(PortalGateColor.LEVEL.hex())
        );
        universe.sendMessage(message);
    }

    /**
     * Show event title to all players (S-Rank gates).
     */
    private static void showTitleToAllPlayers(@Nonnull Message titlePrimary,
                                              @Nonnull Message titleSecondary) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            try {
                EventTitleUtil.showEventTitleToPlayer(playerRef, titlePrimary, titleSecondary, true);
            } catch (Exception ex) {
                LOGGER.atWarning().log("[ELGateSpawner] Failed to show title to player %s: %s",
                        playerRef.getUsername(), ex.getMessage());
            }
        }
    }

    /**
     * Play sound to all players (S-Rank gate spawn sound).
     */
    private static void playSoundToAllPlayers(@Nonnull String soundEventId) {
        // Note: Sound playing requires access to world execution context and entity stores.
        // For now, we log that sound would be played. Full implementation requires
        // more context from the addon's sound utilities.
        LOGGER.atFine().log("[ELGateSpawner] Would play sound: %s", soundEventId);
    }

    /**
     * Announce a gate+wave convergence with countdown.
     */
    private static void announceGateWaveConvergence(int x, int y, int z,
                                                    @Nonnull GateRankTier rankTier,
                                                    int normalLevelMin, int normalLevelMax, int bossLevel) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        int delayMinutes = resolveNaturalWaveDelayMinutes(rankTier);
        String delayLabel = delayMinutes == 1 ? "1 minute" : delayMinutes + " minutes";

        Message message = Message.join(
                Message.raw("[RIFT CONVERGENCE] ").color("#ff6b6b"),
                Message.raw(String.format(Locale.ROOT, "%s-Rank gate and dungeon break have merged.", rankTier.letter())).color("#ffe0cf"),
                Message.raw("\n"),
                Message.raw("[SEAL] ").color("#ffb84d"),
                Message.raw(String.format(Locale.ROOT, "Gate entry is locked until the wave begins in %s.", delayLabel)).color("#fff0cf"),
                Message.raw("\n"),
                Message.raw("[BATTLEFIELD] ").color("#ffb84d"),
                Message.raw(String.format(Locale.ROOT, "Coords (%d, %d, %d) | Threat %d-%d | Boss %d", x, y, z, normalLevelMin, normalLevelMax, bossLevel)).color("#ffe0cf")
        );
        universe.sendMessage(message);
    }

    /**
     * Resolve wave delay minutes based on gate rank tier.
     */
    private static int resolveNaturalWaveDelayMinutes(@Nonnull GateRankTier rankTier) {
        return switch (rankTier) {
            case S -> 10;
            case A -> 5;
            default -> 1;
        };
    }

    @Nonnull
    private static List<PlayerRef> resolveEligiblePlayers(@Nonnull Universe universe) {
        List<PlayerRef> eligible = new ArrayList<>();
        for (PlayerRef player : universe.getPlayers()) {
            if (player == null || !player.isValid()) {
                continue;
            }

            UUID worldUuid = player.getWorldUuid();
            if (worldUuid == null) {
                continue;
            }

            World world = universe.getWorld(worldUuid);
            if (world != null && isPortalWorldAllowed(world)) {
                if (isPlayerLevelEligible(player, configuredMinLevelRequired)) {
                    eligible.add(player);
                }
            }
        }
        return eligible;
    }

    private static boolean isPortalWorldAllowed(@Nonnull World world) {
        String worldName = world.getName();
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return configuredPortalWorlds.stream()
                .anyMatch(w -> w.equalsIgnoreCase(worldName));
    }

    private static boolean isPlayerLevelEligible(@Nonnull PlayerRef playerRef, int minLevel) {
        if (minLevel <= 1) {
            return true;
        }

        int playerLevel = resolvePlayerLevel(playerRef);
        return playerLevel >= minLevel;
    }

    private static long resolveRandomSpawnIntervalMinutes() {
        long min = Math.max(1L, configuredSpawnIntervalMin);
        long max = Math.max(min, configuredSpawnIntervalMax);
        if (min == max) {
            return min;
        }
        return min + (long) (Math.random() * (max - min + 1));
    }

    // Configuration accessors
    public static void setSpawnIntervalMinutes(long min, long max) {
        configuredSpawnIntervalMin = Math.max(1L, min);
        configuredSpawnIntervalMax = Math.max(configuredSpawnIntervalMin, max);
    }

    public static void setMinLevelRequired(int level) {
        configuredMinLevelRequired = Math.max(1, level);
    }

    public static void setMaxConcurrentPortals(int count) {
        configuredMaxConcurrentPortals = count;
    }

    public static void setPortalWorlds(List<String> worlds) {
        configuredPortalWorlds = worlds != null ? worlds : DEFAULT_PORTAL_WORLDS;
    }
}
