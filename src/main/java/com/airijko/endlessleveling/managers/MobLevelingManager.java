package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.PluginFilesManager;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.WorldGenId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates mob leveling logic previously in `MobLevelingSystem`.
 */
public class MobLevelingManager {

    private final Set<Integer> applied = new HashSet<>();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private final ConfigManager configManager;

    public MobLevelingManager(PluginFilesManager filesManager) {
        this.configManager = new ConfigManager(filesManager.getLevelingFile(), false);
    }

    /**
     * Attempts to apply mob-leveling (health scaling) to the entity referenced by
     * {@code ref}.
     * Returns true if the entity was modified and marked as applied.
     */
    public boolean applyLeveling(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || commandBuffer == null)
            return false;

        int idx = ref.getIndex();
        if (applied.contains(idx))
            return false;

        if (!isMobLevelingEnabled())
            return false;

        // skip players
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.isValid())
            return false;

        // check blacklist
        String mobType = null;
        var worldGen = commandBuffer.getComponent(ref, WorldGenId.getComponentType());
        if (worldGen != null) {
            try {
                mobType = worldGen.toString();
            } catch (Throwable ignored) {
            }
        }
        if (mobType != null && isMobTypeBlacklisted(mobType))
            return false;

        // If passive mob leveling is disabled, skip non-NPCs
        if (!allowPassiveMobLeveling()) {
            Object npcComp = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
            if (npcComp == null) {
                return false;
            }
        }

        // Hard-coded level (kept from original implementation)
        int mobLevel = 100;

        EntityStatMap statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        boolean modified = false;
        if (statMap != null) {
            if (!isMobHealthScalingEnabled()) {
                // Health scaling disabled via config; skip and retry later
                return false;
            }
            try {
                EntityStatValue hp = statMap.get(DefaultEntityStatTypes.getHealth());
                if (hp != null) {
                    double mult = getMobHealthMultiplierForLevel(mobLevel);
                    float oldMax = hp.getMax();
                    float newMax = (float) Math.max(1.0, oldMax * mult);
                    float cur = hp.get();
                    float newCur;
                    if (oldMax > 0.0f) {
                        newCur = (cur / oldMax) * newMax;
                    } else {
                        newCur = Math.min(newMax, cur * (float) mult);
                    }
                    newCur = Math.max(0.01f, Math.min(newMax, newCur));
                    boolean setMaxSucceeded = false;
                    try {
                        Field maxField = EntityStatValue.class.getDeclaredField("max");
                        maxField.setAccessible(true);
                        maxField.setFloat(hp, newMax);
                        setMaxSucceeded = true;
                    } catch (Throwable t) {
                        LOGGER.atInfo().log(
                                "MobLeveling: failed to set hp.max via reflection for entity %d: %s",
                                idx, t.toString());
                    }
                    if (setMaxSucceeded) {
                        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), newCur);
                        modified = true;
                    } else {
                        LOGGER.atInfo().log(
                                "MobLeveling: skipping current health update because max update failed for entity %d",
                                idx);
                        modified = false;
                    }
                }
            } catch (Throwable ignored) {
                // avoid crashing server on unexpected errors
            }
        }

        if (modified) {
            applied.add(idx);
            return true;
        }
        return false;
    }

    /** Whether mob leveling is enabled (Mob_Leveling.Enabled) */
    public boolean isMobLevelingEnabled() {
        Object raw = configManager.get("Mob_Leveling.Enabled", Boolean.TRUE, false);
        if (raw instanceof Boolean b)
            return b;
        if (raw instanceof Number n)
            return n.intValue() != 0;
        if (raw instanceof String s)
            return Boolean.parseBoolean(s.trim());
        return false;
    }

    /** Whether passive mobs are allowed to be leveled */
    public boolean allowPassiveMobLeveling() {
        Object raw = configManager.get("Mob_Leveling.allow_passive_mob_leveling", Boolean.FALSE, false);
        if (raw instanceof Boolean b)
            return b;
        if (raw instanceof Number n)
            return n.intValue() != 0;
        if (raw instanceof String s)
            return Boolean.parseBoolean(s.trim());
        return false;
    }

    /** Returns true if mob type is blacklisted (case-insensitive) */
    public boolean isMobTypeBlacklisted(String mobType) {
        if (mobType == null || mobType.isBlank())
            return false;
        Object raw = configManager.get("Mob_Leveling.Blacklist_Mob_Types", null, false);
        if (raw == null)
            return false;

        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry == null)
                    continue;
                if (mobType.equalsIgnoreCase(entry.toString())) {
                    return true;
                }
            }
            return false;
        }

        String single = raw.toString();
        return mobType.equalsIgnoreCase(single);
    }

    public double getMobHealthMultiplierForLevel(int level) {
        double base = getConfigDouble("Mob_Leveling.Scaling.Health.Base_Multiplier", 1.0);
        double per = getConfigDouble("Mob_Leveling.Scaling.Health.Per_Level", 0.05);
        int effectiveLevel = Math.max(1, level);
        return base * (1.0 + per * (effectiveLevel - 1));
    }

    public double getMobDamageMultiplierForLevel(int level) {
        double base = getConfigDouble("Mob_Leveling.Scaling.Damage.Base_Multiplier", 1.0);
        double per = getConfigDouble("Mob_Leveling.Scaling.Damage.Per_Level", 0.03);
        int effectiveLevel = Math.max(1, level);
        return base * (1.0 + per * (effectiveLevel - 1));
    }

    public boolean isMobDamageScalingEnabled() {
        Object raw = configManager.get("Mob_Leveling.Scaling.Damage.Enabled", Boolean.FALSE, false);
        if (raw instanceof Boolean b)
            return b;
        if (raw instanceof Number n)
            return n.intValue() != 0;
        if (raw instanceof String s)
            return Boolean.parseBoolean(s.trim());
        return false;
    }

    public boolean isMobHealthScalingEnabled() {
        Object raw = configManager.get("Mob_Leveling.Scaling.Health.Enabled", Boolean.FALSE, false);
        if (raw instanceof Boolean b)
            return b;
        if (raw instanceof Number n)
            return n.intValue() != 0;
        if (raw instanceof String s)
            return Boolean.parseBoolean(s.trim());
        return false;
    }

    private double getConfigDouble(String path, double defaultValue) {
        Object raw = configManager.get(path, defaultValue, false);
        if (raw == null)
            return defaultValue;
        try {
            if (raw instanceof Number)
                return ((Number) raw).doubleValue();
            return Double.parseDouble(raw.toString());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
