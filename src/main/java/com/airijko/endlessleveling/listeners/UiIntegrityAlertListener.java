package com.airijko.endlessleveling.listeners;

import com.airijko.endlessleveling.security.UiTitleIntegrityGuard;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.airijko.endlessleveling.util.UiIntegrityAlertSound;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public final class UiIntegrityAlertListener {

    private final UiTitleIntegrityGuard integrityGuard;

    public UiIntegrityAlertListener(UiTitleIntegrityGuard integrityGuard) {
        this.integrityGuard = integrityGuard;
    }

    public void onPlayerReady(PlayerReadyEvent event) {
        if (event == null || integrityGuard == null) {
            return;
        }
        var player = event.getPlayer();
        if (player == null || player.getUuid() == null) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        if (!integrityGuard.hasUnauthorizedModifications()) {
            return;
        }

        if (!OperatorHelper.hasAdministrativeAccess(playerRef)) {
            integrityGuard.notifyPlayerIfUnauthorized(playerRef);
        }
        UiIntegrityAlertSound.playForAllOnlinePlayers();
    }
}