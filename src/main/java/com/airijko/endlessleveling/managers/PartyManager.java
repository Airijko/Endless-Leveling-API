package com.airijko.endlessleveling.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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

/**
 * Party integration backed by the external PartyPro mod. All membership and
 * party data is queried from PartyPro at runtime; EndlessLeveling no longer
 * persists or owns party state.
 */
public class PartyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

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
     * Party creation is now handled by PartyPro. We keep this method for
     * compatibility but return false.
     */
    public boolean createParty(@Nonnull UUID leaderUuid) {
        logManagedExternally("create");
        return false;
    }

    public boolean joinParty(@Nonnull UUID leaderUuid, @Nonnull UUID memberUuid) {
        logManagedExternally("join");
        return false;
    }

    public boolean leaveParty(@Nonnull UUID memberUuid) {
        logManagedExternally("leave");
        return false;
    }

    public boolean disbandParty(@Nonnull UUID leaderUuid) {
        logManagedExternally("disband");
        return false;
    }

    public boolean invitePlayer(@Nonnull UUID leaderUuid, @Nonnull UUID targetUuid) {
        logManagedExternally("invite");
        return false;
    }

    public boolean hasInvite(@Nonnull UUID leaderUuid, @Nonnull UUID targetUuid) {
        return false;
    }

    public UUID getPendingInviteLeader(@Nonnull UUID targetUuid) {
        return null;
    }

    public void consumeInvite(@Nonnull UUID targetUuid) {
        // no-op
    }

    /**
     * Split XP among online party members using PartyPro data. If PartyPro is not
     * present, XP is granted fully to the source player.
     */
    public void handleXpGain(@Nonnull UUID sourcePlayerUuid, double totalXp) {
        if (totalXp <= 0.0D) {
            return;
        }

        PartySnapshot snapshot = partyPro.getPartyByPlayer(sourcePlayerUuid);
        if (snapshot == null || snapshot.members().isEmpty()) {
            levelingManager.addXp(sourcePlayerUuid, totalXp);
            return;
        }

        List<UUID> recipients = partyPro.getOnlineMembers(snapshot);
        // Fallback to all members if PartyPro could not provide online-only list
        if (recipients.isEmpty()) {
            recipients = new ArrayList<>(snapshot.members());
        }

        if (recipients.isEmpty()) {
            levelingManager.addXp(sourcePlayerUuid, totalXp);
            return;
        }

        double share = totalXp / (double) recipients.size();
        for (UUID member : recipients) {
            levelingManager.addXp(member, share);
        }

        LOGGER.atInfo().log("Distributed %f XP from %s among %d PartyPro members (%.3f each).",
                totalXp, sourcePlayerUuid, recipients.size(), share);
    }

    /**
     * XP sharing with a range limit using PartyPro membership. Only online party
     * members in the same world and within maxDistance of the source receive XP.
     */
    public void handleXpGainInRange(@Nonnull UUID sourcePlayerUuid, double totalXp, double maxDistance) {
        if (totalXp <= 0.0D) {
            return;
        }

        PlayerRef sourceRef = Universe.get().getPlayer(sourcePlayerUuid);
        if (sourceRef == null || !sourceRef.isValid()) {
            levelingManager.addXp(sourcePlayerUuid, totalXp);
            return;
        }

        Ref<EntityStore> sourceEntity = sourceRef.getReference();
        Store<EntityStore> sourceStore = sourceEntity != null ? sourceEntity.getStore() : null;
        Vector3d sourcePos = resolvePosition(sourceEntity, sourceStore);

        PartySnapshot snapshot = partyPro.getPartyByPlayer(sourcePlayerUuid);
        if (snapshot == null || snapshot.members().isEmpty() || sourcePos == null) {
            levelingManager.addXp(sourcePlayerUuid, totalXp);
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
            levelingManager.addXp(sourcePlayerUuid, totalXp);
            return;
        }

        double share = totalXp / (double) nearbyMembers.size();
        for (UUID member : nearbyMembers) {
            levelingManager.addXp(member, share);
        }

        LOGGER.atInfo().log(
                "Distributed %f XP in-range from %s among %d PartyPro members (%.3f each, radius=%.1f).",
                totalXp, sourcePlayerUuid, nearbyMembers.size(), share, maxDistance);
    }

    /** No-op for compatibility: party ownership lives in PartyPro. */
    public void handlePlayerDisconnect(@Nonnull UUID uuid) {
        // PartyPro owns lifecycle; nothing to do.
    }

    /** No-op for compatibility with shutdown hooks. */
    public void saveAllParties() {
        // PartyPro persists its own data.
    }

    private void logManagedExternally(String action) {
        LOGGER.atWarning().log("Party action '%s' is managed by PartyPro. Use PartyPro commands instead.", action);
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
                "com.partypro.api.PartyProAPI",
                "gg.partypro.api.PartyProAPI",
                "dev.partypro.api.PartyProAPI",
                "io.partypro.api.PartyProAPI",
                "PartyProAPI" };

        private final Object apiInstance;
        private final Class<?> apiClass;
        private final boolean available;

        private PartyProBridge(Object apiInstance, Class<?> apiClass, boolean available) {
            this.apiInstance = apiInstance;
            this.apiClass = apiClass;
            this.available = available;
        }

        static PartyProBridge create() {
            for (String name : API_CANDIDATES) {
                try {
                    Class<?> apiClass = Class.forName(name);
                    Method isAvailable = apiClass.getMethod("isAvailable");
                    Object availableObj = isAvailable.invoke(null);
                    if (!(availableObj instanceof Boolean available) || !available) {
                        continue;
                    }
                    Object instance = apiClass.getMethod("getInstance").invoke(null);
                    if (instance != null) {
                        LOGGER.atInfo().log("PartyPro API connected using %s", name);
                        return new PartyProBridge(instance, apiClass, true);
                    }
                } catch (ClassNotFoundException notFound) {
                    // Try next candidate
                } catch (Exception ex) {
                    LOGGER.atWarning().withCause(ex)
                            .log("Unable to initialize PartyPro API via candidate %s", name);
                }
            }
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

        private PartySnapshot toSnapshot(Object snapshotObj) {
            if (snapshotObj == null) {
                return null;
            }

            UUID id = readUuid(snapshotObj, "id");
            UUID leader = readUuid(snapshotObj, "leader");
            String name = readString(snapshotObj, "name");

            List<UUID> members = toUuidList(invoke(snapshotObj.getClass(), snapshotObj, "getAllMembers"));
            if (members.isEmpty()) {
                members = toUuidList(invoke(snapshotObj.getClass(), snapshotObj, "members"));
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

        private static Object invoke(Class<?> type, Object target, String methodName) {
            try {
                Method method = type.getMethod(methodName);
                return method.invoke(target);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
