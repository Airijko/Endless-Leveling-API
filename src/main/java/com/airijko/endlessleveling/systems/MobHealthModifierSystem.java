package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.LevelingManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.WorldGenId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.Set;

/**
 * Applies mob max-health modifiers using EntityStatMap.putModifier (no
 * reflection).
 */
public class MobHealthModifierSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Set<Integer> applied = new HashSet<>();

    public MobHealthModifierSystem() {
    }

    @Override
    public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        if (store == null || store.isShutdown())
            return;

        LevelingManager levelingManager = EndlessLeveling.getInstance().getLevelingManager();
        if (levelingManager == null || !levelingManager.isMobLevelingEnabled()
                || !levelingManager.isMobHealthScalingEnabled())
            return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        applyModifierToRef(ref, store, commandBuffer);
    }

    private static void applyModifierToRef(Ref<EntityStore> ref, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || store == null)
            return;
        int idx = ref.getIndex();
        if (applied.contains(idx))
            return;

        LevelingManager levelingManager = EndlessLeveling.getInstance().getLevelingManager();
        if (levelingManager == null || !levelingManager.isMobLevelingEnabled()
                || !levelingManager.isMobHealthScalingEnabled())
            return;

        // skip players
        PlayerRef playerRef;
        if (commandBuffer != null) {
            playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        } else {
            playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        }
        if (playerRef != null && playerRef.isValid())
            return;

        // check blacklist
        String mobType = null;
        Object worldGen;
        if (commandBuffer != null) {
            worldGen = commandBuffer.getComponent(ref, WorldGenId.getComponentType());
        } else {
            worldGen = store.getComponent(ref, WorldGenId.getComponentType());
        }
        if (worldGen != null) {
            try {
                mobType = worldGen.toString();
            } catch (Throwable ignored) {
            }
        }
        if (mobType != null && levelingManager.isMobTypeBlacklisted(mobType))
            return;

        // If passive mob leveling is disabled, skip entities that are not NPCs
        if (!levelingManager.allowPassiveMobLeveling()) {
            Object npcComp;
            if (commandBuffer != null) {
                npcComp = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
            } else {
                npcComp = store.getComponent(ref, NPCEntity.getComponentType());
            }
            if (npcComp == null)
                return;
        }

        // Hard-coded level for now
        int mobLevel = 100;

        EntityStatMap statMap;
        if (commandBuffer != null) {
            statMap = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        } else {
            statMap = (EntityStatMap) store.getComponent(ref, EntityStatMap.getComponentType());
        }
        if (statMap == null)
            return;

        try {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue hp = statMap.get(healthIndex);
            if (hp == null)
                return;

            double mult = levelingManager.getMobHealthMultiplierForLevel(mobLevel);
            float oldMax = hp.getMax();
            float add = (float) (oldMax * (mult - 1.0));

            if (add == 0.0f) {
                applied.add(idx);
                return;
            }

            String key = "EL_MOB_HEALTH_" + idx;
            StaticModifier modifier = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, add);
            statMap.putModifier(healthIndex, key, modifier);
            try {
                statMap.maximizeStatValue(healthIndex);
            } catch (Throwable ignore) {
            }
            LOGGER.atInfo().log("MobHealthModifier: applied +%.2f max HP to entity %d (mult=%.3f)", add, idx, mult);
            applied.add(idx);
        } catch (Throwable t) {
            LOGGER.atSevere().log("MobHealthModifier: failed for entity %d: %s", idx, t.toString());
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(EntityStatMap.getComponentType());
    }

    /**
     * Compatibility shim for NPC load event registration. Called reflectively from
     * `EndlessLeveling` if NPC events are present. No-op fallback.
     */
    public static void enqueueFromEvent(Object evt) {
        if (evt == null)
            return;
        try {
            Ref<EntityStore> ref = getRefFromEvent(evt);
            if (ref == null)
                return;

            Store<EntityStore> store = ref.getStore();
            if (store == null)
                return;

            // try to get the world and execute on its thread; fall back to immediate call
            try {
                Object entityStore = store.getExternalData();
                if (entityStore != null) {
                    try {
                        java.lang.reflect.Method gw = entityStore.getClass().getMethod("getWorld");
                        Object world = gw.invoke(entityStore);
                        if (world != null) {
                            java.lang.reflect.Method exec = world.getClass().getMethod("execute", Runnable.class);
                            final Ref<EntityStore> fref = ref;
                            exec.invoke(world, (Runnable) () -> applyModifierToRef(fref, store, null));
                            return;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            // immediate fallback (may be off-thread)
            applyModifierToRef(ref, store, null);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> getRefFromEvent(Object evt) {
        if (evt == null)
            return null;
        try {
            for (java.lang.reflect.Method m : evt.getClass().getMethods()) {
                if (m.getParameterCount() != 0)
                    continue;
                if (Ref.class.isAssignableFrom(m.getReturnType())) {
                    try {
                        Object r = m.invoke(evt);
                        if (r instanceof Ref)
                            return (Ref<EntityStore>) r;
                    } catch (Throwable ignored) {
                    }
                }
            }
            for (java.lang.reflect.Field f : evt.getClass().getFields()) {
                if (Ref.class.isAssignableFrom(f.getType())) {
                    try {
                        Object r = f.get(evt);
                        if (r instanceof Ref)
                            return (Ref<EntityStore>) r;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Compatibility shim for NPC unload event registration. No-op fallback.
     */
    public static void removeOnUnload(Object evt) {
        // No-op: nothing to remove for the static per-entity modifier approach.
    }
}
