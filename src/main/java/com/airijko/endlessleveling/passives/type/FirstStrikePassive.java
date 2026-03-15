package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.managers.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.FirstStrikeSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.function.BiConsumer;

/**
 * Encapsulates First Strike passive behavior and cooldown handling.
 */
public final class FirstStrikePassive {

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

    public float apply(PassiveRuntimeState runtimeState,
            PlayerRef playerRef,
            float currentDamage,
            BiConsumer<PlayerRef, String> messenger) {
        if (runtimeState == null || !settings.enabled() || currentDamage <= 0f) {
            return 0f;
        }

        double bonusPercent = Math.max(0.0D, settings.bonusPercent());
        double flatBonusDamage = Math.max(0.0D, settings.flatBonusDamage());
        if (bonusPercent <= 0.0D || flatBonusDamage <= 0.0D) {
            return 0f;
        }

        long now = System.currentTimeMillis();
        if (now < runtimeState.getFirstStrikeCooldownExpiresAt()) {
            return 0f;
        }

        float bonusDamage = (float) flatBonusDamage;
        if (bonusDamage <= 0f) {
            return 0f;
        }

        runtimeState.setFirstStrikeCooldownExpiresAt(now + settings.cooldownMillis());
        runtimeState.setFirstStrikeReadyNotified(false);

        if (messenger != null) {
            messenger.accept(playerRef,
                    String.format("First Strike triggered! +%.0f flat damage (+%.0f%%). Cooldown: %.0fs",
                            flatBonusDamage,
                            bonusPercent * 100.0D,
                            settings.cooldownMillis() / 1000.0D));
        }
        return bonusDamage;
    }

    public void suppressOnHit(PassiveRuntimeState runtimeState) {
        if (runtimeState == null || !settings.enabled() || settings.cooldownMillis() <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < runtimeState.getFirstStrikeCooldownExpiresAt()) {
            return;
        }

        runtimeState.setFirstStrikeCooldownExpiresAt(now + settings.cooldownMillis());
        runtimeState.setFirstStrikeReadyNotified(false);
    }
}
