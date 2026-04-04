package com.airijko.endlessleveling.compatibility;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

public final class NameplateBuilderCompatibility {

    private static final String API_CLASS = "com.frotty27.nameplatebuilder.api.NameplateAPI";
    private static final String SEGMENT_TARGET_CLASS = "com.frotty27.nameplatebuilder.api.SegmentTarget";
    private static final String SEGMENT_BUILDER_CLASS = "com.frotty27.nameplatebuilder.api.SegmentBuilder";
    private static final String SEGMENT_RESOLVER_CLASS = "com.frotty27.nameplatebuilder.api.SegmentResolver";
    private static final String EL_MOB_LEVEL = "EL_Mob_Level";
    private static final String EL_MOB_NAME = "EL_Mob_Name";
    private static final String EL_MOB_HEALTH = "EL_Mob_Health";
    private static final String EL_PLAYER_LEVEL = "EL_Player_Level";
    private static final String EL_PLAYER_PRESTIGE_LEVEL = "EL_Player_Prestige_Level";
    private static final String EL_PLAYER_RACE = "EL_Player_Race";
    private static final String EL_PLAYER_CLASS_PRIMARY = "EL_Player_Class_Primary";
    private static final String EL_PLAYER_CLASS_SECONDARY = "EL_Player_Class_Secondary";
    private static final String EL_PLAYER_NAME = "EL_Player_Name";
    private static final String EL_SUMMON_LABEL = "EL_Summon_Label";

    private static volatile boolean initialized = false;
    private static volatile Method defineMethod = null;
    private static volatile Method setTextMethod = null;
    private static volatile Method clearTextMethod = null;
    private static volatile Method segmentBuilderResolverMethod = null;
    private static volatile Method segmentBuilderCacheTicksMethod = null;
    private static volatile Method addToAdminChainMethod = null;
    private static volatile Method addToAdminChainIfRegisteredMethod = null;
    private static volatile Method setAdminChainMethod = null;
    private static volatile Method initAdminChainMethod = null;
    private static volatile Class<?> segmentResolverInterface = null;
    private static volatile Object segmentTargetNpcs = null;
    private static volatile Object segmentTargetPlayers = null;
    private static volatile PlayerDataManager playerDataManager = null;

    private static final int PLAYER_SEGMENT_CACHE_TICKS = 20;

    private NameplateBuilderCompatibility() {
    }

    public static boolean isAvailable() {
        return ensureInitialized();
    }

    public static void setPlayerDataManager(PlayerDataManager manager) {
        playerDataManager = manager;
    }

    public static boolean describeMobLevelSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized()) {
            return false;
        }

        try {
            defineMethod.invoke(
                    null,
                    plugin,
                    EL_MOB_LEVEL,
                    "EL Mob Level",
                    segmentTargetNpcs,
                    "Lv.10");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describePlayerLevelSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            Object segmentBuilder = defineMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_LEVEL,
                    "EL Player Level",
                    segmentTargetPlayers,
                    "Lv.10");
            attachPlayerResolver(segmentBuilder, EL_PLAYER_LEVEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeSummonLabelSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized()) {
            return false;
        }

        try {
            defineMethod.invoke(
                    null,
                    plugin,
                    EL_SUMMON_LABEL,
                    "EL Summon Label",
                    segmentTargetNpcs,
                    "Player's Undead Summon");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELShowLevelSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized()) {
            return false;
        }

        try {
            defineMethod.invoke(
                    null,
                    plugin,
                    EL_MOB_LEVEL,
                    "EL Mob Level",
                    segmentTargetNpcs,
                    "Lv.10");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELShowNameSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized()) {
            return false;
        }

        try {
            defineMethod.invoke(
                    null,
                    plugin,
                    EL_MOB_NAME,
                    "EL Mob Name",
                    segmentTargetNpcs,
                    "Zombie");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELShowHealthSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized()) {
            return false;
        }

        try {
            defineMethod.invoke(
                    null,
                    plugin,
                    EL_MOB_HEALTH,
                    "EL Mob Health",
                    segmentTargetNpcs,
                    "100/100❤");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELPlayerPrestigeLevelSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            Object segmentBuilder = defineMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_PRESTIGE_LEVEL,
                    "EL Player Prestige Level",
                    segmentTargetPlayers,
                    "P.3");
            attachPlayerResolver(segmentBuilder, EL_PLAYER_PRESTIGE_LEVEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELPlayerRaceSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            Object segmentBuilder = defineMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_RACE,
                    "EL Player Race",
                    segmentTargetPlayers,
                    "Human");
            attachPlayerResolver(segmentBuilder, EL_PLAYER_RACE);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELPlayerClassPrimarySegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            Object segmentBuilder = defineMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_CLASS_PRIMARY,
                    "EL Player Class Primary",
                    segmentTargetPlayers,
                    "Adventurer");
            attachPlayerResolver(segmentBuilder, EL_PLAYER_CLASS_PRIMARY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELPlayerClassSecondarySegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            Object segmentBuilder = defineMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_CLASS_SECONDARY,
                    "EL Player Class Secondary",
                    segmentTargetPlayers,
                    "Scout");
            attachPlayerResolver(segmentBuilder, EL_PLAYER_CLASS_SECONDARY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean describeELPlayerNameSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            Object segmentBuilder = defineMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_NAME,
                    "EL Player Name",
                    segmentTargetPlayers,
                    "PlayerName");
            attachPlayerResolver(segmentBuilder, EL_PLAYER_NAME);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setAndLockNpcAdminChain(JavaPlugin plugin, String... segmentIds) {
        if (plugin == null || !ensureInitialized() || setAdminChainMethod == null
                || segmentTargetNpcs == null) {
            return false;
        }
        try {
            setAdminChainMethod.invoke(null, plugin, segmentTargetNpcs, true,
                    java.util.Arrays.asList(segmentIds));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setAndLockPlayerAdminChain(JavaPlugin plugin, String... segmentIds) {
        if (plugin == null || !ensureInitialized() || setAdminChainMethod == null
                || segmentTargetPlayers == null) {
            return false;
        }
        try {
            setAdminChainMethod.invoke(null, plugin, segmentTargetPlayers, true,
                    java.util.Arrays.asList(segmentIds));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Sets the NPC admin chain to the given segments in order, but only if no admin chain
     * has been configured yet (initial-setup semantics). Existing admin configurations are
     * preserved.
     */
    public static boolean initAndLockNpcAdminChain(JavaPlugin plugin, String... segmentIds) {
        if (plugin == null || !ensureInitialized() || initAdminChainMethod == null
                || segmentTargetNpcs == null) {
            return false;
        }
        try {
            initAdminChainMethod.invoke(null, plugin, segmentTargetNpcs, true,
                    java.util.Arrays.asList(segmentIds));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Sets the player admin chain to the given segments in order, but only if no admin chain
     * has been configured yet (initial-setup semantics). Existing admin configurations are
     * preserved.
     */
    public static boolean initAndLockPlayerAdminChain(JavaPlugin plugin, String... segmentIds) {
        if (plugin == null || !ensureInitialized() || initAdminChainMethod == null
                || segmentTargetPlayers == null) {
            return false;
        }
        try {
            initAdminChainMethod.invoke(null, plugin, segmentTargetPlayers, true,
                    java.util.Arrays.asList(segmentIds));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean addNpcSegmentToAdminChainIfRegistered(String segmentId) {
        if (segmentId == null || segmentId.isBlank()
                || !ensureInitialized() || addToAdminChainIfRegisteredMethod == null
                || segmentTargetNpcs == null) {
            return false;
        }
        try {
            addToAdminChainIfRegisteredMethod.invoke(null, segmentId, segmentTargetNpcs);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean addNpcSegmentToAdminChain(JavaPlugin plugin, String segmentId) {
        if (plugin == null || segmentId == null || segmentId.isBlank()
                || !ensureInitialized() || addToAdminChainMethod == null || segmentTargetNpcs == null) {
            return false;
        }
        try {
            addToAdminChainMethod.invoke(null, plugin, segmentId, segmentTargetNpcs);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean addPlayerSegmentToAdminChain(JavaPlugin plugin, String segmentId) {
        if (plugin == null || segmentId == null || segmentId.isBlank()
                || !ensureInitialized() || addToAdminChainMethod == null || segmentTargetPlayers == null) {
            return false;
        }
        try {
            addToAdminChainMethod.invoke(null, plugin, segmentId, segmentTargetPlayers);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerMobLevel(Store<EntityStore> store, Ref<EntityStore> entityRef, int level) {
        if (store == null || entityRef == null || level <= 0 || !ensureInitialized()) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_MOB_LEVEL, "Lv." + level);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerMobText(Store<EntityStore> store, Ref<EntityStore> entityRef, String text) {
        if (store == null || entityRef == null || text == null || text.isBlank() || !ensureInitialized()) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_MOB_NAME, text);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerSummonText(Store<EntityStore> store, Ref<EntityStore> entityRef, String text) {
        if (store == null || entityRef == null || text == null || text.isBlank() || !ensureInitialized()) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_SUMMON_LABEL, text);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerPlayerLevel(Store<EntityStore> store, Ref<EntityStore> entityRef, int level) {
        if (store == null || entityRef == null || level <= 0 || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_PLAYER_LEVEL, "Lv." + level);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELShowLevel(Store<EntityStore> store, Ref<EntityStore> entityRef, int level) {
        if (store == null || entityRef == null || level <= 0 || !ensureInitialized()) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_MOB_LEVEL, "Lv." + level);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELShowName(Store<EntityStore> store, Ref<EntityStore> entityRef, String name) {
        if (store == null || entityRef == null || name == null || name.isBlank() || !ensureInitialized()) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_MOB_NAME, name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELShowHealth(Store<EntityStore> store, Ref<EntityStore> entityRef, double currentHealth, double maxHealth) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        if (!Double.isFinite(currentHealth) || !Double.isFinite(maxHealth) || maxHealth <= 0.0D) {
            return false;
        }

        try {
            String healthText = String.format("%.0f/%.0f❤", currentHealth, maxHealth);
            setTextMethod.invoke(null, store, entityRef, EL_MOB_HEALTH, healthText);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELPlayerPrestigeLevel(Store<EntityStore> store, Ref<EntityStore> entityRef, int prestigeLevel) {
        if (store == null || entityRef == null || prestigeLevel < 0 || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_PLAYER_PRESTIGE_LEVEL, "P." + prestigeLevel);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELPlayerRace(Store<EntityStore> store, Ref<EntityStore> entityRef, String race) {
        if (store == null || entityRef == null || race == null || race.isBlank() || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_PLAYER_RACE, race);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELPlayerClassPrimary(Store<EntityStore> store, Ref<EntityStore> entityRef, String classPrimary) {
        if (store == null || entityRef == null || classPrimary == null || classPrimary.isBlank() || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_PRIMARY, classPrimary);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELPlayerClassSecondary(Store<EntityStore> store, Ref<EntityStore> entityRef, String classSecondary) {
        if (store == null || entityRef == null || classSecondary == null || classSecondary.isBlank() || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_SECONDARY, classSecondary);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELPlayerName(Store<EntityStore> store, Ref<EntityStore> entityRef, String playerName) {
        if (store == null || entityRef == null || playerName == null || playerName.isBlank() || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            setTextMethod.invoke(null, store, entityRef, EL_PLAYER_NAME, playerName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeMobLevel(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_MOB_LEVEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeSummonText(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_SUMMON_LABEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removePlayerLevel(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_PLAYER_LEVEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELPlayerPrestigeLevel(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_PLAYER_PRESTIGE_LEVEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELPlayerRace(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_PLAYER_RACE);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELPlayerClassPrimary(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_PRIMARY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELPlayerClassSecondary(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_SECONDARY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELPlayerName(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized() || segmentTargetPlayers == null) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_PLAYER_NAME);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELShowLevel(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_MOB_LEVEL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELShowName(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_MOB_NAME);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeELShowHealth(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        try {
            clearTextMethod.invoke(null, store, entityRef, EL_MOB_HEALTH);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static synchronized boolean ensureInitialized() {
        if (initialized) {
            return defineMethod != null && setTextMethod != null && clearTextMethod != null
                    && segmentTargetNpcs != null;
        }

        initialized = true;

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Class<?> segmentTargetClass = Class.forName(SEGMENT_TARGET_CLASS);
            Class<?> segmentBuilderClass = tryLoadClass(SEGMENT_BUILDER_CLASS);
            segmentResolverInterface = tryLoadClass(SEGMENT_RESOLVER_CLASS);

                defineMethod = resolveMethod(
                    apiClass,
                    new String[] { "define", "describe" },
                    JavaPlugin.class,
                    String.class,
                    String.class,
                    segmentTargetClass,
                    String.class);
                setTextMethod = resolveMethod(
                    apiClass,
                    new String[] { "setText", "register" },
                    Store.class,
                    Ref.class,
                    String.class,
                    String.class);
                clearTextMethod = resolveMethod(
                    apiClass,
                    new String[] { "clearText", "remove" },
                    Store.class,
                    Ref.class,
                    String.class);

                segmentBuilderResolverMethod = resolveOptionalMethod(
                    segmentBuilderClass,
                    new String[] { "resolver" },
                    segmentResolverInterface);
                segmentBuilderCacheTicksMethod = resolveOptionalMethod(
                    segmentBuilderClass,
                    new String[] { "cacheTicks" },
                    int.class);
                addToAdminChainMethod = resolveOptionalMethod(
                    apiClass,
                    new String[] { "addToAdminChain" },
                    JavaPlugin.class,
                    String.class,
                    segmentTargetClass);
                addToAdminChainIfRegisteredMethod = resolveOptionalMethod(
                    apiClass,
                    new String[] { "addToAdminChainIfRegistered" },
                    String.class,
                    segmentTargetClass);
                setAdminChainMethod = resolveOptionalMethod(
                    apiClass,
                    new String[] { "setAdminChain" },
                    JavaPlugin.class,
                    segmentTargetClass,
                    boolean.class,
                    java.util.List.class);
                initAdminChainMethod = resolveOptionalMethod(
                    apiClass,
                    new String[] { "initAdminChain" },
                    JavaPlugin.class,
                    segmentTargetClass,
                    boolean.class,
                    java.util.List.class);

            segmentTargetNpcs = Enum.valueOf((Class<? extends Enum>) segmentTargetClass.asSubclass(Enum.class), "NPCS");
            segmentTargetPlayers = resolveSegmentTargetPlayer(segmentTargetClass);
            return true;
        } catch (Throwable ignored) {
            defineMethod = null;
            setTextMethod = null;
            clearTextMethod = null;
            segmentBuilderResolverMethod = null;
            segmentBuilderCacheTicksMethod = null;
            addToAdminChainMethod = null;
            addToAdminChainIfRegisteredMethod = null;
            setAdminChainMethod = null;
            initAdminChainMethod = null;
            segmentResolverInterface = null;
            segmentTargetNpcs = null;
            segmentTargetPlayers = null;
            return false;
        }
    }

    private interface ResolverEvaluator {
        String resolve(Store<EntityStore> store, Ref<EntityStore> ref, int variantIndex);
    }

    private static void attachPlayerResolver(Object segmentBuilder, String segmentId) {
        if (segmentBuilder == null
                || segmentBuilderResolverMethod == null
                || segmentResolverInterface == null
                || playerDataManager == null
                || segmentId == null
                || segmentId.isBlank()) {
            return;
        }

        ResolverEvaluator evaluator = resolveEvaluatorForSegment(segmentId);
        if (evaluator == null) {
            return;
        }

        try {
            InvocationHandler handler = (proxy, method, args) -> {
                String methodName = method.getName();
                if ("resolve".equals(methodName)) {
                    @SuppressWarnings("unchecked")
                    Store<EntityStore> store = args != null && args.length > 0
                            ? (Store<EntityStore>) args[0]
                            : null;
                    @SuppressWarnings("unchecked")
                    Ref<EntityStore> ref = args != null && args.length > 1
                            ? (Ref<EntityStore>) args[1]
                            : null;
                    int variantIndex = args != null && args.length > 2 && args[2] instanceof Integer
                            ? (Integer) args[2]
                            : 0;
                    return evaluator.resolve(store, ref, variantIndex);
                }
                if ("toString".equals(methodName)) {
                    return "ELResolver(" + segmentId + ")";
                }
                return null;
            };

            Object resolverProxy = Proxy.newProxyInstance(
                    segmentResolverInterface.getClassLoader(),
                    new Class<?>[] { segmentResolverInterface },
                    handler);

            Object configuredBuilder = segmentBuilderResolverMethod.invoke(segmentBuilder, resolverProxy);
            if (configuredBuilder != null && segmentBuilderCacheTicksMethod != null) {
                segmentBuilderCacheTicksMethod.invoke(configuredBuilder, PLAYER_SEGMENT_CACHE_TICKS);
            } else if (segmentBuilderCacheTicksMethod != null) {
                segmentBuilderCacheTicksMethod.invoke(segmentBuilder, PLAYER_SEGMENT_CACHE_TICKS);
            }
        } catch (Throwable ignored) {
        }
    }

    private static ResolverEvaluator resolveEvaluatorForSegment(String segmentId) {
        return switch (segmentId) {
            case EL_PLAYER_LEVEL -> (store, ref, variantIndex) -> {
                PlayerData data = resolvePlayerData(store, ref);
                if (data == null || data.getLevel() <= 0) {
                    return null;
                }
                return "Lv." + data.getLevel();
            };
            case EL_PLAYER_PRESTIGE_LEVEL -> (store, ref, variantIndex) -> {
                PlayerData data = resolvePlayerData(store, ref);
                if (data == null) {
                    return null;
                }
                return "P." + Math.max(0, data.getPrestigeLevel());
            };
            case EL_PLAYER_RACE -> (store, ref, variantIndex) -> {
                PlayerData data = resolvePlayerData(store, ref);
                return data == null ? null : normalizePlayerSegmentValue(data.getRaceId(), "None");
            };
            case EL_PLAYER_CLASS_PRIMARY -> (store, ref, variantIndex) -> {
                PlayerData data = resolvePlayerData(store, ref);
                return data == null ? null : normalizePlayerSegmentValue(data.getPrimaryClassId(), "None");
            };
            case EL_PLAYER_CLASS_SECONDARY -> (store, ref, variantIndex) -> {
                PlayerData data = resolvePlayerData(store, ref);
                return data == null ? null : normalizePlayerSegmentValue(data.getSecondaryClassId(), "None");
            };
            case EL_PLAYER_NAME -> (store, ref, variantIndex) -> {
                PlayerRef playerRef = resolvePlayerRef(store, ref);
                if (playerRef == null) {
                    return null;
                }
                String username = playerRef.getUsername();
                return (username == null || username.isBlank()) ? "Player" : username;
            };
            default -> null;
        };
    }

    private static PlayerData resolvePlayerData(Store<EntityStore> store, Ref<EntityStore> ref) {
        PlayerDataManager manager = playerDataManager;
        if (manager == null) {
            return null;
        }

        PlayerRef playerRef = resolvePlayerRef(store, ref);
        if (playerRef == null) {
            return null;
        }

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return null;
        }

        PlayerData data = manager.get(uuid);
        if (data != null) {
            return data;
        }

        String baseName = playerRef.getUsername();
        if (baseName == null || baseName.isBlank()) {
            baseName = "Player";
        }
        return manager.loadOrCreate(uuid, baseName);
    }

    private static PlayerRef resolvePlayerRef(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return null;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }

        int targetIndex = ref.getIndex();
        for (PlayerRef candidate : universe.getPlayers()) {
            if (candidate == null || !candidate.isValid()) {
                continue;
            }
            Ref<EntityStore> candidateRef = candidate.getReference();
            if (candidateRef == null || !candidateRef.isValid()) {
                continue;
            }
            if (candidateRef.getStore() == store && candidateRef.getIndex() == targetIndex) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalizePlayerSegmentValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static Class<?> tryLoadClass(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveOptionalMethod(Class<?> owner, String[] candidates, Class<?>... parameterTypes) {
        if (owner == null || candidates == null || candidates.length == 0) {
            return null;
        }

        if (parameterTypes != null) {
            for (Class<?> parameterType : parameterTypes) {
                if (parameterType == null) {
                    return null;
                }
            }
        }

        for (String candidate : candidates) {
            try {
                return owner.getMethod(candidate, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
    }

    private static Method resolveMethod(Class<?> owner, String[] candidates, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (String candidate : candidates) {
            try {
                return owner.getMethod(candidate, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }

        throw new NoSuchMethodException("No matching method found: " + String.join(", ", candidates));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object resolveSegmentTargetPlayer(Class<?> segmentTargetClass) {
        Class<? extends Enum> enumClass = segmentTargetClass.asSubclass(Enum.class);
        String[] candidates = new String[] { "PLAYERS", "PLAYER" };
        for (String candidate : candidates) {
            try {
                return Enum.valueOf(enumClass, candidate);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}