package com.airijko.endlessleveling.api.gates;

import java.util.UUID;

public record TrackedWaveGateSnapshot(
        UUID ownerUuid,
        String ownerName,
        String rankTierId,
        String stage,
        String kind,
        UUID worldUuid,
        String worldName,
        String linkedGateId,
        Integer x,
        Integer y,
        Integer z) {
}
