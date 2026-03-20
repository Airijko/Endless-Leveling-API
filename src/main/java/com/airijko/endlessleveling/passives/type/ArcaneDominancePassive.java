package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.ArcaneDominanceSettings;

/**
 * Handles Arcane Dominance passive values.
 */
public final class ArcaneDominancePassive {

    private final ArcaneDominanceSettings settings;

    private ArcaneDominancePassive(ArcaneDominanceSettings settings) {
        this.settings = settings == null ? ArcaneDominanceSettings.disabled() : settings;
    }

    public static ArcaneDominancePassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new ArcaneDominancePassive(ArcaneDominanceSettings.fromSnapshot(snapshot));
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

    public double sorceryFromTotalHealthPercent() {
        return Math.max(0.0D, settings.sorceryFromTotalHealthPercent());
    }
}
