package com.airijko.endlessleveling.compatibility;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;

public final class NameplateBuilderCompatibility {

    private static final String API_CLASS = "com.frotty27.nameplatebuilder.api.NameplateAPI";
    private static final String SEGMENT_TARGET_CLASS = "com.frotty27.nameplatebuilder.api.SegmentTarget";
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
    private static volatile Method describeMethod = null;
    private static volatile Method registerMethod = null;
    private static volatile Method removeMethod = null;
    private static volatile Object segmentTargetNpcs = null;
    private static volatile Object segmentTargetPlayers = null;

    private NameplateBuilderCompatibility() {
    }

    public static boolean isAvailable() {
        return ensureInitialized();
    }

    public static boolean describeMobLevelSegment(JavaPlugin plugin) {
        if (plugin == null || !ensureInitialized()) {
            return false;
        }

        try {
            describeMethod.invoke(
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
            describeMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_LEVEL,
                    "EL Player Level",
                    segmentTargetPlayers,
                    "Lv.10");
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
            describeMethod.invoke(
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
            describeMethod.invoke(
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
            describeMethod.invoke(
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
            describeMethod.invoke(
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
            describeMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_PRESTIGE_LEVEL,
                    "EL Player Prestige Level",
                    segmentTargetPlayers,
                    "P.3");
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
            describeMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_RACE,
                    "EL Player Race",
                    segmentTargetPlayers,
                    "Human");
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
            describeMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_CLASS_PRIMARY,
                    "EL Player Class Primary",
                    segmentTargetPlayers,
                    "Adventurer");
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
            describeMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_CLASS_SECONDARY,
                    "EL Player Class Secondary",
                    segmentTargetPlayers,
                    "Scout");
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
            describeMethod.invoke(
                    null,
                    plugin,
                    EL_PLAYER_NAME,
                    "EL Player Name",
                    segmentTargetPlayers,
                    "PlayerName");
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
            registerMethod.invoke(null, store, entityRef, EL_MOB_LEVEL, "Lv." + level);
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
            registerMethod.invoke(null, store, entityRef, EL_MOB_NAME, text);
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
            registerMethod.invoke(null, store, entityRef, EL_SUMMON_LABEL, text);
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
            registerMethod.invoke(null, store, entityRef, EL_PLAYER_LEVEL, "Lv." + level);
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
            registerMethod.invoke(null, store, entityRef, EL_MOB_LEVEL, "Lv." + level);
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
            registerMethod.invoke(null, store, entityRef, EL_MOB_NAME, name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registerELShowHealth(Store<EntityStore> store, Ref<EntityStore> entityRef, double currentHealth, double maxHealth) {
        if (store == null || entityRef == null || !ensureInitialized()) {
            return false;
        }

        try {
            String healthText = String.format("%.0f/%.0f❤", currentHealth, maxHealth);
            registerMethod.invoke(null, store, entityRef, EL_MOB_HEALTH, healthText);
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
            registerMethod.invoke(null, store, entityRef, EL_PLAYER_PRESTIGE_LEVEL, "P." + prestigeLevel);
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
            registerMethod.invoke(null, store, entityRef, EL_PLAYER_RACE, race);
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
            registerMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_PRIMARY, classPrimary);
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
            registerMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_SECONDARY, classSecondary);
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
            registerMethod.invoke(null, store, entityRef, EL_PLAYER_NAME, playerName);
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
            removeMethod.invoke(null, store, entityRef, EL_MOB_LEVEL);
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
            removeMethod.invoke(null, store, entityRef, EL_SUMMON_LABEL);
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
            removeMethod.invoke(null, store, entityRef, EL_PLAYER_LEVEL);
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
            removeMethod.invoke(null, store, entityRef, EL_PLAYER_PRESTIGE_LEVEL);
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
            removeMethod.invoke(null, store, entityRef, EL_PLAYER_RACE);
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
            removeMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_PRIMARY);
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
            removeMethod.invoke(null, store, entityRef, EL_PLAYER_CLASS_SECONDARY);
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
            removeMethod.invoke(null, store, entityRef, EL_PLAYER_NAME);
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
            removeMethod.invoke(null, store, entityRef, EL_MOB_LEVEL);
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
            removeMethod.invoke(null, store, entityRef, EL_MOB_NAME);
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
            removeMethod.invoke(null, store, entityRef, EL_MOB_HEALTH);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static synchronized boolean ensureInitialized() {
        if (initialized) {
            return describeMethod != null && registerMethod != null && removeMethod != null
                    && segmentTargetNpcs != null;
        }

        initialized = true;

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Class<?> segmentTargetClass = Class.forName(SEGMENT_TARGET_CLASS);

            describeMethod = apiClass.getMethod(
                    "describe",
                    JavaPlugin.class,
                    String.class,
                    String.class,
                    segmentTargetClass,
                    String.class);
            registerMethod = apiClass.getMethod(
                    "register",
                    Store.class,
                    Ref.class,
                    String.class,
                    String.class);
            removeMethod = apiClass.getMethod(
                    "remove",
                    Store.class,
                    Ref.class,
                    String.class);

            segmentTargetNpcs = Enum.valueOf((Class<? extends Enum>) segmentTargetClass.asSubclass(Enum.class), "NPCS");
            segmentTargetPlayers = resolveSegmentTargetPlayer(segmentTargetClass);
            return true;
        } catch (Throwable ignored) {
            describeMethod = null;
            registerMethod = null;
            removeMethod = null;
            segmentTargetNpcs = null;
            segmentTargetPlayers = null;
            return false;
        }
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