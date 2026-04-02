package com.airijko.endlessleveling.listeners;

import com.airijko.endlessleveling.security.UiTitleIntegrityGuard;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.airijko.endlessleveling.util.UiIntegrityAlertSound;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class UiIntegrityAlertListener {

    private final UiTitleIntegrityGuard integrityGuard;

    public UiIntegrityAlertListener(UiTitleIntegrityGuard integrityGuard) {
        this.integrityGuard = integrityGuard;
    }

    public void onPlayerReady(PlayerReadyEvent event) {
        if (event == null || integrityGuard == null) {
            return;
        }
        Ref<EntityStore> entityRef = event.getPlayerRef();
        if (entityRef == null) {
            return;
        }

        Store<EntityStore> store = entityRef.getStore();
        if (store == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
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