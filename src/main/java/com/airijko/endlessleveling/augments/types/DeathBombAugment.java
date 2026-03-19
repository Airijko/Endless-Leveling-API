package com.airijko.endlessleveling.augments.types;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentHooks;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager.AugmentRuntimeState;
import com.airijko.endlessleveling.augments.AugmentUtils;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.YamlAugment;
import com.airijko.endlessleveling.systems.PlayerCombatSystem;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathBombAugment extends YamlAugment
        implements AugmentHooks.OnDamageTakenAugment, AugmentHooks.PassiveStatAugment {
    public static final String ID = "death_bomb";

    private static final String[] TRIGGER_VFX_IDS = new String[] {
        "Explosion_Small",
        "Explosion_Medium"
    };
    private static final String EXPLOSION_PARTICLE_SMALL = "Explosion_Small";
    private static final String EXPLOSION_PARTICLE_MEDIUM = "Explosion_Medium";
    private static final String EXPLOSION_PARTICLE_BIG = "Explosion_Big";
    private static final String[] EXPLOSION_SFX_IDS = new String[] {
        "SFX_Goblin_Lobber_Bomb_Death",
        "SFX_Goblin_Lobber_Bomb_Miss"
    };
    private static final double MIN_VISUAL_BROADCAST_RADIUS = 24.0D;
    private static final double VISUAL_BROADCAST_RADIUS_MULTIPLIER = 4.0D;
    private static final long PENDING_BOMB_STALE_AFTER_MILLIS = 20000L;
    private static final Map<UUID, PendingBomb> PENDING_BOMBS = new ConcurrentHashMap<>();

    private final double healthRatio;
    private final double strengthRatio;
    private final double sorceryRatio;
    private final long delayMillis;
    private final long cooldownMillis;
    private final double radius;

    private static final class PendingBomb {
        final Ref<EntityStore> sourceRef;
        final Vector3d position;
        final long explodeAt;
        final double damage;
        final double radius;

        PendingBomb(Ref<EntityStore> sourceRef,
                Vector3d position,
                long explodeAt,
                double damage,
                double radius) {
            this.sourceRef = sourceRef;
            this.position = position;
            this.explodeAt = explodeAt;
            this.damage = Math.max(0.0D, damage);
            this.radius = Math.max(0.0D, radius);
        }
    }

    public DeathBombAugment(AugmentDefinition definition) {
        super(definition);
        Map<String, Object> passives = definition.getPassives();
        Map<String, Object> bomb = AugmentValueReader.getMap(passives, "death_bomb");
        Map<String, Object> damage = AugmentValueReader.getMap(bomb, "damage_scaling");

        this.healthRatio = normalizeConfiguredRatio(
            AugmentValueReader.getDouble(damage, "max_health_ratio", 0.50D));
        this.strengthRatio = normalizeConfiguredRatio(
            AugmentValueReader.getDouble(damage, "strength_ratio", 0.10D));
        this.sorceryRatio = normalizeConfiguredRatio(
            AugmentValueReader.getDouble(damage, "sorcery_ratio", 0.10D));
        this.delayMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(bomb, "delay", 3.0D));
        this.cooldownMillis = AugmentUtils.secondsToMillis(AugmentValueReader.getDouble(bomb, "cooldown", 0.0D));
        this.radius = Math.max(0.0D, AugmentValueReader.getDouble(bomb, "radius", 5.0D));
    }

        private static double normalizeConfiguredRatio(double configured) {
        double ratio = configured;
        if (ratio > 1.0D && ratio <= 100.0D) {
            ratio = ratio / 100.0D;
        }
        return Math.max(0.0D, ratio);
        }

    @Override
    public float onDamageTaken(AugmentHooks.DamageTakenContext context) {
        if (context == null || context.getRuntimeState() == null || context.getPlayerData() == null
                || context.getPlayerData().getUuid() == null || context.getCommandBuffer() == null
                || context.getDefenderRef() == null || context.getStatMap() == null) {
            return context != null ? context.getIncomingDamage() : 0f;
        }

        float incoming = Math.max(0f, context.getIncomingDamage());
        if (incoming <= 0f) {
            return incoming;
        }

        EntityStatValue hp = context.getStatMap().get(DefaultEntityStatTypes.getHealth());
        if (hp == null || hp.getMax() <= 0f || hp.get() <= 0f) {
            return incoming;
        }

        double projected = hp.get() - incoming;
        if (projected > 0.0D) {
            return incoming;
        }

        UUID uuid = context.getPlayerData().getUuid();
        long now = System.currentTimeMillis();
        PendingBomb existing = PENDING_BOMBS.get(uuid);
        if (existing != null) {
            long staleAt = existing.explodeAt + PENDING_BOMB_STALE_AFTER_MILLIS;
            if (now >= staleAt) {
                PENDING_BOMBS.remove(uuid, existing);
            } else {
                return incoming;
            }
        }

        AugmentRuntimeState runtime = context.getRuntimeState();
        if (!AugmentUtils.consumeCooldown(runtime, ID, getName(), cooldownMillis)) {
            return incoming;
        }

        TransformComponent transform = context.getCommandBuffer().getComponent(context.getDefenderRef(),
                TransformComponent.getComponentType());
        Vector3d position = transform != null ? transform.getPosition() : null;
        if (position == null) {
            return incoming;
        }

        double strength = context.getSkillManager() != null
                ? Math.max(0.0D, context.getSkillManager().calculatePlayerStrength(context.getPlayerData()))
                : 0.0D;
        double sorcery = context.getSkillManager() != null
                ? Math.max(0.0D, context.getSkillManager().calculatePlayerSorcery(context.getPlayerData()))
                : 0.0D;
        double scaledDamage = (hp.getMax() * healthRatio)
                + (strength * strengthRatio)
                + (sorcery * sorceryRatio);
        if (scaledDamage <= 0.0D) {
            return incoming;
        }

        PENDING_BOMBS.put(uuid,
                new PendingBomb(context.getDefenderRef(),
                        position,
                        now + Math.max(1L, delayMillis),
                        scaledDamage,
                        radius));
        spawnTriggerVfx(context.getDefenderRef(), position);

        return incoming;
    }

    @Override
    public void applyPassive(AugmentHooks.PassiveStatContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

        tickPendingBombs(context.getCommandBuffer(), context.getPlayerRef());
    }

    public static void tickPendingBombs(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> fallbackVisualRef) {
        processPendingBombs(commandBuffer, fallbackVisualRef);
    }

    private static void processPendingBombs(CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> fallbackVisualRef) {
        if (commandBuffer == null || PENDING_BOMBS.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingBomb> entry : PENDING_BOMBS.entrySet()) {
            UUID ownerUuid = entry.getKey();
            PendingBomb pending = entry.getValue();
            if (pending == null) {
                PENDING_BOMBS.remove(ownerUuid);
                continue;
            }

            if (now < pending.explodeAt) {
                continue;
            }

            if (fallbackVisualRef != null && pending.sourceRef != null
                    && fallbackVisualRef.getStore() != pending.sourceRef.getStore()) {
                continue;
            }

            if (!PENDING_BOMBS.remove(ownerUuid, pending)) {
                continue;
            }

            explode(commandBuffer, pending, fallbackVisualRef);
        }
    }

    private static void explode(CommandBuffer<EntityStore> commandBuffer,
            PendingBomb pending,
            Ref<EntityStore> fallbackVisualRef) {
        if (pending == null || commandBuffer == null || pending.position == null
                || pending.damage <= 0.0D || pending.radius <= 0.0D) {
            return;
        }

        Ref<EntityStore> sourceRef = EntityRefUtil.isUsable(pending.sourceRef) ? pending.sourceRef : null;
        float configuredDamage = (float) Math.max(0.0D, pending.damage);
        if (configuredDamage <= 0.0f) {
            return;
        }

        spawnExplosionParticles(commandBuffer, sourceRef, fallbackVisualRef, pending);
        HashSet<Integer> visitedEntityIds = new HashSet<>();

        for (Ref<EntityStore> targetRef : TargetUtil.getAllEntitiesInSphere(
                pending.position,
                pending.radius,
                commandBuffer)) {
            if (targetRef == null || !targetRef.isValid()) {
                continue;
            }
            if (!visitedEntityIds.add(targetRef.getIndex())) {
                continue;
            }
            if (sourceRef != null && targetRef.equals(sourceRef)) {
                continue;
            }

            if (!EntityRefUtil.isUsable(targetRef)) {
                continue;
            }

            EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    EntityStatMap.getComponentType());
            EntityStatValue targetHp = targetStats == null ? null : targetStats.get(DefaultEntityStatTypes.getHealth());
            if (targetStats == null || targetHp == null) {
                DamageSystems.executeDamage(targetRef,
                        commandBuffer,
                        PlayerCombatSystem.createAugmentProcDamage(sourceRef, configuredDamage));
                continue;
            }

            float currentHp = targetHp.get();
            if (currentHp <= 0f) {
                continue;
            }

            if (configuredDamage >= currentHp) {
                markBombKill(sourceRef, targetRef, commandBuffer, targetStats);
                continue;
            }

            float updatedHealth = Math.max(0.0f, currentHp - configuredDamage);
            targetStats.setStatValue(DefaultEntityStatTypes.getHealth(), updatedHealth);
        }
    }

    private static void spawnTriggerVfx(Ref<EntityStore> sourceRef, Vector3d position) {
        if (!EntityRefUtil.isUsable(sourceRef) || position == null) {
            return;
        }

        for (String particleSystemId : TRIGGER_VFX_IDS) {
            try {
                ParticleUtil.spawnParticleEffect(particleSystemId, position, sourceRef.getStore());
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void markBombKill(Ref<EntityStore> sourceRef,
            Ref<EntityStore> targetRef,
            CommandBuffer<EntityStore> commandBuffer,
            EntityStatMap targetStats) {
        if (targetRef == null || commandBuffer == null || targetStats == null) {
            return;
        }

        if (EntityRefUtil.tryGetComponent(commandBuffer, targetRef, DeathComponent.getComponentType()) == null) {
            Damage damage;
            if (EntityRefUtil.isUsable(sourceRef)) {
                try {
                    damage = new Damage(new Damage.EntitySource(sourceRef), DamageCause.PHYSICAL, Float.MAX_VALUE);
                } catch (IllegalStateException ignored) {
                    damage = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, Float.MAX_VALUE);
                }
            } else {
                damage = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, Float.MAX_VALUE);
            }
            DeathComponent.tryAddComponent(commandBuffer, targetRef, damage);
        }

        targetStats.setStatValue(DefaultEntityStatTypes.getHealth(), 0.0f);
    }

    private static void spawnExplosionParticles(CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> sourceRef,
            Ref<EntityStore> fallbackVisualRef,
            PendingBomb pending) {
        if (commandBuffer == null || pending == null || pending.position == null) {
            return;
        }

        Ref<EntityStore> visualSource = EntityRefUtil.isUsable(sourceRef) ? sourceRef
                : (EntityRefUtil.isUsable(fallbackVisualRef) ? fallbackVisualRef : null);
        if (visualSource == null) {
            return;
        }

        double broadcastRadius = Math.max(MIN_VISUAL_BROADCAST_RADIUS,
                pending.radius * VISUAL_BROADCAST_RADIUS_MULTIPLIER);
        List<Ref<EntityStore>> nearbyPlayers = collectNearbyPlayers(commandBuffer, pending.position, broadcastRadius);

        if (nearbyPlayers.isEmpty()) {
            nearbyPlayers.add(visualSource);
        } else if (!nearbyPlayers.contains(visualSource)) {
            nearbyPlayers.add(visualSource);
        }

        if (nearbyPlayers.isEmpty()) {
            return;
        }

        playExplosionSound(visualSource, pending.position);

        for (String particleSystemId : getPreferredExplosionParticleOrder(pending.radius)) {
            try {
                ParticleUtil.spawnParticleEffect(
                        particleSystemId,
                        pending.position,
                        visualSource,
                        nearbyPlayers,
                        commandBuffer);
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void playExplosionSound(Ref<EntityStore> sourceRef, Vector3d position) {
        if (sourceRef == null || !sourceRef.isValid() || position == null) {
            return;
        }

        for (String soundId : EXPLOSION_SFX_IDS) {
            int soundIndex = resolveSoundIndex(soundId);
            if (soundIndex == 0) {
                continue;
            }
            SoundUtil.playSoundEvent3d(null, soundIndex, position, sourceRef.getStore());
            return;
        }
    }

    private static int resolveSoundIndex(String id) {
        int index = SoundEvent.getAssetMap().getIndex(id);
        return index == Integer.MIN_VALUE ? 0 : index;
    }

    private static List<Ref<EntityStore>> collectNearbyPlayers(CommandBuffer<EntityStore> commandBuffer,
            Vector3d position,
            double radius) {
        List<Ref<EntityStore>> nearbyPlayers = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();

        for (Ref<EntityStore> targetRef : TargetUtil.getAllEntitiesInSphere(position, radius, commandBuffer)) {
            if (targetRef == null || !targetRef.isValid() || !seen.add(targetRef.getIndex())) {
                continue;
            }

            PlayerRef sourcePlayerRef = EntityRefUtil.tryGetComponent(commandBuffer,
                    targetRef,
                    PlayerRef.getComponentType());
            if (sourcePlayerRef == null || !sourcePlayerRef.isValid()) {
                continue;
            }
            nearbyPlayers.add(targetRef);
        }

        return nearbyPlayers;
    }

    private static String[] getPreferredExplosionParticleOrder(double explosionRadius) {
        return new String[] { EXPLOSION_PARTICLE_BIG, EXPLOSION_PARTICLE_MEDIUM, EXPLOSION_PARTICLE_SMALL };
    }
}
