package com.airijko.endlessleveling.api;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.api.gates.DungeonWaveGateBridge;
import com.airijko.endlessleveling.api.gates.DungeonGateContentProvider;
import com.airijko.endlessleveling.api.gates.DungeonGateLifecycleBridge;
import com.airijko.endlessleveling.api.gates.GateInstanceRoutingBridge;
import com.airijko.endlessleveling.api.gates.InstanceDungeonDefinition;
import com.airijko.endlessleveling.api.gates.WaveGateRuntimeBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionBridge;
import com.airijko.endlessleveling.api.gates.WaveGateSessionExecutorBridge;
import com.airijko.endlessleveling.api.gates.WaveGateContentProvider;
import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.player.PlayerAttributeManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.leveling.PartyManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSource;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.player.SkillManager;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Lightweight public API surface for other mods to query EndlessLeveling state
 * without touching internal manager classes.
 */
public final class EndlessLevelingAPI {

    private static final EndlessLevelingAPI INSTANCE = new EndlessLevelingAPI();
    private static final String DUNGEON_WAVE_GATES_MANAGER_KEY = "dungeon-gates";
    private static final String DUNGEON_GATE_LIFECYCLE_MANAGER_KEY = "dungeon-gates.lifecycle";
    private static final String WAVE_GATE_RUNTIME_MANAGER_KEY = "wave-gates.runtime";
    private static final String WAVE_GATE_SESSION_MANAGER_KEY = "wave-gates.session";
    private static final String WAVE_GATE_SESSION_EXECUTOR_MANAGER_KEY = "wave-gates.session.executor";
    private static final String GATE_INSTANCE_ROUTING_MANAGER_KEY = "gates.instance-routing";
    private static final String DUNGEON_GATE_CONTENT_PROVIDER_MANAGER_KEY = "dungeon-gates.content";
    private static final String WAVE_GATE_CONTENT_PROVIDER_MANAGER_KEY = "wave-gates.content";
    private static final String INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY = "dungeons.instance";

    private final Map<String, Object> managerRegistry = new ConcurrentHashMap<>();

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

    // ----------------
    // Manager registry
    // ----------------

    /**
     * Register an external manager object under a stable key.
     *
     * @param key manager key
     * @param manager manager instance
     * @param replaceExisting whether an existing manager under the same key may be replaced
     * @return true when registration succeeds
     */
    public boolean registerManager(String key, Object manager, boolean replaceExisting) {
        if (key == null || key.isBlank() || manager == null) {
            return false;
        }

        if (replaceExisting) {
            managerRegistry.put(key, manager);
            return true;
        }

        return managerRegistry.putIfAbsent(key, manager) == null;
    }

    /**
     * Unregister a manager only if both key and instance match.
     *
     * @param key manager key
     * @param manager manager instance
     * @return true when removed
     */
    public boolean unregisterManager(String key, Object manager) {
        if (key == null || key.isBlank() || manager == null) {
            return false;
        }
        return managerRegistry.remove(key, manager);
    }

    /**
     * Resolve an external manager by key.
     *
     * @param key manager key
     * @return manager or null
     */
    public Object getManager(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return managerRegistry.get(key);
    }

    /** Register the addon-provided dungeon/wave gate bridge. */
    public boolean registerDungeonWaveGateBridge(DungeonWaveGateBridge bridge, boolean replaceExisting) {
        return registerManager(DUNGEON_WAVE_GATES_MANAGER_KEY, bridge, replaceExisting);
    }

    /** Unregister the dungeon/wave gate bridge. */
    public boolean unregisterDungeonWaveGateBridge(DungeonWaveGateBridge bridge) {
        return unregisterManager(DUNGEON_WAVE_GATES_MANAGER_KEY, bridge);
    }

    /** Resolve the active dungeon/wave gate bridge, if provided by an addon. */
    public DungeonWaveGateBridge getDungeonWaveGateBridge() {
        Object manager = getManager(DUNGEON_WAVE_GATES_MANAGER_KEY);
        if (manager instanceof DungeonWaveGateBridge bridge) {
            return bridge;
        }
        return null;
    }

    /** Register the addon-provided wave gate runtime bridge. */
    public boolean registerWaveGateRuntimeBridge(WaveGateRuntimeBridge bridge, boolean replaceExisting) {
        return registerManager(WAVE_GATE_RUNTIME_MANAGER_KEY, bridge, replaceExisting);
    }

    /** Unregister the wave gate runtime bridge. */
    public boolean unregisterWaveGateRuntimeBridge(WaveGateRuntimeBridge bridge) {
        return unregisterManager(WAVE_GATE_RUNTIME_MANAGER_KEY, bridge);
    }

    /** Resolve the active wave gate runtime bridge, if provided by an addon. */
    public WaveGateRuntimeBridge getWaveGateRuntimeBridge() {
        Object manager = getManager(WAVE_GATE_RUNTIME_MANAGER_KEY);
        if (manager instanceof WaveGateRuntimeBridge bridge) {
            return bridge;
        }
        return null;
    }

    /** Register the addon-provided dungeon gate lifecycle bridge. */
    public boolean registerDungeonGateLifecycleBridge(DungeonGateLifecycleBridge bridge, boolean replaceExisting) {
        return registerManager(DUNGEON_GATE_LIFECYCLE_MANAGER_KEY, bridge, replaceExisting);
    }

    /** Unregister the dungeon gate lifecycle bridge. */
    public boolean unregisterDungeonGateLifecycleBridge(DungeonGateLifecycleBridge bridge) {
        return unregisterManager(DUNGEON_GATE_LIFECYCLE_MANAGER_KEY, bridge);
    }

    /** Resolve the active dungeon gate lifecycle bridge. */
    public DungeonGateLifecycleBridge getDungeonGateLifecycleBridge() {
        Object manager = getManager(DUNGEON_GATE_LIFECYCLE_MANAGER_KEY);
        if (manager instanceof DungeonGateLifecycleBridge bridge) {
            return bridge;
        }
        return null;
    }

    /** Register the addon-provided wave gate session bridge. */
    public boolean registerWaveGateSessionBridge(WaveGateSessionBridge bridge, boolean replaceExisting) {
        return registerManager(WAVE_GATE_SESSION_MANAGER_KEY, bridge, replaceExisting);
    }

    /** Unregister the wave gate session bridge. */
    public boolean unregisterWaveGateSessionBridge(WaveGateSessionBridge bridge) {
        return unregisterManager(WAVE_GATE_SESSION_MANAGER_KEY, bridge);
    }

    /** Resolve the active wave gate session bridge. */
    public WaveGateSessionBridge getWaveGateSessionBridge() {
        Object manager = getManager(WAVE_GATE_SESSION_MANAGER_KEY);
        if (manager instanceof WaveGateSessionBridge bridge) {
            return bridge;
        }
        return null;
    }

    /** Register the addon-provided wave gate session executor bridge. */
    public boolean registerWaveGateSessionExecutorBridge(WaveGateSessionExecutorBridge bridge, boolean replaceExisting) {
        return registerManager(WAVE_GATE_SESSION_EXECUTOR_MANAGER_KEY, bridge, replaceExisting);
    }

    /** Unregister the wave gate session executor bridge. */
    public boolean unregisterWaveGateSessionExecutorBridge(WaveGateSessionExecutorBridge bridge) {
        return unregisterManager(WAVE_GATE_SESSION_EXECUTOR_MANAGER_KEY, bridge);
    }

    /** Resolve the active wave gate session executor bridge. */
    public WaveGateSessionExecutorBridge getWaveGateSessionExecutorBridge() {
        Object manager = getManager(WAVE_GATE_SESSION_EXECUTOR_MANAGER_KEY);
        if (manager instanceof WaveGateSessionExecutorBridge bridge) {
            return bridge;
        }
        return null;
    }

    /** Register the addon-provided gate instance routing bridge. */
    public boolean registerGateInstanceRoutingBridge(GateInstanceRoutingBridge bridge, boolean replaceExisting) {
        return registerManager(GATE_INSTANCE_ROUTING_MANAGER_KEY, bridge, replaceExisting);
    }

    /** Unregister the gate instance routing bridge. */
    public boolean unregisterGateInstanceRoutingBridge(GateInstanceRoutingBridge bridge) {
        return unregisterManager(GATE_INSTANCE_ROUTING_MANAGER_KEY, bridge);
    }

    /** Resolve the active gate instance routing bridge. */
    public GateInstanceRoutingBridge getGateInstanceRoutingBridge() {
        Object manager = getManager(GATE_INSTANCE_ROUTING_MANAGER_KEY);
        if (manager instanceof GateInstanceRoutingBridge bridge) {
            return bridge;
        }
        return null;
    }

    /** Register a gate content provider. */
    public boolean registerDungeonGateContentProvider(DungeonGateContentProvider provider, boolean replaceExisting) {
        return registerManager(DUNGEON_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":" + providerKey(provider), provider,
                replaceExisting);
    }

    /** Unregister a previously registered dungeon gate content provider. */
    public boolean unregisterDungeonGateContentProvider(DungeonGateContentProvider provider) {
        return unregisterManager(DUNGEON_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":" + providerKey(provider), provider);
    }

    /** Resolve a registered dungeon gate content provider by provider id. */
    public DungeonGateContentProvider getDungeonGateContentProvider(String providerId) {
        Object manager = getManager(DUNGEON_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":" + normalizeProviderId(providerId));
        if (manager instanceof DungeonGateContentProvider provider) {
            return provider;
        }
        return null;
    }

    /** List all currently registered dungeon gate content providers. */
    public List<DungeonGateContentProvider> getDungeonGateContentProviders() {
        return managerRegistry.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(DUNGEON_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":"))
                .map(Map.Entry::getValue)
                .filter(DungeonGateContentProvider.class::isInstance)
                .map(DungeonGateContentProvider.class::cast)
                .toList();
    }

    /** Register an instance dungeon definition that Endless Leveling can route into. */
    public boolean registerInstanceDungeon(InstanceDungeonDefinition definition, boolean replaceExisting) {
        return registerManager(INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY + ":" + dungeonKey(definition), definition,
                replaceExisting);
    }

    /** Unregister a previously registered instance dungeon definition. */
    public boolean unregisterInstanceDungeon(InstanceDungeonDefinition definition) {
        return unregisterManager(INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY + ":" + dungeonKey(definition), definition);
    }

    /** Resolve an instance dungeon definition by its registered id. */
    public InstanceDungeonDefinition getInstanceDungeon(String dungeonId) {
        Object manager = getManager(INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY + ":" + normalizeDungeonId(dungeonId));
        if (manager instanceof InstanceDungeonDefinition definition) {
            return definition;
        }
        return null;
    }

    /** List all registered instance dungeon definitions. */
    public List<InstanceDungeonDefinition> getInstanceDungeons() {
        return managerRegistry.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(INSTANCE_DUNGEON_DEFINITION_MANAGER_KEY + ":"))
                .map(Map.Entry::getValue)
                .filter(InstanceDungeonDefinition.class::isInstance)
                .map(InstanceDungeonDefinition.class::cast)
                .sorted((left, right) -> left.dungeonId().compareToIgnoreCase(right.dungeonId()))
                .toList();
    }

    /** Resolve the registered instance dungeon for a dungeon gate block id. */
    public InstanceDungeonDefinition getInstanceDungeonByBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        String normalizedBlockId = stripDungeonRankSuffix(blockId);
        for (InstanceDungeonDefinition definition : getInstanceDungeons()) {
            if (definition.basePortalBlockId().equalsIgnoreCase(blockId)
                    || definition.basePortalBlockId().equalsIgnoreCase(normalizedBlockId)) {
                return definition;
            }
        }
        return null;
    }

    /** Resolve the registered instance dungeon for a routing or legacy template name. */
    public InstanceDungeonDefinition getInstanceDungeonByRoutingTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return null;
        }
        for (InstanceDungeonDefinition definition : getInstanceDungeons()) {
            if (definition.routingTemplateName().equalsIgnoreCase(templateName)) {
                return definition;
            }
            if (definition.legacyTemplateName() != null
                    && definition.legacyTemplateName().equalsIgnoreCase(templateName)) {
                return definition;
            }
        }
        return null;
    }

    /** Resolve the registered instance dungeon from a live world name. */
    public InstanceDungeonDefinition getInstanceDungeonByWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        String normalizedWorldName = worldName.toLowerCase(Locale.ROOT);
        InstanceDungeonDefinition best = null;
        int bestLength = -1;
        for (InstanceDungeonDefinition definition : getInstanceDungeons()) {
            String worldPrefix = "el_gate_" + normalizeWorldToken(definition.worldNameToken());
            String normalizedRouting = definition.routingTemplateName().toLowerCase(Locale.ROOT);
            String normalizedLegacy = definition.legacyTemplateName() == null
                    ? null
                    : definition.legacyTemplateName().toLowerCase(Locale.ROOT);

            boolean matches = normalizedWorldName.equals(worldPrefix)
                    || normalizedWorldName.startsWith(worldPrefix + "_")
                    || normalizedWorldName.equals(normalizedRouting)
                    || normalizedWorldName.contains(normalizedRouting)
                    || (normalizedLegacy != null
                            && (normalizedWorldName.equals(normalizedLegacy)
                                    || normalizedWorldName.contains(normalizedLegacy)));

            if (!matches) {
                continue;
            }

            int candidateLength = Math.max(worldPrefix.length(), normalizedRouting.length());
            if (best == null || candidateLength > bestLength) {
                best = definition;
                bestLength = candidateLength;
            }
        }
        return best;
    }

    /** Resolve the canonical routing template for a registered instance dungeon template variant. */
    public String canonicalizeInstanceDungeonRoutingTemplate(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? templateName : definition.routingTemplateName();
    }

    /** Resolve the original template name associated with a registered instance dungeon. */
    public String resolveInstanceDungeonOriginalTemplateName(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        if (definition == null) {
            return templateName;
        }
        return definition.legacyTemplateName() == null || definition.legacyTemplateName().isBlank()
                ? definition.routingTemplateName()
                : definition.legacyTemplateName();
    }

    /** Resolve the registered display name for a dungeon routing template. */
    public String resolveInstanceDungeonDisplayName(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? templateName : definition.displayName();
    }

    /** Resolve the fixed-spawn suffix for a registered dungeon routing template. */
    public String resolveInstanceDungeonSpawnSuffix(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? null : definition.spawnSuffix();
    }

    /** Resolve the unranked portal block id for a registered dungeon routing template. */
    public String resolveInstanceDungeonBasePortalBlockId(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition == null ? null : definition.basePortalBlockId();
    }

    /** Build the deterministic world id Endless Leveling should use for a dungeon gate instance. */
    public String buildInstanceDungeonWorldName(String templateName, String gateIdentity) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        String tokenSource = definition == null ? templateName : definition.worldNameToken();
        String dungeonToken = sanitizeInstanceToken(tokenSource, "dungeon");
        if (gateIdentity == null || gateIdentity.isBlank()) {
            return "el_gate_" + dungeonToken + "_" + UUID.randomUUID();
        }
        String gateToken = sanitizeInstanceToken(gateIdentity, "gate");
        if (gateToken.startsWith("el_gate_")) {
            gateToken = gateToken.substring("el_gate_".length());
        }
        gateToken = gateToken.replaceAll("_+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
        if (gateToken.isBlank()) {
            gateToken = UUID.randomUUID().toString();
        }
        return "el_gate_" + dungeonToken + "_" + gateToken;
    }

    /** Build the expected instance group id for a dungeon gate entry. */
    public String buildInstanceDungeonGroupId(String gateIdentity, String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        String tokenSource = definition == null ? templateName : definition.worldNameToken();
        String dungeonToken = sanitizeInstanceToken(tokenSource, "dungeon");
        if (gateIdentity == null || gateIdentity.isBlank()) {
            return "el_gate_" + dungeonToken;
        }
        return "el_gate_" + dungeonToken + "_" + sanitizeInstanceToken(gateIdentity, "gate");
    }

    /** Returns true when the supplied template is a registered legacy/original dungeon template. */
    public boolean isInstanceDungeonOriginalTemplate(String templateName) {
        InstanceDungeonDefinition definition = getInstanceDungeonByRoutingTemplate(templateName);
        return definition != null
                && definition.legacyTemplateName() != null
                && definition.legacyTemplateName().equalsIgnoreCase(templateName);
    }

    /** Register a wave gate content provider. */
    public boolean registerWaveGateContentProvider(WaveGateContentProvider provider, boolean replaceExisting) {
        return registerManager(WAVE_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":" + providerKey(provider), provider,
                replaceExisting);
    }

    /** Unregister a previously registered wave gate content provider. */
    public boolean unregisterWaveGateContentProvider(WaveGateContentProvider provider) {
        return unregisterManager(WAVE_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":" + providerKey(provider), provider);
    }

    /** Resolve a registered wave gate content provider by provider id. */
    public WaveGateContentProvider getWaveGateContentProvider(String providerId) {
        Object manager = getManager(WAVE_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":" + normalizeProviderId(providerId));
        if (manager instanceof WaveGateContentProvider provider) {
            return provider;
        }
        return null;
    }

    /** List all currently registered wave gate content providers. */
    public List<WaveGateContentProvider> getWaveGateContentProviders() {
        return managerRegistry.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(WAVE_GATE_CONTENT_PROVIDER_MANAGER_KEY + ":"))
                .map(Map.Entry::getValue)
                .filter(WaveGateContentProvider.class::isInstance)
                .map(WaveGateContentProvider.class::cast)
                .toList();
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

    private static String providerKey(DungeonGateContentProvider provider) {
        return normalizeProviderId(provider != null ? provider.getProviderId() : null);
    }

    private static String providerKey(WaveGateContentProvider provider) {
        return normalizeProviderId(provider != null ? provider.getProviderId() : null);
    }

    private static String dungeonKey(InstanceDungeonDefinition definition) {
        return normalizeDungeonId(definition != null ? definition.dungeonId() : null);
    }

    private static String normalizeProviderId(String providerId) {
        if (providerId == null) {
            return "default";
        }
        String normalized = providerId.trim();
        return normalized.isEmpty() ? "default" : normalized;
    }

    private static String normalizeDungeonId(String dungeonId) {
        if (dungeonId == null) {
            return "default";
        }
        String normalized = dungeonId.trim();
        return normalized.isEmpty() ? "default" : normalized;
    }

    private static String stripDungeonRankSuffix(String blockId) {
        return blockId.replaceAll("_Rank[SABCDE]$", "");
    }

    private static String normalizeWorldToken(String token) {
        return sanitizeInstanceToken(token, "dungeon");
    }

    private static String sanitizeInstanceToken(String input, String fallbackPrefix) {
        String sanitized = input == null ? "" : input.replaceAll("[^A-Za-z0-9._-]", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
        if (sanitized.isBlank()) {
            return fallbackPrefix + "_" + UUID.randomUUID();
        }
        return sanitized;
    }
}
