package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShadowstepAugment extends YamlAugment
        implements AugmentHooks.OnKillAugment, AugmentHooks.OnHitAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "shadowstep";
    private static final String[] INVISIBILITY_EFFECT_IDS = new String[] { "invisibility", "invisible" };

    private static final class StealthState {
        long invisExpiresAt;
        boolean invisApplied;
    }

    private static final Map<UUID, StealthState> STATES = new ConcurrentHashMap<>();

    private final long invisDurationMillis;
    private final long cooldownMillis;
    private final String effectId;
    private final boolean breakOnAttack;
    private final double movementSpeedBonus;

    public ShadowstepAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> invis = AugmentValueReader.getMap(passives, "invisibility_on_kill");
        this.invisDurationMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(invis, "duration", 0.0D));
        this.cooldownMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(invis, "trigger_cooldown", 0.0D));
        Object effectObj = invis.get("effect");
        this.effectId = effectObj != null ? effectObj.toString() : "";
        Map<String, Object> breakConditions = AugmentValueReader.getMap(invis, "break_conditions");
        this.breakOnAttack = AugmentValueReader.getBoolean(breakConditions, "attack", false);
        Map<String, Object> buffs = AugmentValueReader.getMap(invis, "buffs");
        this.movementSpeedBonus = AugmentValueReader.getNestedDouble(buffs, 0.0D, "movement_speed", "value");
    }

    @Override
    public void onKill(AugmentHooks.KillContext context) {
        if (context == null || context.getRuntimeState() == null || context.getCommandBuffer() == null
                || context.getKillerRef() == null || context.getPlayerData() == null) {
            return;
        }

        if (!AugmentUtils.consumeCooldown(context.getRuntimeState(), ID, getName(), cooldownMillis)) {
            return;
        }

        long durationMillis = invisDurationMillis;
        long now = System.currentTimeMillis();
        UUID playerId = context.getPlayerData().getUuid();
        if (playerId != null) {
            StealthState state = STATES.computeIfAbsent(playerId, id -> new StealthState());
            state.invisApplied = applyInvisibility(context.getKillerRef(), context.getCommandBuffer(), effectId,
                    (float) (durationMillis / 1000.0D));
            state.invisExpiresAt = state.invisApplied ? now + durationMillis : 0L;
        }

        if (playerId != null) {
            StealthState state = STATES.get(playerId);
            if (state != null && state.invisApplied) {
                AugmentUtils.sendAugmentMessage(
                        AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getKillerRef()),
                        String.format("Shadowstep triggered! Invisibility and speed for %.1fs.",
                                durationMillis / 1000.0D));
            } else {
                AugmentUtils.sendAugmentMessage(
                        AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getKillerRef()),
                        "Shadowstep triggered, but invisibility effect could not be applied.");
            }
        }

        if (movementSpeedBonus != 0.0D) {
            AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                    ID + "_haste",
                    SkillAttributeType.HASTE,
                    movementSpeedBonus * 100.0D,
                    durationMillis);
        }
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null || context.getCommandBuffer() == null
                || context.getPlayerRef() == null) {
            return;
        }
        var cooldown = context.getRuntimeState().getCooldown(ID);
        if (cooldown == null || cooldown.isReadyNotified() || cooldown.getExpiresAt() <= 0L) {
            return;
        }
        if (System.currentTimeMillis() < cooldown.getExpiresAt()) {
            return;
        }
        AugmentUtils.sendAugmentMessage(
                AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getPlayerRef()),
                "Shadowstep is ready again!");
        cooldown.setReadyNotified(true);
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null || context.getRuntimeState() == null || context.getPlayerData() == null
                || context.getAttackerRef() == null || context.getCommandBuffer() == null) {
            return context != null ? context.getDamage() : 0f;
        }
        if (!breakOnAttack) {
            return context.getDamage();
        }

        UUID playerId = context.getPlayerData().getUuid();
        if (playerId == null) {
            return context.getDamage();
        }

        StealthState state = STATES.get(playerId);
        if (state == null || !state.invisApplied) {
            return context.getDamage();
        }

        long now = System.currentTimeMillis();
        if (state.invisExpiresAt > 0L && now > state.invisExpiresAt) {
            state.invisApplied = false;
            return context.getDamage();
        }

        removeInvisibility(context.getAttackerRef(), context.getCommandBuffer(), effectId);
        state.invisApplied = false;
        state.invisExpiresAt = 0L;
        AugmentUtils.setAttributeBonus(context.getRuntimeState(),
                ID + "_haste",
                SkillAttributeType.HASTE,
                0.0D,
                0L);
        return context.getDamage();
    }

    private boolean applyInvisibility(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            String configuredEffectId,
            float durationSeconds) {
        EffectControllerComponent controller = commandBuffer.getComponent(ref,
                EffectControllerComponent.getComponentType());
        if (controller == null) {
            return false;
        }
        EntityEffect effect = resolveEntityEffect(configuredEffectId);
        if (effect == null) {
            return false;
        }
        return controller.addEffect(ref, effect, durationSeconds, OverlapBehavior.OVERWRITE, commandBuffer);
    }

    private void removeInvisibility(Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            String configuredEffectId) {
        EffectControllerComponent controller = commandBuffer.getComponent(ref,
                EffectControllerComponent.getComponentType());
        if (controller == null) {
            return;
        }
        EntityEffect effect = resolveEntityEffect(configuredEffectId);
        if (effect != null) {
            int idx = EntityEffect.getAssetMap().getIndex(effect.getId());
            if (idx != Integer.MIN_VALUE) {
                controller.removeEffect(ref, idx, commandBuffer);
            }
        }
    }

    private EntityEffect resolveEntityEffect(String configuredId) {
        EntityEffect configured = resolveEntityEffectById(configuredId);
        if (configured != null) {
            return configured;
        }

        for (String fallbackId : INVISIBILITY_EFFECT_IDS) {
            EntityEffect fallback = resolveEntityEffectById(fallbackId);
            if (fallback != null) {
                return fallback;
            }
        }
        return null;
    }

    private EntityEffect resolveEntityEffectById(String configuredId) {
        if (configuredId == null || configuredId.isBlank()) {
            return null;
        }
        String trimmed = configuredId.trim();
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(trimmed);
        if (effect != null) {
            return effect;
        }
        effect = EntityEffect.getAssetMap().getAsset(trimmed.toLowerCase());
        if (effect != null) {
            return effect;
        }
        effect = EntityEffect.getAssetMap().getAsset(trimmed.toUpperCase());
        if (effect != null) {
            return effect;
        }
        if ("invisible".equalsIgnoreCase(trimmed)) {
            effect = EntityEffect.getAssetMap().getAsset("invisibility");
            if (effect != null) {
                return effect;
            }
            return EntityEffect.getAssetMap().getAsset("invisible");
        }
        return null;
    }
}
