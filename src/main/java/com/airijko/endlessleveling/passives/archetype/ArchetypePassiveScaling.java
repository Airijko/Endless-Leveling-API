package com.airijko.endlessleveling.passives.archetype;

import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.races.RacePassiveDefinition;

import java.util.List;

/**
 * Shared helpers for resolving per-passive effectiveness scales across primary
 * and secondary class contributions.
 */
public final class ArchetypePassiveScaling {

    private ArchetypePassiveScaling() {
    }

    public static AuraScales resolveAuraScales(ArchetypePassiveSnapshot snapshot,
            ArchetypePassiveType type,
            PlayerData playerData) {
        if (snapshot == null || type == null) {
            return AuraScales.none();
        }

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(type);
        if (definitions == null || definitions.isEmpty()) {
            double fallback = Math.max(0.0D, snapshot.getValue(type));
            return new AuraScales(fallback, fallback);
        }

        double ratioScale = 0.0D;
        double fullScale = 0.0D;

        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            double value = Math.max(0.0D, definition.value());
            if (value <= 0.0D) {
                continue;
            }

            ratioScale += value;
            fullScale += value;
        }

        if (ratioScale <= 0.0D) {
            double fallback = Math.max(0.0D, snapshot.getValue(type));
            return new AuraScales(fallback, fallback);
        }
        if (fullScale <= 0.0D) {
            fullScale = ratioScale;
        }

        return new AuraScales(ratioScale, fullScale);
    }

    public record AuraScales(double ratioScale, double fullScale) {
        public static AuraScales none() {
            return new AuraScales(0.0D, 0.0D);
        }
    }
}
