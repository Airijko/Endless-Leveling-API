package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.FirstStrikeSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.function.BiConsumer;

/**
 * Encapsulates First Strike passive behavior and cooldown handling.
 */
public final class FirstStrikePassive {

    public record TriggerResult(float bonusDamage, double trueDamageBonus) {
        public static TriggerResult none() {
            return new TriggerResult(0.0f, 0.0D);
        }
    }

    private final FirstStrikeSettings settings;

    private FirstStrikePassive(FirstStrikeSettings settings) {
        this.settings = settings == null ? FirstStrikeSettings.disabled() : settings;
    }

    public static FirstStrikePassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new FirstStrikePassive(FirstStrikeSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public long cooldownMillis() {
        return settings.cooldownMillis();
    }

    public double hasteBonusPercent() {
        return Math.max(0.0D, settings.hasteBonusPercent());
    }

    public TriggerResult apply(PassiveRuntimeState runtimeState,
            PlayerRef playerRef,
            float currentDamage,
            BiConsumer<PlayerRef, String> messenger) {
        if (runtimeState == null || !settings.enabled() || currentDamage <= 0f) {
            return TriggerResult.none();
        }
        long now = System.currentTimeMillis();
        runtimeState.setFirstStrikeHasteActiveUntil(now + settings.hasteDurationMillis());
        return TriggerResult.none();
    }

    public void resetCooldownOnKill(PassiveRuntimeState runtimeState) {
        if (runtimeState == null || !settings.enabled() || !settings.resetOnKill()) {
            return;
        }
        runtimeState.setFirstStrikeCooldownExpiresAt(0L);
        runtimeState.setFirstStrikeKillResetReady(true);
        runtimeState.setFirstStrikeReadyNotified(true);
    }
}
