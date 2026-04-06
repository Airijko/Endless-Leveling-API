package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diagnostic probe that runs after ApplyDamage to inspect final HP/death state.
 *
 * Only flags genuinely anomalous situations — an entity whose HP was at zero
 * snapping back to full max without a death/respawn cycle.  Normal HP increases
 * between damage events (healing, regen, passive-health reconciliation) are
 * expected and not flagged.
 */
public class PlayerCombatPostApplyProbeSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private final Map<Long, Float> lastObservedHpByTarget = new ConcurrentHashMap<>();
    private final MobLevelingManager mobLevelingManager;

    public PlayerCombatPostApplyProbeSystem(MobLevelingManager mobLevelingManager) {
        this.mobLevelingManager = mobLevelingManager;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (!EntityRefUtil.isUsable(attackerRef)) {
            return;
        }

        PlayerRef attackerPlayer = EntityRefUtil.tryGetComponent(commandBuffer, attackerRef,
                PlayerRef.getComponentType());
        if (attackerPlayer == null || !attackerPlayer.isValid()) {
            return;
        }

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        PlayerRef targetPlayer = EntityRefUtil.tryGetComponent(commandBuffer, targetRef, PlayerRef.getComponentType());
        if (targetPlayer != null && targetPlayer.isValid()) {
            return;
        }

        float hp = Float.NaN;
        float max = Float.NaN;
        EntityStatMap targetStats = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                EntityStatMap.getComponentType());
        if (targetStats != null) {
            EntityStatValue targetHp = targetStats.get(DefaultEntityStatTypes.getHealth());
            if (targetHp != null) {
                hp = targetHp.get();
                max = targetHp.getMax();
            }
        }

        boolean dead = EntityRefUtil.tryGetComponent(commandBuffer, targetRef,
                DeathComponent.getComponentType()) != null;
        int targetId = targetRef.getIndex();
        long targetKey = resolveTrackingKey(targetRef, commandBuffer);

        if (dead) {
            lastObservedHpByTarget.remove(targetKey);
            return;
        }

        Float previousObservedHp = lastObservedHpByTarget.get(targetKey);
        if (Float.isFinite(hp)) {
            lastObservedHpByTarget.put(targetKey, hp);
        }

        // Only flag the truly anomalous case: HP was at zero (should be dead) but
        // jumped back to full max without a death cycle.
        if (previousObservedHp != null
                && Float.isFinite(previousObservedHp) && Float.isFinite(hp) && Float.isFinite(max)
                && previousObservedHp <= 0.0001f
                && max > 0.0f
                && hp >= max - 0.001f) {
            LOGGER.atWarning().log(
                    "HpResetAfterZeroWithoutDeath target=%d previousHp=%.3f currentHp=%.3f max=%.3f",
                    targetId, previousObservedHp, hp, max);
        }

        int mobLevel = mobLevelingManager != null ? mobLevelingManager.resolveMobLevel(targetRef, commandBuffer) : 1;
        LOGGER.atFine().log(
                "PlayerHitPostApply target=%d attacker=%d finalDamage=%.3f hp=%.3f max=%.3f mobLevel=%d",
                targetId, attackerRef.getIndex(), damage.getAmount(), hp, max, mobLevel);
    }

    private long resolveTrackingKey(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null) {
            return -1L;
        }

        Store<EntityStore> refStore = ref.getStore();
        UUIDComponent uuidComponent = EntityRefUtil.tryGetComponent(commandBuffer, ref,
                UUIDComponent.getComponentType());

        if (uuidComponent == null && refStore != null) {
            uuidComponent = EntityRefUtil.tryGetComponent(refStore, ref, UUIDComponent.getComponentType());
        }

        if (uuidComponent != null) {
            try {
                UUID uuid = uuidComponent.getUuid();
                if (uuid != null) {
                    return uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 1);
                }
            } catch (Throwable ignored) {
            }
        }

        long storePart = refStore == null ? 0L : Integer.toUnsignedLong(System.identityHashCode(refStore));
        long entityPart = Integer.toUnsignedLong(ref.getIndex());
        return (storePart << 32) | entityPart;
    }
}
