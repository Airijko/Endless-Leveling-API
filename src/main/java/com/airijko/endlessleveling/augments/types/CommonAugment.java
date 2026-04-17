package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CommonAugment extends Augment implements AugmentHooks.PassiveStatAugment {
    public static final String ID = "common";
    private static final String LEGACY_ID = "basic";
    private static final String OFFER_DELIMITER = "::";

    private static final double RANGE_EPSILON = 0.0001D;

    private final BonusRange lifeForceRange;
    private final BonusRange strengthRange;
    private final BonusRange sorceryRange;
    private final BonusRange defenseRange;
    private final BonusRange hasteRange;
    private final BonusRange precisionRange;
    private final BonusRange ferocityRange;
    private final BonusRange disciplineRange;
    private final BonusRange flowRange;
    private final BonusRange staminaRange;

    // Pre-computed flags: true when a stat range can produce non-zero values.
    // Avoids resolveRoll + setAttributeBonus calls for permanently-zero stats.
    private final boolean hasLifeForce;
    private final boolean hasStrength;
    private final boolean hasSorcery;
    private final boolean hasDefense;
    private final boolean hasHaste;
    private final boolean hasPrecision;
    private final boolean hasFerocity;
    private final boolean hasDiscipline;
    private final boolean hasFlow;
    private final boolean hasStamina;

    public CommonAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> buffs = AugmentValueReader.getMap(definition.getPassives(), "buffs");
        this.lifeForceRange = readRange(buffs, "life_force");
        this.strengthRange = readRange(buffs, "strength");
        this.sorceryRange = readRange(buffs, "sorcery");
        this.defenseRange = readRange(buffs, "defense");
        this.hasteRange = readRange(buffs, "haste");
        this.precisionRange = readRange(buffs, "precision");
        this.ferocityRange = readRange(buffs, "ferocity");
        this.disciplineRange = readRange(buffs, "discipline");
        this.flowRange = readRange(buffs, "flow");
        this.staminaRange = readRange(buffs, "stamina");
        this.hasLifeForce = lifeForceRange.max() > RANGE_EPSILON;
        this.hasStrength = strengthRange.max() > RANGE_EPSILON;
        this.hasSorcery = sorceryRange.max() > RANGE_EPSILON;
        this.hasDefense = defenseRange.max() > RANGE_EPSILON;
        this.hasHaste = hasteRange.max() > RANGE_EPSILON;
        this.hasPrecision = precisionRange.max() > RANGE_EPSILON;
        this.hasFerocity = ferocityRange.max() > RANGE_EPSILON;
        this.hasDiscipline = disciplineRange.max() > RANGE_EPSILON;
        this.hasFlow = flowRange.max() > RANGE_EPSILON;
        this.hasStamina = staminaRange.max() > RANGE_EPSILON;
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return;
        }

        String selectionKey = context.getSelectionKey();
        String selectedAugmentId = null;
        if (context.getPlayerData() != null) {
            // Direct per-tier lookup avoids allocating a defensive snapshot (UnmodifiableMap
            // + LinkedHashMap copy + iterator) on every passive tick just to read one key.
            // This path runs per-player per-tick from PassiveRegenSystem; previously the
            // dominant server-side allocation site (~49.6 GB sampled over a 2-minute JFR).
            selectedAugmentId = context.getPlayerData().getSelectedAugmentForTier(selectionKey);
        }
        if (selectedAugmentId == null || selectedAugmentId.isBlank()) {
            selectedAugmentId = extractMobAugmentIdFromSelectionKey(selectionKey);
        }
        CommonStatOffer selectedOffer = parseStatOfferId(selectedAugmentId);
        if (context.getPlayerData() == null && selectedOffer == null) {
            return;
        }
        String sourcePrefix = buildSourcePrefix(selectionKey);

        // Stat offers encode a single rolled stat — write only that attribute
        // regardless of whether this is a mob or player context.
        if (selectedOffer != null) {
            applySelectedMobOffer(context, sourcePrefix, selectedOffer);
            return;
        }

        // Player path: resolve each stat from cached rolls. Skip stats whose
        // configured range is zero — they can never produce a non-zero bonus.
        if (hasLifeForce) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_life_force",
                    SkillAttributeType.LIFE_FORCE,
                    resolveRoll(context, selectionKey, "life_force", lifeForceRange),
                    0L);
        }
        if (hasStrength) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_strength",
                    SkillAttributeType.STRENGTH,
                    resolveRoll(context, selectionKey, "strength", strengthRange),
                    0L);
        }
        if (hasSorcery) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_sorcery",
                    SkillAttributeType.SORCERY,
                    resolveRoll(context, selectionKey, "sorcery", sorceryRange),
                    0L);
        }
        if (hasDefense) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_defense",
                    SkillAttributeType.DEFENSE,
                    resolveRoll(context, selectionKey, "defense", defenseRange),
                    0L);
        }
        if (hasHaste) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_haste",
                    SkillAttributeType.HASTE,
                    resolveRoll(context, selectionKey, "haste", hasteRange),
                    0L);
        }
        if (hasPrecision) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_precision",
                    SkillAttributeType.PRECISION,
                    resolveRoll(context, selectionKey, "precision", precisionRange),
                    0L);
        }
        if (hasFerocity) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_ferocity",
                    SkillAttributeType.FEROCITY,
                    resolveRoll(context, selectionKey, "ferocity", ferocityRange),
                    0L);
        }
        if (hasDiscipline) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_discipline",
                    SkillAttributeType.DISCIPLINE,
                    resolveRoll(context, selectionKey, "discipline", disciplineRange),
                    0L);
        }
        if (hasFlow) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_flow",
                    SkillAttributeType.FLOW,
                    resolveRoll(context, selectionKey, "flow", flowRange),
                    0L);
        }
        if (hasStamina) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    sourcePrefix + "_stamina",
                    SkillAttributeType.STAMINA,
                    resolveRoll(context, selectionKey, "stamina", staminaRange),
                    0L);
        }
    }

    /**
     * Fast mob-only passive path — uses pre-computed sourcePrefix and parsed
     * offer from registration, skipping extractMobAugmentIdFromSelectionKey,
     * parseStatOfferId, and buildSourcePrefix string work every tick.
     */
    public void applyMobPassiveFast(AugmentHooks.PassiveStatContext context,
            String sourcePrefix,
            CommonStatOffer offer) {
        if (context == null || context.getRuntimeState() == null
                || sourcePrefix == null || offer == null) {
            return;
        }
        applySelectedMobOffer(context, sourcePrefix, offer);
    }

    private void applySelectedMobOffer(AugmentHooks.PassiveStatContext context,
            String sourcePrefix,
            CommonStatOffer selectedOffer) {
        if (context == null || context.getRuntimeState() == null || sourcePrefix == null || selectedOffer == null) {
            return;
        }

        SkillAttributeType attributeType = switch (selectedOffer.attributeKey()) {
            case "life_force" -> SkillAttributeType.LIFE_FORCE;
            case "strength" -> SkillAttributeType.STRENGTH;
            case "sorcery" -> SkillAttributeType.SORCERY;
            case "defense" -> SkillAttributeType.DEFENSE;
            case "haste" -> SkillAttributeType.HASTE;
            case "precision" -> SkillAttributeType.PRECISION;
            case "ferocity" -> SkillAttributeType.FEROCITY;
            case "discipline" -> SkillAttributeType.DISCIPLINE;
            case "flow" -> SkillAttributeType.FLOW;
            case "stamina" -> SkillAttributeType.STAMINA;
            default -> null;
        };

        if (attributeType == null) {
            return;
        }

        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                sourcePrefix + "_" + selectedOffer.attributeKey(),
                attributeType,
                selectedOffer.rolledValue(),
                0L);
    }

    private BonusRange readRange(Map<String, Object> buffs, String buffKey) {
        Map<String, Object> section = AugmentValueReader.getMap(buffs, buffKey);
        double base = Math.max(0.0D, AugmentValueReader.getDouble(section, "value", 0.0D));
        double min = Math.max(0.0D, AugmentValueReader.getDouble(section, "min_value", base));
        double max = Math.max(0.0D, AugmentValueReader.getDouble(section, "max_value", base));
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
        return new BonusRange(min, max);
    }

    private double resolveRoll(AugmentHooks.PassiveStatContext context,
            String selectionKey,
            String rollKey,
            BonusRange range) {
        if (context.getPlayerData() == null) {
            return 0.0D;
        }
        if (range.isFixed()) {
              return roundToTwoDecimals(range.min());
        }

        Double existing = context.getPlayerData().getAugmentValueRoll(selectionKey, rollKey);
        if (existing != null && Double.isFinite(existing)) {
              return roundToTwoDecimals(existing);
        }

        double rolled = roundToTwoDecimals(range.rollRandom());
        context.getPlayerData().setAugmentValueRoll(selectionKey, rollKey, rolled);
        return rolled;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private String buildSourcePrefix(String selectionKey) {
        return buildSourcePrefixStatic(selectionKey);
    }

    /**
     * Static variant exposed for pre-computation at registration time so the
     * per-tick hot path can skip the string work entirely.
     */
    public static String buildSourcePrefixStatic(String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return ID;
        }
        return ID + "_" + selectionKey.trim().toLowerCase(Locale.ROOT).replace('#', '_');
    }

    private String extractMobAugmentIdFromSelectionKey(String selectionKey) {
        if (selectionKey == null) {
            return null;
        }
        String normalized = selectionKey.trim();
        String prefix = "mob::";
        if (!normalized.startsWith(prefix)) {
            return null;
        }

        int lastDelimiter = normalized.lastIndexOf(OFFER_DELIMITER);
        if (lastDelimiter <= prefix.length()) {
            return null;
        }

        String augmentId = normalized.substring(prefix.length(), lastDelimiter).trim();
        return augmentId.isEmpty() ? null : augmentId;
    }

    public static String buildStatOfferId(String attributeKey, double rolledValue) {
        String normalizedKey = normalizeAttributeKey(attributeKey);
        if (normalizedKey == null) {
            return ID;
        }
        return ID + OFFER_DELIMITER + normalizedKey + OFFER_DELIMITER + formatOfferValue(rolledValue);
    }

    public static CommonStatOffer parseStatOfferId(String augmentId) {
        if (augmentId == null || augmentId.isBlank()) {
            return null;
        }
        String raw = augmentId.trim().toLowerCase(Locale.ROOT);
        String payload;
        String prefix = ID + OFFER_DELIMITER;
        String legacyPrefix = LEGACY_ID + OFFER_DELIMITER;
        if (raw.startsWith(prefix)) {
            payload = raw.substring(prefix.length());
        } else if (raw.startsWith(legacyPrefix)) {
            payload = raw.substring(legacyPrefix.length());
        } else {
            return null;
        }

        int split = payload.indexOf(OFFER_DELIMITER);
        if (split <= 0 || split >= payload.length() - OFFER_DELIMITER.length()) {
            return null;
        }

        String attributeKey = normalizeAttributeKey(payload.substring(0, split));
        String valueText = payload.substring(split + OFFER_DELIMITER.length()).trim();
        if (attributeKey == null || valueText.isBlank()) {
            return null;
        }

        try {
            return new CommonStatOffer(attributeKey, Double.parseDouble(valueText));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String resolveBaseAugmentId(String augmentId) {
        return parseStatOfferId(augmentId) != null ? ID : augmentId;
    }

    private static String normalizeAttributeKey(String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) {
            return null;
        }
        return attributeKey.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String formatOfferValue(double value) {
        String text = String.format(Locale.ROOT, "%.6f", value);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return end <= 0 ? "0" : text.substring(0, end);
    }

    public record CommonStatOffer(String attributeKey, double rolledValue) {
    }

    private record BonusRange(double min, double max) {
        private static final double EPSILON = 0.0001D;

        boolean isFixed() {
            return Math.abs(max - min) < EPSILON;
        }

        double rollRandom() {
            if (isFixed()) {
                return min;
            }

            if (isWholeNumber(min) && isWholeNumber(max)) {
                long minInt = Math.round(min);
                long maxInt = Math.round(max);
                if (maxInt <= minInt) {
                    return minInt;
                }
                return ThreadLocalRandom.current().nextLong(minInt, maxInt + 1L);
            }

            return ThreadLocalRandom.current().nextDouble(min, max);
        }

        private boolean isWholeNumber(double value) {
            return Math.abs(value - Math.rint(value)) < EPSILON;
        }
    }
}