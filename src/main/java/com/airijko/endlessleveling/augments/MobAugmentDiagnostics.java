package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.augments.types.CommonAugment;
import com.airijko.endlessleveling.enums.PassiveTier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class MobAugmentDiagnostics {
    public static final String LIFE_FORCE_STAT_KEY = "life_force";
    public static final String DEFENSE_STAT_KEY = "defense";
    public static final String PRECISION_STAT_KEY = "precision";
    public static final String FEROCITY_STAT_KEY = "ferocity";
    public static final String STRENGTH_STAT_KEY = "strength";
    public static final String STAMINA_STAT_KEY = "stamina";
    public static final String SORCERY_STAT_KEY = "sorcery";

    private static final List<String> ALLOWED_MOB_COMMON_STAT_KEYS = List.of(
            LIFE_FORCE_STAT_KEY,
            DEFENSE_STAT_KEY,
            PRECISION_STAT_KEY,
            FEROCITY_STAT_KEY,
            STRENGTH_STAT_KEY,
            SORCERY_STAT_KEY);
    private static final List<String> OFFENSE_COMMON_STAT_KEYS = List.of(
            FEROCITY_STAT_KEY,
            PRECISION_STAT_KEY,
            SORCERY_STAT_KEY,
            STRENGTH_STAT_KEY);
    private static final List<String> DEFENSE_COMMON_STAT_KEYS = List.of(
            DEFENSE_STAT_KEY,
            LIFE_FORCE_STAT_KEY);

    private MobAugmentDiagnostics() {
    }

    public static boolean isAllowedMobCommonStatKey(String attributeKey) {
        String normalized = normalizeKey(attributeKey);
        return normalized != null && ALLOWED_MOB_COMMON_STAT_KEYS.contains(normalized);
    }

    public static List<String> getAllowedMobCommonStatKeys() {
        return ALLOWED_MOB_COMMON_STAT_KEYS;
    }

    public static List<String> getOffenseCommonStatKeys() {
        return OFFENSE_COMMON_STAT_KEYS;
    }

    public static List<String> getDefenseCommonStatKeys() {
        return DEFENSE_COMMON_STAT_KEYS;
    }

    public static Summary summarize(List<String> augmentIds, AugmentManager augmentManager) {
        if (augmentIds == null || augmentIds.isEmpty()) {
            return Summary.empty();
        }

        Map<String, Integer> tierCounts = new LinkedHashMap<>();
        Map<String, Integer> nonCommonCounts = new TreeMap<>();
        Map<String, MutableCommonStatSummary> commonTotals = new TreeMap<>();
        int commonCount = 0;
        int unresolvedCommonCount = 0;

        for (String augmentId : augmentIds) {
            String baseAugmentId = CommonAugment.resolveBaseAugmentId(augmentId);
            if (baseAugmentId == null || baseAugmentId.isBlank()) {
                baseAugmentId = "unknown";
            }

            if (CommonAugment.ID.equalsIgnoreCase(baseAugmentId)) {
                commonCount++;
                tierCounts.merge(PassiveTier.COMMON.name(), 1, Integer::sum);

                CommonAugment.CommonStatOffer offer = CommonAugment.parseStatOfferId(augmentId);
                if (offer == null || normalizeKey(offer.attributeKey()) == null) {
                    unresolvedCommonCount++;
                    continue;
                }

                String attributeKey = normalizeKey(offer.attributeKey());
                double rolledValue = Double.isFinite(offer.rolledValue()) ? offer.rolledValue() : 0.0D;
                commonTotals.computeIfAbsent(attributeKey, ignored -> new MutableCommonStatSummary())
                        .add(rolledValue);
                continue;
            }

            String normalizedBaseId = baseAugmentId.trim().toLowerCase(Locale.ROOT);
            nonCommonCounts.merge(normalizedBaseId, 1, Integer::sum);

            PassiveTier tier = resolveTier(baseAugmentId, augmentManager);
            String tierName = tier != null ? tier.name() : "UNKNOWN";
            tierCounts.merge(tierName, 1, Integer::sum);
        }

        Map<String, CommonStatSummary> commonStats = new TreeMap<>();
        for (Map.Entry<String, MutableCommonStatSummary> entry : commonTotals.entrySet()) {
            commonStats.put(entry.getKey(), entry.getValue().toSummary(entry.getKey()));
        }

        return new Summary(augmentIds.size(),
                commonCount,
                unresolvedCommonCount,
                Map.copyOf(tierCounts),
                Map.copyOf(nonCommonCounts),
                Map.copyOf(commonStats));
    }

    private static PassiveTier resolveTier(String augmentId, AugmentManager augmentManager) {
        if (augmentManager == null || augmentId == null || augmentId.isBlank()) {
            return null;
        }
        AugmentDefinition definition = augmentManager.getAugment(augmentId);
        return definition != null ? definition.getTier() : null;
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static final class MutableCommonStatSummary {
        private int count;
        private double totalValue;

        void add(double value) {
            count++;
            totalValue += value;
        }

        CommonStatSummary toSummary(String attributeKey) {
            return new CommonStatSummary(attributeKey, count, totalValue);
        }
    }

    public record CommonStatSummary(String attributeKey, int count, double totalValue) {
    }

    public record Summary(int totalAugments,
            int commonCount,
            int unresolvedCommonCount,
            Map<String, Integer> tierCounts,
            Map<String, Integer> nonCommonCounts,
            Map<String, CommonStatSummary> commonStats) {
        private static Summary empty() {
            return new Summary(0, 0, 0, Map.of(), Map.of(), Map.of());
        }

        public String formatCommonStatTotals(List<String> attributeKeys) {
            if (attributeKeys == null || attributeKeys.isEmpty() || commonStats.isEmpty()) {
                return "none";
            }

            List<String> parts = new ArrayList<>();
            for (String attributeKey : attributeKeys) {
                CommonStatSummary summary = commonStats.get(normalizeKey(attributeKey));
                if (summary == null) {
                    continue;
                }
                parts.add(String.format(Locale.ROOT,
                        "%s=%.3f (count=%d)",
                        summary.attributeKey(),
                        summary.totalValue(),
                        summary.count()));
            }
            return parts.isEmpty() ? "none" : String.join(", ", parts);
        }

        public String formatTierCounts() {
            List<String> ordered = new ArrayList<>();
            appendTierCount(ordered, PassiveTier.COMMON.name());
            appendTierCount(ordered, PassiveTier.ELITE.name());
            appendTierCount(ordered, PassiveTier.LEGENDARY.name());
            appendTierCount(ordered, PassiveTier.MYTHIC.name());
            appendTierCount(ordered, "UNKNOWN");
            return ordered.isEmpty() ? "none" : String.join(", ", ordered);
        }

        private void appendTierCount(List<String> ordered, String tierName) {
            Integer count = tierCounts.get(tierName);
            if (count != null && count > 0) {
                ordered.add(tierName + "=" + count);
            }
        }

        public String formatAugmentList() {
            List<String> entries = new ArrayList<>();

            for (String attributeKey : ALLOWED_MOB_COMMON_STAT_KEYS) {
                appendCommonEntry(entries, attributeKey);
            }

            for (Map.Entry<String, CommonStatSummary> entry : commonStats.entrySet()) {
                if (ALLOWED_MOB_COMMON_STAT_KEYS.contains(entry.getKey())) {
                    continue;
                }
                appendCommonEntry(entries, entry.getKey());
            }

            if (unresolvedCommonCount > 0) {
                entries.add("common:unresolved x" + unresolvedCommonCount);
            }

            for (Map.Entry<String, Integer> entry : nonCommonCounts.entrySet()) {
                entries.add(entry.getKey() + " x" + entry.getValue());
            }

            return entries.isEmpty() ? "none" : String.join(", ", entries);
        }

        private void appendCommonEntry(List<String> entries, String attributeKey) {
            CommonStatSummary summary = commonStats.get(attributeKey);
            if (summary == null) {
                return;
            }

            entries.add(String.format(Locale.ROOT,
                    "common:%s x%d total=%.3f",
                    summary.attributeKey(),
                    summary.count(),
                    summary.totalValue()));
        }
    }
}