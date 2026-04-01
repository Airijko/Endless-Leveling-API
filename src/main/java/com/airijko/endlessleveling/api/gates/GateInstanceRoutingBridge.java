package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

public interface GateInstanceRoutingBridge {

    void saveGateInstances();

    void restoreSavedGateInstances();

    void cleanupGateInstance(World world, int x, int y, int z, String blockId);

    void cleanupGateInstanceByIdentity(String gateIdentity, String blockId);

    String resolveInstanceNameForGate(String gateIdentity);

    void kickPlayersFromGateInstance(String instanceWorldName);

    boolean enterPortalFromBlock(PlayerRef playerRef,
            World sourceWorld,
            int x,
            int y,
            int z,
            String blockId,
            boolean removeSourcePortalOnSuccess,
            String stableGateIdOverride);

    boolean returnPlayerToEntryPortal(PlayerRef playerRef, World sourceWorld);

    int[] getActiveInstanceRange(String worldName);

    Integer getActiveInstanceBossLevel(String worldName);

    String getActiveInstanceRankLetter(String worldName);

    boolean isInstancePairedToActiveGate(String worldName);
}
