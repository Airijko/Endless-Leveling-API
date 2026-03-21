/**
 * This file demonstrates the changes needed for weighted augment selection.
 * Integrate this logic into AugmentUnlockManager.
 */

// Add these imports to AugmentUnlockManager:
// import java.util.NavigableMap;
// import java.util.TreeMap;

// Add this method to AugmentUnlockManager class:

/**
 * Calculate base weight for an augment based on player's damage type focus.
 * Evaluates conditions like sorcery_higher_than_strength or strength_higher_than_sorcery.
 */
private int calculateAugmentWeight(AugmentDefinition augment, PlayerData playerData) {
    if (augment == null || playerData == null) {
        return 10; // neutral weight
    }
    
    // Get player's damage values
    float strengthValue = Math.max(0f, skillManager.calculatePlayerStrength(playerData));
    float sorceryValue = Math.max(0f, skillManager.calculatePlayerSorcery(playerData));
    
    // Default weight
    int weight = 10;
    
    // Check the augment's conditions from YAML parsing
    // This would be done by parsing the augment YAML weights section
    
    // For now, simple logic based on augment ID patterns:
    String id = augment.getId().toLowerCase();
    
    // Sorcery augments get higher weight when sorcery > strength
    if (isSorceryAugment(id)) {
        if (sorceryValue > strengthValue) {
            return 60;  // High weight when sorcery is higher
        } else {
            return 10;  // Low weight when strength is higher
        }
    }
    
    // Strength augments get higher weight when strength > sorcery
    if (isStrengthAugment(id)) {
        if (strengthValue > sorceryValue) {
            return 60;  // High weight when strength is higher
        } else {
            return 10;  // Low weight when sorcery is higher
        }
    }
    
    // Neutral/utility augments
    return 10;
}

/**
 * Perform weighted random selection from a list of augments.
 * Returns the specified count of augments weighted by their calculated weights.
 */
private List<String> weightedRandomSelection(
        List<AugmentDefinition> pool,
        PlayerData playerData,
        int count) {
    
    if (pool == null || pool.isEmpty()) {
        return List.of();
    }
    
    // Calculate total weight
    int totalWeight = 0;
    List<Integer> weights = new ArrayList<>();
    
    for (AugmentDefinition augment : pool) {
        int weight = calculateAugmentWeight(augment, playerData);
        weights.add(weight);
        totalWeight += weight;
    }
    
    List<String> selected = new ArrayList<>();
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    List<AugmentDefinition> remaining = new ArrayList<>(pool);
    List<Integer> remainingWeights = new ArrayList<>(weights);
    
    // Select 'count' augments without replacement
    for (int i = 0; i < count && !remaining.isEmpty(); i++) {
        // Recalculate total weight from remaining
        totalWeight = 0;
        for (int w : remainingWeights) {
            totalWeight += w;
        }
        
        // Pick random value in [0, totalWeight)
        int pick = rng.nextInt(totalWeight);
        
        // Find which augment this corresponds to
        int sum = 0;
        for (int j = 0; j < remainingWeights.size(); j++) {
            sum += remainingWeights.get(j);
            if (pick < sum) {
                selected.add(remaining.get(j).getId());
                remaining.remove(j);
                remainingWeights.remove(j);
                break;
            }
        }
    }
    
    return selected;
}

// Helper methods to identify augment types
private static final Set<String> SORCERY_AUGMENTS = Set.of(
    "arcane_cataclysm", "arcane_comet", "arcane_instability", "arcane_mastery",
    "magic_blade", "magic_missle", "mana_infusion"
);

private static final Set<String> STRENGTH_AUGMENTS = Set.of(
    "bloodthirster", "blood_echo", "blood_frenzy", "blood_surge",
    "conqueror", "cripple", "drain", "executioner", "first_strike",
    "fleet_footwork", "giant_slayer", "overdrive", "overheal",
    "phantom_hits", "phase_rush", "predator", "reckoning",
    "snipers_reach", "soul_reaver", "time_master", "vampiric_strike",
    "vampirism", "wither"
);

private boolean isSorceryAugment(String id) {
    return SORCERY_AUGMENTS.contains(id);
}

private boolean isStrengthAugment(String id) {
    return STRENGTH_AUGMENTS.contains(id);
}

/**
 * In rollOffers method, replace this section:
 * 
 * OLD (Lines 480-485):
 *     Collections.shuffle(pool, ThreadLocalRandom.current());
 *     int count = Math.min(DEFAULT_OFFER_COUNT, pool.size());
 *     List<String> rolled = new ArrayList<>(count);
 *     for (int i = 0; i < count; i++) {
 *         rolled.add(pool.get(i).getId());
 *     }
 *     return rolled;
 * 
 * WITH THIS NEW CODE:
 *     int count = Math.min(DEFAULT_OFFER_COUNT, pool.size());
 *     return weightedRandomSelection(pool, playerData, count);
 */
