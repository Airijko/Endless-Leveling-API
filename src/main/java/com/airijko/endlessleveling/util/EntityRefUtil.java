package com.airijko.endlessleveling.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class EntityRefUtil {

    private EntityRefUtil() {
    }

    public static boolean isUsable(Ref<EntityStore> ref) {
        if (ref == null) {
            return false;
        }
        try {
            return ref.isValid() && ref.getStore() != null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static Store<EntityStore> getStore(Ref<EntityStore> ref) {
        if (ref == null) {
            return null;
        }
        try {
            return ref.getStore();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    public static <T extends Component<EntityStore>> T tryGetComponent(
            ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> ref,
            ComponentType<EntityStore, T> componentType) {
        if (accessor == null || componentType == null || !isUsable(ref)) {
            return null;
        }
        try {
            return accessor.getComponent(ref, componentType);
        } catch (IllegalStateException | IndexOutOfBoundsException ignored) {
            return null;
        }
    }

    /**
     * Returns true when the ref is usable AND the entity has not been marked
     * for removal (no {@link DeathComponent}).  Use this before applying any
     * component updates (damage, effects, movement, stats) to avoid the
     * "Entity can't be removed and also receive an update" tracker error.
     */
    public static boolean isAliveAndUsable(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        if (!isUsable(ref) || accessor == null) {
            return false;
        }
        try {
            return accessor.getComponent(ref, DeathComponent.getComponentType()) == null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}