package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public record TrackedDungeonGateSnapshot(@Nonnull String gateId,
        @Nonnull UUID worldUuid,
        @Nullable String worldName,
        @Nonnull String blockId,
        @Nonnull String rankTierId,
        int x,
        int y,
        int z) {
}
