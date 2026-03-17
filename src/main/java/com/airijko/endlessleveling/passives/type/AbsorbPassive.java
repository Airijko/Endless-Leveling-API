package com.airijko.endlessleveling.passives.type;

import com.airijko.endlessleveling.passives.PassiveManager.PassiveRuntimeState;
import com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSnapshot;
import com.airijko.endlessleveling.passives.settings.AbsorbSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.function.BiConsumer;

/**
 * Applies Absorb passive mitigation and cooldown handling.
 */
public final class AbsorbPassive {

    private final AbsorbSettings settings;

    private AbsorbPassive(AbsorbSettings settings) {
        this.settings = settings == null ? AbsorbSettings.disabled() : settings;
    }

    public static AbsorbPassive fromSnapshot(ArchetypePassiveSnapshot snapshot) {
        return new AbsorbPassive(AbsorbSettings.fromSnapshot(snapshot));
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public float apply(PassiveRuntimeState runtimeState,
            PlayerRef defenderPlayer,
            float incomingDamage,
            BiConsumer<PlayerRef, String> messenger) {
        if (runtimeState == null || !settings.enabled() || incomingDamage <= 0f) {
            return incomingDamage;
        }

        long now = System.currentTimeMillis();
        if (now < runtimeState.getAbsorbCooldownExpiresAt()) {
            return incomingDamage;
        }

        double reduction = Math.max(0.0D, Math.min(1.0D, settings.reductionPercent()));
        if (reduction <= 0.0D) {
            return incomingDamage;
        }

        runtimeState.setAbsorbCooldownExpiresAt(now + settings.cooldownMillis());
        float reduced = (float) (incomingDamage * (1.0D - reduction));

        if (messenger != null) {
            messenger.accept(defenderPlayer,
                    String.format("Absorb triggered! Reduced incoming damage by %.0f%%. Cooldown: %.0fs",
                            reduction * 100.0D,
                            settings.cooldownMillis() / 1000.0D));
        }
        return Math.max(0.0f, reduced);
    }
}
