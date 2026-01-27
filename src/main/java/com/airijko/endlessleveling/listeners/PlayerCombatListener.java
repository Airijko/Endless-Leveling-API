package com.airijko.endlessleveling.listeners;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.enums.PassiveType;
import com.airijko.endlessleveling.managers.PassiveManager;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.airijko.endlessleveling.managers.SkillManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Listens for player-inflicted damage and applies EndlessLeveling combat logic.
 */
public class PlayerCombatListener extends DamageEventSystem {
    private final PlayerDataManager playerDataManager;
    private final SkillManager skillManager;
    private final PassiveManager passiveManager;

    public PlayerCombatListener(@Nonnull PlayerDataManager playerDataManager,
            @Nonnull SkillManager skillManager,
            @Nonnull PassiveManager passiveManager) {
        this.playerDataManager = playerDataManager;
        this.skillManager = skillManager;
        this.passiveManager = passiveManager;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        // Run before vanilla damage is applied
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        // Only notify if the source is a player
        if (damage.getSource() instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            PlayerRef attackerPlayer = commandBuffer.getComponent(attackerRef, PlayerRef.getComponentType());
            if (attackerPlayer != null && attackerPlayer.isValid()) {
                PlayerData playerData = playerDataManager.get(attackerPlayer.getUuid());
                if (playerData != null) {
                    // Calculate strength bonus using SkillManager
                    float baseAmount = skillManager.applyStrengthModifier(damage.getAmount(), playerData);
                    // Apply critical hit system
                    SkillManager.CritResult critResult = skillManager.applyCriticalHit(playerData, baseAmount);
                    damage.setAmount(critResult.damage);
                    applyLifeSteal(attackerRef, commandBuffer, playerData, critResult.damage);
                    passiveManager.markCombat(playerData.getUuid());
                }
            }
        }
    }

    private void applyLifeSteal(@Nonnull Ref<EntityStore> attackerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlayerData playerData,
            float damageDealt) {
        if (passiveManager == null || damageDealt <= 0) {
            return;
        }

        PassiveManager.PassiveSnapshot snapshot = passiveManager.getSnapshot(playerData, PassiveType.LIFE_STEAL);
        if (snapshot == null || !snapshot.isUnlocked() || snapshot.value() <= 0) {
            return;
        }

        double healPercent = snapshot.value() / 100.0D;
        double healAmount = damageDealt * healPercent;
        if (healAmount <= 0) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();
        float maxHealth = healthStat.getMax();
        float updatedHealth = (float) Math.min(maxHealth, currentHealth + healAmount);
        if (updatedHealth > currentHealth) {
            statMap.setStatValue(DefaultEntityStatTypes.getHealth(), updatedHealth);
        }
    }
}