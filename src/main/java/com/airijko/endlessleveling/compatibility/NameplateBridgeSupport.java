package com.airijko.endlessleveling.compatibility;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Soft-dependency bridge to NameplateBuilder.
 *
 * <p>When NameplateBuilder is installed, EL registers "mob-level" and "mob-prefix"
 * segments so that mob level tags and external prefixes (e.g. elite tier "[S]")
 * appear in the aggregated nameplate instead of being overwritten by
 * NameplateBuilder's {@code queueUpdate} mechanism.</p>
 *
 * <p>All NameplateBuilder class references are isolated in the {@link Impl} inner
 * class which is only loaded when we confirm the API is on the classpath. If
 * NameplateBuilder is absent, every public method is a silent no-op.</p>
 */
public final class NameplateBridgeSupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    static final String SEGMENT_MOB_LEVEL = "mob-level";
    static final String SEGMENT_MOB_PREFIX = "mob-prefix";

    private static volatile boolean available;

    private NameplateBridgeSupport() {
    }

    /** Attempt to register segments. Safe to call even if NameplateBuilder is absent. */
    public static void tryInit(JavaPlugin plugin) {
        try {
            Class.forName("com.frotty27.nameplatebuilder.api.NameplateAPI");
            Impl.init(plugin);
            available = true;
            LOGGER.atInfo().log("[EL-NPB] NameplateBuilder detected — mob-level and mob-prefix segments registered.");
        } catch (Throwable t) {
            available = false;
            LOGGER.atInfo().log("[EL-NPB] NameplateBuilder not present — using native Nameplate component only.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Push mob level and external prefix text into NameplateBuilder segments.
     *
     * <p>Called from {@code MobLevelingSystem.applyNameplate()} each time the
     * nameplate is re-rendered. Must be called with a valid store reference
     * (reads {@code NameplateData} from the store, not the command buffer,
     * to match DefaultSegmentSystem and the aggregator).</p>
     */
    public static void setMobSegments(Store<EntityStore> store, Ref<EntityStore> ref,
            int level, boolean showLevel, String externalPrefix) {
        if (!available) return;
        try {
            Impl.setMobSegments(store, ref, level, showLevel, externalPrefix);
        } catch (Throwable t) {
            available = false;
        }
    }

    /** Clear mob segments when the nameplate is removed (death, out of range, etc.). */
    public static void clearMobSegments(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (!available) return;
        try {
            Impl.clearMobSegments(store, ref);
        } catch (Throwable t) {
            available = false;
        }
    }

    // ── Inner class: loaded only when NameplateBuilder is confirmed present ──

    private static final class Impl {

        private static ComponentType<EntityStore, com.frotty27.nameplatebuilder.api.NameplateData> npType;

        static void init(JavaPlugin plugin) {
            com.frotty27.nameplatebuilder.api.NameplateAPI.define(
                    plugin, SEGMENT_MOB_LEVEL, "Mob Level",
                    com.frotty27.nameplatebuilder.api.SegmentTarget.NPCS, "[Lv.42]");
            com.frotty27.nameplatebuilder.api.NameplateAPI.define(
                    plugin, SEGMENT_MOB_PREFIX, "Mob Prefix",
                    com.frotty27.nameplatebuilder.api.SegmentTarget.NPCS, "[S]");
            com.frotty27.nameplatebuilder.api.NameplateAPI.addToAdminChain(
                    plugin, SEGMENT_MOB_PREFIX,
                    com.frotty27.nameplatebuilder.api.SegmentTarget.NPCS);
            com.frotty27.nameplatebuilder.api.NameplateAPI.addToAdminChain(
                    plugin, SEGMENT_MOB_LEVEL,
                    com.frotty27.nameplatebuilder.api.SegmentTarget.NPCS);
            npType = com.frotty27.nameplatebuilder.api.NameplateAPI.getComponentType();
        }

        static void setMobSegments(Store<EntityStore> store, Ref<EntityStore> ref,
                int level, boolean showLevel, String externalPrefix) {
            com.frotty27.nameplatebuilder.api.NameplateData data = store.getComponent(ref, npType);
            if (data == null) return; // DefaultSegmentSystem hasn't seeded yet

            if (showLevel && level > 0) {
                data.setText(SEGMENT_MOB_LEVEL, "[Lv." + level + "]");
            } else {
                data.removeText(SEGMENT_MOB_LEVEL);
            }

            if (externalPrefix != null && !externalPrefix.isBlank()) {
                data.setText(SEGMENT_MOB_PREFIX, externalPrefix.trim());
            } else {
                data.removeText(SEGMENT_MOB_PREFIX);
            }
        }

        static void clearMobSegments(Store<EntityStore> store, Ref<EntityStore> ref) {
            com.frotty27.nameplatebuilder.api.NameplateData data = store.getComponent(ref, npType);
            if (data == null) return;
            data.removeText(SEGMENT_MOB_LEVEL);
            data.removeText(SEGMENT_MOB_PREFIX);
        }
    }
}
