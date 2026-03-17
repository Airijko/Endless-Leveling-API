package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.races.RacePassiveDefinition;

import java.util.List;
import java.util.Map;

/**
 * Resolves Ravenous Strike (legacy Vampiric Blade) healing contributions.
 */
public final class RavenousStrikePassive {

    private RavenousStrikePassive() {
    }

    public static double resolveHeal(ArchetypePassiveSnapshot snapshot,
            PlayerData playerData,
            SkillManager skillManager) {
        if (snapshot == null) {
            return 0.0D;
        }

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(ArchetypePassiveType.RAVENOUS_STRIKE);
        if (definitions.isEmpty()) {
            return 0.0D;
        }

        double strength = 0.0D;
        double sorcery = 0.0D;
        if (skillManager != null && playerData != null) {
            strength = Math.max(0.0D, skillManager.calculatePlayerStrength(playerData));
            sorcery = Math.max(0.0D, skillManager.calculatePlayerSorcery(playerData));
        }

        double totalHeal = 0.0D;
        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }

            Map<String, Object> props = definition.properties();
            double flatHeal = Math.max(0.0D, definition.value());
            double configuredFlat = parsePositiveDouble(props == null ? null : props.get("flat_heal"));
            if (configuredFlat > 0.0D) {
                flatHeal = configuredFlat;
            }

            double strengthScaling = parsePositiveDouble(props == null ? null : props.get("strength_scaling"));
            double sorceryScaling = parsePositiveDouble(props == null ? null : props.get("sorcery_scaling"));
            double scaledHeal = (strength * strengthScaling) + (sorcery * sorceryScaling);
            totalHeal += flatHeal + scaledHeal;
        }

        return Math.max(0.0D, totalHeal);
    }

    private static double parsePositiveDouble(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0.0D ? value : 0.0D;
        }
        if (raw instanceof String string) {
            try {
                double parsed = Double.parseDouble(string.trim());
                return parsed > 0.0D ? parsed : 0.0D;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0D;
    }
}
