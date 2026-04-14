package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.api.ELNotificationType;
import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.passives.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveScaling;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.races.RacePassiveDefinition;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.airijko.endlessleveling.util.ChatMessageTemplate;
import com.airijko.endlessleveling.util.PlayerChatNotifier;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically pulses party healing around the source player.
 */
public final class HealingAuraPassive {

    private static final long PULSE_INTERVAL_MILLIS = 2000L;
    private static final double BASE_RANGE_BLOCKS = PartyHealingDistributor.DEFAULT_BASE_RADIUS_BLOCKS;
    private static final double MANA_PER_RANGE_BLOCK = PartyHealingDistributor.DEFAULT_MANA_PER_RADIUS_BLOCK;
    private static final double HEAL_FROM_TOTAL_MANA = 0.10D;
    private static final double HEAL_FROM_TOTAL_STAMINA = 0.20D;
    private static final double DEFAULT_SELF_HEAL_EFFECTIVENESS = 1.0D;
    private static final double DEFAULT_DAMAGE_PAUSE_SECONDS = 4.0D;

    private static final String AURA_EFFECT_ID = "EL_Healing_Aura_Effect";
    private static final float AURA_EFFECT_DURATION = 10.0F;
    private static final long AURA_VISUAL_INTERVAL_MILLIS = 8000L;
    private static final Map<String, AuraVisualState> AURA_VISUAL_STATE = new ConcurrentHashMap<>();

    private static final class AuraVisualState {
        long lastVisualAt;
    }

    private HealingAuraPassive() {
    }

    public static long pulseIntervalMillis() {
        return PULSE_INTERVAL_MILLIS;
    }

    public static void pulse(PlayerData playerData,
            Ref<EntityStore> sourceRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap sourceStats,
            ArchetypePassiveSnapshot archetypeSnapshot,
            PassiveRuntimeState runtimeState) {
        if (playerData == null || sourceRef == null || commandBuffer == null
                || sourceStats == null || archetypeSnapshot == null || runtimeState == null) {
            return;
        }

        ArchetypePassiveScaling.AuraScales auraScales = ArchetypePassiveScaling.resolveAuraScales(
            archetypeSnapshot,
            ArchetypePassiveType.HEALING_AURA,
            playerData);
        if (auraScales.fullScale() <= 0.0D && auraScales.ratioScale() <= 0.0D) {
            return;
        }
        HealingAuraConfig config = resolveConfig(archetypeSnapshot);

        long now = System.currentTimeMillis();
        PlayerRef sourcePlayer = EntityRefUtil.tryGetComponent(commandBuffer, sourceRef, PlayerRef.getComponentType());

        if (isPausedByDamage(runtimeState, now, config.damagePauseMillis())) {
            if (!runtimeState.isHealingAuraPaused()) {
                runtimeState.setHealingAuraPaused(true);
                clearAuraVisual(sourceRef, commandBuffer);
                notifyPassive(sourcePlayer,
                        "Healing Aura paused after taking damage.");
            }
            return;
        }

        if (runtimeState.isHealingAuraPaused()) {
            runtimeState.setHealingAuraPaused(false);
            notifyPassive(sourcePlayer,
                    "Healing Aura reactivated.");
        }

        EntityStatValue sourceHealth = sourceStats.get(DefaultEntityStatTypes.getHealth());
        if (sourceHealth == null || sourceHealth.getMax() <= 0f || sourceHealth.get() <= 0f) {
            return;
        }

        EntityStatValue sourceMana = sourceStats.get(DefaultEntityStatTypes.getMana());
        EntityStatValue sourceStamina = sourceStats.get(DefaultEntityStatTypes.getStamina());
        double totalMana = sourceMana != null ? Math.max(0.0D, sourceMana.getMax()) : 0.0D;
        double totalStamina = sourceStamina != null ? Math.max(0.0D, sourceStamina.getMax()) : 0.0D;

        double radius = Math.max(0.0D, config.baseRadius());
        if (config.manaPerBlock() > 0.0D) {
            radius += Math.floor(totalMana / config.manaPerBlock());
        }

        if (radius > 0.0D) {
            updateAuraVisual(sourceRef, commandBuffer, now, radius);
        }

        long lastPulse = runtimeState.getPartyMendingLastPulseMillis();
        if (lastPulse > 0L && now - lastPulse < PULSE_INTERVAL_MILLIS) {
            return;
        }

        double healPerPulse = (config.flatHealValue() * auraScales.fullScale())
            + (totalMana * config.manaRatio() * auraScales.ratioScale())
            + (totalStamina * config.staminaRatio() * auraScales.ratioScale());
        if (healPerPulse <= 0.0D) {
            return;
        }

        runtimeState.setPartyMendingLastPulseMillis(now);
        PartyHealingDistributor.applySplitHealingToWoundedParty(playerData,
                sourceRef,
                commandBuffer,
                sourceStats,
                healPerPulse,
                config.baseRadius(),
                config.manaPerBlock(),
                config.selfHealEffectiveness());
    }

    private static HealingAuraConfig resolveConfig(ArchetypePassiveSnapshot snapshot) {
        double flatHealValue = 0.0D;
        double manaRatio = HEAL_FROM_TOTAL_MANA;
        double staminaRatio = HEAL_FROM_TOTAL_STAMINA;
        double baseRadius = BASE_RANGE_BLOCKS;
        double manaPerBlock = MANA_PER_RANGE_BLOCK;
        double selfHealEffectiveness = DEFAULT_SELF_HEAL_EFFECTIVENESS;
        long damagePauseMillis = (long) Math.max(0L, Math.round(DEFAULT_DAMAGE_PAUSE_SECONDS * 1000.0D));

        if (snapshot == null) {
            return new HealingAuraConfig(flatHealValue,
                    manaRatio,
                    staminaRatio,
                    baseRadius,
                    manaPerBlock,
                    selfHealEffectiveness,
                    damagePauseMillis);
        }

        RacePassiveDefinition strongestDefinition = resolveStrongestDefinition(
                snapshot.getDefinitions(ArchetypePassiveType.HEALING_AURA));
        if (strongestDefinition == null || strongestDefinition.properties() == null
                || strongestDefinition.properties().isEmpty()) {
            return new HealingAuraConfig(flatHealValue,
                    manaRatio,
                    staminaRatio,
                    baseRadius,
                    manaPerBlock,
                    selfHealEffectiveness,
                    damagePauseMillis);
        }

        Map<String, Object> props = strongestDefinition.properties();
        flatHealValue = parseNonNegative(props.get("flat_heal_value"), flatHealValue);
        manaRatio = parseNonNegative(props.get("mana_ratio"), manaRatio);
        staminaRatio = parseNonNegative(props.get("stamina_ratio"), staminaRatio);
        baseRadius = parseNonNegative(props.get("radius"), baseRadius);

        Object radiusScalingRaw = props.get("radius_mana_scaling");
        if (radiusScalingRaw instanceof Map<?, ?> radiusScaling) {
            manaPerBlock = parsePositive(radiusScaling.get("mana_per_block"), manaPerBlock);
        }

        Object selfEffectRaw = firstNonNull(props.get("self_heal_effectiveness"), props.get("self_heal_ratio"));
        selfHealEffectiveness = parseBoundedRatio(selfEffectRaw, selfHealEffectiveness);

        Object damagePauseRaw = firstNonNull(props.get("damage_pause_seconds"),
                firstNonNull(props.get("pause_after_damage_seconds"), props.get("combat_pause_seconds")));
        double damagePauseSeconds = parseNonNegative(damagePauseRaw, DEFAULT_DAMAGE_PAUSE_SECONDS);
        damagePauseMillis = (long) Math.max(0L, Math.round(damagePauseSeconds * 1000.0D));

        return new HealingAuraConfig(flatHealValue,
                manaRatio,
                staminaRatio,
                baseRadius,
                manaPerBlock,
                selfHealEffectiveness,
                damagePauseMillis);
    }

    private static boolean isPausedByDamage(PassiveRuntimeState runtimeState, long now, long pauseMillis) {
        if (runtimeState == null || pauseMillis <= 0L) {
            return false;
        }
        long lastDamageTakenMillis = runtimeState.getLastDamageTakenMillis();
        if (lastDamageTakenMillis <= 0L) {
            return false;
        }
        return now - lastDamageTakenMillis < pauseMillis;
    }

    private static void notifyPassive(PlayerRef playerRef, String message) {
        if (playerRef == null || !playerRef.isValid() || message == null || message.isBlank()
                || EndlessLevelingAPI.get().isNotificationSuppressed(ELNotificationType.PASSIVE_TRIGGERED)) {
            return;
        }
        PlayerChatNotifier.send(playerRef, ChatMessageTemplate.PASSIVE_GENERIC, message);
    }

    private static RacePassiveDefinition resolveStrongestDefinition(List<RacePassiveDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        RacePassiveDefinition strongest = null;
        double strongestValue = Double.NEGATIVE_INFINITY;
        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            double value = definition.value();
            if (strongest == null || value > strongestValue) {
                strongest = definition;
                strongestValue = value;
            }
        }
        return strongest;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static double parseBoundedRatio(Object raw, double fallback) {
        double value = parseRawDouble(raw, fallback);
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double parseNonNegative(Object raw, double fallback) {
        double value = parseRawDouble(raw, fallback);
        return Math.max(0.0D, value);
    }

    private static double parsePositive(Object raw, double fallback) {
        double value = parseRawDouble(raw, fallback);
        return value > 0.0D ? value : fallback;
    }

    private static double parseRawDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static void updateAuraVisual(Ref<EntityStore> sourceRef,
            CommandBuffer<EntityStore> commandBuffer,
            long now,
            double radius) {
        if (sourceRef == null || commandBuffer == null || radius <= 0.0D) {
            return;
        }

        String key = resolveVisualKey(sourceRef, commandBuffer);
        AuraVisualState state = AURA_VISUAL_STATE.computeIfAbsent(key, unused -> new AuraVisualState());
        if (state.lastVisualAt > 0L && now - state.lastVisualAt < AURA_VISUAL_INTERVAL_MILLIS) {
            return;
        }

        EffectControllerComponent controller = EntityRefUtil.tryGetComponent(commandBuffer,
                sourceRef, EffectControllerComponent.getComponentType());
        if (controller == null) {
            return;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(AURA_EFFECT_ID);
        if (effect == null) {
            return;
        }

        state.lastVisualAt = now;
        try {
            controller.addEffect(sourceRef, effect, AURA_EFFECT_DURATION,
                    OverlapBehavior.OVERWRITE, commandBuffer);
        } catch (Exception ignored) {
        }
    }

    private static void clearAuraVisual(Ref<EntityStore> sourceRef, CommandBuffer<EntityStore> commandBuffer) {
        if (sourceRef == null || commandBuffer == null) {
            return;
        }
        AURA_VISUAL_STATE.remove(resolveVisualKey(sourceRef, commandBuffer));

        EffectControllerComponent controller = EntityRefUtil.tryGetComponent(commandBuffer,
                sourceRef, EffectControllerComponent.getComponentType());
        if (controller == null) {
            return;
        }
        int idx = EntityEffect.getAssetMap().getIndex(AURA_EFFECT_ID);
        if (idx != Integer.MIN_VALUE) {
            controller.removeEffect(sourceRef, idx, commandBuffer);
        }
    }

    private static String resolveVisualKey(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef playerRef = EntityRefUtil.tryGetComponent(commandBuffer, ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.isValid() && playerRef.getUuid() != null) {
            return playerRef.getUuid().toString();
        }
        Object store = ref.getStore();
        if (store != null) {
            return System.identityHashCode(store) + ":" + ref.getIndex();
        }
        return String.valueOf(ref.getIndex());
    }

    public static int clearAllRuntimeState() {
        int cleared = AURA_VISUAL_STATE.size();
        AURA_VISUAL_STATE.clear();
        return cleared;
    }

    private record HealingAuraConfig(double flatHealValue,
            double manaRatio,
            double staminaRatio,
            double baseRadius,
            double manaPerBlock,
            double selfHealEffectiveness,
            long damagePauseMillis) {
    }
}
