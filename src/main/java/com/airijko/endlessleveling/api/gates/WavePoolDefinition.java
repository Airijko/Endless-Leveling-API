package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Immutable wave pool definition exposed through API provider contracts.
 */
public record WavePoolDefinition(@Nonnull List<Pool> pools, @Nonnull List<String> bossPool) {

    public WavePoolDefinition {
        pools = List.copyOf(pools);
        bossPool = List.copyOf(bossPool);
    }

    public boolean isEmpty() {
        return pools.isEmpty() && bossPool.isEmpty();
    }

    public record Pool(@Nonnull String id, @Nonnull List<String> mobs) {
        public Pool {
            mobs = List.copyOf(mobs);
        }
    }
}
