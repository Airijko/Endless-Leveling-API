package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.Augment;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentState;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentRuntimeState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

public final class ConquerorAugment extends Augment
    implements AugmentHooks.OnHitAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "conqueror";
    public static final long INTERNAL_COOLDOWN_MILLIS = 400L;
    public static final long INTERNAL_STACKING_DELAY_MILLIS = 400L;
    private static final String STACK_DELAY_STATE_ID = ID + "_stack_delay";
    private static final String AURA_STATE_ID = ID + "_aura";
    private static final double TRIGGER_VFX_Y_OFFSET = 0.8D;
    private static final long AURA_REFRESH_INTERVAL_MILLIS = 900L;
    private static final String[] TRIGGER_AURA_EFFECT_IDS = new String[] {
        "Sword_Signature_SpinStab",
        "Mace_Signature",
        "Dagger_Signature"
    };
    private static final String[] TRIGGER_SFX_PRIMARY_IDS = new String[] {
        "SFX_Staff_Flame_Fireball_Impact",
        "SFX_Ice_Ball_Death",
        "SFX_Arrow_Frost_Miss"
    };
    private static final int TRIGGER_SFX_PLAY_COUNT = 1;

    private final double bonusDamagePerStack;
    private final int maxStacks;
    private final double maxStackFlatTrueDamage;
    private final double maxStackTrueDamagePercent;
    private final long stackDurationMillis;

    public ConquerorAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> buffs = AugmentValueReader.getMap(passives, "buffs");
        Map<String, Object> bonusDamage = AugmentValueReader.getMap(buffs, "bonus_damage");
        Map<String, Object> maxStackBonus = AugmentValueReader.getMap(passives, "max_stack_bonus");
        Map<String, Object> trueDamage = AugmentValueReader.getMap(maxStackBonus, "bonus_true_damage");
        Map<String, Object> duration = AugmentValueReader.getMap(passives, "duration");

        this.bonusDamagePerStack = AugmentUtils
                .normalizeConfiguredBonusMultiplier(AugmentValueReader.getDouble(bonusDamage, "value", 0.0D));
        this.maxStacks = Math.max(1, AugmentValueReader.getInt(buffs, "max_stacks", 1));
        this.maxStackFlatTrueDamage = Math.max(0.0D, AugmentValueReader.getDouble(trueDamage, "value", 0.0D));
        this.maxStackTrueDamagePercent = normalizePercent(AugmentValueReader.getDouble(trueDamage,
                "true_damage_percent",
                0.0D));
        this.stackDurationMillis = AugmentUtils
                .secondsToMillis(AugmentValueReader.getDouble(duration, "seconds", 0.0D));
    }

    @Override
    public float onHit(AugmentHooks.HitContext context) {
        if (context == null) {
            return 0f;
        }

        AugmentRuntimeState runtime = context != null ? context.getRuntimeState() : null;
        if (runtime == null) {
            return context.getDamage();
        }

        PlayerRef playerRef = AugmentUtils.getPlayerRef(context.getCommandBuffer(), context.getAttackerRef());
        var state = runtime.getState(ID);
        long now = System.currentTimeMillis();
        if (stackDurationMillis > 0L && state.getStacks() > 0 && state.getExpiresAt() > 0L
                && now >= state.getExpiresAt()) {
            AugmentUtils.setStacksWithNotify(runtime, ID, 0, maxStacks, playerRef, getName());
            state.setStoredValue(0.0D);
            state.setExpiresAt(0L);
            clearAura(context.getAttackerRef(), context.getCommandBuffer(), runtime.getState(AURA_STATE_ID));
        }

        int stacks = state.getStacks();
        boolean wasAtFullStacks = isAtFullStacks(stacks);
        boolean gainedStack = false;
        if (isStackDelayReady(runtime, now)) {
            stacks = AugmentUtils.setStacksWithNotify(runtime,
                    ID,
                    stacks + 1,
                    maxStacks,
                    playerRef,
                    getName());
            if (stackDurationMillis > 0L) {
                state.setExpiresAt(now + stackDurationMillis);
            }
            markStackDelay(runtime, now);
            gainedStack = stacks > 0;
        }

        float updatedDamage = AugmentUtils.applyAdditiveBonusFromBase(
                context.getDamage(),
                context.getBaseDamage(),
                stacks * bonusDamagePerStack);
        if (stacks >= maxStacks && (maxStackFlatTrueDamage > 0.0D || maxStackTrueDamagePercent > 0.0D)) {
            boolean cooldownReady = state.getLastProc() <= 0L || now - state.getLastProc() >= INTERNAL_COOLDOWN_MILLIS;
            if (cooldownReady) {
                double bonusTrueDamage = maxStackFlatTrueDamage
                        + (Math.max(0.0D, context.getBaseDamage()) * maxStackTrueDamagePercent);
                context.addTrueDamageBonus(bonusTrueDamage);
                state.setLastProc(now);
            }
        }

        if (isAtFullStacks(stacks)) {
            AugmentState auraState = runtime.getState(AURA_STATE_ID);
            auraState.setStacks(1);
            auraState.setExpiresAt(state.getExpiresAt());
            if (auraState.getStoredValue() <= 0.0D) {
                auraState.setStoredValue(now);
            }

            if (gainedStack && !wasAtFullStacks) {
                boolean applied = refreshAuraEffect(context.getAttackerRef(), context.getCommandBuffer(), state, now);
                if (applied) {
                    auraState.setStoredValue(now + AURA_REFRESH_INTERVAL_MILLIS);
                    auraState.setLastProc(now);
                } else {
                    auraState.setStoredValue(now + 150L);
                }
                playTriggerSound(context);
            } else if ((long) auraState.getStoredValue() <= now) {
                boolean applied = refreshAuraEffect(context.getAttackerRef(), context.getCommandBuffer(), state, now);
                if (applied) {
                    auraState.setStoredValue(now + AURA_REFRESH_INTERVAL_MILLIS);
                    auraState.setLastProc(now);
                } else {
                    auraState.setStoredValue(now + 150L);
                }
            }
        } else {
            clearAura(context.getAttackerRef(), context.getCommandBuffer(), runtime.getState(AURA_STATE_ID));
        }

        return updatedDamage;
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getRuntimeState() == null || context.getCommandBuffer() == null
                || context.getPlayerRef() == null) {
            return;
        }

        AugmentRuntimeState runtime = context.getRuntimeState();
        var conquerorState = runtime.getState(ID);
        long now = System.currentTimeMillis();

        if (stackDurationMillis > 0L && conquerorState.getStacks() > 0 && conquerorState.getExpiresAt() > 0L
                && now >= conquerorState.getExpiresAt()) {
            clearAura(context.getPlayerRef(), context.getCommandBuffer(), runtime.getState(AURA_STATE_ID));
            return;
        }

        if (!isAtFullStacks(conquerorState.getStacks())) {
            clearAura(context.getPlayerRef(), context.getCommandBuffer(), runtime.getState(AURA_STATE_ID));
            return;
        }

        var auraState = runtime.getState(AURA_STATE_ID);
        auraState.setStacks(1);
        auraState.setExpiresAt(conquerorState.getExpiresAt());

        long nextRefreshAt = (long) auraState.getStoredValue();
        if (nextRefreshAt > now) {
            return;
        }

        boolean applied = refreshAuraEffect(context.getPlayerRef(), context.getCommandBuffer(), conquerorState, now);
        if (applied) {
            auraState.setStoredValue(now + AURA_REFRESH_INTERVAL_MILLIS);
            auraState.setLastProc(now);
        } else {
            auraState.setStoredValue(now + 150L);
        }
    }

    private static double normalizePercent(double raw) {
        if (!Double.isFinite(raw) || raw <= 0.0D) {
            return 0.0D;
        }
        return raw > 1.0D ? raw / 100.0D : raw;
    }

    private static boolean isStackDelayReady(AugmentRuntimeState runtime, long now) {
        var delayState = runtime.getState(STACK_DELAY_STATE_ID);
        return delayState.getLastProc() <= 0L || now - delayState.getLastProc() >= INTERNAL_STACKING_DELAY_MILLIS;
    }

    private static void markStackDelay(AugmentRuntimeState runtime, long now) {
        runtime.getState(STACK_DELAY_STATE_ID).setLastProc(now);
    }

    private boolean isAtFullStacks(int stacks) {
        return maxStacks > 0 && stacks >= maxStacks;
    }

    private void playTriggerSound(AugmentHooks.HitContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

        Ref<EntityStore> attackerRef = context.getAttackerRef();
        if (!EntityRefUtil.isUsable(attackerRef)) {
            return;
        }

        Vector3d attackerPosition = resolveEffectPosition(context.getCommandBuffer(), attackerRef);
        if (attackerPosition == null) {
            return;
        }

        int primaryIndex = resolveFirstAvailableSoundIndex(TRIGGER_SFX_PRIMARY_IDS, 0);
        if (primaryIndex == 0) {
            return;
        }

        PlayerRef playerRef = EntityRefUtil.tryGetComponent(
                context.getCommandBuffer(),
                attackerRef,
                PlayerRef.getComponentType());

        for (int i = 0; i < TRIGGER_SFX_PLAY_COUNT; i++) {
            if (playerRef != null && playerRef.isValid()) {
                SoundUtil.playSoundEvent2d(attackerRef, primaryIndex, SoundCategory.SFX, attackerRef.getStore());
            } else {
                SoundUtil.playSoundEvent3d(null, primaryIndex, attackerPosition, attackerRef.getStore());
            }
        }
    }

    private static int resolveFirstAvailableSoundIndex(String[] ids, int excludedIndex) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (String id : ids) {
            int index = resolveSoundIndex(id);
            if (index == 0 || index == excludedIndex) {
                continue;
            }
            return index;
        }
        return 0;
    }

    private static int resolveSoundIndex(String id) {
        int index = SoundEvent.getAssetMap().getIndex(id);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    private boolean refreshAuraEffect(Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            AugmentState conquerorState,
            long now) {
        if (!EntityRefUtil.isUsable(targetRef) || commandBuffer == null || conquerorState == null
                || !isAtFullStacks(conquerorState.getStacks())) {
            return false;
        }

        EffectControllerComponent controller = EntityRefUtil.tryGetComponent(commandBuffer,
                targetRef,
                EffectControllerComponent.getComponentType());
        if (controller == null) {
            return false;
        }

        EntityEffect auraEffect = resolveAuraEffect();
        if (auraEffect == null) {
            return false;
        }

        float durationSeconds;
        if (stackDurationMillis > 0L && conquerorState.getExpiresAt() > now) {
            durationSeconds = Math.max(1.5F, (float) ((conquerorState.getExpiresAt() - now) / 1000.0D));
        } else {
            durationSeconds = 2.0F;
        }

        return controller.addEffect(targetRef,
                auraEffect,
                durationSeconds,
                OverlapBehavior.OVERWRITE,
                commandBuffer);
    }

    private static EntityEffect resolveAuraEffect() {
        for (String candidate : TRIGGER_AURA_EFFECT_IDS) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(candidate);
            if (effect != null) {
                return effect;
            }
            effect = EntityEffect.getAssetMap().getAsset(candidate.toLowerCase());
            if (effect != null) {
                return effect;
            }
            effect = EntityEffect.getAssetMap().getAsset(candidate.toUpperCase());
            if (effect != null) {
                return effect;
            }
        }
        return null;
    }

    private void clearAura(Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            AugmentState auraState) {
        if (EntityRefUtil.isUsable(targetRef) && commandBuffer != null) {
            EffectControllerComponent controller = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    EffectControllerComponent.getComponentType());
            if (controller != null) {
                for (String candidate : TRIGGER_AURA_EFFECT_IDS) {
                    int effectIndex = EntityEffect.getAssetMap().getIndex(candidate);
                    if (effectIndex != Integer.MIN_VALUE) {
                        controller.removeEffect(targetRef, effectIndex, commandBuffer);
                    }
                }
            }
        }

        clearAuraState(auraState);
    }

    private void clearAuraState(AugmentState auraState) {
        if (auraState == null) {
            return;
        }
        auraState.setStacks(0);
        auraState.setExpiresAt(0L);
        auraState.setStoredValue(0.0D);
        auraState.setLastProc(0L);
    }

    private Vector3d resolveEffectPosition(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> sourceRef) {
        TransformComponent transform = EntityRefUtil.tryGetComponent(
                commandBuffer,
                sourceRef,
                TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }

        Vector3d position = transform.getPosition();
        return new Vector3d(
                position.getX(),
                position.getY() + TRIGGER_VFX_Y_OFFSET,
                position.getZ());
    }
}
