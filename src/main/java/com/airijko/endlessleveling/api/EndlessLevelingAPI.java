package com.airijko.endlessleveling.api;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.mob.MobLevelingSystem;
import com.airijko.endlessleveling.player.PlayerAttributeManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSource;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.systems.PlayerNameplateSystem;
import com.airijko.endlessleveling.api.gates.DungeonGateContentProvider;
import com.airijko.endlessleveling.api.gates.DungeonGateLifecycleBridge;
import com.airijko.endlessleveling.api.gates.DungeonWaveGateBridge;
import com.airijko.endlessleveling.api.gates.GateInstanceRoutingBridge;
import com.airijko.endlessleveling.api.gates.InstanceDungeonDefinition;
import com.airijko.endlessleveling.api.gates.WaveGateContentProvider;
import com.airijko.endlessleveling.api.gates.WaveGateRuntimeBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionExecutorBridge;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lightweight public API surface for other mods to query EndlessLeveling state
 * without touching internal manager classes.
 */
public final class EndlessLevelingAPI {

    private static final EndlessLevelingAPI INSTANCE = new EndlessLevelingAPI();

    private static final String DUNGEON_GATES_MANAGER_KEY = "dungeon-gates";
    private static final String DUNGEON_GATES_LIFECYCLE_MANAGER_KEY = "dungeon-gates.lifecycle";
    private static final String WAVE_GATES_RUNTIME_MANAGER_KEY = "wave-gates.runtime";
    private static final String WAVE_GATES_SESSION_MANAGER_KEY = "wave-gates.session";
    private static final String WAVE_GATES_SESSION_EXECUTOR_MANAGER_KEY = "wave-gates.session.executor";
    private static final String GATE_INSTANCE_ROUTING_MANAGER_KEY = "gates.instance-routing";
    private static final String INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY = "dungeons.instance";
    private static final String DUNGEON_CONTENT_PROVIDER_KEY = "dungeon-content-provider";
    private static final String WAVE_CONTENT_PROVIDER_KEY = "wave-content-provider";

    private final Map<String, Object> managers = new ConcurrentHashMap<>();
    private final Map<String, InstanceDungeonDefinition> instanceDungeonsById = new ConcurrentHashMap<>();

    // Hidden skill attributes — these are excluded from the skills UI and cannot receive points.
    private final Set<SkillAttributeType> hiddenSkillAttributes = ConcurrentHashMap.newKeySet();

    // Runtime mob-type blacklist — entries are matched as substring against normalized mob type IDs.
    private final Set<String> runtimeMobBlacklist = ConcurrentHashMap.newKeySet();

    // Auto-allocate guards — ALL must pass for auto-allocation to proceed.
    private final List<Predicate<UUID>> autoAllocateGuards = new CopyOnWriteArrayList<>();

    private EndlessLevelingAPI() {
    }

    /** Global access point. */
    public static EndlessLevelingAPI get() {
        return INSTANCE;
    }

    /** Snapshot basic player info; returns null if player data is not loaded. */
    public PlayerSnapshot getPlayerSnapshot(UUID uuid) {
        PlayerData data = getData(uuid);
        if (data == null) {
            return null;
        }

        Map<SkillAttributeType, Integer> levels = new EnumMap<>(SkillAttributeType.class);
        for (SkillAttributeType type : SkillAttributeType.values()) {
            levels.put(type, data.getPlayerSkillAttributeLevel(type));
        }

        SkillManager skillManager = skillManager();
        double xpMultiplier = skillManager != null ? skillManager.getXpGainMultiplier(data) : 1.0D;

        return new PlayerSnapshot(
                data.getUuid(),
                data.getPlayerName(),
                data.getLevel(),
                data.getXp(),
                data.getSkillPoints(),
                data.getRaceId(),
                data.getPrimaryClassId(),
                data.getSecondaryClassId(),
                Collections.unmodifiableMap(levels),
                xpMultiplier);
    }

    /** Raw skill attribute level from EndlessLeveling (0 if missing). */
    public int getSkillAttributeLevel(UUID uuid, SkillAttributeType type) {
        PlayerData data = getData(uuid);
        if (data == null || type == null) {
            return 0;
        }
        return data.getPlayerSkillAttributeLevel(type);
    }

    /**
     * Additive bonus value contributed by the player's skill levels (and innate
     * race bonuses) for the given attribute. Returns 0 when unavailable.
     */
    public double getSkillAttributeBonus(UUID uuid, SkillAttributeType type) {
        PlayerData data = getData(uuid);
        SkillManager skillManager = skillManager();
        if (data == null || skillManager == null || type == null) {
            return 0.0D;
        }
        return skillManager.calculateSkillAttributeBonus(data, type, -1);
    }

    /**
     * Race base value + skill bonus for the requested attribute (ignores runtime
     * gear/buffs). Useful for UI and external scaling.
     */
    public double getCombinedAttribute(UUID uuid, SkillAttributeType type, double fallback) {
        PlayerData data = getData(uuid);
        PlayerAttributeManager attributeManager = attributeManager();
        if (data == null || attributeManager == null || type == null) {
            return fallback;
        }
        double skillBonus = getSkillAttributeBonus(uuid, type);
        return attributeManager.combineAttribute(data, type, skillBonus, fallback);
    }

    /**
     * Returns an attribute breakdown with an optional external additive bonus
     * (e.g., other mods' health) applied on top of EndlessLeveling race + skill
     * values. Use this to keep third-party stats in sync.
     */
    public AttributeBreakdown getAttributeBreakdown(UUID uuid, SkillAttributeType type,
            double externalBonus, double fallback) {
        PlayerData data = getData(uuid);
        RaceManager raceManager = raceManager();
        if (data == null || type == null) {
            return new AttributeBreakdown(fallback, 0.0D, externalBonus, fallback + externalBonus);
        }

        double raceBase = raceManager != null ? raceManager.getAttribute(data, type, fallback) : fallback;
        double skillBonus = getSkillAttributeBonus(uuid, type);
        double total = raceBase + skillBonus + externalBonus;
        return new AttributeBreakdown(raceBase, skillBonus, externalBonus, total);
    }

    /** Convenience overload with no external bonus. */
    public AttributeBreakdown getAttributeBreakdown(UUID uuid, SkillAttributeType type, double fallback) {
        return getAttributeBreakdown(uuid, type, 0.0D, fallback);
    }

    /** Current player level (0 if missing). */
    public int getPlayerLevel(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getLevel() : 0;
    }

    /** Current XP (0 if missing). */
    public double getPlayerXp(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getXp() : 0.0D;
    }

    /** Current prestige level (0 if missing). */
    public int getPlayerPrestigeLevel(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getPrestigeLevel() : 0;
    }

    /** Maximum configured level cap. */
    public int getLevelCap() {
        LevelingManager levelingManager = levelingManager();
        return levelingManager != null ? levelingManager.getLevelCap() : 0;
    }

    /** Player-specific level cap (includes prestige scaling). */
    public int getLevelCap(UUID uuid) {
        LevelingManager levelingManager = levelingManager();
        if (levelingManager == null) {
            return 0;
        }
        PlayerData data = getData(uuid);
        return data != null ? levelingManager.getLevelCap(data) : levelingManager.getLevelCap();
    }

    /**
     * Level needed XP; returns POSITIVE_INFINITY if at/above cap or unavailable.
     */
    public double getXpForNextLevel(int level) {
        LevelingManager levelingManager = levelingManager();
        return levelingManager != null ? levelingManager.getXpForNextLevel(level) : Double.POSITIVE_INFINITY;
    }

    /**
     * Player-specific next-level XP; includes prestige base XP scaling.
     * Returns POSITIVE_INFINITY if unavailable or at/above cap.
     */
    public double getXpForNextLevel(UUID uuid, int level) {
        LevelingManager levelingManager = levelingManager();
        if (levelingManager == null) {
            return Double.POSITIVE_INFINITY;
        }
        PlayerData data = getData(uuid);
        return levelingManager.getXpForNextLevel(data, level);
    }

    public String getRaceId(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getRaceId() : null;
    }

    public String getPrimaryClassId(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getPrimaryClassId() : null;
    }

    public String getSecondaryClassId(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getSecondaryClassId() : null;
    }

    /** Resolve a registered race definition by id; returns null if missing. */
    public RaceDefinition getRaceDefinition(String id) {
        RaceManager raceManager = raceManager();
        return raceManager == null ? null : raceManager.getRace(id);
    }

    /** Resolve a registered class definition by id; returns null if missing. */
    public CharacterClassDefinition getClassDefinition(String id) {
        ClassManager classManager = classManager();
        return classManager == null ? null : classManager.getClass(id);
    }

    /**
     * Return all currently loaded race definitions, including API-registered ones.
     */
    public Collection<RaceDefinition> getRaceDefinitions() {
        RaceManager raceManager = raceManager();
        return raceManager == null ? List.of() : List.copyOf(raceManager.getLoadedRaces());
    }

    /**
     * Return all currently loaded class definitions, including API-registered ones.
     */
    public Collection<CharacterClassDefinition> getClassDefinitions() {
        ClassManager classManager = classManager();
        return classManager == null ? List.of() : List.copyOf(classManager.getLoadedClasses());
    }

    /** Current UI and messaging brand name. */
    public String getBrandName() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getBrandName() : EndlessLeveling.DEFAULT_BRAND_NAME;
    }

    /** Current chat message prefix used by EL notifications. */
    public String getMessagePrefix() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getMessagePrefix() : EndlessLeveling.DEFAULT_MESSAGE_PREFIX;
    }

    /** Current root command token used in EL messaging. */
    public String getCommandPrefix() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getCommandPrefix() : EndlessLeveling.DEFAULT_COMMAND_PREFIX;
    }

    /** Effective mob nameplate state after config and API override resolution. */
    public boolean areMobNameplatesEnabled() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager == null || mobLevelingManager.shouldRenderMobNameplate();
    }

    /** Force mob nameplates on or off at runtime without editing leveling.yml. */
    public void setMobNameplatesEnabled(boolean enabled) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager == null) {
            return;
        }
        mobLevelingManager.setMobNameplateEnabledOverride(enabled);

        MobLevelingSystem mobLevelingSystem = mobLevelingSystem();
        if (mobLevelingSystem != null) {
            if (!enabled) {
                mobLevelingSystem.removeAllKnownMobNameplates();
            }
            mobLevelingSystem.requestFullMobRescale();
        }
    }

    /** Clear the runtime mob-nameplate override and fall back to leveling.yml. */
    public void resetMobNameplatesEnabled() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager == null) {
            return;
        }
        mobLevelingManager.setMobNameplateEnabledOverride(null);

        MobLevelingSystem mobLevelingSystem = mobLevelingSystem();
        if (mobLevelingSystem != null) {
            mobLevelingSystem.requestFullMobRescale();
        }
    }

    /** Effective player nameplate state after config and API override resolution. */
    public boolean arePlayerNameplatesEnabled() {
        PlayerNameplateSystem playerNameplateSystem = playerNameplateSystem();
        return playerNameplateSystem == null || playerNameplateSystem.arePlayerNameplatesEnabled();
    }

    /** Force player nameplates on or off at runtime without editing leveling.yml. */
    public void setPlayerNameplatesEnabled(boolean enabled) {
        PlayerNameplateSystem playerNameplateSystem = playerNameplateSystem();
        if (playerNameplateSystem == null) {
            return;
        }
        playerNameplateSystem.setPlayerNameplatesEnabledOverride(enabled);
        if (!enabled) {
            playerNameplateSystem.removeAllKnownNameplates();
        }
    }

    /** Clear the runtime player-nameplate override and fall back to leveling.yml. */
    public void resetPlayerNameplatesEnabled() {
        PlayerNameplateSystem playerNameplateSystem = playerNameplateSystem();
        if (playerNameplateSystem == null) {
            return;
        }
        playerNameplateSystem.clearPlayerNameplatesEnabledOverride();
    }

    /**
     * Underlying per-point config value for a skill attribute (from config.yml).
     */
    public double getSkillAttributeConfigValue(SkillAttributeType type) {
        SkillManager skillManager = skillManager();
        if (skillManager == null || type == null) {
            return 0.0D;
        }
        return skillManager.getSkillAttributeConfigValue(type);
    }

    // ------------
    // XP helpers
    // ------------

    /** Grant raw XP to a player (passes through EL's XP bonuses and level cap). */
    public void grantXp(UUID playerUuid, double xpAmount) {
        LevelingManager levelingManager = levelingManager();
        if (playerUuid == null || levelingManager == null || xpAmount <= 0) {
            return;
        }
        levelingManager.addXp(playerUuid, xpAmount);
    }

    /**
     * Grant XP and share it with the source's party members within maxDistance
     * (same world). If no party or no one in range, only the source receives XP.
     */
    public void grantSharedXpInRange(UUID sourcePlayerUuid, double totalXp, double maxDistance) {
        PartyManager partyManager = partyManager();
        LevelingManager levelingManager = levelingManager();
        if (sourcePlayerUuid == null || totalXp <= 0) {
            return;
        }
        if (partyManager != null) {
            partyManager.handleXpGainInRange(sourcePlayerUuid, totalXp, maxDistance);
            return;
        }
        if (levelingManager != null) {
            levelingManager.addXp(sourcePlayerUuid, totalXp);
        }
    }

    // ------------
    // Mob overrides
    // ------------

    /**
     * Register a radius/area override (min/max or flat) for mob levels. This is
     * applied before Level_Source (player/distance/fixed/tiers), so overrides are
     * not
     * modified by the normal resolver.
     */
    public boolean registerMobAreaLevelOverride(String id, String worldId,
            double centerX, double centerZ, double radius, int minLevel, int maxLevel) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null
                && mobLevelingManager.registerAreaLevelOverride(id, worldId, centerX, centerZ, radius, minLevel,
                        maxLevel);
    }

    /**
     * Register a world-wide override; min/max equal means flat. Also bypasses the
     * normal Level_Source resolver.
     */
    public boolean registerMobWorldLevelOverride(String id, String worldId, int minLevel, int maxLevel) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null
                && mobLevelingManager.registerWorldLevelOverride(id, worldId, minLevel, maxLevel);
    }

        /**
         * Register a world-wide GATE override; min/max equal means flat.
         * GATE overrides are evaluated before generic area overrides.
         */
        public boolean registerMobWorldGateLevelOverride(String id, String worldId, int minLevel, int maxLevel) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null
            && mobLevelingManager.registerWorldGateLevelOverride(id, worldId, minLevel, maxLevel);
        }

        /**
         * Register a world-wide GATE override with optional runtime replacement for
         * Mob_Overrides.Level_From_Range_Max_Offset.
         */
        public boolean registerMobWorldGateLevelOverride(String id,
            String worldId,
            int minLevel,
            int maxLevel,
            int bossLevelFromRangeMaxOffset) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null
            && mobLevelingManager.registerWorldGateLevelOverride(
                id,
                worldId,
                minLevel,
                maxLevel,
                bossLevelFromRangeMaxOffset);
        }

    /**
     * Register a world-wide dynamic FIXED Level_Source override.
     * This keeps scaling on the normal config path while replacing the live
     * Fixed_Level range for the matched world.
     */
    public boolean registerMobWorldFixedLevelOverride(String id, String worldId, int minLevel, int maxLevel) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null
                && mobLevelingManager.registerRuntimeFixedLevelOverride(id, worldId, minLevel, maxLevel);
    }

        /**
         * Register a world-wide dynamic FIXED Level_Source override with optional
         * runtime replacement for Mob_Overrides.Level_From_Range_Max_Offset.
         */
        public boolean registerMobWorldFixedLevelOverride(String id,
            String worldId,
            int minLevel,
            int maxLevel,
            int bossLevelFromRangeMaxOffset) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null
            && mobLevelingManager.registerRuntimeFixedLevelOverride(
                id,
                worldId,
                minLevel,
                maxLevel,
                bossLevelFromRangeMaxOffset);
        }

    /** Remove a previously registered area/world override. */
    public boolean removeMobAreaLevelOverride(String id) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null && mobLevelingManager.removeAreaLevelOverride(id);
    }

    /** Remove a previously registered GATE override. */
    public boolean removeMobGateLevelOverride(String id) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null && mobLevelingManager.removeGateLevelOverride(id);
    }

    /** Remove a previously registered world-wide dynamic FIXED Level_Source override. */
    public boolean removeMobWorldFixedLevelOverride(String id) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        return mobLevelingManager != null && mobLevelingManager.removeRuntimeFixedLevelOverride(id);
    }

    /** Clear all area/world overrides. */
    public void clearMobAreaLevelOverrides() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.clearAreaLevelOverrides();
        }
    }

    /** Clear all GATE overrides. */
    public void clearMobGateLevelOverrides() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.clearGateLevelOverrides();
        }
    }

    /** Clear all dynamic FIXED Level_Source overrides. */
    public void clearMobWorldFixedLevelOverrides() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.clearRuntimeFixedLevelOverrides();
        }
    }

    /**
     * Reload world-settings JSON files from disk without clearing runtime overrides
     * or other plugin state. Use this after programmatically writing new JSON files
     * into the world-settings folder so the changes take effect immediately.
     */
    public void reloadWorldSettings() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.reloadWorldSettingsOnly();
        }
    }

    /**
     * Set a fixed level for a specific entity index (e.g., a spawned boss). This
     * is checked before any Level_Source logic.
     */
    public void setMobEntityLevelOverride(int entityIndex, int level) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.setEntityLevelOverride(entityIndex, level);
        }
    }

    /** Remove a specific entity override. */
    public void clearMobEntityLevelOverride(int entityIndex) {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.clearEntityLevelOverride(entityIndex);
        }
    }

    /** Clear all per-entity overrides. */
    public void clearAllMobEntityLevelOverrides() {
        MobLevelingManager mobLevelingManager = mobLevelingManager();
        if (mobLevelingManager != null) {
            mobLevelingManager.clearAllEntityLevelOverrides();
        }
    }

    /** Resolve a registered augment definition by id; returns null if missing. */
    public AugmentDefinition getAugmentDefinition(String id) {
        AugmentManager augmentManager = augmentManager();
        return augmentManager == null ? null : augmentManager.getAugment(id);
    }

    /**
     * Register a custom augment definition backed by EndlessLeveling's default
     * augment fallback unless a custom factory is also registered.
     */
    public boolean registerAugment(AugmentDefinition definition) {
        return registerAugment(definition, null, false);
    }

    /** Register a custom augment definition and Java factory. */
    public boolean registerAugment(AugmentDefinition definition,
            Function<AugmentDefinition, Augment> factory) {
        return registerAugment(definition, factory, false);
    }

    /**
     * Register a custom augment definition and optional Java factory.
     * When replaceExisting is true, external registrations may override built-in
     * or file-backed augments using the same id.
     */
    public boolean registerAugment(AugmentDefinition definition,
            Function<AugmentDefinition, Augment> factory,
            boolean replaceExisting) {
        if (definition == null) {
            return false;
        }

        AugmentManager augmentManager = augmentManager();
        String augmentId = definition.getId();
        if (augmentManager == null || augmentId == null || augmentId.isBlank()) {
            return false;
        }
        if (!augmentManager.canRegisterExternalAugment(augmentId, replaceExisting)) {
            return false;
        }
        if (factory != null && !AugmentManager.canRegisterFactory(augmentId, replaceExisting)) {
            return false;
        }

        augmentManager.registerExternalAugment(definition);
        if (factory != null) {
            AugmentManager.registerFactory(augmentId, factory);
        }
        return true;
    }

    /** Remove a previously registered external augment definition and factory. */
    public boolean unregisterAugment(String id) {
        AugmentManager augmentManager = augmentManager();
        boolean definitionRemoved = augmentManager != null && augmentManager.unregisterExternalAugment(id);
        boolean factoryRemoved = AugmentManager.unregisterFactory(id) != null;
        return definitionRemoved || factoryRemoved;
    }

    /** Register a custom race definition. */
    public boolean registerRace(RaceDefinition definition) {
        return registerRace(definition, false);
    }

    /**
     * Register a custom race definition.
     * When replaceExisting is true, external registrations may override built-in
     * or file-backed races using the same id.
     */
    public boolean registerRace(RaceDefinition definition, boolean replaceExisting) {
        if (definition == null) {
            return false;
        }
        RaceManager raceManager = raceManager();
        String raceId = definition.getId();
        if (raceManager == null || raceId == null || raceId.isBlank()) {
            return false;
        }
        if (!raceManager.canRegisterExternalRace(raceId, replaceExisting)) {
            return false;
        }
        raceManager.registerExternalRace(definition);
        return true;
    }

    /** Remove a previously registered external race definition. */
    public boolean unregisterRace(String id) {
        RaceManager raceManager = raceManager();
        return raceManager != null && raceManager.unregisterExternalRace(id);
    }

    /** Register a custom class definition. */
    public boolean registerClass(CharacterClassDefinition definition) {
        return registerClass(definition, false);
    }

    /**
     * Register a custom class definition.
     * When replaceExisting is true, external registrations may override built-in
     * or file-backed classes using the same id.
     */
    public boolean registerClass(CharacterClassDefinition definition, boolean replaceExisting) {
        if (definition == null) {
            return false;
        }
        ClassManager classManager = classManager();
        String classId = definition.getId();
        if (classManager == null || classId == null || classId.isBlank()) {
            return false;
        }
        if (!classManager.canRegisterExternalClass(classId, replaceExisting)) {
            return false;
        }
        classManager.registerExternalClass(definition);
        return true;
    }

    /** Remove a previously registered external class definition. */
    public boolean unregisterClass(String id) {
        ClassManager classManager = classManager();
        return classManager != null && classManager.unregisterExternalClass(id);
    }

    // ----------------------
    // Archetype Passive APIs
    // ----------------------

    /**
     * Register a custom archetype passive source.
     * The source will be called during snapshot generation for each player to
     * provide
     * additional passives. Use this to add conditional passives based on external
     * criteria.
     */
    public boolean registerArchetypePassiveSource(ArchetypePassiveSource source) {
        if (source == null) {
            return false;
        }
        ArchetypePassiveManager manager = archetypePassiveManager();
        return manager != null && manager.registerArchetypePassiveSource(source);
    }

    /**
     * Unregister a previously registered custom archetype passive source.
     */
    public boolean unregisterArchetypePassiveSource(ArchetypePassiveSource source) {
        if (source == null) {
            return false;
        }
        ArchetypePassiveManager manager = archetypePassiveManager();
        return manager != null && manager.unregisterArchetypePassiveSource(source);
    }

    // ----------------------
    // Skill attribute visibility
    // ----------------------

    /**
     * Hide a skill attribute from the skills UI. Hidden attributes cannot
     * receive point allocations and their UI section is not rendered.
     */
    public void hideSkillAttribute(SkillAttributeType type) {
        if (type != null) {
            hiddenSkillAttributes.add(type);
        }
    }

    /** Restore a previously hidden skill attribute so it appears in the UI again. */
    public void showSkillAttribute(SkillAttributeType type) {
        if (type != null) {
            hiddenSkillAttributes.remove(type);
        }
    }

    /** Check whether a skill attribute is currently hidden. */
    public boolean isSkillAttributeHidden(SkillAttributeType type) {
        return type != null && hiddenSkillAttributes.contains(type);
    }

    /** Returns an unmodifiable snapshot of all currently hidden skill attributes. */
    public Set<SkillAttributeType> getHiddenSkillAttributes() {
        return hiddenSkillAttributes.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(hiddenSkillAttributes));
    }

    // ----------------------
    // Runtime mob blacklist
    // ----------------------

    /**
     * Add a mob-type blacklist entry at runtime. The entry is matched as a
     * case-insensitive substring against NPC type IDs (same rules as
     * Blacklist_Mob_Types in leveling.yml). Blacklisted mobs will not
     * receive EL levels or nameplates.
     */
    public void addMobBlacklistEntry(String entry) {
        if (entry != null && !entry.isBlank()) {
            runtimeMobBlacklist.add(entry.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        }
    }

    /** Remove a previously added runtime mob-type blacklist entry. */
    public void removeMobBlacklistEntry(String entry) {
        if (entry != null && !entry.isBlank()) {
            runtimeMobBlacklist.remove(entry.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        }
    }

    /** Check whether the runtime blacklist contains a given entry. */
    public boolean hasMobBlacklistEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        return runtimeMobBlacklist.contains(entry.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    /** Returns an unmodifiable snapshot of all runtime mob blacklist entries. */
    public Set<String> getRuntimeMobBlacklist() {
        return runtimeMobBlacklist.isEmpty()
                ? Set.of()
                : Set.copyOf(runtimeMobBlacklist);
    }

    /**
     * Returns a live read-only view of the runtime mob blacklist. Safe for
     * concurrent iteration but may reflect concurrent modifications. Use this
     * in hot paths (per-entity-per-tick) to avoid copying overhead.
     */
    public Set<String> getRuntimeMobBlacklistView() {
        return Collections.unmodifiableSet(runtimeMobBlacklist);
    }

    /** Clear all runtime mob blacklist entries. */
    public void clearRuntimeMobBlacklist() {
        runtimeMobBlacklist.clear();
    }

    // ----------------------
    // Auto-allocate guards
    // ----------------------

    /**
     * Register a guard predicate for auto-allocation on level-up.
     * All registered guards must return {@code true} for the given player UUID
     * for auto-allocation to proceed. If any guard returns {@code false},
     * auto-allocation is skipped (skill points are still granted, just not spent).
     *
     * <p>Guards are evaluated on the server thread during level-up processing.
     * Keep implementations fast and non-blocking.</p>
     */
    public void addAutoAllocateGuard(Predicate<UUID> guard) {
        if (guard != null) {
            autoAllocateGuards.add(guard);
        }
    }

    /** Remove a previously registered auto-allocate guard. */
    public void removeAutoAllocateGuard(Predicate<UUID> guard) {
        if (guard != null) {
            autoAllocateGuards.remove(guard);
        }
    }

    /**
     * Spend all available skill points using the player's auto-allocate settings.
     * Unlike the per-level-up allocation (which spends only the configured
     * points-per-level), this drains all unspent points into the selected
     * attribute. Useful for deferred/lazy allocation triggered by an external
     * event (e.g. NPC interaction).
     *
     * @return the number of points actually spent, or 0 if nothing was allocated.
     */
    public int applyPendingAutoAllocate(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        SkillManager sm = skillManager();
        PlayerData data = getData(playerUuid);
        if (sm == null || data == null) {
            return 0;
        }
        return sm.applyBulkAutoAllocate(data);
    }

    /**
     * Test whether auto-allocation is currently permitted for the given player.
     * Returns {@code true} if no guards are registered or all guards pass.
     */
    public boolean isAutoAllocateAllowed(UUID playerUuid) {
        if (playerUuid == null || autoAllocateGuards.isEmpty()) {
            return true;
        }
        for (Predicate<UUID> guard : autoAllocateGuards) {
            if (!guard.test(playerUuid)) {
                return false;
            }
        }
        return true;
    }

    // ----------------------
    // Generic manager registry
    // ----------------------

    public boolean registerManager(String key, Object manager, boolean replaceExisting) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null || manager == null) {
            return false;
        }

        if (!replaceExisting && managers.containsKey(normalizedKey)) {
            return false;
        }

        managers.put(normalizedKey, manager);
        return true;
    }

    public boolean unregisterManager(String key, Object manager) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null || manager == null) {
            return false;
        }
        return managers.remove(normalizedKey, manager);
    }

    public Object getManager(String key) {
        String normalizedKey = normalizeKey(key);
        return normalizedKey == null ? null : managers.get(normalizedKey);
    }

    // ----------------------
    // Gate bridge registration
    // ----------------------

    public boolean registerDungeonWaveGateBridge(DungeonWaveGateBridge bridge, boolean replaceExisting) {
        return registerManager(DUNGEON_GATES_MANAGER_KEY, bridge, replaceExisting);
    }

    public boolean unregisterDungeonWaveGateBridge(DungeonWaveGateBridge bridge) {
        return unregisterManager(DUNGEON_GATES_MANAGER_KEY, bridge);
    }

    public boolean registerWaveGateRuntimeBridge(WaveGateRuntimeBridge bridge, boolean replaceExisting) {
        return registerManager(WAVE_GATES_RUNTIME_MANAGER_KEY, bridge, replaceExisting);
    }

    public boolean unregisterWaveGateRuntimeBridge(WaveGateRuntimeBridge bridge) {
        return unregisterManager(WAVE_GATES_RUNTIME_MANAGER_KEY, bridge);
    }

    public boolean registerDungeonGateLifecycleBridge(DungeonGateLifecycleBridge bridge, boolean replaceExisting) {
        return registerManager(DUNGEON_GATES_LIFECYCLE_MANAGER_KEY, bridge, replaceExisting);
    }

    public boolean unregisterDungeonGateLifecycleBridge(DungeonGateLifecycleBridge bridge) {
        return unregisterManager(DUNGEON_GATES_LIFECYCLE_MANAGER_KEY, bridge);
    }

    public boolean registerWaveGateSessionBridge(WaveGateSessionBridge bridge, boolean replaceExisting) {
        return registerManager(WAVE_GATES_SESSION_MANAGER_KEY, bridge, replaceExisting);
    }

    public boolean unregisterWaveGateSessionBridge(WaveGateSessionBridge bridge) {
        return unregisterManager(WAVE_GATES_SESSION_MANAGER_KEY, bridge);
    }

    public boolean registerWaveGateSessionExecutorBridge(WaveGateSessionExecutorBridge bridge, boolean replaceExisting) {
        return registerManager(WAVE_GATES_SESSION_EXECUTOR_MANAGER_KEY, bridge, replaceExisting);
    }

    public boolean unregisterWaveGateSessionExecutorBridge(WaveGateSessionExecutorBridge bridge) {
        return unregisterManager(WAVE_GATES_SESSION_EXECUTOR_MANAGER_KEY, bridge);
    }

    public boolean registerGateInstanceRoutingBridge(GateInstanceRoutingBridge bridge, boolean replaceExisting) {
        return registerManager(GATE_INSTANCE_ROUTING_MANAGER_KEY, bridge, replaceExisting);
    }

    public boolean unregisterGateInstanceRoutingBridge(GateInstanceRoutingBridge bridge) {
        return unregisterManager(GATE_INSTANCE_ROUTING_MANAGER_KEY, bridge);
    }

    public DungeonGateLifecycleBridge getDungeonGateLifecycleBridge() {
        Object manager = getManager(DUNGEON_GATES_LIFECYCLE_MANAGER_KEY);
        return manager instanceof DungeonGateLifecycleBridge bridge ? bridge : null;
    }

    public WaveGateSessionBridge getWaveGateSessionBridge() {
        Object manager = getManager(WAVE_GATES_SESSION_MANAGER_KEY);
        return manager instanceof WaveGateSessionBridge bridge ? bridge : null;
    }

    public GateInstanceRoutingBridge getGateInstanceRoutingBridge() {
        Object manager = getManager(GATE_INSTANCE_ROUTING_MANAGER_KEY);
        return manager instanceof GateInstanceRoutingBridge bridge ? bridge : null;
    }

    // ----------------------
    // Content providers
    // ----------------------

    public boolean registerDungeonGateContentProvider(DungeonGateContentProvider provider, boolean replaceExisting) {
        return registerManager(DUNGEON_CONTENT_PROVIDER_KEY + ":" + provider.getProviderId(), provider, replaceExisting);
    }

    public boolean unregisterDungeonGateContentProvider(DungeonGateContentProvider provider) {
        return unregisterManager(DUNGEON_CONTENT_PROVIDER_KEY + ":" + provider.getProviderId(), provider);
    }

    public boolean registerGateContentProvider(DungeonGateContentProvider provider, boolean replaceExisting) {
        return registerDungeonGateContentProvider(provider, replaceExisting);
    }

    public boolean unregisterGateContentProvider(DungeonGateContentProvider provider) {
        return unregisterDungeonGateContentProvider(provider);
    }

    public boolean registerWaveGateContentProvider(WaveGateContentProvider provider, boolean replaceExisting) {
        return registerManager(WAVE_CONTENT_PROVIDER_KEY + ":" + provider.getProviderId(), provider, replaceExisting);
    }

    public boolean unregisterWaveGateContentProvider(WaveGateContentProvider provider) {
        return unregisterManager(WAVE_CONTENT_PROVIDER_KEY + ":" + provider.getProviderId(), provider);
    }

    public boolean registerWaveContentProvider(WaveGateContentProvider provider, boolean replaceExisting) {
        return registerWaveGateContentProvider(provider, replaceExisting);
    }

    public boolean unregisterWaveContentProvider(WaveGateContentProvider provider) {
        return unregisterWaveGateContentProvider(provider);
    }

    // ----------------------
    // Instance dungeons
    // ----------------------

    public boolean registerInstanceDungeon(InstanceDungeonDefinition definition, boolean replaceExisting) {
        if (definition == null || normalizeKey(definition.dungeonId()) == null) {
            return false;
        }

        String id = normalizeKey(definition.dungeonId());
        if (!replaceExisting && instanceDungeonsById.containsKey(id)) {
            return false;
        }

        instanceDungeonsById.put(id, definition);
        registerManager(INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY + ":" + id, definition, true);
        return true;
    }

    public boolean unregisterInstanceDungeon(InstanceDungeonDefinition definition) {
        if (definition == null || normalizeKey(definition.dungeonId()) == null) {
            return false;
        }

        String id = normalizeKey(definition.dungeonId());
        InstanceDungeonDefinition removed = instanceDungeonsById.remove(id);
        if (removed != null) {
            unregisterManager(INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY + ":" + id, removed);
            return true;
        }
        return false;
    }

    public InstanceDungeonDefinition getInstanceDungeon(String dungeonId) {
        String id = normalizeKey(dungeonId);
        return id == null ? null : instanceDungeonsById.get(id);
    }

    public List<InstanceDungeonDefinition> getInstanceDungeons() {
        return List.copyOf(instanceDungeonsById.values());
    }

    public InstanceDungeonDefinition getInstanceDungeonByBlockId(String blockId) {
        String normalizedBlock = normalizeToken(blockId);
        if (normalizedBlock == null) {
            return null;
        }

        for (InstanceDungeonDefinition definition : instanceDungeonsById.values()) {
            if (normalizedBlock.equals(normalizeToken(definition.basePortalBlockId()))) {
                return definition;
            }
        }
        return null;
    }

    public InstanceDungeonDefinition getInstanceDungeonByRoutingTemplate(String templateName) {
        String normalizedTemplate = normalizeToken(templateName);
        if (normalizedTemplate == null) {
            return null;
        }

        for (InstanceDungeonDefinition definition : instanceDungeonsById.values()) {
            if (normalizedTemplate.equals(normalizeToken(definition.routingTemplateName()))
                    || normalizedTemplate.equals(normalizeToken(definition.legacyTemplateName()))) {
                return definition;
            }
        }
        return null;
    }

    public InstanceDungeonDefinition getInstanceDungeonByWorldName(String worldName) {
        String normalizedWorld = normalizeToken(worldName);
        if (normalizedWorld == null) {
            return null;
        }

        for (InstanceDungeonDefinition definition : instanceDungeonsById.values()) {
            String originalTemplateToken = normalizeToken(resolveInstanceDungeonWorldPrefix(definition));
            String routingTemplateToken = normalizeToken(definition.routingTemplateName());
            String legacyWorldToken = normalizeToken(definition.worldNameToken());

            if ((originalTemplateToken != null && normalizedWorld.startsWith("el_gate_" + originalTemplateToken + "_"))
                    || (routingTemplateToken != null && normalizedWorld.startsWith("el_gate_" + routingTemplateToken + "_"))
                    || (legacyWorldToken != null && normalizedWorld.startsWith("el_gate_" + legacyWorldToken + "_"))) {
                return definition;
            }
        }
        return null;
    }

    public String canonicalizeInstanceDungeonRoutingTemplate(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? templateName : definition.routingTemplateName();
    }

    public String resolveInstanceDungeonOriginalTemplateName(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? templateName : definition.legacyTemplateName();
    }

    public String resolveInstanceDungeonDisplayName(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? templateName : definition.displayName();
    }

    public String resolveInstanceDungeonSpawnSuffix(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? null : definition.spawnSuffix();
    }

    public String resolveInstanceDungeonBasePortalBlockId(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? null : definition.basePortalBlockId();
    }

    public String buildInstanceDungeonWorldName(String routingTemplateName, String gateIdentity) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(routingTemplateName);
        String dungeonToken = definition == null
                ? sanitizeToken(routingTemplateName)
                : sanitizeToken(resolveInstanceDungeonWorldPrefix(definition));
        String gateToken = sanitizeToken(stripGateIdentityPrefix(gateIdentity));
        return "el_gate_" + dungeonToken + "_" + gateToken;
    }

    public String buildInstanceDungeonGroupId(String gateIdentity, String routingTemplateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(routingTemplateName);
        String dungeonToken = definition == null
                ? sanitizeToken(routingTemplateName)
                : sanitizeToken(resolveInstanceDungeonWorldPrefix(definition));
        String gateToken = sanitizeToken(stripGateIdentityPrefix(gateIdentity));
        return "el_gate_" + dungeonToken + "_" + gateToken;
    }

    public boolean isInstanceDungeonOriginalTemplate(String templateName) {
        return getInstanceDungeonByRoutingTemplate(templateName) != null;
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeToken(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return "unknown";
        }

        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private static String resolveInstanceDungeonWorldPrefix(InstanceDungeonDefinition definition) {
        if (definition.legacyTemplateName() != null && !definition.legacyTemplateName().isBlank()) {
            return definition.legacyTemplateName();
        }
        if (definition.routingTemplateName() != null && !definition.routingTemplateName().isBlank()) {
            return definition.routingTemplateName();
        }
        return definition.worldNameToken();
    }

    private static String stripGateIdentityPrefix(String gateIdentity) {
        String normalized = normalizeToken(gateIdentity);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("el_gate:")) {
            return normalized.substring("el_gate:".length());
        }
        if (normalized.startsWith("el_gate_")) {
            return normalized.substring("el_gate_".length());
        }
        return normalized;
    }

    private PlayerData getData(UUID uuid) {
        PlayerDataManager playerDataManager = playerDataManager();
        return uuid == null || playerDataManager == null ? null : playerDataManager.get(uuid);
    }

    private EndlessLeveling plugin() {
        return EndlessLeveling.getInstance();
    }

    private PlayerDataManager playerDataManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getPlayerDataManager() : null;
    }

    private SkillManager skillManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getSkillManager() : null;
    }

    private LevelingManager levelingManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getLevelingManager() : null;
    }

    private RaceManager raceManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getRaceManager() : null;
    }

    private ClassManager classManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getClassManager() : null;
    }

    private PlayerAttributeManager attributeManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getPlayerAttributeManager() : null;
    }

    private MobLevelingManager mobLevelingManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getMobLevelingManager() : null;
    }

    private MobLevelingSystem mobLevelingSystem() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getMobLevelingSystem() : null;
    }

    private PlayerNameplateSystem playerNameplateSystem() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getPlayerNameplateSystem() : null;
    }

    private PartyManager partyManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getPartyManager() : null;
    }

    private AugmentManager augmentManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getAugmentManager() : null;
    }

    private ArchetypePassiveManager archetypePassiveManager() {
        EndlessLeveling plugin = plugin();
        return plugin != null ? plugin.getArchetypePassiveManager() : null;
    }
}
