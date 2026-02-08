package com.airijko.endlessleveling.listeners;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.enums.PassiveType;
import com.airijko.endlessleveling.managers.PassiveManager;
import com.airijko.endlessleveling.managers.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.airijko.endlessleveling.managers.SkillManager;
import com.airijko.endlessleveling.passives.ArchetypePassiveManager;
import com.airijko.endlessleveling.passives.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.BerzerkerSettings;
import com.airijko.endlessleveling.passives.ExecutionerSettings;
import com.airijko.endlessleveling.passives.FirstStrikeSettings;
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
import com.hypixel.hytale.server.core.Message;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Listens for player-inflicted damage and applies EndlessLeveling combat logic.
 */
public class PlayerCombatListener extends DamageEventSystem {
    private final PlayerDataManager playerDataManager;
    private final SkillManager skillManager;
    private final PassiveManager passiveManager;
    private final ArchetypePassiveManager archetypePassiveManager;

    public PlayerCombatListener(@Nonnull PlayerDataManager playerDataManager,
            @Nonnull SkillManager skillManager,
            @Nonnull PassiveManager passiveManager,
            ArchetypePassiveManager archetypePassiveManager) {
        this.playerDataManager = playerDataManager;
        this.skillManager = skillManager;
        this.passiveManager = passiveManager;
        this.archetypePassiveManager = archetypePassiveManager;
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
            Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
            PlayerRef attackerPlayer = commandBuffer.getComponent(attackerRef, PlayerRef.getComponentType());
            if (attackerPlayer != null && attackerPlayer.isValid()) {
                PlayerData playerData = playerDataManager.get(attackerPlayer.getUuid());
                if (playerData != null) {
                    PassiveRuntimeState runtimeState = passiveManager != null
                            ? passiveManager.getRuntimeState(playerData.getUuid())
                            : null;
                    ArchetypePassiveSnapshot archetypeSnapshot = archetypePassiveManager != null
                            ? archetypePassiveManager.getSnapshot(playerData)
                            : ArchetypePassiveSnapshot.empty();
                    FirstStrikeSettings firstStrikeSettings = FirstStrikeSettings.fromSnapshot(archetypeSnapshot);
                    BerzerkerSettings berzerkerSettings = BerzerkerSettings.fromSnapshot(archetypeSnapshot);
                    ExecutionerSettings executionerSettings = ExecutionerSettings.fromSnapshot(archetypeSnapshot);

                    // Calculate strength bonus using SkillManager
                    float baseAmount = skillManager.applyStrengthModifier(damage.getAmount(), playerData);
                    // Apply critical hit system
                    SkillManager.CritResult critResult = skillManager.applyCriticalHit(playerData, baseAmount);
                    float finalDamage = critResult.damage;
                    damage.setAmount(finalDamage);
                    applyLifeSteal(attackerRef, commandBuffer, playerData, finalDamage);

                    if (runtimeState != null && firstStrikeSettings.enabled()) {
                        float bonusDamage = applyFirstStrike(runtimeState, firstStrikeSettings, attackerPlayer,
                                finalDamage);
                        if (bonusDamage > 0) {
                            finalDamage += bonusDamage;
                            damage.setAmount(finalDamage);
                            applyLifeSteal(attackerRef, commandBuffer, playerData, bonusDamage);
                        }
                    }
                    float beforeArchetypeDamage = finalDamage;
                    finalDamage = applyBerzerkerBonus(berzerkerSettings, attackerRef, commandBuffer, finalDamage);
                    finalDamage = applyExecutionerBonus(executionerSettings, targetRef, commandBuffer, finalDamage);
                    float bonusFromArchetypes = finalDamage - beforeArchetypeDamage;
                    if (bonusFromArchetypes > 0f) {
                        applyLifeSteal(attackerRef, commandBuffer, playerData, bonusFromArchetypes);
                    }
                    damage.setAmount(finalDamage);

                    passiveManager.markCombat(playerData.getUuid());
                }
            }
        }
    }

    private float applyFirstStrike(@Nonnull PassiveRuntimeState runtimeState,
            @Nonnull FirstStrikeSettings settings,
            PlayerRef playerRef,
            float currentDamage) {
        if (!settings.enabled() || currentDamage <= 0) {
            return 0f;
        }

        double bonusPercent = Math.max(0.0D, settings.bonusPercent());
        if (bonusPercent <= 0) {
            return 0f;
        }

        long now = System.currentTimeMillis();
        if (now < runtimeState.getFirstStrikeCooldownExpiresAt()) {
            return 0f;
        }

        float bonusDamage = (float) (currentDamage * bonusPercent);
        if (bonusDamage <= 0) {
            return 0f;
        }

        runtimeState.setFirstStrikeCooldownExpiresAt(now + settings.cooldownMillis());
        runtimeState.setFirstStrikeReadyNotified(false);
        sendPassiveMessage(playerRef,
                String.format("First Strike triggered! Cooldown: %.0fs",
                        settings.cooldownMillis() / 1000.0D));
        return bonusDamage;
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

    private float applyBerzerkerBonus(@Nonnull BerzerkerSettings settings,
            Ref<EntityStore> attackerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            float currentDamage) {
        if (!settings.enabled() || attackerRef == null || currentDamage <= 0f) {
            return currentDamage;
        }
        EntityStatMap statMap = commandBuffer.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return currentDamage;
        }
        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return currentDamage;
        }
        float max = healthStat.getMax();
        float current = healthStat.get();
        if (max <= 0f || current <= 0f) {
            return currentDamage;
        }
        float ratio = current / max;
        double totalBonus = 0.0D;
        for (BerzerkerSettings.Entry entry : settings.entries()) {
            if (ratio <= entry.thresholdPercent()) {
                totalBonus += Math.max(0.0D, entry.bonusPercent());
            }
        }
        if (totalBonus <= 0.0D) {
            return currentDamage;
        }
        float bonusDamage = (float) (currentDamage * totalBonus);
        return bonusDamage > 0f ? currentDamage + bonusDamage : currentDamage;
    }

    private float applyExecutionerBonus(@Nonnull ExecutionerSettings settings,
            Ref<EntityStore> targetRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            float currentDamage) {
        if (!settings.enabled() || targetRef == null || currentDamage <= 0f) {
            return currentDamage;
        }
        EntityStatMap statMap = commandBuffer.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return currentDamage;
        }
        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return currentDamage;
        }
        float current = healthStat.get();
        float max = healthStat.getMax();
        if (max <= 0f || current <= 0f) {
            return currentDamage;
        }
        float predicted = Math.max(0f, current - currentDamage);
        double bonusPercent = 0.0D;
        boolean execute = false;
        for (ExecutionerSettings.Entry entry : settings.entries()) {
            double threshold = entry.thresholdPercent();
            if (threshold <= 0.0D) {
                continue;
            }
            float thresholdHealth = (float) (max * threshold);
            if (current <= thresholdHealth || predicted <= thresholdHealth) {
                if (entry.isExecute()) {
                    execute = true;
                    break;
                }
                bonusPercent += Math.max(0.0D, entry.bonusPercent());
            }
        }
        if (execute) {
            return current;
        }
        if (bonusPercent <= 0.0D) {
            return currentDamage;
        }
        float bonusDamage = (float) (currentDamage * bonusPercent);
        return bonusDamage > 0f ? currentDamage + bonusDamage : currentDamage;
    }

    private void sendPassiveMessage(PlayerRef playerRef, String text) {
        if (playerRef == null || !playerRef.isValid() || text == null || text.isBlank()) {
            return;
        }
        playerRef.sendMessage(Message.raw(text).color("#4fd7f7"));
    }
}