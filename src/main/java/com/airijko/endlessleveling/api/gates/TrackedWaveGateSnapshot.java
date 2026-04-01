package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public record TrackedWaveGateSnapshot(@Nullable UUID ownerUuid,
        @Nullable String ownerName,
        @Nonnull String rankTierId,
        @Nonnull String stage,
        @Nonnull String kind,
        @Nullable UUID worldUuid,
        @Nullable String worldName,
        @Nullable String linkedGateId,
        @Nullable Integer x,
        @Nullable Integer y,
        @Nullable Integer z) {
}
