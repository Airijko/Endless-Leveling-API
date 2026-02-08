package com.airijko.endlessleveling.passives;

import com.airijko.endlessleveling.races.RacePassiveDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents executioner-style bonus damage entries applied against low-health
 * targets.
 */
public record ExecutionerSettings(List<Entry> entries) {

    public record Entry(double thresholdPercent, double bonusPercent) {
        public boolean isExecute() {
            return bonusPercent >= 1.0D;
        }
    }

    public static ExecutionerSettings fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        List<Entry> entries = new ArrayList<>();
        if (snapshot == null) {
            return new ExecutionerSettings(List.of());
        }

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(ArchetypePassiveType.EXECUTIONER);
        if (definitions.isEmpty()) {
            return new ExecutionerSettings(List.of());
        }

        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            double bonusPercent = Math.max(0.0D, definition.value());
            if (bonusPercent <= 0.0D) {
                continue;
            }
            Map<String, Object> props = definition.properties();
            double threshold = parsePositiveDouble(props, "threshold", 0.35D);
            if (threshold <= 0.0D) {
                continue;
            }
            entries.add(new Entry(threshold, bonusPercent));
        }
        return new ExecutionerSettings(List.copyOf(entries));
    }

    public boolean enabled() {
        return !entries.isEmpty();
    }

    private static double parsePositiveDouble(Map<String, Object> props, String key, double fallback) {
        if (props == null || key == null) {
            return fallback;
        }
        Object raw = props.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0.0D ? value : fallback;
        }
        if (raw instanceof String string) {
            try {
                double value = Double.parseDouble(string.trim());
                return value > 0.0D ? value : fallback;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
