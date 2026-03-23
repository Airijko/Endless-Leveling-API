package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.passives.PassiveManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import javax.annotation.Nonnull;

public final class HaymakerAugment extends Augment implements AugmentHooks.OnHitAugment {
    public static final String ID = "haymaker";
    public static final String COMBAT_START_STATE_ID = ID + "_combat_start";

    private static final String[] TRIGGER_VFX_IDS = new String[] {
        "Explosion_Medium",
        "Impact_Blade_01"
    };
    private static final String[] TRIGGER_SFX_IDS = new String[] {
        "SFX_Sword_T2_Impact",
        "SFX_Goblin_Lobber_Bomb_Death"
    };

    private final long requiredCombatMillis;
    private final long combatTimeoutMillis;
    private final long cooldownMillis;
    private final double maxHealthScaling;

    public HaymakerAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> haymaker = AugmentValueReader.getMap(passives, "haymaker");

        this.requiredCombatMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(haymaker,
                "required_combat_time",
                5.0D));
        this.combatTimeoutMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(haymaker,
                "combat_timeout",
                3.0D));
        this.cooldownMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(haymaker, "cooldown", 10.0D));
        this.maxHealthScaling = AugmentUtils
                .normalizeConfiguredBonusMultiplier(AugmentValueReader.getDouble(haymaker, "max_health_scaling", 0.10D));
    }

    @Override
    public float onHit(@Nonnull AugmentHooks.HitContext context) {
        if (context == null || context.getRuntimeState() == null) {
            return context != null ? context.getDamage() : 0f;
        }

        long now = System.currentTimeMillis();
        var combatState = context.getRuntimeState().getState(COMBAT_START_STATE_ID);

        long lastCombat = resolveLastCombatMillis(context);
        boolean outOfCombat = combatTimeoutMillis > 0L && lastCombat > 0L && now - lastCombat > combatTimeoutMillis;
        if (combatState.getStoredValue() <= 0.0D || outOfCombat) {
            combatState.setStoredValue(now);
            return context.getDamage();
        }

        long combatStartMillis = (long) combatState.getStoredValue();
        if (requiredCombatMillis > 0L && now - combatStartMillis < requiredCombatMillis) {
            return context.getDamage();
        }

        if (!AugmentUtils.consumeCooldown(context.getRuntimeState(), ID, getName(), cooldownMillis)) {
            return context.getDamage();
        }

        double maxHealth = AugmentUtils.getMaxHealth(context.getAttackerStats());
        double bonusDamage = maxHealth * maxHealthScaling;
        if (bonusDamage <= 0.0D) {
            return context.getDamage();
        }

        playTriggerVfx(context.getCommandBuffer(), context.getTargetRef());
        playTriggerSfx(context.getCommandBuffer(), context.getTargetRef());
        return context.getDamage() + (float) bonusDamage;
    }

    private long resolveLastCombatMillis(AugmentHooks.HitContext context) {
        if (context == null || context.getPlayerData() == null || context.getPlayerData().getUuid() == null) {
            return 0L;
        }

        EndlessLeveling plugin = EndlessLeveling.getInstance();
        PassiveManager passiveManager = plugin == null ? null : plugin.getPassiveManager();
        if (passiveManager == null) {
            return 0L;
        }

        var passiveState = passiveManager.getRuntimeState(context.getPlayerData().getUuid());
        return passiveState == null ? 0L : passiveState.getLastCombatMillis();
    }

    private void playTriggerVfx(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        Vector3d position = resolveTargetEffectPosition(commandBuffer, targetRef);
        if (position == null || !EntityRefUtil.isUsable(targetRef)) {
            return;
        }

        for (String vfxId : TRIGGER_VFX_IDS) {
            try {
                ParticleUtil.spawnParticleEffect(vfxId, position, targetRef.getStore());
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void playTriggerSfx(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        Vector3d position = resolveTargetEffectPosition(commandBuffer, targetRef);
        if (position == null || !EntityRefUtil.isUsable(targetRef)) {
            return;
        }

        for (String soundId : TRIGGER_SFX_IDS) {
            int soundIndex = resolveSoundIndex(soundId);
            if (soundIndex == 0) {
                continue;
            }
            SoundUtil.playSoundEvent3d(null, soundIndex, position, targetRef.getStore());
            return;
        }
    }

    private Vector3d resolveTargetEffectPosition(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> targetRef) {
        if (commandBuffer == null || !EntityRefUtil.isUsable(targetRef)) {
            return null;
        }

        TransformComponent targetTransform = EntityRefUtil.tryGetComponent(
                commandBuffer,
                targetRef,
                TransformComponent.getComponentType());
        if (targetTransform == null || targetTransform.getPosition() == null) {
            return null;
        }

        Vector3d baseTargetPosition = targetTransform.getPosition();
        return new Vector3d(baseTargetPosition.getX(), baseTargetPosition.getY() + 0.8D, baseTargetPosition.getZ());
    }

    private int resolveSoundIndex(String id) {
        int index = SoundEvent.getAssetMap().getIndex(id);
        return index == Integer.MIN_VALUE ? 0 : index;
    }
}
