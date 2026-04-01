package com.airijko.endlessleveling.api.gates;

import com.hypixel.hytale.server.core.universe.world.World;

public interface WaveGateRuntimeBridge {

	String resolveGateIdAt(World world, int x, int y, int z);

	void forceRemoveGateAt(World world, int x, int y, int z, String blockId);

	boolean isGateEntryLocked(String gateIdentity);
}
