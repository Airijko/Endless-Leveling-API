package com.airijko.endlessleveling.api;

// NOTE
// ────────────────────────────────────────────────────────────────────────────
// This file is a REFERENCE stub of the public EndlessLeveling API surface.
// It is NOT intended to be compiled. Method bodies are elided, and imports
// reference types from the closed-source EndlessLeveling core (augments,
// classes, races, leveling, passives, tracking, etc.) that are NOT shipped
// in this repository.
//
// Use this file to:
//   • See exactly which methods are public on EndlessLevelingAPI.
//   • Read javadoc / contract notes for each method.
//   • Cross-reference the API.md companion doc for usage recipes.
//
// See API.md in this repository for usage examples and calculation formulas.
// ────────────────────────────────────────────────────────────────────────────

import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSource;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.airijko.endlessleveling.tracking.DamageSource;
import com.airijko.endlessleveling.tracking.LeaderboardSnapshot;
import com.airijko.endlessleveling.tracking.PlayerDamageStats;
import com.airijko.endlessleveling.api.gates.DungeonGateContentProvider;
import com.airijko.endlessleveling.api.gates.DungeonGateLifecycleBridge;
import com.airijko.endlessleveling.api.gates.DungeonWaveGateBridge;
import com.airijko.endlessleveling.api.gates.GateInstanceRoutingBridge;
import com.airijko.endlessleveling.api.gates.InstanceDungeonDefinition;
import com.airijko.endlessleveling.api.gates.WaveGateContentProvider;
import com.airijko.endlessleveling.api.gates.WaveGateRuntimeBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionExecutorBridge;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lightweight public API surface for other mods to query EndlessLeveling state
 * without touching internal manager classes.
 */
public final class EndlessLevelingAPI {

    // ────────────────────────────────────────────────────────────────────
    // Event records
    // ────────────────────────────────────────────────────────────────────

    /** Fired after a successful prestige gain. */
    public record PrestigeEvent(UUID playerUuid, int oldPrestigeLevel, int newPrestigeLevel) {}

    /** Fired after a player levels up (once per level crossed). */
    public record LevelUpEvent(UUID playerUuid, int oldLevel, int newLevel, int prestigeLevel) {}

    /** Fired when all Outlander Bridge waves are cleared. */
    public record OutlanderBridgeCompletedEvent(UUID playerUuid, int wavesCompleted, String worldName) {}

    /** Fired by Rifts & Raids when a wave-gate clears its final wave. */
    public record WaveGateCompletedEvent(UUID playerUuid, String rankLetter, int wavesCompleted,
                                         String worldName, UUID sessionId) {}

    /** Fired when a player kills a mob. Keyed by normalized entity type id. */
    public record MobKillEvent(UUID playerUuid, String mobTypeId, String worldName) {}

    /**
     * Default "player is in combat" detection window in ms. Shared across
     * damage-meter HUD, passive regen gating, haymaker "out-of-combat" bonus,
     * and any other combat-state consumer.
     */
    public static final long DEFAULT_COMBAT_WINDOW_MS = 5000L;

    private EndlessLevelingAPI() { /* singleton */ }

    /** Global access point. */
    public static EndlessLevelingAPI get() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Swing-heal (lifesteal integration)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Deposits a potential-healing amount that will be folded into the
     * attacker's current/next outgoing swing. Third-party lifesteal weapons
     * and healing mods should call this from their own on-hit path (before
     * or during the player's attack) so the contribution is counted by
     * Blood Surge / Blood Echo bonus-damage conversion and multiplied by the
     * attacker's race HEALING_BONUS.
     *
     * <p>The amount is the pre-cap potential heal (overheal counts). Callers
     * must NOT separately add HP on the stat map — doing so will double-count.
     *
     * @param playerUuid    attacker UUID
     * @param potentialHeal potential heal amount (non-negative)
     */
    public void contributeSwingHeal(UUID playerUuid, double potentialHeal) { /* ... */ }

    /**
     * Drains and returns the pending external potential-heal total for a
     * player. Called once at swing finalize; subsequent calls within the
     * same tick return 0.0 until another contribution arrives.
     */
    public double drainPendingSwingHeal(UUID playerUuid) { /* ... */ return 0; }

    // ────────────────────────────────────────────────────────────────────
    // Player snapshot / attributes
    // ────────────────────────────────────────────────────────────────────

    /** Snapshot basic player info; returns null if player data is not loaded. */
    public PlayerSnapshot getPlayerSnapshot(UUID uuid) { /* ... */ return null; }

    /** Raw skill attribute level from EndlessLeveling (0 if missing). */
    public int getSkillAttributeLevel(UUID uuid, SkillAttributeType type) { /* ... */ return 0; }

    /**
     * Additive bonus value contributed by the player's skill levels (and
     * innate race bonuses) for the given attribute. Returns 0 when unavailable.
     */
    public double getSkillAttributeBonus(UUID uuid, SkillAttributeType type) { /* ... */ return 0; }

    /**
     * Race base value + skill bonus for the requested attribute (ignores
     * runtime gear/buffs). Useful for UI and external scaling.
     */
    public double getCombinedAttribute(UUID uuid, SkillAttributeType type, double fallback) { /* ... */ return 0; }

    /**
     * Returns an attribute breakdown with an optional external additive bonus
     * (e.g., other mods' health) applied on top of EndlessLeveling race +
     * skill values. Use this to keep third-party stats in sync.
     */
    public AttributeBreakdown getAttributeBreakdown(UUID uuid, SkillAttributeType type,
                                                    double externalBonus, double fallback) { /* ... */ return null; }

    /** Convenience overload with no external bonus. */
    public AttributeBreakdown getAttributeBreakdown(UUID uuid, SkillAttributeType type, double fallback) { /* ... */ return null; }

    /** Current player level (0 if missing). */
    public int getPlayerLevel(UUID uuid) { /* ... */ return 0; }

    /** Current XP (0 if missing). */
    public double getPlayerXp(UUID uuid) { /* ... */ return 0; }

    /** Current prestige level (0 if missing). */
    public int getPlayerPrestigeLevel(UUID uuid) { /* ... */ return 0; }

    /** Maximum configured level cap. */
    public int getLevelCap() { /* ... */ return 0; }

    /** Player-specific level cap (includes prestige scaling). */
    public int getLevelCap(UUID uuid) { /* ... */ return 0; }

    /** Level-needed XP; returns POSITIVE_INFINITY if at/above cap or unavailable. */
    public double getXpForNextLevel(int level) { /* ... */ return 0; }

    /**
     * Player-specific next-level XP; includes prestige base XP scaling.
     * Returns POSITIVE_INFINITY if unavailable or at/above cap.
     */
    public double getXpForNextLevel(UUID uuid, int level) { /* ... */ return 0; }

    public String getRaceId(UUID uuid) { /* ... */ return null; }
    public String getPrimaryClassId(UUID uuid) { /* ... */ return null; }
    public String getSecondaryClassId(UUID uuid) { /* ... */ return null; }

    /** Resolve a registered race definition by id; returns null if missing. */
    public RaceDefinition getRaceDefinition(String id) { /* ... */ return null; }

    /** Resolve a registered class definition by id; returns null if missing. */
    public CharacterClassDefinition getClassDefinition(String id) { /* ... */ return null; }

    /** Return all currently loaded race definitions, including API-registered ones. */
    public Collection<RaceDefinition> getRaceDefinitions() { /* ... */ return null; }

    /** Return all currently loaded class definitions, including API-registered ones. */
    public Collection<CharacterClassDefinition> getClassDefinitions() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Brand / chat prefix
    // ────────────────────────────────────────────────────────────────────

    /** Current UI and messaging brand name. */
    public String getBrandName() { /* ... */ return null; }

    /** Current chat message prefix used by EL notifications. */
    public String getMessagePrefix() { /* ... */ return null; }

    /** Current root command token used in EL messaging. */
    public String getCommandPrefix() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Nameplate toggles
    // ────────────────────────────────────────────────────────────────────

    /** Effective mob nameplate state after config and API override resolution. */
    public boolean areMobNameplatesEnabled() { /* ... */ return false; }

    /** Force mob nameplates on or off at runtime without editing leveling.yml. */
    public void setMobNameplatesEnabled(boolean enabled) { /* ... */ }

    /** Clear the runtime mob-nameplate override and fall back to leveling.yml. */
    public void resetMobNameplatesEnabled() { /* ... */ }

    /** Effective player nameplate state after config and API override resolution. */
    public boolean arePlayerNameplatesEnabled() { /* ... */ return false; }

    /** Force player nameplates on or off at runtime without editing leveling.yml. */
    public void setPlayerNameplatesEnabled(boolean enabled) { /* ... */ }

    /** Clear the runtime player-nameplate override and fall back to leveling.yml. */
    public void resetPlayerNameplatesEnabled() { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Skill config values
    // ────────────────────────────────────────────────────────────────────

    /** Underlying per-point config value for a skill attribute (from config.yml). */
    public double getSkillAttributeConfigValue(SkillAttributeType type) { /* ... */ return 0; }

    // ────────────────────────────────────────────────────────────────────
    // XP helpers
    // ────────────────────────────────────────────────────────────────────

    /** Grant raw XP to a player (passes through EL's XP bonuses and level cap). */
    public void grantXp(UUID playerUuid, double xpAmount) { /* ... */ }

    /**
     * Directly adjust a player's XP pool without applying personal bonuses
     * (discipline, luck, archetype) and without triggering XP grant listeners.
     * Positive values add XP (with level-up checks), negative values subtract
     * (clamped at 0). Used by the marriage even-split system.
     */
    public void adjustRawXp(UUID playerUuid, double delta) { /* ... */ }

    /** Check whether a player is currently in a party. */
    public boolean isInParty(UUID playerUuid) { /* ... */ return false; }

    /**
     * Grant XP and share it with the source's party members within maxDistance
     * (same world). If no party or no one in range, only the source receives XP.
     */
    public void grantSharedXpInRange(UUID sourcePlayerUuid, double totalXp, double maxDistance) { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Entity XP multipliers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register an XP multiplier for a specific entity. When this entity dies,
     * the base XP reward is multiplied by this value. Multipliers from
     * different sources do not stack — only a single value per entity is
     * stored. Set to 1.0 to clear.
     */
    public void setEntityXpMultiplier(int entityIndex, double multiplier) { /* ... */ }
    public void clearEntityXpMultiplier(int entityIndex) { /* ... */ }
    public double getEntityXpMultiplier(int entityIndex) { /* ... */ return 1.0; }

    // ────────────────────────────────────────────────────────────────────
    // Mob post-process hooks
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register a listener that is called after MobLevelingSystem assigns a
     * level to a mob for the first time. External mods can use this to apply
     * additional modifiers (health, effects, nameplates) immediately after
     * level assignment.
     */
    public void registerMobPostProcessListener(MobPostProcessListener listener) { /* ... */ }
    public void unregisterMobPostProcessListener(MobPostProcessListener listener) { /* ... */ }

    /**
     * Register a listener fired every time damage is credited to a player via
     * the EndlessLeveling damage tracker. Powers the damage meter and
     * leaderboard event system; also exposed so addons can build their own
     * dashboards.
     */
    public void registerDamageEventListener(DamageEventListener listener) { /* ... */ }
    public void unregisterDamageEventListener(DamageEventListener listener) { /* ... */ }

    /** Fire damage listeners. Called internally by DamageTracker. */
    public void fireDamageEventListeners(UUID playerUuid, int worldId, double amount,
                                         DamageSource source, boolean critical) { /* ... */ }

    /**
     * Start a damage-tracking leaderboard event in the given world.
     *
     * @return the new event id, or {@code null} if an event was already active there.
     */
    public String startLeaderboardEvent(int worldId, String eventName) { /* ... */ return null; }

    /** Stop the active event in the given world; returns the frozen snapshot or null if none. */
    public LeaderboardSnapshot stopLeaderboardEventForWorld(int worldId) { /* ... */ return null; }

    /** Stop an event by id regardless of which world it's running in. */
    public LeaderboardSnapshot stopLeaderboardEvent(String eventId) { /* ... */ return null; }

    public LeaderboardSnapshot getLeaderboardSnapshot(String eventId) { /* ... */ return null; }

    public PlayerDamageStats getPlayerDamageStats(UUID playerUuid) { /* ... */ return null; }

    /**
     * Fire all registered mob post-process listeners. Called internally by
     * MobLevelingSystem — external mods should not call this directly.
     */
    public void fireMobPostProcessListeners(Ref<EntityStore> ref, Store<EntityStore> store,
                                            CommandBuffer<EntityStore> commandBuffer, int assignedLevel) { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // World mob rank tier registry
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register the mob rank tier for an instance world. Called by gate mods
     * when a gate instance world is created or restored from persistence.
     */
    public void registerWorldMobRankTier(String worldName, MobRankTier tier) { /* ... */ }

    /** Remove the mob rank tier for an instance world. */
    public void unregisterWorldMobRankTier(String worldName) { /* ... */ }

    /** Look up the mob rank tier for a world. Returns null if none registered. */
    public MobRankTier getWorldMobRankTier(String worldName) { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Entity nameplate prefix
    // ────────────────────────────────────────────────────────────────────

    /**
     * Set a prefix that MobLevelingSystem will prepend to the mob's nameplate.
     * For example, "[S] " produces "[S] [Lv.42] Trork Warrior [500/500]".
     */
    public void setEntityNameplatePrefix(int entityIndex, String prefix) { /* ... */ }
    public void removeEntityNameplatePrefix(int entityIndex) { /* ... */ }
    public String getEntityNameplatePrefix(int entityIndex) { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Entity stat modifier helpers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Apply a named multiplicative health modifier to a mob's max HP via
     * EntityStatMap. The modifier stacks with existing modifiers (EL health
     * scaling, etc.) and is identified by the given key for later removal.
     */
    public boolean applyHealthModifier(Ref<EntityStore> ref, Store<EntityStore> store,
                                       CommandBuffer<EntityStore> commandBuffer,
                                       String modifierKey, float multiplier) { /* ... */ return false; }

    /** Remove a previously applied named health modifier from a mob. */
    public boolean removeHealthModifier(Ref<EntityStore> ref, Store<EntityStore> store,
                                        CommandBuffer<EntityStore> commandBuffer,
                                        String modifierKey) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Mob level queries
    // ────────────────────────────────────────────────────────────────────

    /**
     * Query the resolved mob level for an entity. Returns {@code null} if EL
     * has not assigned a level to this mob (or the entity is not tracked).
     */
    public Integer getEntityMobLevel(Ref<EntityStore> ref, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> commandBuffer) { /* ... */ return null; }

    /**
     * Returns {@code true} if the entity is a managed Necromancer summon
     * (Army of the Dead). Managed summons are excluded from the mob leveling
     * pipeline and should not receive elite promotions or other mob-specific
     * processing.
     */
    public boolean isEntityManagedSummon(Ref<EntityStore> ref, Store<EntityStore> store,
                                         CommandBuffer<EntityStore> commandBuffer) { /* ... */ return false; }

    /** Lightweight UUID-only variant — no ECS component resolution required. */
    public boolean isEntityManagedSummonByUuid(UUID entityUuid) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Mob level overrides
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register a radius/area override (min/max or flat) for mob levels.
     * Applied before Level_Source, so overrides are not modified by the normal
     * resolver.
     */
    public boolean registerMobAreaLevelOverride(String id, String worldId,
                                                double centerX, double centerZ, double radius,
                                                int minLevel, int maxLevel) { /* ... */ return false; }

    /** Register a world-wide override; min/max equal means flat. Bypasses Level_Source. */
    public boolean registerMobWorldLevelOverride(String id, String worldId, int minLevel, int maxLevel) { /* ... */ return false; }

    /** Register a world-wide GATE override; evaluated before generic area overrides. */
    public boolean registerMobWorldGateLevelOverride(String id, String worldId, int minLevel, int maxLevel) { /* ... */ return false; }

    /**
     * Register a world-wide GATE override with optional runtime replacement for
     * Mob_Overrides.Level_From_Range_Max_Offset.
     */
    public boolean registerMobWorldGateLevelOverride(String id, String worldId, int minLevel, int maxLevel,
                                                     int bossLevelFromRangeMaxOffset) { /* ... */ return false; }

    /**
     * Register a world-wide dynamic FIXED Level_Source override. Keeps scaling
     * on the normal config path while replacing the live Fixed_Level range.
     */
    public boolean registerMobWorldFixedLevelOverride(String id, String worldId, int minLevel, int maxLevel) { /* ... */ return false; }

    /** FIXED override with runtime Level_From_Range_Max_Offset replacement. */
    public boolean registerMobWorldFixedLevelOverride(String id, String worldId, int minLevel, int maxLevel,
                                                      int bossLevelFromRangeMaxOffset) { /* ... */ return false; }

    public boolean removeMobAreaLevelOverride(String id) { /* ... */ return false; }
    public boolean removeMobGateLevelOverride(String id) { /* ... */ return false; }

    /**
     * Register a scaling override attached to a gate override ID. When a mob
     * is in a world matched by the gate override, this scaling takes absolute
     * priority over world-settings. Map mirrors world-settings structure:
     * {@code {"Scaling": {"Health": {...}, ...}, "Mob_Overrides": {...}}}
     */
    public boolean registerGateScalingOverride(String id, Map<String, Object> scalingConfig) { /* ... */ return false; }
    public boolean removeGateScalingOverride(String id) { /* ... */ return false; }

    public boolean removeMobWorldFixedLevelOverride(String id) { /* ... */ return false; }

    public void clearMobAreaLevelOverrides() { /* ... */ }
    public void clearMobGateLevelOverrides() { /* ... */ }
    public void clearMobWorldFixedLevelOverrides() { /* ... */ }

    /**
     * Reload world-settings JSON files from disk without clearing runtime
     * overrides or other plugin state. Use after programmatically writing new
     * JSON files into the world-settings folder.
     */
    public void reloadWorldSettings() { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Runtime world overrides (programmatic world-settings)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Set the mob level range for a world. Internally sets Level_Source to
     * FIXED with the given range. Call {@link #reloadWorldSettings()} to apply.
     */
    public void setWorldLevelRange(String worldKey, int minLevel, int maxLevel) { /* ... */ }

    /**
     * Pin a specific mob type to a fixed level in a world. The mob type ID
     * is normalized (uppercased, dashes/spaces to underscores). Call
     * {@link #reloadWorldSettings()} afterwards to apply.
     */
    public void setWorldMobLevel(String worldKey, String mobTypeId, int fixedLevel) { /* ... */ }

    /**
     * Set a global XP multiplier for a world. Use "default" to affect the
     * overworld / any world without a specific override. Call
     * {@link #reloadWorldSettings()} to apply.
     */
    public void setWorldXpMultiplier(String worldKey, double multiplier) { /* ... */ }

    /** Remove all runtime overrides for a specific world key. */
    public void clearWorldOverrides(String worldKey) { /* ... */ }

    /** Remove all runtime world overrides. */
    public void clearAllWorldOverrides() { /* ... */ }

    /**
     * Build the runtime overrides as a single map matching the World_Overrides
     * JSON structure. Called internally by MobLevelingManager during settings
     * reload. Returns an empty map if no runtime overrides are registered.
     */
    public Map<String, Object> buildRuntimeWorldOverridesMap() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Runtime XP world blacklist
    // ────────────────────────────────────────────────────────────────────

    /**
     * Add a world pattern to the runtime XP blacklist. Mobs killed in worlds
     * matching this pattern grant no XP. Supports wildcards (*) and substring
     * matching. Takes effect immediately — no reload needed.
     */
    public void addXpBlacklistedWorld(String pattern) { /* ... */ }
    public void removeXpBlacklistedWorld(String pattern) { /* ... */ }
    public void clearXpBlacklistedWorlds() { /* ... */ }
    public Set<String> getRuntimeXpBlacklistedWorlds() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Notification suppression
    // ────────────────────────────────────────────────────────────────────

    /**
     * Suppress a specific EL notification type. While suppressed, EL will
     * not send that notification to any player. External mods can send their
     * own replacement messages instead.
     */
    public void suppressNotification(ELNotificationType type) { /* ... */ }
    public void unsuppressNotification(ELNotificationType type) { /* ... */ }
    public boolean isNotificationSuppressed(ELNotificationType type) { /* ... */ return false; }
    public void clearNotificationSuppressions() { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Chat / notification customization
    // ────────────────────────────────────────────────────────────────────

    /**
     * Override the command shown in EL's skill-point notifications and chat
     * messages (default: "/lvl"). Pass null to reset.
     */
    public void setCommandPrefix(String prefix) { /* ... */ }

    /**
     * Override the chat prefix EL prepends to chat messages
     * (default: "[EndlessLeveling] "). Pass null to reset.
     */
    public void setChatPrefix(String prefix) { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Augment snapshot / restore / temp apply
    // ────────────────────────────────────────────────────────────────────

    /**
     * Take a snapshot of a player's currently selected augments. Returns an
     * immutable copy of the tier-key → augment-id map.
     */
    public Map<String, String> snapshotAugments(UUID playerUuid) { /* ... */ return null; }

    /** Replace a player's selected augments with a previously captured snapshot. */
    public void restoreAugments(UUID playerUuid, Map<String, String> snapshot) { /* ... */ }

    /**
     * Add a temporary augment selection for a player, stacking with existing
     * picks. Uses the augment's tier as the selection key.
     */
    public boolean applyTempAugment(UUID playerUuid, String augmentId) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Per-entity level overrides
    // ────────────────────────────────────────────────────────────────────

    /**
     * @deprecated Entity indices are per-store — can cross-contaminate mobs
     *             in different worlds. Use the Ref overload.
     */
    @Deprecated
    public void setMobEntityLevelOverride(int entityIndex, int level) { /* ... */ }

    /**
     * Pin a fixed level for a specific entity (e.g., a plugin-spawned boss or
     * companion). Takes precedence over gate / area / LevelSourceMode
     * resolution, clamped to the configured range. Safe across worlds:
     * keyed by (store, index).
     */
    public void setMobEntityLevelOverride(Ref<EntityStore> ref, int level) { /* ... */ }

    /** @deprecated see {@link #setMobEntityLevelOverride(int, int)} — no-op. */
    @Deprecated
    public void clearMobEntityLevelOverride(int entityIndex) { /* ... */ }

    /** Remove the per-entity override for {@code ref}. */
    public void clearMobEntityLevelOverride(Ref<EntityStore> ref) { /* ... */ }

    /** Clear all per-entity overrides. */
    public void clearAllMobEntityLevelOverrides() { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Augment / race / class registration
    // ────────────────────────────────────────────────────────────────────

    /** Resolve a registered augment definition by id; returns null if missing. */
    public AugmentDefinition getAugmentDefinition(String id) { /* ... */ return null; }

    /**
     * Register a custom augment definition backed by EndlessLeveling's default
     * augment fallback unless a custom factory is also registered.
     */
    public boolean registerAugment(AugmentDefinition definition) { /* ... */ return false; }

    /** Register a custom augment definition and Java factory. */
    public boolean registerAugment(AugmentDefinition definition,
                                   Function<AugmentDefinition, Augment> factory) { /* ... */ return false; }

    /**
     * Register a custom augment definition and optional Java factory. When
     * replaceExisting is true, external registrations may override built-in
     * or file-backed augments using the same id.
     */
    public boolean registerAugment(AugmentDefinition definition,
                                   Function<AugmentDefinition, Augment> factory,
                                   boolean replaceExisting) { /* ... */ return false; }

    /** Remove a previously registered external augment definition and factory. */
    public boolean unregisterAugment(String id) { /* ... */ return false; }

    /** Register a custom race definition. */
    public boolean registerRace(RaceDefinition definition) { /* ... */ return false; }

    /**
     * Register a custom race definition. When replaceExisting is true,
     * external registrations may override built-in or file-backed races.
     */
    public boolean registerRace(RaceDefinition definition, boolean replaceExisting) { /* ... */ return false; }

    public boolean unregisterRace(String id) { /* ... */ return false; }

    /** Register a custom class definition. */
    public boolean registerClass(CharacterClassDefinition definition) { /* ... */ return false; }

    /**
     * Register a custom class definition. When replaceExisting is true,
     * external registrations may override built-in or file-backed classes.
     */
    public boolean registerClass(CharacterClassDefinition definition, boolean replaceExisting) { /* ... */ return false; }

    public boolean unregisterClass(String id) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Archetype passive sources
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register a custom archetype passive source. The source will be called
     * during snapshot generation for each player to provide additional
     * passives. Use this to add conditional passives based on external criteria.
     */
    public boolean registerArchetypePassiveSource(ArchetypePassiveSource source) { /* ... */ return false; }

    public boolean unregisterArchetypePassiveSource(ArchetypePassiveSource source) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Skill attribute visibility
    // ────────────────────────────────────────────────────────────────────

    /**
     * Hide a skill attribute from the skills UI. Hidden attributes cannot
     * receive point allocations and their UI section is not rendered.
     */
    public void hideSkillAttribute(SkillAttributeType type) { /* ... */ }
    public void showSkillAttribute(SkillAttributeType type) { /* ... */ }
    public boolean isSkillAttributeHidden(SkillAttributeType type) { /* ... */ return false; }
    public Set<SkillAttributeType> getHiddenSkillAttributes() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Runtime mob blacklist
    // ────────────────────────────────────────────────────────────────────

    /**
     * Add a mob-type blacklist entry at runtime. Matched as a case-insensitive
     * substring against NPC type IDs (same rules as Blacklist_Mob_Types in
     * leveling.yml). Blacklisted mobs receive no EL levels or nameplates.
     */
    public void addMobBlacklistEntry(String entry) { /* ... */ }
    public void removeMobBlacklistEntry(String entry) { /* ... */ }
    public boolean hasMobBlacklistEntry(String entry) { /* ... */ return false; }
    public Set<String> getRuntimeMobBlacklist() { /* ... */ return null; }

    /**
     * Live read-only view of the runtime mob blacklist. Safe for concurrent
     * iteration. Use in hot paths to avoid copy overhead.
     */
    public Set<String> getRuntimeMobBlacklistView() { /* ... */ return null; }
    public void clearRuntimeMobBlacklist() { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Auto-allocate guards
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register a guard predicate for auto-allocation on level-up. ALL guards
     * must return {@code true} for auto-allocation to proceed. If any guard
     * returns {@code false}, auto-allocation is skipped (skill points are
     * still granted, just not spent).
     */
    public void addAutoAllocateGuard(Predicate<UUID> guard) { /* ... */ }
    public void removeAutoAllocateGuard(Predicate<UUID> guard) { /* ... */ }

    /**
     * Spend all available skill points using the player's auto-allocate
     * settings. Unlike per-level allocation, this drains all unspent points
     * into the selected attribute.
     *
     * @return number of points actually spent, or 0 if nothing was allocated.
     */
    public int applyPendingAutoAllocate(UUID playerUuid) { /* ... */ return 0; }

    /** Test whether auto-allocation is currently permitted for the given player. */
    public boolean isAutoAllocateAllowed(UUID playerUuid) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Listeners
    // ────────────────────────────────────────────────────────────────────

    /**
     * Register a listener notified after a player receives XP via
     * {@link #grantXp}. Runs on the server thread — keep implementations
     * fast and non-blocking. Guard against recursion if the listener grants
     * further XP.
     */
    public void addXpGrantListener(BiConsumer<UUID, Double> listener) { /* ... */ }
    public void removeXpGrantListener(BiConsumer<UUID, Double> listener) { /* ... */ }

    public void addPrestigeListener(Consumer<PrestigeEvent> listener) { /* ... */ }
    public void removePrestigeListener(Consumer<PrestigeEvent> listener) { /* ... */ }
    public void notifyPrestigeListeners(UUID uuid, int oldLevel, int newLevel) { /* ... */ }

    public void addLevelUpListener(Consumer<LevelUpEvent> listener) { /* ... */ }
    public void removeLevelUpListener(Consumer<LevelUpEvent> listener) { /* ... */ }
    public void notifyLevelUpListeners(UUID uuid, int oldLevel, int newLevel, int prestigeLevel) { /* ... */ }

    public void addOutlanderBridgeCompletedListener(Consumer<OutlanderBridgeCompletedEvent> listener) { /* ... */ }
    public void removeOutlanderBridgeCompletedListener(Consumer<OutlanderBridgeCompletedEvent> listener) { /* ... */ }
    public void notifyOutlanderBridgeCompleted(UUID uuid, int wavesCompleted, String worldName) { /* ... */ }

    public void addWaveGateCompletedListener(Consumer<WaveGateCompletedEvent> listener) { /* ... */ }
    public void removeWaveGateCompletedListener(Consumer<WaveGateCompletedEvent> listener) { /* ... */ }
    public void notifyWaveGateCompleted(UUID uuid, String rankLetter, int waves, String worldName, UUID sessionId) { /* ... */ }

    public void addMobKillListener(Consumer<MobKillEvent> listener) { /* ... */ }
    public void removeMobKillListener(Consumer<MobKillEvent> listener) { /* ... */ }
    public void notifyMobKill(UUID uuid, String mobTypeId, String worldName) { /* ... */ }

    /**
     * Register a listener called just before a player is teleported to a
     * different world. Mods can use this to clean up transient entity state
     * (mount components, visual effects) that would become invalid.
     */
    public void addPreTeleportListener(Consumer<UUID> listener) { /* ... */ }
    public void removePreTeleportListener(Consumer<UUID> listener) { /* ... */ }

    /** Called by teleport code — external mods should not call directly. */
    public void notifyPreTeleportListeners(UUID uuid) { /* ... */ }

    /** Called by LevelingManager after XP is added — external mods should not call directly. */
    public void notifyXpGrantListeners(UUID uuid, double adjustedXp) { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Combat tags
    // ────────────────────────────────────────────────────────────────────

    /** Mark a player as being in combat right now. */
    public void markInCombat(UUID uuid) { /* ... */ }

    /** {@code true} if the player was in combat within the given window (ms). */
    public boolean isInCombat(UUID uuid, long windowMs) { /* ... */ return false; }

    /** Convenience overload using {@link #DEFAULT_COMBAT_WINDOW_MS}. */
    public boolean isInCombat(UUID uuid) { /* ... */ return false; }

    /** Epoch-ms of the most recent combat event for the player, or 0 if unknown. */
    public long getLastCombatMs(UUID uuid) { /* ... */ return 0; }

    /** Clear the combat tag for a player (e.g. on disconnect). */
    public void clearCombatTag(UUID uuid) { /* ... */ }

    // ────────────────────────────────────────────────────────────────────
    // Marriage convenience helpers
    // ────────────────────────────────────────────────────────────────────

    /** Check whether a player is currently married (via registered marriage manager). */
    public boolean isMarried(UUID uuid) { /* ... */ return false; }

    /** Get the spouse UUID for a married player, or null. */
    public UUID getSpouseUuid(UUID uuid) { /* ... */ return null; }

    /** Whether a married player is within their spouse's proximity range. */
    public boolean isNearSpouse(UUID uuid) { /* ... */ return false; }

    /**
     * True when the given player's party contains exactly themselves and
     * their spouse. Used to route couple-only parties through the marriage
     * 50/50 XP split.
     */
    public boolean isCoupleOnlyParty(UUID uuid) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Generic manager registry
    // ────────────────────────────────────────────────────────────────────

    public boolean registerManager(String key, Object manager, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterManager(String key, Object manager) { /* ... */ return false; }
    public Object getManager(String key) { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Gate bridge registration
    // ────────────────────────────────────────────────────────────────────

    public boolean registerDungeonWaveGateBridge(DungeonWaveGateBridge bridge, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterDungeonWaveGateBridge(DungeonWaveGateBridge bridge) { /* ... */ return false; }

    public boolean registerWaveGateRuntimeBridge(WaveGateRuntimeBridge bridge, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterWaveGateRuntimeBridge(WaveGateRuntimeBridge bridge) { /* ... */ return false; }

    public boolean registerDungeonGateLifecycleBridge(DungeonGateLifecycleBridge bridge, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterDungeonGateLifecycleBridge(DungeonGateLifecycleBridge bridge) { /* ... */ return false; }

    public boolean registerWaveGateSessionBridge(WaveGateSessionBridge bridge, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterWaveGateSessionBridge(WaveGateSessionBridge bridge) { /* ... */ return false; }

    public boolean registerWaveGateSessionExecutorBridge(WaveGateSessionExecutorBridge bridge, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterWaveGateSessionExecutorBridge(WaveGateSessionExecutorBridge bridge) { /* ... */ return false; }

    public boolean registerGateInstanceRoutingBridge(GateInstanceRoutingBridge bridge, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterGateInstanceRoutingBridge(GateInstanceRoutingBridge bridge) { /* ... */ return false; }

    public DungeonGateLifecycleBridge getDungeonGateLifecycleBridge() { /* ... */ return null; }
    public WaveGateSessionBridge getWaveGateSessionBridge() { /* ... */ return null; }
    public GateInstanceRoutingBridge getGateInstanceRoutingBridge() { /* ... */ return null; }

    // ────────────────────────────────────────────────────────────────────
    // Content providers
    // ────────────────────────────────────────────────────────────────────

    public boolean registerDungeonGateContentProvider(DungeonGateContentProvider provider, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterDungeonGateContentProvider(DungeonGateContentProvider provider) { /* ... */ return false; }

    /** Alias for {@link #registerDungeonGateContentProvider}. */
    public boolean registerGateContentProvider(DungeonGateContentProvider provider, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterGateContentProvider(DungeonGateContentProvider provider) { /* ... */ return false; }

    public boolean registerWaveGateContentProvider(WaveGateContentProvider provider, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterWaveGateContentProvider(WaveGateContentProvider provider) { /* ... */ return false; }

    /** Alias for {@link #registerWaveGateContentProvider}. */
    public boolean registerWaveContentProvider(WaveGateContentProvider provider, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterWaveContentProvider(WaveGateContentProvider provider) { /* ... */ return false; }

    // ────────────────────────────────────────────────────────────────────
    // Instance dungeons
    // ────────────────────────────────────────────────────────────────────

    public boolean registerInstanceDungeon(InstanceDungeonDefinition definition, boolean replaceExisting) { /* ... */ return false; }
    public boolean unregisterInstanceDungeon(InstanceDungeonDefinition definition) { /* ... */ return false; }

    public InstanceDungeonDefinition getInstanceDungeon(String dungeonId) { /* ... */ return null; }
    public List<InstanceDungeonDefinition> getInstanceDungeons() { /* ... */ return null; }
    public InstanceDungeonDefinition getInstanceDungeonByBlockId(String blockId) { /* ... */ return null; }
    public InstanceDungeonDefinition getInstanceDungeonByRoutingTemplate(String templateName) { /* ... */ return null; }
    public InstanceDungeonDefinition getInstanceDungeonByWorldName(String worldName) { /* ... */ return null; }

    public String canonicalizeInstanceDungeonRoutingTemplate(String templateName) { /* ... */ return null; }
    public String resolveInstanceDungeonOriginalTemplateName(String templateName) { /* ... */ return null; }
    public String resolveInstanceDungeonDisplayName(String templateName) { /* ... */ return null; }
    public String resolveInstanceDungeonSpawnSuffix(String templateName) { /* ... */ return null; }
    public String resolveInstanceDungeonBasePortalBlockId(String templateName) { /* ... */ return null; }
    public String buildInstanceDungeonWorldName(String routingTemplateName, String gateIdentity) { /* ... */ return null; }
    public String buildInstanceDungeonGroupId(String gateIdentity, String routingTemplateName) { /* ... */ return null; }
    public boolean isInstanceDungeonOriginalTemplate(String templateName) { /* ... */ return false; }
}
