package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Augments page that displays three random augment definitions.
 */
public class AugmentsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final int CARD_COUNT = 3;

    private static final Map<String, String> BUFF_NAME_OVERRIDES = createBuffNameOverrides();

    private final AugmentManager augmentManager;

    public AugmentsUIPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.augmentManager = plugin != null ? plugin.getAugmentManager() : null;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Augments/AugmentsCards.ui");

        List<AugmentDefinition> augments = pickRandomAugments();
        for (int i = 0; i < CARD_COUNT; i++) {
            AugmentDefinition augment = i < augments.size() ? augments.get(i) : null;
            applyCard(ui, i + 1, augment);
        }
    }

    private List<AugmentDefinition> pickRandomAugments() {
        if (augmentManager == null || augmentManager.getAugments().isEmpty()) {
            return List.of();
        }
        List<AugmentDefinition> available = new ArrayList<>(augmentManager.getAugments().values());
        Collections.shuffle(available);
        int limit = Math.min(CARD_COUNT, available.size());
        return available.subList(0, limit);
    }

    private void applyCard(@Nonnull UICommandBuilder ui, int slotIndex, AugmentDefinition augment) {
        String titleSelector = "#AugmentCard" + slotIndex + "Title";
        String descriptionSelector = "#AugmentCard" + slotIndex + "Description";
        String iconSelector = "#AugmentCard" + slotIndex + "Icon";
        String cooldownSelector = "#AugmentCard" + slotIndex + "Cooldown";
        String durationSelector = "#AugmentCard" + slotIndex + "Duration";
        String buffsSelector = "#AugmentCard" + slotIndex + "Buffs";
        String debuffsSelector = "#AugmentCard" + slotIndex + "Debuffs";

        // Temporary placeholder icon until augments supply their own.
        ui.set(iconSelector + ".ItemId", "Ingredient_Ice_Essence");
        ui.set(iconSelector + ".Visible", true);

        if (augment == null) {
            ui.set(titleSelector + ".Text", "NO AUGMENT");
            ui.set(descriptionSelector + ".Text", "Add augments to /mods/EndlessLeveling/augments to see them here.");
            ui.set(cooldownSelector + ".Visible", false);
            ui.set(durationSelector + ".Visible", false);
            ui.set(buffsSelector + ".Visible", false);
            ui.set(debuffsSelector + ".Visible", false);
            return;
        }

        ui.set(titleSelector + ".Text", augment.getName().toUpperCase(Locale.ROOT));

        String description = augment.getDescription();
        if (description == null || description.isBlank()) {
            description = "No description provided.";
        }
        ui.set(descriptionSelector + ".Text", description);

        Map<String, Object> passives = augment.getPassives();

        String cooldownText = formatCooldown(passives);
        if (cooldownText == null) {
            ui.set(cooldownSelector + ".Visible", false);
        } else {
            ui.set(cooldownSelector + ".Text", "Cooldown: " + cooldownText);
            ui.set(cooldownSelector + ".Visible", true);
        }

        String durationText = formatDuration(passives);
        if (durationText == null) {
            ui.set(durationSelector + ".Visible", false);
        } else {
            ui.set(durationSelector + ".Text", "Duration: " + durationText);
            ui.set(durationSelector + ".Visible", true);
        }

        String buffsText = formatBuffs(passives);
        if (buffsText == null || buffsText.isBlank()) {
            ui.set(buffsSelector + ".Visible", false);
        } else {
            ui.set(buffsSelector + ".Text", "Buffs: " + buffsText);
            ui.set(buffsSelector + ".Visible", true);
        }

        String debuffsText = formatDebuffs(passives);
        if (debuffsText == null || debuffsText.isBlank()) {
            ui.set(debuffsSelector + ".Visible", false);
        } else {
            ui.set(debuffsSelector + ".Text", "Debuffs: " + debuffsText);
            ui.set(debuffsSelector + ".Visible", true);
        }
    }

    private static Map<String, String> createBuffNameOverrides() {
        Map<String, String> map = new HashMap<>();
        map.put("strength", "Strength");
        map.put("sorcery", "Sorcery");
        map.put("haste", "Haste");
        map.put("haste_bonus", "Haste");
        map.put("crit_damage", "Critical Damage");
        map.put("crit_damage_bonus", "Critical Damage");
        map.put("critical_damage", "Critical Damage");
        map.put("crit_chance", "Critical Chance");
        map.put("critical_chance", "Critical Chance");
        map.put("life_steal", "Life Steal");
        map.put("movement_speed_bonus", "Move Speed");
        map.put("resistance_bonus", "Resistance");
        map.put("wither", "Wither");
        map.put("slow_percent", "Slow");
        return map;
    }

    private String formatCooldown(Map<String, Object> passives) {
        Double value = findNumericField(passives, "trigger_cooldown", "cooldown", "proc_cooldown");
        if (value == null) {
            return null;
        }
        return formatSeconds(value);
    }

    private String formatDuration(Map<String, Object> passives) {
        Double value = findNumericField(passives, "duration", "effect_duration");
        if (value == null) {
            return null;
        }
        return formatSeconds(value);
    }

    private Double findNumericField(Map<String, Object> passives, String... keys) {
        if (passives == null || passives.isEmpty()) {
            return null;
        }
        for (Object passiveObj : passives.values()) {
            Map<String, Object> passive = asMap(passiveObj);
            if (passive == null) {
                continue;
            }
            for (String key : keys) {
                Object val = passive.get(key);
                Double number = toDouble(val);
                if (number != null) {
                    return number;
                }
            }
        }
        return null;
    }

    private String formatBuffs(Map<String, Object> passives) {
        return formatEffects(passives, true);
    }

    private String formatDebuffs(Map<String, Object> passives) {
        return formatEffects(passives, false);
    }

    private String formatEffects(Map<String, Object> passives, boolean positives) {
        if (passives == null || passives.isEmpty()) {
            return null;
        }

        // Priority: explicit buffs/debuffs map on any passive
        for (Object passiveObj : passives.values()) {
            Map<String, Object> passive = asMap(passiveObj);
            if (passive == null) {
                continue;
            }
            String primaryKey = positives ? "buffs" : "debuffs";
            Map<String, Object> effects = asMap(passive.get(primaryKey));
            if (effects == null && positives) {
                effects = asMap(passive.get("buffs")); // fallback if only buffs map exists
            }
            if (effects != null && !effects.isEmpty()) {
                String rendered = renderBuffMap(effects, positives);
                if (!rendered.isBlank()) {
                    return rendered;
                }
            }
        }

        // Fallback: collect numeric fields that look like effects.
        List<String> parts = new ArrayList<>();
        for (Object passiveObj : passives.values()) {
            Map<String, Object> passive = asMap(passiveObj);
            if (passive == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : passive.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                if (key.equalsIgnoreCase("duration") || key.toLowerCase(Locale.ROOT).contains("cooldown")) {
                    continue; // skip timing fields
                }
                Object val = entry.getValue();
                Double number = toDouble(val);
                if (number != null) {
                    if ((positives && number > 0) || (!positives && number < 0)) {
                        parts.add(formatBuffEntry(key, number, null));
                    }
                    continue;
                }
                Map<String, Object> nested = asMap(val);
                if (nested != null) {
                    Double nestedValue = toDouble(nested.get("value_percent"));
                    if (nestedValue != null) {
                        if ((positives && nestedValue > 0) || (!positives && nestedValue < 0)) {
                            parts.add(formatBuffEntry(key, nestedValue, "%"));
                        }
                        continue;
                    }
                    nestedValue = toDouble(nested.get("value"));
                    if (nestedValue != null) {
                        if ((positives && nestedValue > 0) || (!positives && nestedValue < 0)) {
                            parts.add(formatBuffEntry(key, nestedValue, null));
                        }
                    }
                }
            }
        }

        if (parts.isEmpty()) {
            return null;
        }
        return String.join(", ", parts);
    }

    private String renderBuffMap(Map<String, Object> buffs, boolean positives) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : buffs.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            Object val = entry.getValue();
            Double percent = toDouble(val);
            if (percent == null) {
                Map<String, Object> nested = asMap(val);
                if (nested != null) {
                    percent = toDouble(nested.get("value_percent"));
                    if (percent == null) {
                        percent = toDouble(nested.get("value"));
                    }
                }
            }
            if (percent != null) {
                if ((positives && percent > 0) || (!positives && percent < 0)) {
                    parts.add(formatBuffEntry(key, percent, null));
                }
            }
        }
        return String.join(", ", parts);
    }

    private String formatBuffEntry(String key, double value, String forcedSuffix) {
        String label = BUFF_NAME_OVERRIDES.getOrDefault(key.toLowerCase(Locale.ROOT), key.replace('_', ' '));
        String suffix = forcedSuffix;
        if (suffix == null) {
            if (key.toLowerCase(Locale.ROOT).contains("percent") || Math.abs(value) <= 1.0) {
                suffix = "%";
                value = value * 100.0;
            } else {
                suffix = "";
            }
        }
        return capitalize(label) + ": " + formatNumber(value) + suffix;
    }

    private String formatSeconds(double seconds) {
        if (seconds <= 0) {
            return "-";
        }
        // trim trailing zeros for cleaner output
        String base = formatNumber(seconds);
        return base + "s";
    }

    private String formatNumber(double value) {
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}