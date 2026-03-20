package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.PrimalDominanceSettings;

/**
 * Handles Primal Dominance passive values.
 */
public final class PrimalDominancePassive {

    private final PrimalDominanceSettings settings;

    private PrimalDominancePassive(PrimalDominanceSettings settings) {
        this.settings = settings == null ? PrimalDominanceSettings.disabled() : settings;
    }

    public static PrimalDominancePassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new PrimalDominancePassive(PrimalDominanceSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public double targetHasteSlowOnHitPercent() {
        return Math.max(0.0D, settings.targetHasteSlowOnHitPercent());
    }

    public long targetHasteSlowDurationMillis() {
        return Math.max(0L, settings.targetHasteSlowDurationMillis());
    }

    public double strengthFromTotalHealthPercent() {
        return Math.max(0.0D, settings.strengthFromTotalHealthPercent());
    }
}
