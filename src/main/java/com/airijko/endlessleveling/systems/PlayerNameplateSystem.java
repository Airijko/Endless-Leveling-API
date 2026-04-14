package com.airijko.endlessleveling.systems;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.util.PlayerStoreSelector;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Keeps player nameplates in sync with the native Hytale Nameplate component. */
public class PlayerNameplateSystem extends TickingSystem<EntityStore> {

    private static final Query<EntityStore> PLAYER_QUERY = Query.any();

    private final PlayerDataManager playerDataManager;
    private final Map<UUID, String> lastLabels = new ConcurrentHashMap<>();
    private final Set<Store<EntityStore>> knownStores = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> dirtyPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile Boolean enabledOverride;

    public PlayerNameplateSystem(@Nonnull PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    public int removeAllNameplatesForStore(Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return 0;
        }

        Map<Integer, PlayerRef> playersByEntityIndex = PlayerStoreSelector.snapshotPlayersByEntityIndex(store);
        if (playersByEntityIndex.isEmpty()) {
            lastLabels.clear();
            return 0;
        }

        final int[] removed = { 0 };
        store.forEachChunk(PLAYER_QUERY, (ArchetypeChunk<EntityStore> chunk,
                CommandBuffer<EntityStore> commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null) {
                    continue;
                }

                PlayerRef playerRef = playersByEntityIndex.get(ref.getIndex());
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                removeNameplateForPlayerRef(ref, commandBuffer, playerRef);
                removed[0]++;
            }
        });

        if (!lastLabels.isEmpty()) {
            lastLabels.clear();
        }
        return removed[0];
    }

    public int removeAllKnownNameplates() {
        int removed = 0;
        for (Store<EntityStore> store : new HashSet<>(knownStores)) {
            removed += removeAllNameplatesForStore(store);
        }
        return removed;
    }

    public boolean arePlayerNameplatesEnabled() {
        if (enabledOverride != null) {
            return enabledOverride.booleanValue();
        }
        MobLevelingManager mobLevelingManager = resolveMobLevelingManager();
        return mobLevelingManager == null || mobLevelingManager.shouldRenderPlayerNameplate();
    }

    public void setPlayerNameplatesEnabledOverride(boolean enabled) {
        boolean wasEnabled = arePlayerNameplatesEnabled();
        enabledOverride = enabled;
        handleEnabledStateTransition(wasEnabled, enabled);
    }

    public void clearPlayerNameplatesEnabledOverride() {
        boolean wasEnabled = arePlayerNameplatesEnabled();
        enabledOverride = null;
        handleEnabledStateTransition(wasEnabled, arePlayerNameplatesEnabled());
    }

    public void requestRefresh(@Nonnull UUID playerUuid) {
        dirtyPlayers.add(playerUuid);
    }

    public void forgetPlayer(@Nonnull UUID playerUuid) {
        dirtyPlayers.remove(playerUuid);
        lastLabels.remove(playerUuid);
    }

    public void removeNameplateForPlayerRef(@Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            @Nonnull PlayerRef playerRef) {
        Nameplate nameplate = componentAccessor.getComponent(ref, Nameplate.getComponentType());
        if (nameplate != null) {
            String baseName = playerRef.getUsername() != null ? playerRef.getUsername() : "Player";
            nameplate.setText(baseName);
        }
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown() || playerDataManager == null) {
            if (store != null) {
                knownStores.remove(store);
            }
            return;
        }

        boolean firstStoreTick = knownStores.add(store);
        boolean enabled = arePlayerNameplatesEnabled();
        Map<Integer, PlayerRef> playersByEntityIndex = PlayerStoreSelector.snapshotPlayersByEntityIndex(store);
        if (playersByEntityIndex.isEmpty()) {
            return;
        }

        if (firstStoreTick && enabled) {
            requestRefreshForPlayers(playersByEntityIndex.values());
        }

        if (!enabled) {
            removeAllNameplatesForStore(store);
            dirtyPlayers.clear();
            if (!lastLabels.isEmpty()) {
                lastLabels.clear();
            }
            return;
        }

        if (dirtyPlayers.isEmpty()) {
            return;
        }

        store.forEachChunk(PLAYER_QUERY, (ArchetypeChunk<EntityStore> chunk,
                CommandBuffer<EntityStore> commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null) {
                    continue;
                }

                PlayerRef playerRef = playersByEntityIndex.get(ref.getIndex());
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                UUID uuid = playerRef.getUuid();
                if (uuid == null || !dirtyPlayers.contains(uuid)) {
                    continue;
                }

                String baseName = playerRef.getUsername() != null ? playerRef.getUsername() : "Player";
                PlayerData playerData = playerDataManager.get(uuid);
                if (playerData == null) {
                    playerData = playerDataManager.loadOrCreate(uuid, baseName);
                }
                if (playerData == null) {
                    continue;
                }

                String race = normalizePlayerSegmentValue(playerData.getRaceId(), "None");
                String classPrimary = normalizePlayerSegmentValue(playerData.getPrimaryClassId(), "None");
                String classSecondary = normalizePlayerSegmentValue(playerData.getSecondaryClassId(), "None");

                String signature = String.join("|",
                        Integer.toString(playerData.getLevel()),
                        Integer.toString(Math.max(0, playerData.getPrestigeLevel())),
                        race,
                        classPrimary,
                        classSecondary,
                        baseName);
                String previous = lastLabels.get(uuid);
                if (signature.equals(previous)) {
                    dirtyPlayers.remove(uuid);
                    continue;
                }

                String label = String.format("Lv. %d %s", playerData.getLevel(), baseName);
                Nameplate nameplate = commandBuffer.getComponent(ref, Nameplate.getComponentType());
                if (nameplate != null) {
                    nameplate.setText(label);
                } else {
                    commandBuffer.run(s -> {
                        if (!ref.isValid()) return;
                        // Re-check inside the deferred run — the component may have
                        // been added between the outer check and this consume cycle.
                        // Skip the add in that case to avoid engine's duplicate-add
                        // crash.
                        Nameplate existing = s.getComponent(ref, Nameplate.getComponentType());
                        if (existing != null) {
                            existing.setText(label);
                            return;
                        }
                        try {
                            Nameplate fresh = new Nameplate();
                            fresh.setText(label);
                            s.addComponent(ref, Nameplate.getComponentType(), fresh);
                        } catch (IllegalArgumentException ignored) {
                            Nameplate retry = s.getComponent(ref, Nameplate.getComponentType());
                            if (retry != null) retry.setText(label);
                        }
                    });
                }
                lastLabels.put(uuid, signature);
                dirtyPlayers.remove(uuid);
            }
        });
    }

    private MobLevelingManager resolveMobLevelingManager() {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        return plugin != null ? plugin.getMobLevelingManager() : null;
    }

    private void handleEnabledStateTransition(boolean wasEnabled, boolean enabled) {
        if (!enabled) {
            removeAllKnownNameplates();
            dirtyPlayers.clear();
            lastLabels.clear();
            return;
        }

        if (!wasEnabled || lastLabels.isEmpty()) {
            requestRefreshAllKnownPlayers();
        }
    }

    private void requestRefreshAllKnownPlayers() {
        for (Store<EntityStore> store : new HashSet<>(knownStores)) {
            if (store == null || store.isShutdown()) {
                continue;
            }
            requestRefreshForPlayers(PlayerStoreSelector.snapshotPlayersByEntityIndex(store).values());
        }
    }

    private void requestRefreshForPlayers(Iterable<PlayerRef> players) {
        for (PlayerRef playerRef : players) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            UUID uuid = playerRef.getUuid();
            if (uuid != null) {
                dirtyPlayers.add(uuid);
            }
        }
    }

    private static String normalizePlayerSegmentValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}