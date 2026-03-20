package com.airijko.endlessleveling.passives.settings;

import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.races.RacePassiveDefinition;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates Primal Dominance tuning (health scaling + on-hit slow).
 */
public record PrimalDominanceSettings(boolean enabled,
        double strengthFromTotalHealthPercent,
        double targetHasteSlowOnHitPercent,
        double targetHasteSlowDurationSeconds) {

    private static final double DEFAULT_STRENGTH_FROM_TOTAL_HEALTH_PERCENT = 0.05D;
    private static final double DEFAULT_TARGET_HASTE_SLOW_ON_HIT_PERCENT = 0.20D;
    private static final double DEFAULT_TARGET_HASTE_SLOW_DURATION_SECONDS = 2.0D;

    public static PrimalDominanceSettings fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        if (snapshot == null) {
            return disabled();
        }

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(ArchetypePassiveType.PRIMAL_DOMINANCE);
        if (definitions.isEmpty()) {
            return disabled();
        }

        double strengthFromTotalHealthPercent = Math.max(0.0D, snapshot.getValue(ArchetypePassiveType.PRIMAL_DOMINANCE));
        double targetSlowPercent = DEFAULT_TARGET_HASTE_SLOW_ON_HIT_PERCENT;
        double targetSlowDuration = DEFAULT_TARGET_HASTE_SLOW_DURATION_SECONDS;

        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            Map<String, Object> props = definition.properties();

            double scalingCandidate = parsePercent(props,
                    "strength_from_total_health_percent",
                    "total_health_scaling_percent",
                    "health_scaling_percent",
                    "from_total_health_percent");
            if (scalingCandidate > 0.0D) {
                strengthFromTotalHealthPercent = Math.max(strengthFromTotalHealthPercent, scalingCandidate);
            }

            double slowCandidate = parsePercent(props,
                    "target_haste_slow_on_hit",
                    "target_haste_slow",
                    "on_hit_target_haste_slow");
            if (slowCandidate > 0.0D) {
                targetSlowPercent = slowCandidate;
            }

            double slowDurationCandidate = parsePositiveDouble(props, "target_haste_slow_duration", 0.0D);
            if (slowDurationCandidate > 0.0D) {
                targetSlowDuration = slowDurationCandidate;
            }
        }

        if (strengthFromTotalHealthPercent <= 0.0D) {
            strengthFromTotalHealthPercent = DEFAULT_STRENGTH_FROM_TOTAL_HEALTH_PERCENT;
        }

        return new PrimalDominanceSettings(true,
                strengthFromTotalHealthPercent,
                targetSlowPercent,
                targetSlowDuration);
    }

    public long targetHasteSlowDurationMillis() {
        return (long) Math.max(0L, Math.round(targetHasteSlowDurationSeconds * 1000.0D));
    }

    public static PrimalDominanceSettings disabled() {
        return new PrimalDominanceSettings(false, 0.0D, 0.0D, 0.0D);
    }

    private static double parsePercent(Map<String, Object> props, String... keys) {
        if (props == null || keys == null) {
            return 0.0D;
        }
        for (String key : keys) {
            double value = parsePositiveDouble(props, key, 0.0D);
            if (value > 0.0D) {
                return value > 1.0D ? value / 100.0D : value;
            }
        }
        return 0.0D;
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
