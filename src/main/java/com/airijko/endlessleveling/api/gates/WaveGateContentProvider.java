package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Supplies rank-specific wave gate content to the core runtime.
 */
public interface WaveGateContentProvider {

    @Nonnull
    String getProviderId();

    @Nullable
    WavePoolDefinition loadWavePool(@Nonnull String rankTierId);
}
