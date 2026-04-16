package com.airijko.endlessleveling.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Minimal 1×1px hidden HUD used to replace OutlanderBridgeWaveHud when closing.
 */
public final class OutlanderBridgeWaveHudHide extends CustomUIHud {

    public OutlanderBridgeWaveHudHide(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("Hud/OutlanderBridgeWaveHide.ui");
    }
}
