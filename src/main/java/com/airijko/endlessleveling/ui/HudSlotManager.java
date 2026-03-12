package com.airijko.endlessleveling.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HudSlotManager {

    static final String MULTI_HUD_SLOT = "EndlessLevelingHud";

    private static final Map<UUID, Object> HUD_LOCKS = new ConcurrentHashMap<>();

    private HudSlotManager() {
    }

    static Object getHudLock(UUID uuid) {
        return HUD_LOCKS.computeIfAbsent(uuid, ignored -> new Object());
    }
}