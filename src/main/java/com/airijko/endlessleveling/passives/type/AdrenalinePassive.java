package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.managers.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.AdrenalineSettings;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.function.BiConsumer;

/**
 * Handles Adrenaline trigger and stamina restore-over-time behavior.
 */
public final class AdrenalinePassive {

    private final AdrenalineSettings settings;

    private AdrenalinePassive(AdrenalineSettings settings) {
        this.settings = settings == null ? AdrenalineSettings.disabled() : settings;
    }

    public static AdrenalinePassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new AdrenalinePassive(AdrenalineSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public void apply(PlayerRef playerRef,
            EntityStatMap statMap,
            float deltaSeconds,
            PassiveRuntimeState runtimeState,
            BiConsumer<PlayerRef, String> messenger) {
        if (statMap == null || runtimeState == null || deltaSeconds <= 0f) {
            return;
        }

        if (!settings.enabled()) {
            clearState(runtimeState);
            return;
        }

        EntityStatValue staminaStat = statMap.get(DefaultEntityStatTypes.getStamina());
        if (staminaStat == null) {
            return;
        }

        float max = staminaStat.getMax();
        if (max <= 0f) {
            return;
        }

        long now = System.currentTimeMillis();
        if (runtimeState.getAdrenalineActiveUntil() > 0L && now >= runtimeState.getAdrenalineActiveUntil()) {
            clearState(runtimeState);
        }

        double perSecond = runtimeState.getAdrenalineRestorePerSecond();
        double remaining = runtimeState.getAdrenalineRestoreRemaining();
        boolean effectActive = perSecond > 0.0D
                && remaining > 0.0D
                && runtimeState.getAdrenalineActiveUntil() > now;

        if (effectActive) {
            float current = staminaStat.get();
            if (current < max) {
                double potential = perSecond * deltaSeconds;
                double allowed = Math.min(remaining, potential);
                if (allowed > 0.0D) {
                    float applied = (float) Math.min(max - current, allowed);
                    if (applied > 0f) {
                        statMap.setStatValue(DefaultEntityStatTypes.getStamina(), current + applied);
                        runtimeState.setAdrenalineRestoreRemaining(remaining - applied);
                    }
                }
            }

            if (runtimeState.getAdrenalineRestoreRemaining() <= 0.0001D
                    || staminaStat.get() >= staminaStat.getMax()) {
                clearState(runtimeState);
            }
            return;
        }

        float ratio = staminaStat.get() / max;
        if (ratio > settings.thresholdPercent()) {
            return;
        }

        if (now < runtimeState.getAdrenalineCooldownExpiresAt()) {
            return;
        }

        double restorePercent = Math.max(0.0D, settings.restorePercent());
        if (restorePercent <= 0.0D) {
            return;
        }

        double totalRestore = Math.min(max, max * restorePercent);
        if (totalRestore <= 0.0D) {
            return;
        }

        double durationSeconds = Math.max(0.1D, settings.durationSeconds());
        double perSecondRestore = totalRestore / durationSeconds;

        runtimeState.setAdrenalineRestorePerSecond(perSecondRestore);
        runtimeState.setAdrenalineRestoreRemaining(totalRestore);
        runtimeState.setAdrenalineActiveUntil(now + settings.durationMillis());
        runtimeState.setAdrenalineCooldownExpiresAt(now + settings.cooldownMillis());
        runtimeState.setAdrenalineReadyNotified(false);

        if (messenger != null) {
            double percentDisplay = Math.max(0.0D, restorePercent) * 100.0D;
            messenger.accept(playerRef,
                    String.format("Adrenaline triggered! Restoring %.0f%% stamina over %.0fs.",
                            percentDisplay,
                            Math.max(0.0D, settings.durationSeconds())));
        }
    }

    private void clearState(PassiveRuntimeState runtimeState) {
        runtimeState.setAdrenalineRestorePerSecond(0.0D);
        runtimeState.setAdrenalineRestoreRemaining(0.0D);
        runtimeState.setAdrenalineActiveUntil(0L);
    }
}
