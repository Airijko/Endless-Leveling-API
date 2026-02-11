package com.airijko.endlessleveling.managers;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

/**
 * Centralizes computation of player attributes by merging the live Hytale
 * baselines with race overrides and EndlessLeveling skill bonuses.
 */
public class PlayerAttributeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    public enum AttributeSlot {
        LIFE_FORCE("EL_RACE_BASE_HEALTH", "SKILL_BONUS_HEALTH", DefaultEntityStatTypes.getHealth(),
                SkillAttributeType.LIFE_FORCE),
        STAMINA("EL_RACE_BASE_STAMINA", "SKILL_BONUS_STAMINA", DefaultEntityStatTypes.getStamina(),
                SkillAttributeType.STAMINA),
        INTELLIGENCE("EL_RACE_BASE_INTELLIGENCE", "SKILL_BONUS_INTELLIGENCE", DefaultEntityStatTypes.getMana(),
                SkillAttributeType.INTELLIGENCE);

        private final String raceModifierKey;
        private final String skillModifierKey;
        private final int statIndex;
        private final SkillAttributeType attributeType;

        AttributeSlot(String raceModifierKey, String skillModifierKey, int statIndex,
                SkillAttributeType attributeType) {
            this.raceModifierKey = raceModifierKey;
            this.skillModifierKey = skillModifierKey;
            this.statIndex = statIndex;
            this.attributeType = attributeType;
        }

        public String raceModifierKey() {
            return raceModifierKey;
        }

        public String skillModifierKey() {
            return skillModifierKey;
        }

        public int statIndex() {
            return statIndex;
        }

        public SkillAttributeType attributeType() {
            return attributeType;
        }
    }

    public record AttributeComputation(float observedBase,
            float vanillaBase,
            float raceBase,
            float raceDelta,
            float skillBonus,
            float finalMax) {
    }

    private static final EnumMap<SkillAttributeType, Float> DEFAULT_ATTRIBUTE_BASES = new EnumMap<>(
            SkillAttributeType.class);

    static {
        DEFAULT_ATTRIBUTE_BASES.put(SkillAttributeType.LIFE_FORCE, 100.0f);
        DEFAULT_ATTRIBUTE_BASES.put(SkillAttributeType.STAMINA, 10.0f);
        DEFAULT_ATTRIBUTE_BASES.put(SkillAttributeType.INTELLIGENCE, 0.0f);
    }

    private final RaceManager raceManager;
    private final EnumMap<AttributeSlot, Float> cachedVanillaBaselines = new EnumMap<>(AttributeSlot.class);
    private final Map<UUID, EnumMap<AttributeSlot, AttributeSnapshot>> lastAppliedAdjustments = new ConcurrentHashMap<>();

    public PlayerAttributeManager(@Nonnull RaceManager raceManager) {
        this.raceManager = raceManager;
    }

    public boolean applyAttribute(@Nonnull AttributeSlot slot,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull PlayerData playerData,
            float skillBonus) {

        EntityStatMap statMap = accessor.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            LOGGER.atWarning().log("PlayerAttributeManager: EntityStatMap missing for %s", ref);
            return false;
        }

        EntityStatValue current = statMap.get(slot.statIndex());
        if (current == null) {
            LOGGER.atWarning().log("PlayerAttributeManager: stat %s missing for %s", slot.name(), ref);
            return false;
        }

        float previousMax = current.getMax();
        float previousValue = current.get();

        UUID playerId = playerData != null ? playerData.getUuid() : null;
        AttributeSnapshot snapshot = getOrCreateSnapshot(playerId, slot);
        float observedTotal = current.getMax();
        float observedBase = Math.max(0.0f, observedTotal - snapshot.raceDelta - snapshot.skillBonus);
        float vanillaBase = resolveVanillaBaseValue(slot, observedBase);
        AttributeComputation computation = computeContribution(slot.attributeType(), observedBase, vanillaBase,
                skillBonus, playerData);

        statMap.removeModifier(slot.statIndex(), slot.raceModifierKey());
        statMap.removeModifier(slot.statIndex(), slot.skillModifierKey());

        if (slot.attributeType() == SkillAttributeType.INTELLIGENCE) {
            logIntelligenceBreakdown(playerData, computation);
        }

        applyStatModifier(statMap, slot.statIndex(), slot.raceModifierKey(), computation.raceDelta());
        applyStatModifier(statMap, slot.statIndex(), slot.skillModifierKey(), computation.skillBonus());

        EntityStatValue updated = statMap.get(slot.statIndex());
        float newMax = updated != null ? updated.getMax() : computation.finalMax();
        float ratio = previousMax > 0.01f ? previousValue / previousMax : 1.0f;
        float newValue = Math.max(0.01f, Math.min(newMax, ratio * newMax));

        if (slot.attributeType() == SkillAttributeType.INTELLIGENCE) {
            LOGGER.atInfo().log(
                    "Mana post-update for %s -> predictedFinal=%.2f, statMax=%.2f, statValue=%.2f, ratio=%.4f",
                    playerData.getPlayerName(), computation.finalMax(), newMax, newValue, ratio);
        }

        statMap.setStatValue(slot.statIndex(), newValue);
        statMap.update();

        if (playerId != null) {
            snapshot.raceDelta = computation.raceDelta();
            snapshot.skillBonus = computation.skillBonus();
        }

        LOGGER.atInfo().log(
                "PlayerAttributeManager: %s -> observed=%.2f, vanilla=%.2f, race=%.2f, skill=%.2f, finalMax=%.2f for %s",
                slot.name(), computation.observedBase(), computation.vanillaBase(), computation.raceBase(),
                computation.skillBonus(), newMax, playerData.getPlayerName());
        return true;
    }

    public AttributeComputation computeContribution(@Nonnull SkillAttributeType attributeType,
            float observedBase,
            float vanillaBase,
            float skillBonus,
            @Nonnull PlayerData playerData) {
        float effectiveVanilla = vanillaBase;
        float raceFallback = vanillaBase;

        if (attributeType == SkillAttributeType.INTELLIGENCE) {
            effectiveVanilla = 0.0f;
            raceFallback = 0.0f;
        }

        float raceBase = resolveRaceBaseValue(playerData, attributeType, raceFallback);
        float raceDelta = raceBase - effectiveVanilla;
        float finalMax = observedBase + raceDelta + skillBonus;
        return new AttributeComputation(observedBase, effectiveVanilla, raceBase, raceDelta, skillBonus, finalMax);
    }

    private float resolveVanillaBaseValue(AttributeSlot slot, float observedBase) {
        Float cached = cachedVanillaBaselines.get(slot);
        if (cached != null) {
            return cached;
        }

        Float defaultBaseline = DEFAULT_ATTRIBUTE_BASES.get(slot.attributeType());
        if (defaultBaseline != null) {
            cachedVanillaBaselines.put(slot, defaultBaseline);
            return defaultBaseline;
        }

        Float vanilla = lookupVanillaFromAssets(slot.statIndex());
        if (vanilla != null) {
            cachedVanillaBaselines.put(slot, vanilla);
            return vanilla;
        }

        return observedBase;
    }

    private Float lookupVanillaFromAssets(int statIndex) {
        try {
            EntityStatType type = EntityStatType.getAssetMap().getAsset(statIndex);
            if (type == null) {
                return null;
            }
            float initial = type.getInitialValue();
            if (!Float.isNaN(initial)) {
                return initial;
            }
            float max = type.getMax();
            if (!Float.isNaN(max)) {
                return max;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("PlayerAttributeManager: Failed to resolve vanilla base for stat %s: %s",
                    statIndex, e.getMessage());
        }
        return null;
    }

    private float resolveRaceBaseValue(PlayerData playerData, SkillAttributeType attributeType, float fallback) {
        if (playerData == null) {
            return fallback;
        }
        double raceValue = getRaceAttribute(playerData, attributeType, fallback);
        return (float) raceValue;
    }

    public double getRaceAttribute(PlayerData playerData, SkillAttributeType attributeType, double fallback) {
        if (raceManager == null || !raceManager.isEnabled()) {
            return fallback;
        }
        return raceManager.getAttribute(playerData, attributeType, fallback);
    }

    public double combineAttribute(PlayerData playerData, SkillAttributeType attributeType, double skillBonus,
            double fallback) {
        double raceValue = getRaceAttribute(playerData, attributeType, fallback);
        return raceValue + skillBonus;
    }

    private void logIntelligenceBreakdown(PlayerData playerData, AttributeComputation computation) {
        if (playerData == null) {
            return;
        }
        float externalSources = computation.observedBase() - computation.vanillaBase();
        LOGGER.atInfo().log(
                "Mana breakdown for %s -> external=%.2f, raceBase=%.2f (delta=%.2f), skill=%.2f, targetMax=%.2f",
                playerData.getPlayerName(), externalSources, computation.raceBase(), computation.raceDelta(),
                computation.skillBonus(), computation.finalMax());
    }

    private AttributeSnapshot getOrCreateSnapshot(UUID uuid, AttributeSlot slot) {
        if (uuid == null) {
            return new AttributeSnapshot();
        }
        EnumMap<AttributeSlot, AttributeSnapshot> slots = lastAppliedAdjustments.computeIfAbsent(uuid,
                ignored -> new EnumMap<>(AttributeSlot.class));
        AttributeSnapshot snapshot = slots.get(slot);
        if (snapshot == null) {
            snapshot = new AttributeSnapshot();
            slots.put(slot, snapshot);
        }
        return snapshot;
    }

    private static final class AttributeSnapshot {
        private float raceDelta;
        private float skillBonus;
    }

    private void applyStatModifier(EntityStatMap statMap, int statIndex, String key, float amount) {
        statMap.removeModifier(statIndex, key);
        if (Math.abs(amount) <= 0.0001f) {
            return;
        }
        try {
            StaticModifier modifier = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, amount);
            statMap.putModifier(statIndex, key, modifier);
        } catch (Exception e) {
            LOGGER.atSevere().log("PlayerAttributeManager: Failed to apply modifier %s: %s", key, e.getMessage());
        }
    }
}
