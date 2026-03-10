package com.airijko.endlessleveling.passives.settings;

import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.races.RacePassiveDefinition;
import java.util.List;
import java.util.Map;

/**
 * Resolved configuration for the First Strike passive, including bonus values
 * and cooldown.
 */
public record FirstStrikeSettings(boolean enabled, double bonusPercent, long cooldownMillis, double flatBonusDamage) {

    private static final double DEFAULT_COOLDOWN_SECONDS = 30.0D;
    private static final double DEFAULT_FLAT_BONUS_DAMAGE = 25.0D;

    public static FirstStrikeSettings fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        if (snapshot == null) {
            return disabled();
        }
        double bonusPercent = Math.max(0.0D, snapshot.getValue(ArchetypePassiveType.FIRST_STRIKE));
        if (bonusPercent <= 0) {
            return disabled();
        }

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(ArchetypePassiveType.FIRST_STRIKE);
        double cooldownSum = 0.0D;
        int cooldownSources = 0;
        double resolvedFlatBonusDamage = 0.0D;
        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            Map<String, Object> props = definition.properties();

            double flatCandidate = parsePositiveDouble(props != null ? props.get("flat_bonus_damage") : null);
            if (flatCandidate > 0) {
                // FIRST_STRIKE stacks as UNIQUE, so keep the highest configured flat bonus.
                resolvedFlatBonusDamage = Math.max(resolvedFlatBonusDamage, flatCandidate);
            }

            double candidate = parsePositiveDouble(props != null ? props.get("cooldown") : null);
            if (candidate > 0) {
                cooldownSum += candidate;
                cooldownSources++;
            }
        }

        double resolvedSeconds = cooldownSources > 0
                ? cooldownSum / cooldownSources
                : DEFAULT_COOLDOWN_SECONDS;
        long cooldownMillis = (long) Math.max(0L, Math.round(resolvedSeconds * 1000.0D));
        if (resolvedFlatBonusDamage <= 0.0D) {
            resolvedFlatBonusDamage = DEFAULT_FLAT_BONUS_DAMAGE;
        }
        return new FirstStrikeSettings(true, bonusPercent, cooldownMillis, resolvedFlatBonusDamage);
    }

    public static FirstStrikeSettings disabled() {
        return new FirstStrikeSettings(false, 0.0D, 0L, 0.0D);
    }

    private static double parsePositiveDouble(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0 ? value : 0.0D;
        }
        if (raw instanceof String string) {
            try {
                double parsed = Double.parseDouble(string.trim());
                return parsed > 0 ? parsed : 0.0D;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0D;
    }
}
