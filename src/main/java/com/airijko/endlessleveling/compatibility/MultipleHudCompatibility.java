package com.airijko.endlessleveling.compatibility;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;

/**
 * Optional bridge for Buuz135 MultipleHUD.
 */
public final class MultipleHudCompatibility {

    private static final String API_CLASS = "com.buuz135.mhud.MultipleHUD";

    private static volatile boolean initialized = false;
    private static volatile Method getInstanceMethod = null;
    private static volatile Method setCustomHudMethod = null;

    private MultipleHudCompatibility() {
    }

    public static boolean isAvailable() {
        return ensureInitialized();
    }

    public static boolean showHud(Player player, PlayerRef playerRef, String slot, CustomUIHud hud) {
        if (player == null || playerRef == null || slot == null || slot.isBlank() || hud == null) {
            return false;
        }
        if (!ensureInitialized()) {
            return false;
        }

        try {
            Object api = getInstanceMethod.invoke(null);
            if (api == null) {
                return false;
            }
            setCustomHudMethod.invoke(api, player, playerRef, slot, hud);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) {
            return getInstanceMethod != null && setCustomHudMethod != null;
        }

        initialized = true;
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            getInstanceMethod = apiClass.getMethod("getInstance");
            setCustomHudMethod = apiClass.getMethod(
                    "setCustomHud",
                    Player.class,
                    PlayerRef.class,
                    String.class,
                    CustomUIHud.class);
            return true;
        } catch (Throwable ignored) {
            getInstanceMethod = null;
            setCustomHudMethod = null;
            return false;
        }
    }
}