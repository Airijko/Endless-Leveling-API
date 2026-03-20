package com.airijko.endlessleveling.passives.settings;

import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.races.RacePassiveDefinition;
import java.util.List;
import java.util.Map;

/**
 * Resolved settings for Arcane Wisdom passive.
 *
 * Value semantics: ARCANE_WISDOM value is interpreted as restore percent.
 * Example: value=0.50 restores 50% mana over the configured duration.
 */
public record ArcaneWisdomSettings(boolean enabled,
        double restorePercent,
        double thresholdPercent,
        double durationSeconds,
        double cooldownSeconds) {

    private static final double DEFAULT_RESTORE_PERCENT = 0.25D;
    private static final double DEFAULT_THRESHOLD_PERCENT = 0.10D;
    private static final double DEFAULT_DURATION_SECONDS = 5.0D;
    private static final double DEFAULT_COOLDOWN_SECONDS = 30.0D;

    public static ArcaneWisdomSettings fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        if (snapshot == null) {
            return disabled();
        }

        double restorePercent = normalizePercent(snapshot.getValue(ArchetypePassiveType.ARCANE_WISDOM));
        if (restorePercent <= 0.0D) {
            return disabled();
        }

        double thresholdPercent = DEFAULT_THRESHOLD_PERCENT;
        double durationSeconds = DEFAULT_DURATION_SECONDS;
        double cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(ArchetypePassiveType.ARCANE_WISDOM);
        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            Map<String, Object> props = definition.properties();
            thresholdPercent = parsePercent(props, "threshold", thresholdPercent);
            durationSeconds = parsePositiveDouble(props, "restore_duration_seconds", durationSeconds);
            durationSeconds = parsePositiveDouble(props, "duration", durationSeconds);
            cooldownSeconds = parsePositiveDouble(props, "cooldown", cooldownSeconds);
        }

        return new ArcaneWisdomSettings(true,
                clamp01(restorePercent),
                clamp01(thresholdPercent),
                Math.max(0.1D, durationSeconds),
                Math.max(0.0D, cooldownSeconds));
    }

    private static double parsePercent(Map<String, Object> props, String key, double fallback) {
        if (props == null || key == null) {
            return fallback;
        }
        Object raw = props.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (value > 1.0D) {
                value /= 100.0D;
            }
            return value;
        }
        if (raw instanceof String string) {
            try {
                double value = Double.parseDouble(string.trim());
                if (value > 1.0D) {
                    value /= 100.0D;
                }
                return value;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
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

    private static double clamp01(double value) {
        if (value <= 0.0D) {
            return 0.0D;
        }
        if (value >= 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static double normalizePercent(double raw) {
        double value = Math.max(0.0D, raw);
        if (value > 1.0D) {
            value /= 100.0D;
        }
        return clamp01(value);
    }

    public static ArcaneWisdomSettings disabled() {
        return new ArcaneWisdomSettings(false,
                DEFAULT_RESTORE_PERCENT,
                DEFAULT_THRESHOLD_PERCENT,
                DEFAULT_DURATION_SECONDS,
                DEFAULT_COOLDOWN_SECONDS);
    }

    public long durationMillis() {
        return (long) Math.max(0L, Math.round(durationSeconds * 1000.0D));
    }

    public long cooldownMillis() {
        return (long) Math.max(0L, Math.round(cooldownSeconds * 1000.0D));
    }
}
