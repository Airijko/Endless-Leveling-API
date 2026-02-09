package com.airijko.endlessleveling.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Convenience helpers for detecting whether a {@link PlayerRef} currently has
 * operator privileges.
 */
public final class OperatorHelper {

    private static final Method PLAYER_REF_IS_OPERATOR;

    static {
        Method discovered;
        try {
            discovered = PlayerRef.class.getMethod("isOperator");
            discovered.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            discovered = null;
        }
        PLAYER_REF_IS_OPERATOR = discovered;
    }

    private OperatorHelper() {
    }

    /**
     * Returns {@code true} if the provided {@link PlayerRef} exposes an
     * {@code isOperator()} method and it reports the
     * player as having operator privileges.
     */
    public static boolean isOperator(PlayerRef playerRef) {
        if (playerRef == null || PLAYER_REF_IS_OPERATOR == null) {
            return false;
        }
        try {
            Object result = PLAYER_REF_IS_OPERATOR.invoke(playerRef);
            return result instanceof Boolean bool && bool;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }
}
