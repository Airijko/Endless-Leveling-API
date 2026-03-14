package com.airijko.endlessleveling.ui;

import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager;
import com.airijko.endlessleveling.augments.AugmentValueReader;
import com.airijko.endlessleveling.augments.types.ConquerorAugment;
import com.airijko.endlessleveling.augments.types.EndurePainAugment;
import com.airijko.endlessleveling.augments.types.FortressAugment;
import com.airijko.endlessleveling.augments.types.FrozenDomainAugment;
import com.airijko.endlessleveling.augments.types.OverhealAugment;
import com.airijko.endlessleveling.augments.types.PredatorAugment;
import com.airijko.endlessleveling.augments.types.ProtectiveBubbleAugment;
import com.airijko.endlessleveling.augments.types.RagingMomentumAugment;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AugmentHudOverlayController {

    private static final double EPSILON = 0.0001D;
    private static final double MIN_VISIBLE_BAR_PROGRESS = 1.0D / 634.0D;
    private static final List<String> DURATION_PRIORITY = List.of(
            RagingMomentumAugment.ID,
            PredatorAugment.ID,
            ConquerorAugment.ID,
            FrozenDomainAugment.ID,
            EndurePainAugment.ID);
    private static final List<String> SHIELD_PRIORITY = List.of(
            ProtectiveBubbleAugment.ID,
            FortressAugment.ID,
            OverhealAugment.ID);

    private final AugmentManager augmentManager;
    private final AugmentRuntimeManager runtimeManager;
    private final Map<String, Long> durationCache = new ConcurrentHashMap<>();
    private final Map<String, Double> overhealShieldPercentCache = new ConcurrentHashMap<>();

    public AugmentHudOverlayController(AugmentManager augmentManager, AugmentRuntimeManager runtimeManager) {
        this.augmentManager = augmentManager;
        this.runtimeManager = runtimeManager;
    }

    public HudOverlayState resolve(@Nonnull PlayerRef playerRef) {
        if (playerRef == null || !playerRef.isValid() || runtimeManager == null) {
            return HudOverlayState.hidden();
        }

        var uuid = playerRef.getUuid();
        if (uuid == null) {
            return HudOverlayState.hidden();
        }

        long now = System.currentTimeMillis();
        AugmentRuntimeManager.AugmentRuntimeState runtimeState = runtimeManager.getRuntimeState(uuid);
        EntityStatMap statMap = resolveStatMap(playerRef);

        return new HudOverlayState(resolveDurationBar(runtimeState, now), resolveShieldBar(runtimeState, statMap, now));
    }

    private BarState resolveDurationBar(AugmentRuntimeManager.AugmentRuntimeState runtimeState, long now) {
        if (runtimeState == null) {
            return BarState.hidden();
        }

        for (String augmentId : DURATION_PRIORITY) {
            AugmentRuntimeManager.AugmentState state = runtimeState.getState(augmentId);
            if (!isTimedStateActive(state, now)) {
                continue;
            }

            long totalDuration = resolveConfiguredDurationMillis(augmentId);
            if (totalDuration <= 0L) {
                continue;
            }

            long remaining = Math.max(0L, state.getExpiresAt() - now);
            double progress = clamp01(remaining / (double) totalDuration);
            return new BarState(resolveDisplayName(augmentId), progress, true);
        }

        return BarState.hidden();
    }

    private BarState resolveShieldBar(AugmentRuntimeManager.AugmentRuntimeState runtimeState,
            EntityStatMap statMap,
            long now) {
        if (runtimeState == null) {
            return BarState.hidden();
        }

        for (String augmentId : SHIELD_PRIORITY) {
            BarState candidate = switch (augmentId) {
                case ProtectiveBubbleAugment.ID -> resolveProtectiveBubbleBar(runtimeState, now);
                case FortressAugment.ID -> resolveFortressShieldBar(runtimeState, now);
                case OverhealAugment.ID -> resolveOverhealShieldBar(runtimeState, statMap, now);
                default -> BarState.hidden();
            };
            if (candidate.visible()) {
                return candidate;
            }
        }

        return BarState.hidden();
    }

    private BarState resolveProtectiveBubbleBar(AugmentRuntimeManager.AugmentRuntimeState runtimeState, long now) {
        AugmentRuntimeManager.AugmentState state = runtimeState.getState(ProtectiveBubbleAugment.ID);
        if (state == null || state.getStacks() <= 0 || state.getExpiresAt() <= now) {
            return BarState.hidden();
        }

        return new BarState(resolveDisplayName(ProtectiveBubbleAugment.ID), 1.0D, true);
    }

    private BarState resolveFortressShieldBar(AugmentRuntimeManager.AugmentRuntimeState runtimeState, long now) {
        AugmentRuntimeManager.AugmentState state = runtimeState.getState(FortressAugment.ID);
        if (state == null || state.getStacks() <= 0 || state.getExpiresAt() <= now) {
            return BarState.hidden();
        }

        long totalDuration = resolveConfiguredShieldDurationMillis(FortressAugment.ID);
        if (totalDuration <= 0L) {
            totalDuration = Math.max(1L, state.getExpiresAt() - now);
        }

        long remaining = Math.max(0L, state.getExpiresAt() - now);
        return new BarState(resolveDisplayName(FortressAugment.ID), clamp01(remaining / (double) totalDuration), true);
    }

    private BarState resolveOverhealShieldBar(AugmentRuntimeManager.AugmentRuntimeState runtimeState,
            EntityStatMap statMap,
            long now) {
        AugmentRuntimeManager.AugmentState state = runtimeState.getState(OverhealAugment.ID);
        if (state == null || state.getStoredValue() <= EPSILON || state.getExpiresAt() <= now) {
            return BarState.hidden();
        }

        double progress = 0.0D;
        float maxHealth = statMap == null ? 0.0F
                : com.airijko.endlessleveling.augments.AugmentUtils.getMaxHealth(statMap);
        double shieldPercent = resolveOverhealShieldPercent();
        if (maxHealth > 0.0F && shieldPercent > 0.0D) {
            double maxShieldValue = maxHealth * shieldPercent;
            if (maxShieldValue > EPSILON) {
                progress = clamp01(state.getStoredValue() / maxShieldValue);
            }
        }

        if (!isVisiblyFilled(progress)) {
            return BarState.hidden();
        }

        return new BarState("Overheal Shield", progress, true);
    }

    private boolean isTimedStateActive(AugmentRuntimeManager.AugmentState state, long now) {
        return state != null
                && state.getExpiresAt() > now
                && (state.getStacks() > 0 || state.getStoredValue() > EPSILON);
    }

    private long resolveConfiguredDurationMillis(String augmentId) {
        return durationCache.computeIfAbsent(augmentId, this::loadDurationMillis);
    }

    private long resolveConfiguredShieldDurationMillis(String augmentId) {
        AugmentDefinition definition = augmentManager == null ? null : augmentManager.getAugment(augmentId);
        if (definition == null) {
            return 0L;
        }

        Map<String, Object> passives = definition.getPassives();
        if (OverhealAugment.ID.equals(augmentId)) {
            Map<String, Object> shield = AugmentValueReader.getMap(passives, "overheal_shield");
            return secondsToMillis(AugmentValueReader.getDouble(shield,
                    "decay_duration",
                    AugmentValueReader.getDouble(shield, "duration", 0.0D)));
        }
        if (ProtectiveBubbleAugment.ID.equals(augmentId)) {
            Map<String, Object> bubble = AugmentValueReader.getMap(passives, "immunity_bubble");
            return secondsToMillis(AugmentValueReader.getDouble(bubble, "immunity_window", 0.0D));
        }
        if (FortressAugment.ID.equals(augmentId)) {
            Map<String, Object> shield = AugmentValueReader.getMap(passives, "shield_phase");
            return secondsToMillis(AugmentValueReader.getDouble(shield, "duration", 0.0D));
        }
        return 0L;
    }

    private long loadDurationMillis(String augmentId) {
        AugmentDefinition definition = augmentManager == null ? null : augmentManager.getAugment(augmentId);
        if (definition == null) {
            return 0L;
        }

        Map<String, Object> passives = definition.getPassives();
        if (passives == null || passives.isEmpty()) {
            return 0L;
        }

        long nestedDuration = extractNestedDurationMillis(passives);
        if (nestedDuration > 0L) {
            return nestedDuration;
        }

        return switch (augmentId) {
            case OverhealAugment.ID, ProtectiveBubbleAugment.ID, FortressAugment.ID ->
                resolveConfiguredShieldDurationMillis(
                        augmentId);
            default -> 0L;
        };
    }

    private long extractNestedDurationMillis(Map<String, Object> section) {
        if (section == null || section.isEmpty()) {
            return 0L;
        }

        long duration = secondsToMillis(AugmentValueReader.getDouble(section, "duration_per_stack", 0.0D));
        if (duration > 0L) {
            return duration;
        }

        duration = secondsToMillis(AugmentValueReader.getDouble(section, "duration", 0.0D));
        if (duration > 0L) {
            return duration;
        }

        duration = secondsToMillis(AugmentValueReader.getDouble(section, "effect_duration", 0.0D));
        if (duration > 0L) {
            return duration;
        }

        duration = secondsToMillis(AugmentValueReader.getDouble(section, "immunity_window", 0.0D));
        if (duration > 0L) {
            return duration;
        }

        for (Object value : section.values()) {
            if (value instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) raw;
                duration = extractNestedDurationMillis(nested);
                if (duration > 0L) {
                    return duration;
                }
            }
        }

        return 0L;
    }

    private double resolveOverhealShieldPercent() {
        return overhealShieldPercentCache.computeIfAbsent(OverhealAugment.ID, id -> {
            AugmentDefinition definition = augmentManager == null ? null : augmentManager.getAugment(id);
            if (definition == null) {
                return 0.0D;
            }

            Map<String, Object> shield = AugmentValueReader.getMap(definition.getPassives(), "overheal_shield");
            return Math.max(0.0D,
                    AugmentValueReader.getDouble(shield,
                            "max_shield_percent",
                            AugmentValueReader.getDouble(shield, "max_bonus_health_percent", 0.0D)));
        });
    }

    private String resolveDisplayName(String augmentId) {
        AugmentDefinition definition = augmentManager == null ? null : augmentManager.getAugment(augmentId);
        if (definition != null && definition.getName() != null && !definition.getName().isBlank()) {
            return definition.getName();
        }
        if (augmentId == null || augmentId.isBlank()) {
            return "Active Augment";
        }
        String normalized = augmentId.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "Active Augment";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private EntityStatMap resolveStatMap(PlayerRef playerRef) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                return null;
            }
            Store<EntityStore> store = ref.getStore();
            return store == null ? null : store.getComponent(ref, EntityStatMap.getComponentType());
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private long secondsToMillis(double seconds) {
        if (seconds <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.round(seconds * 1000.0D));
    }

    private double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private boolean isVisiblyFilled(double progress) {
        return progress > MIN_VISIBLE_BAR_PROGRESS;
    }

    public record HudOverlayState(BarState durationBar, BarState shieldBar) {
        public static HudOverlayState hidden() {
            return new HudOverlayState(BarState.hidden(), BarState.hidden());
        }
    }

    public record BarState(String label, double progress, boolean visible) {
        public static BarState hidden() {
            return new BarState("", 0.0D, false);
        }
    }
}