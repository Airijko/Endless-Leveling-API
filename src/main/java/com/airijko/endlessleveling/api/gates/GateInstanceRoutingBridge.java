package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface GateInstanceRoutingBridge {

    void saveGateInstances();

    void restoreSavedGateInstances();

    void cleanupGateInstance(@Nonnull World world, int x, int y, int z, @Nonnull String blockId);

    void cleanupGateInstanceByIdentity(@Nonnull String gateIdentity, @Nonnull String blockId);

    @Nullable
    String resolveInstanceNameForGate(@Nonnull String gateIdentity);

    void kickPlayersFromGateInstance(@Nonnull String instanceWorldName);

    boolean enterPortalFromBlock(@Nonnull PlayerRef playerRef,
            @Nonnull World sourceWorld,
            int x,
            int y,
            int z,
            @Nonnull String blockId,
            boolean removeSourcePortalOnSuccess,
            @Nullable String stableGateIdOverride);

    boolean returnPlayerToEntryPortal(@Nonnull PlayerRef playerRef, @Nonnull World sourceWorld);

    @Nullable
    int[] getActiveInstanceRange(@Nonnull String worldName);

    @Nullable
    Integer getActiveInstanceBossLevel(@Nonnull String worldName);

    @Nullable
    String getActiveInstanceRankLetter(@Nonnull String worldName);

    boolean isInstancePairedToActiveGate(@Nonnull String worldName);
}
