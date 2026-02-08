package com.airijko.endlessleveling.passives;

import java.util.List;

/**
 * Configuration container for the Swiftness passive.
 */
public record SwiftnessSettings(boolean enabled,
        double bonusPercent) {

    public static SwiftnessSettings fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        if (snapshot == null) {
            return disabled();
        }
        double bonusPercent = Math.max(0.0D, snapshot.getValue(ArchetypePassiveType.SWIFTNESS));
        if (bonusPercent <= 0.0D) {
            return disabled();
        }

        List<com.airijko.endlessleveling.races.RacePassiveDefinition> definitions = snapshot
                .getDefinitions(ArchetypePassiveType.SWIFTNESS);
        for (com.airijko.endlessleveling.races.RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            bonusPercent += Math.max(0.0D, definition.value());
        }
        return new SwiftnessSettings(true, bonusPercent);
    }

    public double multiplier() {
        return enabled ? (1.0D + bonusPercent) : 1.0D;
    }

    public static SwiftnessSettings disabled() {
        return new SwiftnessSettings(false, 0.0D);
    }
}
