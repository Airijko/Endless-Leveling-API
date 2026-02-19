package com.airijko.endlessleveling.augments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player augment cooldowns and ready-notification state.
 */
public final class AugmentRuntimeManager {

    private final Map<UUID, AugmentRuntimeState> runtimeStates = new ConcurrentHashMap<>();

    public AugmentRuntimeState getRuntimeState(UUID uuid) {
        return runtimeStates.computeIfAbsent(uuid, AugmentRuntimeState::new);
    }

    public void clear(UUID uuid) {
        runtimeStates.remove(uuid);
    }

    public void clearAll() {
        runtimeStates.clear();
    }

    public void markCooldown(UUID uuid, String augmentId, long cooldownMillis) {
        markCooldown(uuid, augmentId, augmentId, cooldownMillis);
    }

    public void markCooldown(UUID uuid, String augmentId, String displayName, long cooldownMillis) {
        if (uuid == null || augmentId == null || augmentId.isBlank() || cooldownMillis <= 0) {
            return;
        }
        AugmentRuntimeState state = getRuntimeState(uuid);
        state.setCooldown(augmentId, displayName, System.currentTimeMillis() + cooldownMillis);
    }

    public static final class AugmentRuntimeState {
        private final UUID playerId;
        private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();

        private AugmentRuntimeState(UUID playerId) {
            this.playerId = playerId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public Collection<CooldownState> getCooldowns() {
            return Collections.unmodifiableCollection(new ArrayList<>(cooldowns.values()));
        }

        public void setCooldown(String augmentId, String displayName, long expiresAtMillis) {
            if (augmentId == null || augmentId.isBlank()) {
                return;
            }
            CooldownState state = cooldowns.computeIfAbsent(normalizeId(augmentId), CooldownState::new);
            state.setDisplayName(displayName);
            state.setExpiresAt(expiresAtMillis);
            state.setReadyNotified(false);
        }

        public CooldownState getCooldown(String augmentId) {
            if (augmentId == null) {
                return null;
            }
            return cooldowns.get(normalizeId(augmentId));
        }

        public void clearCooldown(String augmentId) {
            if (augmentId == null) {
                return;
            }
            cooldowns.remove(normalizeId(augmentId));
        }

        public void clearAll() {
            cooldowns.clear();
        }

        private String normalizeId(String augmentId) {
            return augmentId == null ? null : augmentId.trim().toLowerCase();
        }
    }

    public static final class CooldownState {
        private final String augmentId;
        private String displayName;
        private long expiresAt;
        private boolean readyNotified = true;

        private CooldownState(String augmentId) {
            this.augmentId = augmentId;
            this.displayName = augmentId;
        }

        public String getAugmentId() {
            return augmentId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            if (displayName != null && !displayName.isBlank()) {
                this.displayName = displayName;
            }
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public boolean isReadyNotified() {
            return readyNotified;
        }

        public void setReadyNotified(boolean readyNotified) {
            this.readyNotified = readyNotified;
        }
    }
}
