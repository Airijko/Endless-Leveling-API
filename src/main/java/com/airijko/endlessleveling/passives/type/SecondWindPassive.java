package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.augments.types.OverhealAugment;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.managers.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.races.RacePassiveDefinition;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Handles Second Wind trigger configuration and healing-over-time state.
 */
public final class SecondWindPassive {

    private static final double DEFAULT_THRESHOLD = 0.2D;
    private static final double DEFAULT_DURATION = 5.0D;
    private static final double DEFAULT_COOLDOWN = 60.0D;

    private final boolean enabled;
    private final double healPercent;
    private final double thresholdPercent;
    private final double durationSeconds;
    private final double cooldownSeconds;

    private SecondWindPassive(boolean enabled,
            double healPercent,
            double thresholdPercent,
            double durationSeconds,
            double cooldownSeconds) {
        this.enabled = enabled;
        this.healPercent = healPercent;
        this.thresholdPercent = thresholdPercent;
        this.durationSeconds = durationSeconds;
        this.cooldownSeconds = cooldownSeconds;
    }

    public static SecondWindPassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        if (snapshot == null) {
            return disabled();
        }

        double healPercent = Math.max(0.0D, snapshot.getValue(ArchetypePassiveType.SECOND_WIND));
        if (healPercent <= 0.0D) {
            return disabled();
        }

        List<RacePassiveDefinition> definitions = snapshot.getDefinitions(ArchetypePassiveType.SECOND_WIND);
        double thresholdSum = 0.0D;
        int thresholdSources = 0;
        double durationSum = 0.0D;
        int durationSources = 0;
        double cooldownSum = 0.0D;
        int cooldownSources = 0;

        for (RacePassiveDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            Map<String, Object> props = definition.properties();
            if (props == null || props.isEmpty()) {
                continue;
            }

            double thresholdValue = parsePositiveDouble(props.get("threshold"));
            if (thresholdValue > 0.0D) {
                thresholdSum += thresholdValue;
                thresholdSources++;
            }

            double durationValue = parsePositiveDouble(props.get("duration"));
            if (durationValue > 0.0D) {
                durationSum += durationValue;
                durationSources++;
            }

            double cooldownValue = parsePositiveDouble(props.get("cooldown"));
            if (cooldownValue > 0.0D) {
                cooldownSum += cooldownValue;
                cooldownSources++;
            }
        }

        double threshold = thresholdSources > 0 ? thresholdSum / thresholdSources : DEFAULT_THRESHOLD;
        double duration = durationSources > 0 ? durationSum / durationSources : DEFAULT_DURATION;
        double cooldown = cooldownSources > 0 ? cooldownSum / cooldownSources : DEFAULT_COOLDOWN;

        return new SecondWindPassive(true, healPercent, threshold, duration, cooldown);
    }

    public static SecondWindPassive disabled() {
        return new SecondWindPassive(false, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public boolean enabled() {
        return enabled;
    }

    public float tryTrigger(PassiveRuntimeState runtimeState,
            PlayerRef defenderPlayer,
            EntityStatMap statMap,
            float incomingDamage,
            BiConsumer<PlayerRef, String> messenger) {
        if (runtimeState == null || !enabled || statMap == null || incomingDamage <= 0f) {
            return incomingDamage;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return incomingDamage;
        }

        float maxHealth = healthStat.getMax();
        float currentHealth = healthStat.get();
        if (maxHealth <= 0f || currentHealth <= 0f) {
            return incomingDamage;
        }

        float predictedHealth = Math.max(0f, currentHealth - incomingDamage);
        if (predictedHealth <= 0f) {
            return incomingDamage;
        }

        float thresholdHealth = (float) (maxHealth * thresholdPercent);
        if (thresholdHealth <= 0f || predictedHealth > thresholdHealth) {
            return incomingDamage;
        }

        long now = System.currentTimeMillis();
        if (now < runtimeState.getSecondWindCooldownExpiresAt() || now < runtimeState.getSecondWindActiveUntil()) {
            return incomingDamage;
        }

        float healAmount = (float) Math.max(0.0D, maxHealth * healPercent);
        if (healAmount <= 0f) {
            return incomingDamage;
        }

        double safeDurationSeconds = Math.max(0.1D, durationSeconds);
        double totalHeal = Math.min(healAmount, maxHealth);
        double perSecond = totalHeal / safeDurationSeconds;
        runtimeState.setSecondWindHealPerSecond(perSecond);
        runtimeState.setSecondWindHealRemaining(totalHeal);
        runtimeState.setSecondWindCooldownExpiresAt(now + cooldownMillis());
        runtimeState.setSecondWindActiveUntil(now + durationMillis());
        runtimeState.setSecondWindReadyNotified(false);

        if (messenger != null) {
            messenger.accept(defenderPlayer,
                    String.format("Second Wind triggered! Healing %.0f%% HP over %.0fs",
                            healPercent * 100.0D,
                            safeDurationSeconds));
        }
        return incomingDamage;
    }

    public static void tickHealing(EntityStatMap statMap,
            PassiveRuntimeState runtimeState,
            float deltaSeconds) {
        if (runtimeState == null || statMap == null || deltaSeconds <= 0f) {
            return;
        }

        double perSecond = runtimeState.getSecondWindHealPerSecond();
        double remaining = runtimeState.getSecondWindHealRemaining();
        if (perSecond <= 0.0D || remaining <= 0.0D) {
            return;
        }

        long activeUntil = runtimeState.getSecondWindActiveUntil();
        if (activeUntil > 0L && System.currentTimeMillis() > activeUntil) {
            runtimeState.setSecondWindHealPerSecond(0.0D);
            runtimeState.setSecondWindHealRemaining(0.0D);
            return;
        }

        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthStat == null) {
            return;
        }

        float current = healthStat.get();
        float max = healthStat.getMax();
        if (current >= max) {
            return;
        }

        double potential = perSecond * deltaSeconds;
        if (potential <= 0.0D) {
            return;
        }

        double allowed = Math.min(remaining, potential);
        if (current >= max) {
            if (allowed > 0.0D) {
                OverhealAugment.recordOverhealOverflow(statMap, allowed);
            }
            return;
        }

        float applied = (float) Math.min(max - current, allowed);
        double overflow = Math.max(0.0D, allowed - applied);
        if (overflow > 0.0D) {
            OverhealAugment.recordOverhealOverflow(statMap, overflow);
        }
        if (applied <= 0f) {
            return;
        }

        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), current + applied);
        runtimeState.setSecondWindHealRemaining(remaining - applied);
        if (runtimeState.getSecondWindHealRemaining() <= 0.0001D) {
            runtimeState.setSecondWindHealPerSecond(0.0D);
            runtimeState.setSecondWindHealRemaining(0.0D);
        }
    }

    public long durationMillis() {
        return (long) Math.max(0L, Math.round(durationSeconds * 1000.0D));
    }

    public long cooldownMillis() {
        return (long) Math.max(0L, Math.round(cooldownSeconds * 1000.0D));
    }

    private static double parsePositiveDouble(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0.0D ? value : 0.0D;
        }
        if (raw instanceof String string) {
            try {
                double parsed = Double.parseDouble(string.trim());
                return parsed > 0.0D ? parsed : 0.0D;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0D;
    }
}
