package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.ArcaneWisdomSettings;

/**
 * Handles Arcane Wisdom passive values.
 */
public final class ArcaneWisdomPassive {

    private final ArcaneWisdomSettings settings;

    private ArcaneWisdomPassive(ArcaneWisdomSettings settings) {
        this.settings = settings == null ? ArcaneWisdomSettings.disabled() : settings;
    }

    public static ArcaneWisdomPassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new ArcaneWisdomPassive(ArcaneWisdomSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public double restorePercent() {
        return settings.restorePercent();
    }

    public double cooldownSeconds() {
        return settings.cooldownSeconds();
    }

    public long cooldownMillis() {
        return settings.cooldownMillis();
    }
}
