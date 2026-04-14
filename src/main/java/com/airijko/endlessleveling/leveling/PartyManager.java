package com.airijko.endlessleveling.leveling;

import com.airijko.endlessleveling.managers.LoggingManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.airijko.endlessleveling.player.PlayerDataManager;

/**
 * Party integration backed by the external PartyPro mod. Membership and party
 * state are delegated to PartyPro at runtime via reflection.
 */
public class PartyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String DEBUG_SECTION_PARTY_XP = "party_xp_share";

    private final PlayerDataManager playerDataManager;
    private final LevelingManager levelingManager;
    private final PartyProBridge partyPro;

    public PartyManager(@Nonnull PlayerDataManager playerDataManager,
            @Nonnull LevelingManager levelingManager) {
        this.playerDataManager = playerDataManager;
        this.levelingManager = levelingManager;
        this.partyPro = PartyProBridge.create();

        if (partyPro.isAvailable()) {
            LOGGER.atInfo().log("PartyPro detected; delegating party features to PartyPro.");
        } else {
            LOGGER.atWarning().log("PartyPro not detected or unavailable. Party features will be disabled.");
        }
    }

    /** True when PartyPro is loaded and responding. */
    public boolean isAvailable() {
        return partyPro.isAvailable();
    }

    /** True if the player currently belongs to a PartyPro party. */
    public boolean isInParty(@Nonnull UUID memberUuid) {
        return partyPro.isInParty(memberUuid);
    }

    /** Get the leader UUID for the party that this player is in, or null. */
    public UUID getPartyLeader(@Nonnull UUID memberUuid) {
        PartySnapshot snapshot = partyPro.getPartyByPlayer(memberUuid);
        return snapshot != null ? snapshot.leader() : null;
    }

    /** Get the display name of the party the player is in, if any. */
    @Nullable
    public String getPartyName(@Nonnull UUID memberUuid) {
        PartySnapshot snapshot = partyPro.getPartyByPlayer(memberUuid);
        return snapshot != null ? snapshot.name() : null;
    }

    /**
     * Get an immutable snapshot of all members (leader included) for the party
     * this player is in.
     */
    public Set<UUID> getPartyMembers(@Nonnull UUID memberUuid) {
        PartySnapshot snapshot = partyPro.getPartyByPlayer(memberUuid);
        if (snapshot == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(snapshot.members()));
    }

    /** Convenience wrapper for online members only. */
    public Set<UUID> getOnlinePartyMembers(@Nonnull UUID memberUuid) {
        PartySnapshot snapshot = partyPro.getPartyByPlayer(memberUuid);
        if (snapshot == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(partyPro.getOnlineMembers(snapshot)));
    }

    /**
     * Shared XP distribution without range filtering. Killer always receives 100%
     * of the kill XP, and eligible party members each receive the configured member
     * share.
     */
    public void handleXpGain(@Nonnull UUID sourcePlayerUuid, double totalXp) {
        handleXpGainInRange(sourcePlayerUuid, totalXp, 0.0D);
    }

    /**
     * Shared XP with range filtering using PartyPro membership. Killer always keeps
     * the full kill XP. Each in-range eligible party member receives a fixed share
     * of the killer's kill XP (configured in leveling.yml).
     */
    public void handleXpGainInRange(@Nonnull UUID sourcePlayerUuid, double totalXp, double maxDistance) {
        if (totalXp <= 0.0D) {
            return;
        }

        // Killer always gets full XP for their own kill.
        levelingManager.addXp(sourcePlayerUuid, totalXp);

        if (!levelingManager.isPartySharedXpEnabled()) {
            return;
        }

        double memberShareMultiplier = levelingManager.getPartyMemberSharedXpMultiplier();
        if (memberShareMultiplier <= 0.0D) {
            return;
        }

        PlayerRef sourceRef = Universe.get().getPlayer(sourcePlayerUuid);
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }

        Ref<EntityStore> sourceEntity = sourceRef.getReference();
        Store<EntityStore> sourceStore = sourceEntity != null ? sourceEntity.getStore() : null;
        Vector3d sourcePos = resolvePosition(sourceEntity, sourceStore);

        PartySnapshot snapshot = partyPro.getPartyByPlayer(sourcePlayerUuid);
        if (snapshot == null || snapshot.members().isEmpty() || sourcePos == null) {
            return;
        }

        double radius = Math.max(0.0D, maxDistance);
        double radiusSq = radius <= 0.0D ? 0.0D : radius * radius;

        List<UUID> candidates = partyPro.getOnlineMembers(snapshot);
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(snapshot.members());
        }

        List<UUID> nearbyMembers = new ArrayList<>();
        for (UUID member : candidates) {
            if (sourcePlayerUuid.equals(member)) {
                continue;
            }
            PlayerRef memberRef = Universe.get().getPlayer(member);
            if (memberRef == null || !memberRef.isValid()) {
                continue;
            }
            Ref<EntityStore> memberEntity = memberRef.getReference();
            if (memberEntity == null) {
                continue;
            }
            if (sourceStore != null && memberEntity.getStore() != sourceStore) {
                continue;
            }
            Vector3d pos = resolvePosition(memberEntity, memberEntity.getStore());
            if (pos == null) {
                continue;
            }
            if (radiusSq > 0.0D) {
                double dx = pos.getX() - sourcePos.getX();
                double dy = pos.getY() - sourcePos.getY();
                double dz = pos.getZ() - sourcePos.getZ();
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq > radiusSq) {
                    continue;
                }
            }
            nearbyMembers.add(member);
        }

        if (nearbyMembers.isEmpty()) {
            return;
        }

        double memberShareXp = totalXp * memberShareMultiplier;
        if (memberShareXp <= 0.0D) {
            return;
        }

        for (UUID member : nearbyMembers) {
            levelingManager.addXp(member, memberShareXp);
        }

        LOGGER.atInfo().log(
                "Party shared XP: killer=%s baseXP=%.3f membersInRange=%d memberShare=%.2f%% radius=%.1f",
                sourcePlayerUuid, totalXp, nearbyMembers.size(), memberShareMultiplier * 100.0D, maxDistance);
    }

    /**
     * Shared XP for mob kills with per-member rule evaluation.
     *
     * Killer receives their pre-bonus XP after mob kill rules have been applied.
     * Party members receive a share of the mob base XP, then run through their own
     * mob kill rules before personal bonuses are applied by LevelingManager#addXp.
     */
    public void handleMobKillXpGainInRange(@Nonnull UUID sourcePlayerUuid,
            double killerXpAfterKillRules,
            double maxDistance,
            int mobLevel,
            double baseMobXp,
            boolean skipLevelRangeChecks) {
        if (killerXpAfterKillRules <= 0.0D) {
            return;
        }

        // Killer always gets full XP for their own kill.
        levelingManager.addXp(sourcePlayerUuid, killerXpAfterKillRules);

        if (!levelingManager.isPartySharedXpEnabled()) {
            return;
        }

        double memberShareMultiplier = levelingManager.getPartyMemberSharedXpMultiplier();
        if (memberShareMultiplier <= 0.0D) {
            return;
        }

        PlayerRef sourceRef = Universe.get().getPlayer(sourcePlayerUuid);
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }

        Ref<EntityStore> sourceEntity = sourceRef.getReference();
        Store<EntityStore> sourceStore = sourceEntity != null ? sourceEntity.getStore() : null;
        Vector3d sourcePos = resolvePosition(sourceEntity, sourceStore);

        PartySnapshot snapshot = partyPro.getPartyByPlayer(sourcePlayerUuid);
        if (snapshot == null || snapshot.members().isEmpty() || sourcePos == null) {
            return;
        }

        double radius = Math.max(0.0D, maxDistance);
        double radiusSq = radius <= 0.0D ? 0.0D : radius * radius;

        List<UUID> candidates = partyPro.getOnlineMembers(snapshot);
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(snapshot.members());
        }

        List<UUID> nearbyMembers = new ArrayList<>();
        for (UUID member : candidates) {
            if (sourcePlayerUuid.equals(member)) {
                continue;
            }
            PlayerRef memberRef = Universe.get().getPlayer(member);
            if (memberRef == null || !memberRef.isValid()) {
                continue;
            }
            Ref<EntityStore> memberEntity = memberRef.getReference();
            if (memberEntity == null) {
                continue;
            }
            if (sourceStore != null && memberEntity.getStore() != sourceStore) {
                continue;
            }
            Vector3d pos = resolvePosition(memberEntity, memberEntity.getStore());
            if (pos == null) {
                continue;
            }
            if (radiusSq > 0.0D) {
                double dx = pos.getX() - sourcePos.getX();
                double dy = pos.getY() - sourcePos.getY();
                double dz = pos.getZ() - sourcePos.getZ();
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq > radiusSq) {
                    continue;
                }
            }
            nearbyMembers.add(member);
        }

        if (nearbyMembers.isEmpty()) {
            return;
        }

        double memberBaseShareXp = Math.max(0.0D, baseMobXp) * memberShareMultiplier;
        if (memberBaseShareXp <= 0.0D) {
            return;
        }

        boolean partyXpDebug = LoggingManager.isDebugSectionEnabled(DEBUG_SECTION_PARTY_XP);
        int grantedCount = 0;
        for (UUID member : nearbyMembers) {
            PlayerData memberData = playerDataManager.get(member);
            if (memberData == null) {
                continue;
            }

            double memberXpAfterKillRules = levelingManager.applyMobKillXpRules(
                    memberData,
                    mobLevel,
                    memberBaseShareXp,
                    skipLevelRangeChecks,
                    sourceStore,
                    null);

            if (partyXpDebug) {
                int memberLvl = memberData.getLevel();
                int diff = memberLvl - Math.max(1, mobLevel);
                double ruleMult = memberBaseShareXp > 0.0D
                        ? memberXpAfterKillRules / memberBaseShareXp
                        : 0.0D;
                LOGGER.atInfo().log(
                        "[PARTY_XP_SHARE] member=%s memberLvl=%d mobLvl=%d diff=%+d skipRange=%s memberBaseShareXP=%.3f memberAfterKillRulesXP=%.3f ruleMult=%.4f (expected ~BelowRangeMult if diff<<0, ~AboveRangeMult if diff>>0, ~1.0 if in-range)",
                        member,
                        memberLvl,
                        mobLevel,
                        diff,
                        skipLevelRangeChecks,
                        memberBaseShareXp,
                        memberXpAfterKillRules,
                        ruleMult);
            }

            if (memberXpAfterKillRules <= 0.0D) {
                continue;
            }

            levelingManager.addXp(member, memberXpAfterKillRules);
            grantedCount++;
        }

        LOGGER.atInfo().log(
                "Party shared mob XP: killer=%s killerKillRulesXP=%.3f mobBaseXP=%.3f mobLevel=%d membersInRange=%d membersGranted=%d memberShare=%.2f%% radius=%.1f",
                sourcePlayerUuid,
                killerXpAfterKillRules,
                baseMobXp,
                mobLevel,
                nearbyMembers.size(),
                grantedCount,
                memberShareMultiplier * 100.0D,
                maxDistance);
    }

    /**
     * Push custom HUD text to PartyPro: level plus race and primary class.
     */
    public void updatePartyHudCustomText(@Nonnull PlayerData data) {
        if (data == null || !partyPro.isAvailable()) {
            return;
        }
        UUID uuid = data.getUuid();
        if (uuid == null) {
            return;
        }

        String levelText = "Lv. " + data.getLevel();
        String race = safeLabel(data.getRaceId());

        // Line 1: level plus race if present; classes are intentionally omitted.
        String text1 = race.isEmpty() ? levelText : levelText + " | " + race;

        // No secondary line when classes are hidden.
        partyPro.setCustomText(uuid, text1, null);
    }

    private static String safeLabel(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    /** No-op for compatibility: party ownership lives in PartyPro. */
    public void handlePlayerDisconnect(@Nonnull UUID uuid) {
        // PartyPro owns lifecycle; nothing to do.
    }

    /** No-op for compatibility with shutdown hooks. */
    public void saveAllParties() {
        // PartyPro persists its own data.
    }

    @Nullable
    private Vector3d resolvePosition(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || store == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Lightweight snapshot of PartyPro data so callers never touch reflection
     * directly.
     */
    private record PartySnapshot(UUID id, UUID leader, String name, List<UUID> members) {
        PartySnapshot {
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    /** Reflection bridge to avoid a compile-time dependency on PartyPro. */
    private static final class PartyProBridge {
        private static final String[] API_CANDIDATES = {
                "me.tsumori.partypro.api.PartyProAPI" };

        private final Object apiInstance;
        private final Class<?> apiClass;
        private final boolean available;

        private PartyProBridge(Object apiInstance, Class<?> apiClass, boolean available) {
            this.apiInstance = apiInstance;
            this.apiClass = apiClass;
            this.available = available;
        }

        static PartyProBridge create() {
            String connected = null;
            for (String name : API_CANDIDATES) {
                LOGGER.atFine().log("PartyPro API candidate check: %s", name);
                try {
                    Class<?> apiClass = Class.forName(name);
                    Method isAvailable = apiClass.getMethod("isAvailable");
                    Object availableObj = isAvailable.invoke(null);
                    if (!(availableObj instanceof Boolean available) || !available) {
                        LOGGER.atFine().log("PartyPro API candidate present but unavailable: %s", name);
                        continue;
                    }
                    Object instance = apiClass.getMethod("getInstance").invoke(null);
                    if (instance == null) {
                        LOGGER.atFine().log("PartyPro API candidate returned null instance: %s", name);
                        continue;
                    }
                    connected = name;
                    LOGGER.atInfo().log("PartyPro API connected using %s", connected);
                    return new PartyProBridge(instance, apiClass, true);
                } catch (ClassNotFoundException notFound) {
                    LOGGER.atFine().log("PartyPro API candidate not found: %s", name);
                } catch (Exception ex) {
                    LOGGER.atWarning().withCause(ex)
                            .log("Unable to initialize PartyPro API via candidate %s", name);
                }
            }

            LOGGER.atWarning().log("PartyPro API not found after checking %d candidates", API_CANDIDATES.length);
            return new PartyProBridge(null, null, false);
        }

        boolean isAvailable() {
            return available && apiInstance != null && apiClass != null;
        }

        boolean isInParty(UUID playerId) {
            if (!isAvailable() || playerId == null) {
                return false;
            }
            Object result = invoke(apiClass, apiInstance, "isInParty", new Class<?>[] { UUID.class }, playerId);
            return result instanceof Boolean bool && bool;
        }

        PartySnapshot getPartyByPlayer(UUID playerId) {
            if (!isAvailable() || playerId == null) {
                return null;
            }
            Object snapshotObj = invoke(apiClass, apiInstance, "getPartyByPlayer",
                    new Class<?>[] { UUID.class }, playerId);
            return toSnapshot(snapshotObj);
        }

        List<UUID> getOnlineMembers(PartySnapshot snapshot) {
            if (!isAvailable() || snapshot == null || snapshot.id() == null) {
                return Collections.emptyList();
            }
            Object result = invoke(apiClass, apiInstance, "getOnlinePartyMembers", new Class<?>[] { UUID.class },
                    snapshot.id());
            List<UUID> online = toUuidList(result);
            if (!online.isEmpty()) {
                return online;
            }
            Object fallback = invoke(apiClass, apiInstance, "getPartyMembers", new Class<?>[] { UUID.class },
                    snapshot.id());
            return toUuidList(fallback);
        }

        void setCustomText(UUID playerId, String text1, String text2) {
            if (!isAvailable() || playerId == null) {
                return;
            }
            Object result = invoke(apiClass, apiInstance, "setPlayerCustomText",
                    new Class<?>[] { UUID.class, String.class, String.class }, playerId, text1, text2);
            if (result != null) {
                return;
            }
            invoke(apiClass, apiInstance, "setPlayerCustomText1", new Class<?>[] { UUID.class, String.class },
                    playerId, text1);
            invoke(apiClass, apiInstance, "setPlayerCustomText2", new Class<?>[] { UUID.class, String.class },
                    playerId, text2);
        }

        private PartySnapshot toSnapshot(Object snapshotObj) {
            if (snapshotObj == null) {
                return null;
            }

            UUID id = readUuid(snapshotObj, "id");
            UUID leader = readUuid(snapshotObj, "leader");
            String name = readString(snapshotObj, "name");

            List<UUID> members = toUuidList(invoke(snapshotObj.getClass(), snapshotObj, "getAllMembers",
                    new Class<?>[0]));
            if (members.isEmpty()) {
                members = toUuidList(invoke(snapshotObj.getClass(), snapshotObj, "members", new Class<?>[0]));
            }
            if (leader != null && members.stream().noneMatch(leader::equals)) {
                List<UUID> withLeader = new ArrayList<>();
                withLeader.add(leader);
                withLeader.addAll(members);
                members = withLeader;
            }

            return new PartySnapshot(id, leader, name, members);
        }

        private static Object invoke(Class<?> type, Object target, String methodName, Class<?>[] parameterTypes,
                Object... args) {
            try {
                Method method = type.getMethod(methodName, parameterTypes);
                return method.invoke(target, args);
            } catch (Exception ex) {
                LOGGER.atFine().withCause(ex).log("PartyPro reflection call failed for method %s", methodName);
                return null;
            }
        }

        private static Object invoke(Class<?> type, Object target, String methodName) {
            return invoke(type, target, methodName, new Class<?>[0]);
        }

        private static List<UUID> toUuidList(Object value) {
            if (value == null) {
                return Collections.emptyList();
            }
            List<UUID> result = new ArrayList<>();
            if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    if (element instanceof UUID uuid) {
                        result.add(uuid);
                    } else if (element instanceof String str) {
                        try {
                            result.add(UUID.fromString(str));
                        } catch (IllegalArgumentException ignored) {
                            // ignore invalid entries
                        }
                    }
                }
            }
            return result;
        }

        private static UUID readUuid(Object target, String methodName) {
            Object value = invoke(target.getClass(), target, methodName, new Class<?>[0]);
            if (value instanceof UUID uuid) {
                return uuid;
            }
            if (value instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static String readString(Object target, String methodName) {
            Object value = invoke(target.getClass(), target, methodName, new Class<?>[0]);
            return value instanceof String str ? str : null;
        }
    }
}
