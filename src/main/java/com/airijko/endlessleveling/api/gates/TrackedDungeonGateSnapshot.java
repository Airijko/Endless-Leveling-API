package com.airijko.endlessleveling.api.gates;

import java.util.UUID;

public record TrackedDungeonGateSnapshot(
        String gateId,
        UUID worldUuid,
        String worldName,
        String blockId,
        String rankTierId,
        Integer x,
        Integer y,
        Integer z,
        long expiresAtEpochMillis) {
}
