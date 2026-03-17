package com.airijko.endlessleveling.passives.archetype;

import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.races.RacePassiveDefinition;

import java.util.EnumMap;
import java.util.List;

/**
 * Public interface for custom archetype passive sources.
 * Implement this to register custom passive contributions that apply to
 * players.
 * Use with EndlessLevelingAPI.registerArchetypePassiveSource().
 */
public interface ArchetypePassiveSource {
    /**
     * Called during snapshot generation to collect passives for a player.
     * Add passives to the grouped map using the internal helper, or build custom
     * logic.
     * 
     * @param playerData the player to collect passives for
     * @param totals     accumulator for passive values (used for stacking logic)
     * @param grouped    map of passive types to their definitions
     */
    void collect(PlayerData playerData,
            EnumMap<ArchetypePassiveType, StackAccumulator> totals,
            EnumMap<ArchetypePassiveType, List<RacePassiveDefinition>> grouped);

    /**
     * Helper class for combining passive values with stacking rules.
     * Use: accumulator.addValue(newValue) to add to the total.
     */
    class StackAccumulator {
        private final com.airijko.endlessleveling.enums.PassiveStackingStyle stackingStyle;
        private double value;

        /**
         * Create an accumulator with the given stacking style.
         */
        public StackAccumulator(com.airijko.endlessleveling.enums.PassiveStackingStyle stackingStyle) {
            this.stackingStyle = stackingStyle == null
                    ? com.airijko.endlessleveling.enums.PassiveStackingStyle.ADDITIVE
                    : stackingStyle;
            this.value = 0.0D;
        }

        /**
         * Add a new value using the configured stacking style.
         */
        public void addValue(double newValue) {
            value = stackingStyle.combine(value, newValue);
        }

        /**
         * Get the accumulated value.
         */
        public double value() {
            return value;
        }
    }
}
