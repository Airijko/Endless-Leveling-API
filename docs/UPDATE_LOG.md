# Endless Leveling - Update Log

## 2026-04-15 — 7.9.0 (compared to 7.8.3)

A **content release** introducing the **Outlander Bridge** — a wave-based instanced dungeon with XP banking, tiered reward claiming, and a full HUD overlay. Seven escalating waves of Outlander mobs spawn across a flat arena bridge; XP earned during waves is banked rather than applied directly, split into **pending** (current wave, lost on death) and **saved** (checkpointed on wave clear). After victory or death, a 60-second reward window opens for claiming banked XP, subject to a 1-hour cooldown. The system ships with 4 new ECS systems, a wave orchestration manager, dedicated UI pages, combat music, a portal item, and a pre-built instance world.

### Highlights

- **Outlander Bridge wave dungeon** — 7-wave instanced dungeon with escalating mob compositions (Peon → Marauder → Berserker melee; Hunter → Stalker → Cultist ranged; Priest/Sorcerer mages). Each wave ends with an Outlander_Brute boss (1.8× size scale). Mobs spawn in multi-batch pools with 70% kill-threshold or 25-second fallback triggers. Combat zone enforced within ±30 radius; mobs snap-back if they escape leash bounds.
- **XP banking system** — `LevelingManager.addXp()` intercepts final XP via `OutlanderBridgeXpBank.tryDivertXp()` and redirects it into a per-player bank instead of the player profile. Pending XP (current wave) wipes on death; saved XP (cleared waves) persists. `checkpointSession()` promotes all pending → saved between waves.
- **Reward claim flow** — victory triggers a 5-second grace period, then opens `OutlanderBridgeRewardsPage` with a 60-second countdown. Players can CLAIM (applies cooldown, grants XP, kicks from instance) or CANCEL (forfeits XP, no cooldown). Timeout force-closes the page. Session locking prevents TP-exploit re-entry after claim/cancel.
- **1-hour claim cooldown** — `OutlanderBridgeRewardCooldowns` persists per-player cooldown expiry to `outlander-bridge-reward-cooldowns.json` with atomic `.tmp` + move writes. Expired entries auto-purged on load. Bypassable via `endlessleveling.outlander.bypass_cooldown` permission.
- **Wave HUD overlay** — real-time HUD showing wave count, remaining mobs, banked XP (green), unsecured XP (orange), and up to 5 mob coordinate hints. State-change detection prevents update spam; 250ms refresh throttle via `OutlanderBridgeWaveHudRefreshSystem`. Multi-HUD mod compatibility via `MultipleHudCompatibility.showHud()`.
- **Dungeons page integration** — `DungeonsUIPage` lists Outlander Bridge as a native dungeon card with cooldown-aware status badge ("Rewards Available" / remaining cooldown). Cooldown warning confirm dialog shown on entry when on cooldown.
- **Combat music** — `Outlander-Bridge.ogg` triggered per-player with cooldown, single-instance playback, music-category audio.
- **Portal item** — `EL_Portal_Outlander_Bridge` (Rare quality, Howling Sands portal visual, 30-minute time limit) links to `Endless_Outlander_Bridge` instance via portal type definition. Spawn at (0.5, 80, 0.5) facing Yaw 180.
- **Block protection** — two complementary guards: `OutlanderBridgeBlockDamageGuardSystem` cancels `DamageBlockEvent`, and `BreakBlockEntitySystem` early-exits for Outlander Bridge worlds.
- **Admin command** — `/lvl start` skips wave countdown for testing; validates player is in an active Outlander Bridge instance.

### Outlander Bridge Wave Manager (`Feature`)

Core orchestration lives in [`OutlanderBridgeWaveManager`](src/main/java/com/airijko/endlessleveling/mob/outlander/OutlanderBridgeWaveManager.java) (~1000 lines), extending `TickingSystem`.

#### Game Phases

Four phases drive the session lifecycle: **COUNTDOWN** → **WAVE_ACTIVE** → **WAVE_CLEARED** → **COMPLETED**. Each tick processes the active phase's logic (spawn batches, death pruning, bounds enforcement, aggro, HUD refresh).

#### Wave Definitions

Loaded from [`waves/outlander_bridge_waves.json`](src/main/resources/waves/outlander_bridge_waves.json) (327 lines). Global settings: `spawn_radius: 18`, `batch_kill_percent: 70%`, `batch_fallback_seconds: 25`. Seven waves with escalating pools:

| Wave | Melee Pool | Ranged Pool | Boss |
|------|-----------|-------------|------|
| 1–3 | 6–10 Peon/Marauder | 3–7 Hunter/Stalker | Outlander_Brute |
| 4–5 | 7–10 Marauder/Berserker | 2–3 Priest/Sorcerer | Outlander_Brute |
| 6–7 | 8–9 Berserker warband | 3–5 Cultist/Sorcerer ritual | Outlander_Brute |

Pools use variant arrays for RNG selection; late-wave swarm variants can field 20+ total mobs.

#### Combat Geometry

- Two spawning flanks at ±50 X, ±8 Z offset from arena center
- Combat zone enforcement: ±30 radius, mobs snap-back if they escape
- Y-bounds enforcement prevents mobs falling off the bridge
- 500m aggro radius with `TargetMemory` updates and ghost-hit aggression

#### Session Management

Per-world instances keyed by UUID. Return portal hidden during waves, restored on victory. Death detection prunes `DeathComponent` mobs each tick. Boss mobs receive 1.8× size scale.

#### Files

- [`OutlanderBridgeWaveManager`](src/main/java/com/airijko/endlessleveling/mob/outlander/OutlanderBridgeWaveManager.java) — ~1000 lines. Core wave orchestration, mob spawning, phase transitions, aggro, bounds, audio, portal management.

### XP Banking System (`Feature`)

[`OutlanderBridgeXpBank`](src/main/java/com/airijko/endlessleveling/mob/outlander/OutlanderBridgeXpBank.java) (249 lines) manages per-player XP state during sessions.

#### Banking Model

Two XP tiers per player per session:
- **Pending XP** — accumulated during current wave; wiped to zero on player death
- **Saved XP** — checkpointed from pending on wave clear; safe across deaths

#### State Maps

- `activeBanking` — player UUID → session world UUID
- `sessionBanks` — world UUID → player UUID → `BankState`
- `sessionLockedPlayers` — world UUID → set of locked player UUIDs (post-claim/cancel)

#### Integration Point

[`LevelingManager.addXp()`](src/main/java/com/airijko/endlessleveling/leveling/LevelingManager.java) (~line 228): after applying all XP bonuses, checks `OutlanderBridgeXpBank.get().tryDivertXp(uuid, adjustedXp)`. If returns `true`, XP is redirected to the bank instead of the player profile.

#### Key Methods

- `tryDivertXp()` — intercepts XP from `LevelingManager`, adds to pending
- `checkpointSession()` — moves all pending → saved for all players in session
- `onPlayerDied()` — zeros pending, queues respawn reward panel if saved > 0
- `lockAndClear()` — permanent session lockout after claim/cancel/timeout
- `snapshotSessionSavedXp()` — read-only snapshot for victory panels

#### Files

- [`OutlanderBridgeXpBank`](src/main/java/com/airijko/endlessleveling/mob/outlander/OutlanderBridgeXpBank.java) — 249 lines. Inner types: `BankState`, `PendingReward` record, `BankView` record.

### Reward Claim Cooldown (`Feature`)

[`OutlanderBridgeRewardCooldowns`](src/main/java/com/airijko/endlessleveling/mob/outlander/OutlanderBridgeRewardCooldowns.java) (125 lines) persists per-player 1-hour cooldowns for claiming rewards.

- Singleton with `init()` / `get()` pattern
- Persists to `outlander-bridge-reward-cooldowns.json`
- Atomic write: `.tmp` file + move to prevent corruption on crash
- Auto-purges expired entries on load
- Methods: `isOnCooldown()`, `remainingMs()`, `setClaimedNow()`

### Rewards Page UI (`Feature`)

[`OutlanderBridgeRewardsPage`](src/main/java/com/airijko/endlessleveling/ui/OutlanderBridgeRewardsPage.java) (263 lines) — interactive modal for claiming or forfeiting banked XP.

#### UI Template

[`OutlanderBridgeRewards.ui`](src/main/resources/Common/UI/Custom/Pages/OutlanderBridge/OutlanderBridgeRewards.ui) (147 lines) — 520×420 px centred modal with:
- XP card (green accent bar, formatted amount with k/M suffixes)
- Countdown card (orange accent bar, "Dungeon closes in: 60s")
- Optional cooldown status message (red)
- CLAIM / CANCEL buttons

#### Claim Logic

- CLAIM: applies 1-hour cooldown, locks player in session, grants XP via `LevelingManager.addXp()`, kicks from instance
- CANCEL: forfeits XP, no cooldown applied, kicks from instance
- Timeout (60s): manager force-closes page

#### Permission

`endlessleveling.outlander.bypass_cooldown` — bypasses the 1-hour cooldown. Also granted implicitly via op/admin/wildcard permissions (resolved through [`OperatorHelper`](src/main/java/com/airijko/endlessleveling/util/OperatorHelper.java)).

### Wave HUD (`Feature`)

[`OutlanderBridgeWaveHud`](src/main/java/com/airijko/endlessleveling/ui/OutlanderBridgeWaveHud.java) (266 lines) — custom HUD overlay with real-time wave progress.

#### Display Elements

- Wave count (current / total)
- Remaining mobs (alive / spawned)
- Banked XP (green `#6cff78`) — saved across wave clears
- Unsecured XP (orange `#ffc98b`) — current wave, lost on death
- Up to 5 mob coordinate hint labels

#### UI Template

[`OutlanderBridgeWaveHud.ui`](src/main/resources/Common/UI/Custom/Hud/OutlanderBridgeWaveHud.ui) (123 lines) — positioned top-right, 320 px width, golden "Wave Tracker" header banner.

#### Refresh System

[`OutlanderBridgeWaveHudRefreshSystem`](src/main/java/com/airijko/endlessleveling/systems/OutlanderBridgeWaveHudRefreshSystem.java) (64 lines) — `TickingSystem` that pushes state to HUDs every 250ms per player. Sweeps ghost HUDs for players not in active sessions (post-restart, cross-world bleed).

#### Multi-HUD Compatibility

Checks `MultipleHudCompatibility.showHud()` for mod slots; falls back to `hudManager` directly if unavailable.

#### Hide Stub

[`OutlanderBridgeWaveHudHide`](src/main/java/com/airijko/endlessleveling/ui/OutlanderBridgeWaveHudHide.java) (22 lines) — minimal 1×1 px hidden HUD used as placeholder when closing the wave HUD to prevent flicker.

### ECS Systems (`Feature`)

Four new systems registered in [`EndlessLeveling.setup()`](src/main/java/com/airijko/endlessleveling/EndlessLeveling.java):

| System | Type | Purpose |
|--------|------|---------|
| `OutlanderBridgeWaveManager` | `TickingSystem` | Wave orchestration, mob spawning, phase transitions |
| `OutlanderBridgeWaveHudRefreshSystem` | `TickingSystem` | 250ms throttled HUD state push + ghost sweep |
| `OutlanderBridgeBlockDamageGuardSystem` | `EntityEventSystem<DamageBlockEvent>` | Cancels all block damage in Outlander worlds |
| `OutlanderBridgePlayerDeathSystem` | `DeathSystems.OnDeathSystem` | Routes player death to wave manager for XP wipe + respawn reward queue |

### Block Protection (`Feature`)

Two complementary guards prevent block modification during Outlander Bridge sessions:

- [`OutlanderBridgeBlockDamageGuardSystem`](src/main/java/com/airijko/endlessleveling/systems/OutlanderBridgeBlockDamageGuardSystem.java) (36 lines) — cancels `DamageBlockEvent` in Outlander worlds
- [`BreakBlockEntitySystem`](src/main/java/com/airijko/endlessleveling/systems/BreakBlockEntitySystem.java) (~line 41) — early-exit guard added for Outlander Bridge worlds (complementary to the event system)

### Dungeons Page Integration (`Feature`)

[`DungeonsUIPage`](src/main/java/com/airijko/endlessleveling/ui/DungeonsUIPage.java) (462 lines) updated with Outlander Bridge as a selectable native dungeon.

- New constants: `OUTLANDER_BRIDGE_ID`, `OUTLANDER_BRIDGE_DESCRIPTION`
- Side panel shows cooldown status via `OutlanderBridgeRewardCooldowns.get()` — "Rewards Available" (green) or remaining cooldown time
- Cooldown warning confirm dialog on entry when player is on claim cooldown (XP earned won't be claimable)
- Reuses existing portal/instance teleport flow

[`DungeonsPage.ui`](src/main/resources/Common/UI/Custom/Pages/Dungeons/DungeonsPage.ui) (360 lines) — Outlander Bridge card in "ENDLESS LEVELING DUNGEONS" section with status badge, description, VIEW DETAILS and ENTER DUNGEON buttons.

### Portal & Instance World (`Feature`)

#### Portal Item

[`EL_Portal_Outlander_Bridge.json`](src/main/resources/Server/Item/Items/EL_Portal_Outlander_Bridge.json) (47 lines):
- Rare quality, Howling Sands portal visual (`PortalKey_Howling_Sands.png`)
- 1800-second (30-minute) time limit
- `Portal_Shard.blockymodel` with custom texture
- Tags: `Portal`, `Temporary`

#### Portal Type

[`EL_Portal_Outlander_Bridge.json`](src/main/resources/Server/PortalTypes/EL_Portal_Outlander_Bridge.json) (25 lines):
- Links to `Endless_Outlander_Bridge` instance
- Cyan theme colour (`#5bceffff`)
- Spawn: (0.5, 80.0, 0.5), Yaw 180
- Tip: "Hold the center to win."

#### Instance World

[`Endless_Outlander_Bridge/`](src/main/resources/Server/Instances/Endless_Outlander_Bridge/) — pre-built flat arena with:
- `Default_Flat` world generator
- Time frozen at 07:00 (morning)
- PvP disabled
- 16 chunk region files (4×4 grid around origin)
- `DeleteOnRemove: false` — arena template persists

### World Settings (`Feature`)

[`endless-dungeons.json`](src/main/resources/world-settings/endless-dungeons.json) — Outlander Bridge scaling configuration:

| Setting | Standard Mob | Outlander_Brute (Boss) |
|---------|-------------|----------------------|
| Base Health Multiplier | 2.5× | 5.0× |
| Health Per Level | +0.075 | +0.075 |
| Base Damage Multiplier | 1.0× | 1.2× |
| Damage Per Level | +0.025 | +0.025 |
| Level Range | 20–40 base | 20–40 base |

Player adaptation: ABOVE mode with ±10 level allowance. Endless tiers (20 levels per tier).

### Audio (`Feature`)

- [`Outlander-Bridge.ogg`](src/main/resources/Common/Sounds/EndlessLeveling/Outlander-Bridge.ogg) — combat music track (also mirrored to `Common/Music/`)
- [`SFX_EL_OutlanderBridge_Combat_Music.json`](src/main/resources/Server/Audio/SoundEvents/SFX/EndlessLeveling/SFX_EL_OutlanderBridge_Combat_Music.json) (19 lines) — music-category audio event, volume 5, single-instance playback, `PreventSoundInterruption: true`

### Localization (`Feature`)

[`server.lang`](src/main/resources/Server/Languages/en-US/server.lang) — 6 new strings:

| Key | Value |
|-----|-------|
| `items.EL_Portal_Outlander_Bridge.name` | Outlander Bridge Portal |
| `items.EL_Portal_Outlander_Bridge.description` | Placeable portal that opens a gateway to the Outlander Bridge arena. |
| `portals.outlander_bridge` | Outlander Bridge |
| `portals.outlander_bridge.description` | A contested bridge arena. Fight across the span to claim the other side. |
| `portals.outlander_bridge.tip1` | Tip: Hold the center to win. |

### Admin Command (`Feature`)

[`OutlanderBridgeStartCommand`](src/main/java/com/airijko/endlessleveling/commands/subcommands/OutlanderBridgeStartCommand.java) (42 lines) — `/lvl start` subcommand. Validates player is in an active Outlander Bridge instance, calls `OutlanderBridgeWaveManager.forceStart()` to skip countdown. Registered in [`EndlessLevelingCommand`](src/main/java/com/airijko/endlessleveling/commands/EndlessLevelingCommand.java) (~line 81).

### Plugin Lifecycle Integration

[`EndlessLeveling.setup()`](src/main/java/com/airijko/endlessleveling/EndlessLeveling.java) changes:
- Registers all 4 new ECS systems
- `OutlanderBridgeWaveManager.get().load()` — loads wave config
- `OutlanderBridgeRewardCooldowns.init()` — initializes cooldown persistence
- Hooks into `onPlayerEntered` and player-ready events for session management
- Creates `InstanceDungeonDefinition` for `"outlander-bridge"`
- `onPlayerDrain` hook calls `OutlanderBridgeWaveManager.get().onPlayerDrain(uuid)`

### Version Bump

- [`manifest.json`](src/main/resources/manifest.json) — `Version`: `7.8.3` → `7.9.0`.
- `gradle.properties` — `version=7.9.0`.

### Files Changed Summary

- **Java (new, 9):** `OutlanderBridgeWaveManager`, `OutlanderBridgeXpBank`, `OutlanderBridgeRewardsPage`, `OutlanderBridgeRewardCooldowns`, `OutlanderBridgeWaveHud`, `OutlanderBridgeWaveHudHide`, `OutlanderBridgeWaveHudRefreshSystem`, `OutlanderBridgeBlockDamageGuardSystem`, `OutlanderBridgePlayerDeathSystem`, `OutlanderBridgeStartCommand`.
- **Java (modified, 5):** `EndlessLeveling`, `EndlessLevelingCommand`, `LevelingManager`, `DungeonsUIPage`, `BreakBlockEntitySystem`.
- **UI (new, 4):** `OutlanderBridgeWaveHud.ui`, `OutlanderBridgeWaveHide.ui`, `OutlanderBridgeRewards.ui`, `DungeonsPage.ui`.
- **Config/Data (new, 4):** `outlander_bridge_waves.json`, `EL_Portal_Outlander_Bridge.json` (item), `EL_Portal_Outlander_Bridge.json` (portal type), `SFX_EL_OutlanderBridge_Combat_Music.json`.
- **Instance (new, 16):** `Endless_Outlander_Bridge/` — instance.bson + 12 chunk regions + 4 resource JSONs.
- **Audio (new, 2):** `Outlander-Bridge.ogg` (×2 paths).
- **Config (modified, 3):** `manifest.json`, `gradle.properties`, `endless-dungeons.json`, `server.lang`.

---

## 2026-04-14 — 7.8.3 (compared to 7.8.2)

A **crash-fix + external-addon compatibility hotfix release** on top of 7.8.2. Three issues addressed: (1) the engine's "already contains component type" crash when two systems try to add a `Nameplate` component to the same entity on the same tick (affected entities in instance worlds `el_gate_*` where vanilla spawn + mob leveling nameplate apply concurrently); (2) external addon-provided races/classes being permanently nullified on player-data save when the addon's own `setup()` hadn't finished registering yet; (3) the profile Gates nav button being hidden whenever the active dungeon addon was the renamed `EndlessRiftsAndRaids` variant instead of the legacy `EndlessDungeonsAndGates` class.

Nameplate fix merged from PR [#6](https://github.com/Airijko/Hytale-Skills/pull/6) by `HazemSb` / `zbevee` (branch `fix/nameplate-race`).

### Highlights

- **Nameplate duplicate-add crash fix** — `MobLevelingSystem` and `PlayerNameplateSystem` now re-check the `Nameplate` component inside the deferred `commandBuffer.run(...)` closure and fall back to `setText` on the existing component instead of blindly calling `addComponent`, which crashed when another system added the nameplate between the outer check and the consume cycle.
- **External race/class preservation** — `PlayerDataManager.ensureValidRace` / `ensureValidClasses` no longer null out IDs whose definition isn't currently registered (e.g., addon-provided race/class registered after EL's setup completes). Also removed both `ensureValid*` calls from the save path, so a mid-boot save no longer corrupts the stored identifiers. Active-profile `setPlayer*` calls are now gated on the registry actually containing the ID.
- **EndlessRiftsAndRaids addon detection** — `NavUIHelper.isEndlessDungeonsPresent` now probes both `EndlessRiftsAndRaids` and legacy `EndlessDungeonsAndGates` classnames, so the profile "Gates" button stays visible under the renamed addon.
- **Version bump** — `manifest.json` / `gradle.properties`: `7.8.2` → `7.8.3`.

### Nameplate Duplicate-Add Crash (`Bug Fix`)

#### Problem

`MobLevelingSystem.refreshMobNameplate` (and the parallel path in `PlayerNameplateSystem`) scheduled a `commandBuffer.run` closure that called `s.ensureAndGetComponent(ref, Nameplate.getComponentType()).setText(label)`. Under `ensureAndGetComponent`, if the component is absent the engine calls `addComponent` internally. In instance worlds (`el_gate_*`), the vanilla spawn pipeline sometimes added a `Nameplate` between the outer `isValid()` check on the owning tick and the deferred `commandBuffer` consume cycle — so by the time the closure ran, the component was already present, and the internal `addComponent` threw `IllegalArgumentException: already contains component type: Nameplate`, crashing the tick.

#### Fix

Both systems now inline a guarded add:

```java
commandBuffer.run(s -> {
    if (!ref.isValid()) return;
    // Re-check inside the deferred run — another system may have added
    // the Nameplate between the outer check and this consume cycle.
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
```

Two-layer defense: explicit `getComponent` re-check first, then a `try/catch` on the add itself to cover the window between the re-check and the `addComponent` call.

#### Files Changed

- [`MobLevelingSystem`](src/main/java/com/airijko/endlessleveling/mob/MobLevelingSystem.java) — +17 / -2 around line 1610 (the mob nameplate refresh path).
- [`PlayerNameplateSystem`](src/main/java/com/airijko/endlessleveling/systems/PlayerNameplateSystem.java) — +17 / -2 around line 204 (the player nameplate refresh path).

### External Race/Class Preservation (`Bug Fix`)

#### Problem

`PlayerDataManager.ensureValidRace` and `ensureValidClasses` ran on every save and resolved each profile's `raceId` / `primaryClassId` / `secondaryClassId` against the currently-registered `RaceManager` / `ClassManager` registries. If the ID resolved to a definition that wasn't present (`raceManager.getRace(resolved) == null`), the profile field was set to `null`. This was destructive for addon-provided races/classes: if an external addon registered its content in its own `setup()` that ran *after* EL's first save of the tick, the player's stored `raceId` would be nullified and permanently lost once the save flushed — even though the addon would have registered the ID seconds later. The `setPlayerRaceSilently` / `setPlayerPrimaryClass` / `setPlayerSecondaryClass` tail calls then also ran unconditionally, attempting to apply an ID the registry couldn't resolve.

Additional aggravating factor: `saveData` itself called `ensureValidRace(data)` and `ensureValidClasses(data)` before writing to disk, so every save was a potential data-corruption event during boot or reload windows.

#### Fix

Three changes in [`PlayerDataManager`](src/main/java/com/airijko/endlessleveling/player/PlayerDataManager.java):

1. Removed the `ensureValidRace(data)` / `ensureValidClasses(data)` calls from the `saveData` path (around line 210) — validation on save is no longer destructive.
2. `ensureValidRace`: when `raceManager.resolveRaceIdentifier(original)` returns `null` but the stored ID is non-blank, **preserve the original ID** instead of nullifying. Comment explains the rationale (addon race may register later).
3. `ensureValidClasses`: same preservation logic applied to both `primaryClassId` and `secondaryClassId`.
4. Active-profile re-apply (`raceManager.setPlayerRaceSilently`, `classManager.setPlayerPrimaryClass`, `classManager.setPlayerSecondaryClass`) now guarded by `registry.getRace(id) != null` / `registry.getClass(id) != null` — skips the apply when the definition isn't registered yet rather than pushing a broken ID downstream.

```java
String original = profile.getRaceId();
String resolved = raceManager.resolveRaceIdentifier(original);
// Preserve IDs whose definition isn't currently registered (e.g., an addon-provided
// race that hasn't finished its own setup() yet). Nullifying would corrupt the save
// once the addon re-registers later.
if (resolved == null && original != null && !original.isBlank()) {
    resolved = original;
}
profile.setRaceId(resolved);
```

```java
String activeRaceId = data.getRaceId();
if (activeRaceId != null && raceManager.getRace(activeRaceId) != null) {
    raceManager.setPlayerRaceSilently(data, activeRaceId);
}
```

#### Files Changed

- [`PlayerDataManager`](src/main/java/com/airijko/endlessleveling/player/PlayerDataManager.java) — +28 / -19. Save-path `ensureValid*` calls removed (~line 210); `ensureValidRace` rewritten (~line 1148); `ensureValidClasses` rewritten (~line 1180).

### EndlessRiftsAndRaids Addon Detection (`Bug Fix`)

#### Problem

`NavUIHelper.isEndlessDungeonsPresent` hardcoded a single classpath probe — `Class.forName("com.airijko.endlessleveling.EndlessDungeonsAndGates", false, cl)` — to decide whether the profile page's "Gates" nav button should be visible and wired. The dungeon addon was renamed/forked to `EndlessRiftsAndRaids` (new main class `com.airijko.endlessleveling.EndlessRiftsAndRaids`); servers running the renamed variant had the Gates button hidden because the legacy classname was absent.

#### Fix

`NavUIHelper.isEndlessDungeonsPresent` now iterates a list of both main classes and returns `true` on the first that resolves:

```java
private static final String[] ENDLESS_DUNGEONS_CLASSES = {
        "com.airijko.endlessleveling.EndlessRiftsAndRaids",
        "com.airijko.endlessleveling.EndlessDungeonsAndGates",
};

private static boolean isEndlessDungeonsPresent() {
    ClassLoader cl = NavUIHelper.class.getClassLoader();
    for (String name : ENDLESS_DUNGEONS_CLASSES) {
        try {
            Class.forName(name, false, cl);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
    }
    return false;
}
```

RiftsAndRaids is checked first (current addon); `EndlessDungeonsAndGates` remains as legacy fallback so older deployments still pick up the button.

#### Files Changed

- [`NavUIHelper`](src/main/java/com/airijko/endlessleveling/ui/NavUIHelper.java) — +13 / -8 around line 433 (classname constant + probe loop).

### Version Bump

- [`manifest.json`](src/main/resources/manifest.json) — `Version`: `7.8.2` → `7.8.3`.
- `gradle.properties` — `version=7.8.3`.

### Known Issues / Followups

- 7.8.2 has no dedicated `UPDATE_LOG.md` section — the 7.8.2 commit re-used the "7.8.1" heading for its entry. The pagination-fix + FrozenDomain aura VFX + RecoveredForce / SupportsDream / UnyieldingFramework augments + HealingAura passive rework described under the 7.8.1 section below were actually shipped in 7.8.2. Next release should add a proper 7.8.2 backfill or merge that scope into the 7.8.1 header.

---

## 2026-04-13 — 7.8.1 (compared to 7.8.0)

A **polish and bug-fix release** on top of the 7.8.0 UI overhaul. Focus: **pagination controls** on all list/leaderboard pages, **leaderboard podium fixes** plus a new partner-branded leaderboard page, a **FrozenDomainAugment VFX rewrite** (pulse rings → aura circles), a **WitherAugment slow fallback fix**, a **race-ascension crit-lock workaround** in `RaceManager`, **tier-aware augment entry backgrounds** on the profile page, and a heavy **PNG asset recompression pass**.

### Highlights

- **Pagination bar** added to `LeaderboardsPage.ui`, `LeaderboardsPagePartner.ui`, `XpStatsPage.ui`, `XpStatsAdminPage.ui`, `XpStatsLeaderboardPage.ui` — PREV / NEXT buttons + `#PageLabel` showing `"Page N/M"`. Java controllers (`LeaderboardsUIPage`, `XpStatsUIPage`, `XpStatsAdminUIPage`, `XpStatsLeaderboardUIPage`) wired to drive the new controls.
- **Leaderboards podium fixes** — `LeaderboardsUIPage` rewritten (+203 / -64 across two commits), podium first/second/third templates restyled, `LeaderboardsRow.ui` tweaked, new `LeaderboardsPagePartner.ui` (332 lines) for partner branding.
- **FrozenDomainAugment reworked** — replaced the expanding pulse-ring VFX system (`TRIGGER_PULSE_*` constants, `ActivePulse` state machine) with a simpler standing-aura VFX (`AURA_VFX_IDS = { "Totem_Slow_Circle1", "Totem_Slow_Circle2" }`, `AuraVisualState`, 500 ms refresh interval). Net −135 / +53 lines.
- **WitherAugment slow fallback** — `applySlowEffectFallback` now returns `boolean`; "MovementManager missing" / "movement settings missing" warnings only fire when the effect fallback also fails, eliminating false-positive log spam when the fallback succeeds.
- **RaceManager crit-lock ascension fallback** — ascension `Requires PRECISION >= N` / `Requires FEROCITY >= N` checks now fall back to the class's primary damage stat (`STRENGTH` for physical, `SORCERY` for magic, higher-of-two for hybrid) when the requested crit attribute is locked by `SkillManager.isCritAttributeLocked`. Prevents players from being permanently blocked from ascension by a crit-lock on their class's off-stat.
- **ProfileUIPage augment entry tier backgrounds** — each augment row now toggles `#ItemBgCommon` / `#ItemBgElite` / `#ItemBgLegendary` / `#ItemBgMythic` visibility based on the parsed `PassiveTier`. `ProfileAugmentEntry.ui` restructured to host the 4 tier backgrounds.
- **Prestige tuning** — `leveling.yml`: MYTHIC tier `prestige_levels: [15]` → `[20]`.
- **Asset recompression** — ~35 PNGs shrunk (banners, HUD XP/shield/duration bars, tile/border sprites for all 5 rarities, nav icons, dungeon placeholder cards). Typical 30–60% size drop per file. Visual output unchanged.
- **ClassesPage.ui** — 133-line layout pass.
- **ProfilePagePartner.ui** — 869-line restructure to match the new profile layout from 7.8.0 on the partner-branded path.

### Pagination (`Bug Fix + Feature`)

#### UI Template

New `#PaginationBar` group appended inside each list/table section:

```xml
Group #PaginationBar {
    LayoutMode: Left;
    Anchor: (Height: 36);
    Padding: (Horizontal: 8, Top: 6);

    $C.@TextButton #PrevPageButton { Text: "< PREV"; Anchor: (Width: 90, Height: 28); Style: @TabStyle; }
    Group { FlexWeight: 1; }
    Label #PageLabel { Style: (TextColor: #9ab6d4, FontSize: 13, HorizontalAlignment: Center, VerticalAlignment: Center); Text: "Page 1/1"; }
    Group { FlexWeight: 1; }
    $C.@TextButton #NextPageButton { Text: "NEXT >"; Anchor: (Width: 90, Height: 28); Style: @TabStyle; }
}
```

Added to:
- [`LeaderboardsPage.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPage.ui)
- [`LeaderboardsPagePartner.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPagePartner.ui)
- [`XpStatsPage.ui`](src/main/resources/Common/UI/Custom/Pages/XpStats/XpStatsPage.ui)
- [`XpStatsAdminPage.ui`](src/main/resources/Common/UI/Custom/Pages/XpStats/XpStatsAdminPage.ui)
- [`XpStatsLeaderboardPage.ui`](src/main/resources/Common/UI/Custom/Pages/XpStats/XpStatsLeaderboardPage.ui)

#### Java Changes

- [`LeaderboardsUIPage`](src/main/java/com/airijko/endlessleveling/ui/LeaderboardsUIPage.java) — +53 lines of pagination state + `prev` / `next` actions driving a page index against the filtered row list; page label formatted as `Page <idx+1>/<pageCount>`.
- [`XpStatsUIPage`](src/main/java/com/airijko/endlessleveling/ui/XpStatsUIPage.java) — +92 / -35 for pagination on personal history rows.
- [`XpStatsAdminUIPage`](src/main/java/com/airijko/endlessleveling/ui/XpStatsAdminUIPage.java) — +45 / -11 for pagination on admin table.
- [`XpStatsLeaderboardUIPage`](src/main/java/com/airijko/endlessleveling/ui/XpStatsLeaderboardUIPage.java) — +30 / -5 for pagination on XP leaderboard.

### Leaderboards Podium Fixes (`Bug Fix + UI Polish`)

Two sequential commits (`LEADERBOARD FIXES`, `UI UPDATE FIX`) restyled the podium layout introduced in 7.8.0 and added a partner-branded variant.

- [`LeaderboardsUIPage`](src/main/java/com/airijko/endlessleveling/ui/LeaderboardsUIPage.java) — 200+ line churn (+157 / -46 then +41 / -18) for podium population, table filter restructure, and the new partner-page code path.
- [`LeaderboardsPage.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPage.ui) — layout adjustments around the podium + table boundary.
- `LeaderboardsPodiumFirst.ui` / `LeaderboardsPodiumSecond.ui` / `LeaderboardsPodiumThird.ui` — polish: tile anchor, icon frame, stat row spacing, text colours.
- [`LeaderboardsRow.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsRow.ui) — minor styling pass.
- **New [`LeaderboardsPagePartner.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPagePartner.ui)** — 332 lines, partner-branded mirror of `LeaderboardsPage.ui` (same podium + table + pagination structure, partner assets / colour theme).

### FrozenDomainAugment — Aura VFX Rewrite (`Refactor + Performance`)

The old expanding-ring pulse VFX driven by `ActivePulse` / `ACTIVE_PULSES` is removed. Replaced with a simpler stationary aura.

**Removed:**

- Constants: `TRIGGER_PULSE_VFX_IDS` (`Impact_Blade_01`), `TRIGGER_PULSE_DURATION_MILLIS` (250), `TRIGGER_PULSE_STEP_MILLIS` (50), `TRIGGER_PULSE_MIN_POINT_COUNT` (10), `TRIGGER_PULSE_MAX_POINT_COUNT` (36), `TRIGGER_PULSE_MIN_LAYER_COUNT` (2), `TRIGGER_PULSE_MAX_LAYER_COUNT` (4), `TRIGGER_PULSE_START_RADIUS` (0.1), `TRIGGER_PULSE_MIN_LAYER_SPACING` (0.2), `TRIGGER_PULSE_MAX_LAYER_SPACING` (0.45), `TRIGGER_PULSE_Y_OFFSET` (0.3).
- State class `ActivePulse { sourceRef, sourceUuid, startedAt, expiresAt, lastVisualAt, endRadius, soundPlayed }` and its `ConcurrentHashMap<String, ActivePulse> ACTIVE_PULSES`.

**Added:**

- Constant `AURA_VFX_IDS = { "Totem_Slow_Circle1", "Totem_Slow_Circle2" }`.
- Constant `AURA_VISUAL_INTERVAL_MILLIS = 500L` (was 50 ms step on the pulse path).
- Constant `AURA_VFX_Y_OFFSET = 0.3D` (kept the existing Y offset).
- State class `AuraVisualState { long lastVisualAt }` and its `ConcurrentHashMap<String, AuraVisualState> AURA_VISUAL_STATE`.
- New imports: `SpatialResource`, `EntityModule`, `it.unimi.dsi.fastutil.objects.ObjectList` (used by the new aura spawn path).

Net diff: **−135 / +53** in [`FrozenDomainAugment.java`](src/main/java/com/airijko/endlessleveling/augments/types/FrozenDomainAugment.java). Trigger-pulse SFX IDs (`SFX_Arrow_Frost_Miss`, `SFX_Arrow_Frost_Hit`, `SFX_Ice_Ball_Death`) are preserved.

### WitherAugment — Slow Fallback Logging Fix (`Bug Fix`)

[`WitherAugment.applySlowEffectFallback`](src/main/java/com/airijko/endlessleveling/augments/types/WitherAugment.java) now returns `boolean`. Caller logic flipped so the `"MovementManager missing"` / `"movement settings missing"` warnings fire only when the effect-fallback path *also* fails:

```java
boolean fallbackApplied = applySlowEffectFallback(state, commandBuffer, ref, key);
if (!fallbackApplied && !state.loggedMissingMovementManager) {
    LOGGER.atWarning().log(
            "Wither slow unavailable: MovementManager missing and fallback failed key=%s target=%s",
            key, ref);
    state.loggedMissingMovementManager = true;
}
return;
```

Previously both the warning and the fallback ran unconditionally, producing noisy logs on every Wither tick where the MovementManager path was unavailable even when the effect fallback was successfully applying slow.

### RaceManager — Crit-Lock Ascension Fallback (`Bug Fix`)

[`RaceManager`](src/main/java/com/airijko/endlessleveling/races/RaceManager.java): when an ascension requirement names `PRECISION` or `FEROCITY` and the player has that attribute crit-locked (via `SkillManager.isCritAttributeLocked`), the check now falls back to the class's primary damage stat:

```java
if (isCritLockedAttribute(attr, data)) {
    SkillAttributeType fallback = resolvePrimaryDamageStat(data);
    if (fallback != null && fallback != attr) {
        int fallbackLevel = data.getPlayerSkillAttributeLevel(fallback);
        if (fallbackLevel >= requiredLevel) {
            continue;
        }
    }
}
```

`resolvePrimaryDamageStat(PlayerData)` reads the player's primary `CharacterClassDefinition.getDamageType()`:
- `"magic"` → `SORCERY`
- `"hybrid"` → higher of `STRENGTH` vs `SORCERY`
- anything else (including null class) → `STRENGTH`

**Why:** a crit-lock on `PRECISION`/`FEROCITY` was preventing ascension for classes whose real damage stat is the *other* attribute, even though the player had the required attribute level on their true damage stat. New imports: `CharacterClassDefinition`, `ClassManager`, `SkillManager`.

### ProfileUIPage — Tiered Augment Entry Backgrounds (`UI Polish`)

[`ProfileUIPage`](src/main/java/com/airijko/endlessleveling/ui/ProfileUIPage.java) per-augment-entry binding now toggles tier backgrounds:

```java
PassiveTier resolvedTier = parseTierLabel(tierLabel);
ui.set(base + " #ItemBgCommon.Visible", resolvedTier == null || resolvedTier == PassiveTier.COMMON);
ui.set(base + " #ItemBgElite.Visible", resolvedTier == PassiveTier.ELITE);
ui.set(base + " #ItemBgLegendary.Visible", resolvedTier == PassiveTier.LEGENDARY);
ui.set(base + " #ItemBgMythic.Visible", resolvedTier == PassiveTier.MYTHIC);
```

[`ProfileAugmentEntry.ui`](src/main/resources/Common/UI/Custom/Pages/Profile/ProfileAugmentEntry.ui) restructured to host the 4 stacked tier-background images. Mythic / Legendary / Elite augments now visually match their rarity on the profile page.

### Config Changes

- [`leveling.yml`](src/main/resources/leveling.yml) — `prestige.tiers[MYTHIC].prestige_levels`: `[15]` → `[20]`.
- [`manifest.json`](src/main/resources/manifest.json) — `Version`: `7.8.0` → `7.8.1`.
- `gradle.properties` — `version=7.8.1`.

### Asset Recompression

~35 PNG files recompressed with no visual change. Representative deltas:

| Asset | Before | After |
|-------|-------|-------|
| `Common/Images/Branding/EndlessLeveling.png` | 59 055 | 28 123 |
| `UI/Custom/Hud/Endless_XPFill.png` | 605 657 | 322 086 |
| `UI/Custom/Hud/Partner_XPFill.png` | 617 838 | 325 029 |
| `UI/Custom/Pages/Addons/Images/EndlessBanner.png` | 1 053 055 | 698 961 |
| `UI/Custom/Tiles/TileLegendary.png` | 196 470 | 86 693 |
| `UI/Custom/Tiles/TileMythic.png` | 193 676 | 82 941 |
| `UI/Custom/Dungeons/Cards/Images/EndgameFrozenPlaceholder.png` | 58 020 | 21 055 |
| `UI/Custom/Pages/Nav/Icons/NavDungeonsIcon@2x.png` | 3 753 | 2 979 |

All 5 tile rarities, all 5 tile borders, HUD XP/shield/duration bars, nav icons, dungeon placeholder cards, and branding banners recompressed. Overall plugin JAR size drops materially.

### Misc UI

- [`ClassesPage.ui`](src/main/resources/Common/UI/Custom/Pages/Classes/ClassesPage.ui) — 133-line layout pass on top of the 7.8.0 carousel refactor.
- [`ProfilePagePartner.ui`](src/main/resources/Common/UI/Custom/Pages/Profile/ProfilePagePartner.ui) — 869-line restructure to align the partner profile page with the 7.8.0 main profile layout (weapon / augment icons, promoted XP bar, widened main panel).

### Files Changed

- **Java (8):** `FrozenDomainAugment`, `WitherAugment`, `RaceManager`, `LeaderboardsUIPage`, `ProfileUIPage`, `XpStatsAdminUIPage`, `XpStatsLeaderboardUIPage`, `XpStatsUIPage`.
- **.ui (12):** `ClassesPage`, `LeaderboardsPage`, `LeaderboardsPagePartner` (new), `LeaderboardsPodiumFirst/Second/Third`, `LeaderboardsRow`, `ProfileAugmentEntry`, `ProfilePagePartner`, `XpStatsPage`, `XpStatsAdminPage`, `XpStatsLeaderboardPage`.
- **Config (3):** `gradle.properties`, `manifest.json`, `leveling.yml`.
- **Assets (~35 PNGs):** branding, HUD, tiles, nav icons, dungeon placeholder cards.

---

## 2026-04-12 — 7.8.0 (compared to 7.7.4 and 7.7.5)

A **UI overhaul** release. The headlines are a **carousel card layout** replacing the row-based class and race browsers, a **leaderboard podium** for the top 3 players, **weapon and augment icons** on the profile page, and a broad **layout and visual polish pass** across all five main UI pages.

### Highlights

- **Classes page — carousel cards** — the old left-panel row list is replaced by a horizontally-scrolling carousel of tall cards. Each card shows the class icon, name, role tag, truncated description, weapon multipliers, passives, and evolution-path count. Clicking a card transitions to the existing detail view; a new BACK button returns to the carousel.
- **Races page — carousel cards** — same carousel pattern as classes. Race cards additionally show all 10 skill-attribute stat previews (Life Force, Strength, Sorcery, Defense, Haste, Precision, Ferocity, Stamina, Flow, Discipline) with colour-coded values. Carousel header now displays active race name and cooldown inline.
- **Leaderboards page — podium layout** — top 3 players render in a dedicated podium section with 2nd | 1st | 3rd visual arrangement, themed tile backgrounds (Legendary/Lightning/Life), and unique essence icons per rank. 4th place onward renders in a standard table with alternating row backgrounds. Container widened from 1000 → 1400 px.
- **Profile page — weapon & augment icons** — weapon bonuses now display item icons resolved via the new `WeaponIconTheme` enum. Augment entries show category-based icons resolved from `PassiveCategory.getIconItemId()`. Layout widened (sidebar removed, main panel 1320 px), XP bar and identity stats promoted to header.
- **XP Stats page — layout widened** — container widened from 1000 → 1400 px, content switched from left-split to top-stacked layout, tab bar added, header restyled with `ObjectivePanelContainer` background.
- **Skills page — visual polish** — removed opaque dark backgrounds from info block, auto-allocate, and quick-spend sections; adjusted divider and footer colours.
- **New `WeaponIconTheme` enum** — maps 12 weapon category keys (sword, longsword, dagger, daggers, bow, shortbow, staff, spear, mace, battleaxe, spellbook, grimoire) to representative Hytale item icon IDs with a fallback.
- **11 new `.ui` templates** added; 14 existing `.ui` templates restyled.

### Classes Page — Carousel Card Layout (`UI Overhaul`)

The classes browser is restructured around a two-state view: a **carousel** for browsing and a **detail panel** for inspecting/selecting.

#### New Templates

- [`ClassCarouselCard.ui`](src/main/resources/Common/UI/Custom/Pages/Classes/ClassCarouselCard.ui) — 340×580 px card with bordered surface, item icon, class name, role tag, description (truncated to 120 chars), weapon rows, passive rows, evolution-path hint, and a status badge overlay (PRIMARY / SECONDARY). Active-class cards get a highlighted background and cyan top-accent.
- [`ClassCardWeaponRow.ui`](src/main/resources/Common/UI/Custom/Pages/Classes/ClassCardWeaponRow.ui) — compact row displaying a weapon type name and its damage multiplier.
- [`ClassCardPassiveRow.ui`](src/main/resources/Common/UI/Custom/Pages/Classes/ClassCardPassiveRow.ui) — compact row displaying a passive name label.

#### Layout Changes

- [`ClassesPage.ui`](src/main/resources/Common/UI/Custom/Pages/Classes/ClassesPage.ui): the 300 px `ObjectiveContainer` left sidebar and `#ClassRows` list are removed. Replaced by a `#CarouselView` (full-width carousel header + horizontally-scrolling card strip) and a `#DetailView` (existing detail panel). Only one view is visible at a time.
- Carousel header shows class count, primary class name, secondary class name, and a subheading — all inside an `ObjectivePanelContainer` background.
- Cooldown labels shortened: `"Class Swap Cooldown"` → `"Cooldown"`, `"Primary"` → `"Pri:"`, `"Secondary"` → `"Sec:"`.

#### Java Changes

- [`ClassesUIPage`](src/main/java/com/airijko/endlessleveling/ui/ClassesUIPage.java):
  - New `detailViewActive` boolean controls which view group is visible via `applyViewState()`.
  - `buildClassList` / `refreshClassList` renamed to `buildCarousel` / `refreshCarousel`. Card population delegated to `applyCarouselCard()` which sets icon, role tag, description, status badge, background state, weapon rows, passive rows (non-innate), and evolution-path hint.
  - Event bindings changed: the entire card is the click target (not a nested `#ViewClassButton`).
  - New `class:back` action sets `detailViewActive = false` and refreshes the UI.
  - `class:view:<id>` now sets `detailViewActive = true`.
  - Detail view now sets `#DetailClassIcon.ItemId` when an icon is available.

### Races Page — Carousel Card Layout (`UI Overhaul`)

Mirror of the classes carousel pattern, adapted for race data.

#### New Templates

- [`RaceCarouselCard.ui`](src/main/resources/Common/UI/Custom/Pages/Races/RaceCarouselCard.ui) — 340×580 px card identical in structure to `ClassCarouselCard.ui`, but with a 10-row stat preview section instead of weapon rows. Each attribute has its own colour-coded label/value pair.
- [`RaceCardPassiveRow.ui`](src/main/resources/Common/UI/Custom/Pages/Races/RaceCardPassiveRow.ui) — compact passive name row.

#### Layout Changes

- [`RacesPage.ui`](src/main/resources/Common/UI/Custom/Pages/Races/RacesPage.ui): same `#CarouselView` / `#DetailView` split as classes. Carousel header shows active race name, cooldown status, race count, and subheading.

#### Java Changes

- [`RacesUIPage`](src/main/java/com/airijko/endlessleveling/ui/RacesUIPage.java):
  - New `detailViewActive` boolean and `applyViewState()`, same pattern as `ClassesUIPage`.
  - `buildRaceList` / `refreshRaceList` renamed to `buildCarousel` / `refreshCarousel`.
  - `applyCarouselCard()` sets icon, description (abbreviated to 120 chars), status badge, background state, all 10 stat values via `applyCardStat()` (respects `isSkillAttributeHidden`), passive rows, and evolution-path hint.
  - `updateCarouselHeader()` displays active race name and pre-computes cooldown status inline (ready / bypassed / exhausted / formatted duration).
  - New `race:back` action; `race:view:<id>` now sets `detailViewActive = true`.
  - Detail view now sets `#DetailRaceIcon.ItemId` when an icon is available.
  - Confirm button text changed: `"SWAP"` → `"SWAP RACE"`.

### Leaderboards Page — Podium Layout (`UI Overhaul`)

Top 3 players graduate from coloured rows into a visual podium; 4th place onward stays tabular.

#### New Templates

- [`LeaderboardsPodiumFirst.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPodiumFirst.ui) — 320×300 px card with `TileLegendary.png` background, `TileBorderLegendary.png` icon frame, gold text colours, and stat breakdown (Race, Class, Prestige, Level, XP) with alternating row backgrounds.
- [`LeaderboardsPodiumSecond.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPodiumSecond.ui) — same structure, silver/blue theme.
- [`LeaderboardsPodiumThird.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPodiumThird.ui) — same structure, bronze/green theme.

#### Layout Changes

- [`LeaderboardsPage.ui`](src/main/resources/Common/UI/Custom/Pages/Leaderboards/LeaderboardsPage.ui): container widened 1000 → 1400 px. Content layout changed from `Left` to `Top`. New `#PodiumSection` with `#PodiumCards` container and `#PodiumTitle` label. Separate `#TableSection` for `#RowCards`. Header and controls restyled with `ObjectivePanelContainer` pill backgrounds. Filter controls restructured into a dedicated `#ControlSection`.

#### Java Changes

- [`LeaderboardsUIPage`](src/main/java/com/airijko/endlessleveling/ui/LeaderboardsUIPage.java):
  - Podium population uses a `visualOrder` array to render cards in 2nd | 1st | 3rd order (classic podium arrangement). Each podium card gets a rank label (`1ST` / `2ND` / `3RD`), a unique essence icon (`Ingredient_Ice_Essence`, `Ingredient_Lightning_Essence`, `Ingredient_Life_Essence`), and the full stat set.
  - `#PodiumSection.Visible` and `#TableSection.Visible` toggled based on entry count.
  - Table rows (4th+) now use zero-based indexing into `#RowCards` and apply `#RowBgAlt.Visible` on odd rows for alternating striping.
  - Filter label truncation widened from 14 → 24 characters (substring 11 → 21).

### Profile Page — Weapon & Augment Icons (`UI Overhaul`)

Weapon bonuses and augments now render with item icons instead of plain text entries.

#### New Templates

- [`ProfileWeaponEntry.ui`](src/main/resources/Common/UI/Custom/Pages/Profile/ProfileWeaponEntry.ui) — row with a 34×34 `ItemIcon #WeaponIcon` (framed by `TileDefault.png`), weapon name, and multiplier value.
- [`ProfileAugmentEntry.ui`](src/main/resources/Common/UI/Custom/Pages/Profile/ProfileAugmentEntry.ui) — row with a 34×34 `ItemIcon #AugmentIcon` (framed by `TileBorderCommon.png`), augment name, and tier label.

#### Java Changes

- [`ProfileUIPage`](src/main/java/com/airijko/endlessleveling/ui/ProfileUIPage.java):
  - New `WeaponEntry(weaponKey, label, value)` record replaces `PassiveEntry` for weapon data, carrying the raw weapon key so the icon can be resolved.
  - `renderWeaponSection()` replaces the generic `renderPassiveSection()` call for weapons. Each row uses the `WEAPON_ENTRY_TEMPLATE` and sets `#WeaponIcon.ItemId` via `WeaponIconTheme.resolveIcon(entry.weaponKey())`.
  - `AugmentEntry` and `AugmentGroupMeta` records gain an `iconItemId` field.
  - New `resolveAugmentIcon(AugmentDefinition)` method resolves the icon from the augment's `PassiveCategory`, falling back to `Ingredient_Ice_Essence`.
  - `renderAugmentSection()` now uses the `AUGMENT_ENTRY_TEMPLATE` and sets `#AugmentIcon.ItemId`.
- [`ProfilePage.ui`](src/main/resources/Common/UI/Custom/Pages/Profile/ProfilePage.ui): sidebar removed, main panel widened to 1320 px. XP bar (`#DetailXpBar`) and identity stats (`#DetailSummary` — Prestige, Level, Race, Class) promoted to an `ObjectivePanelContainer` header. Title changed from `$Nav.@PanelTitleStyle` to inline styled `"ENDLESS LEVELING"`.

### WeaponIconTheme Enum (`UI Overhaul`)

- [`WeaponIconTheme`](src/main/java/com/airijko/endlessleveling/enums/themes/WeaponIconTheme.java): new enum mapping 12 weapon category keys to representative Hytale item icon IDs. Case-insensitive lookup via `resolveIcon(String)` with `Weapon_Sword_Onyxium` as fallback. Covers: `sword`, `longsword`, `dagger`/`daggers`, `bow`/`shortbow`, `staff`, `spear`, `mace`, `battleaxe`, `spellbook`/`grimoire`.

### XP Stats Page (`UI Overhaul`)

- [`XpStatsPage.ui`](src/main/resources/Common/UI/Custom/Pages/XpStats/XpStatsPage.ui): container widened 1000 → 1400 px. Content layout switched from `Left` (side-by-side) to `Top` (stacked). Tab bar added (`#TabMyStats`). Header restyled: title left-aligned, subtitle moved into an `ObjectivePanelContainer` pill on the right.
- `XpStatsRow.ui`, `XpStatsRowFirst.ui`, `XpStatsRowSecond.ui`, `XpStatsRowThird.ui` — minor styling adjustments.

### Skills Page — Visual Polish (`UI Overhaul`)

- [`SkillsPage.ui`](src/main/resources/Common/UI/Custom/Pages/Skills/SkillsPage.ui): removed opaque `#0b141f` backgrounds from `#SkillsInfoBlock`, `#AutoAllocateRow`, and `#QuickSpendSection`. Divider colours lightened (`#243142` → `#2b3f58`). Main content background adjusted (`#050910(0.85)` → `#0d1a28(0.80)`). Footer restyled with lighter tones.

### Detail Template Restyling (`UI Overhaul`)

Several existing entry templates received tile-background upgrades and padding adjustments:

- `ClassEvolutionEntry.ui`, `RaceEvolutionEntry.ui` — switched from flat `#0f1824` background to `TileDefault2x.png` tile with `#1a2538` tint. Internal layout restructured with explicit padding group.
- `ClassPassiveEntry.ui`, `RacePassiveEntry.ui` — restyled with tile backgrounds.
- `LeaderboardsRow.ui` — added `#RowBgAlt` group for alternating row striping.
- `ProfileRacePassiveEntry.ui`, `ProfileSkillPassiveEntry.ui` — restyled.
- `ProfilePagePartner.ui` — minor adjustment.

---

## 2026-04-10 — 7.7.0 (compared to 7.6.1)

A security, necromancer, and balance patch on top of 7.6.1. The headlines are a **secondary class exploit fix**, a **necromancer augment overhaul** that filters COMMON-tier augments from summon mirroring and wires up owner-stat resolution on summon kills, **operator-only permission guards** on all admin commands, and a broad **dungeon damage scaling rebalance** that normalises per-level damage across all world settings.

### Highlights

- **Secondary class exploit fix** — upgraded (non-base-stage) classes can no longer be set as a secondary. Existing upgraded secondaries are automatically reverted to their base form on login.
- **Same-path secondary block** — a secondary class can no longer share the same ascension path as the player's primary (e.g. picking `brawler_exalted` as secondary when `brawler` is primary).
- **Necromancer augment tier filter** — summons now only inherit ELITE, LEGENDARY, and MYTHIC augments from the owner; COMMON-tier augments are excluded from mirroring.
- **Summon kill augment resolution** — `ArmyOfTheDeadDeathSystem` now resolves the summoner's `PlayerData` and `SkillManager` so OnKill augments (e.g. SoulReaver, BloodFrenzy) scale from the owner's stats instead of null fallbacks.
- **Summon death-system guard** — `MobLevelingSystem` no longer clears augments for managed summons on death, preventing the summon's mirrored loadout from being wiped before OnKill hooks fire.
- **Admin permission guards** — all admin/operator commands now use `OperatorHelper.denyNonAdmin()` instead of ad-hoc checks, with a unified denial message.
- **XP gain cap toggle** — new `xp_gain_cap_enabled` config key; when `false`, the per-kill XP cap is bypassed entirely.
- **Death XP penalty reduction** — `current_xp_percent` reduced from 15 → 12.5, `max_xp_percent` from 5 → 2.5.
- **Dungeon damage scaling normalised** — `Damage.Per_Level` set to `0.05` across shiva, major, and endgame dungeon world settings (was 0.3–0.4); default world `Per_Level` increased from `0.015` → `0.03`.
- **Global mob damage scaling softened** — `At_Negative_Max_Difference` raised from `0.5` → `0.75`, `Below_Negative_Max_Difference` from `0.25` → `0.5` (under-leveled mobs hit harder than 7.6.1 but still weaker than 7.6.0).
- **Gates nav button fix** — classpath check for `EndlessDungeons` before dispatching `/gate`; partner servers show "Gates are disabled." instead of the Patreon link.
- **Tracked gate snapshot** — `TrackedWaveGateSnapshot` now carries `expiryAtEpochMillis` for downstream consumers.
- New `necromancer_summons` debug section for detailed summon augment logging.

### Secondary Class Exploit Fix (`secondary class exploit fix`)

Players could previously equip an upgraded class form (e.g. exalted, elite) as their secondary class, or pick a secondary from the same ascension path as their primary, gaining passive bonuses they should not have access to.

#### New ClassManager Methods

- [`ClassManager`](src/main/java/com/airijko/endlessleveling/classes/ClassManager.java):
  - `isBaseStageClass(definition)` — returns `true` when the class's ascension stage is `"base"`.
  - `sharesClassPath(a, b)` — returns `true` when both classes share the same non-null ascension path.
  - `resolveBaseClassForPath(definition)` — finds the base-stage class registered on the same ascension path. Returns `null` when no base-stage class exists.

#### Data Resolution (Login / Profile Load)

- `ClassManager.resolveSecondaryClass()`:
  - If the stored secondary is an upgraded class, it is automatically **reverted** to the base form for that path (logged at INFO). If no base form exists, the secondary is cleared.
  - If the stored secondary shares the same ascension path as the primary, it is **cleared** (logged at INFO).

#### Assignment Guard (Runtime)

- `ClassManager.setSecondaryClass()`: rejects upgraded classes and same-path classes at assignment time, returning `null` without persisting.

#### Command Validation

- [`ClassChooseCommand`](src/main/java/com/airijko/endlessleveling/commands/classes/ClassChooseCommand.java): `/class choose secondary <class>` now returns an error message when the target class is not base-stage or shares a path with the primary.

#### UI Validation

- [`ClassesUIPage`](src/main/java/com/airijko/endlessleveling/ui/ClassesUIPage.java):
  - The `canSecondary` flag now additionally requires `isBaseStageClass(selection)` and `!sharesClassPath(selection, primary)` — the button is disabled in the UI when either condition fails.
  - The server-side secondary selection handler rejects upgraded and same-path classes with a localised error message.

### Necromancer Augment Fixes (`Fixed Necromancer Augments`)

#### COMMON-Tier Filter

- [`ArmyOfTheDeadPassive.filterNonCommonAugments`](src/main/java/com/airijko/endlessleveling/passives/type/ArmyOfTheDeadPassive.java) — new method that filters the owner's `selectedAugmentsMap` by `PassiveTier`, excluding `COMMON`-tier entries. Both `mirrorPlayerAugmentsToSummon` and `ensureSummonAugmentsInSync` now use this filter so summons only inherit ELITE, LEGENDARY, and MYTHIC augments.

#### Summon Kill — Owner Stat Resolution

- [`ArmyOfTheDeadDeathSystem`](src/main/java/com/airijko/endlessleveling/systems/ArmyOfTheDeadDeathSystem.java):
  - Now calls `ArmyOfTheDeadPassive.ensureSummonAugmentsInSync` before dispatching the kill hook — a summon that spawned before the owner selected augments would otherwise miss OnKill effects.
  - Resolves the summoner's `PlayerData` and `SkillManager` via `getManagedSummonOwnerUuid`, so OnKill augments can scale from the owner's stats instead of null fallbacks.

#### Summon Death Guard

- [`MobLevelingSystem`](src/main/java/com/airijko/endlessleveling/mob/MobLevelingSystem.java): the augment-clear path on mob death now checks `ArmyOfTheDeadPassive.isManagedSummonByUuid(entityUuid)` and skips managed summons, preventing the mirrored augment set from being wiped before OnKill hooks fire.

#### Debug Section

- [`MobAugmentExecutor`](src/main/java/com/airijko/endlessleveling/augments/MobAugmentExecutor.java): new `necromancer_summons` debug section with detailed logging across `dispatchSummonOnHit` (miss detection, per-augment before/after damage, final result).
- [`MobDamageScalingSystem`](src/main/java/com/airijko/endlessleveling/mob/MobDamageScalingSystem.java): summon combat path logs before/after damage and proc-skip events when `necromancer_summons` is enabled.
- [`LoggingManager`](src/main/java/com/airijko/endlessleveling/managers/LoggingManager.java): `necromancer_summons` debug section expands to three logger prefixes (`ArmyOfTheDeadPassive`, `MobDamageScalingSystem`, `MobAugmentExecutor`).

### Admin Permission Guards (`permission nodes`)

- New [`OperatorHelper.denyNonAdmin`](src/main/java/com/airijko/endlessleveling/util/OperatorHelper.java) — checks `hasAdministrativeAccess` and sends a denial message to non-admins, returning `true` (denied) so the caller can early-return.
- All 17 admin commands (`ReloadCommand`, `ResetAllCommand`, `SetLevelCommand`, `SetPrestigeCommand`, `DebugCommand`, `AugmentTestCommand`, `ApplyModifiersCommand`, `ResetLevelCommand`, `ResetPrestigeCommand`, `ResetSkillPointsCommand`, `ResetCooldownsCommand`, `SyncSkillPointsCommand`, `ResetAllPlayersCommand`, `ResetAugmentsCommand`, `ResetAugmentsAllPlayersCommand`, `AugmentAddRerollCommand`, `AugmentRefreshCommand`) now use `OperatorHelper.denyNonAdmin(senderRef)` as their first check.
- `XpStatsAdminSubCommand` gated to operators.

### XP Gain Cap Toggle (`Tracked Gates Improvements`)

- [`LevelingManager`](src/main/java/com/airijko/endlessleveling/leveling/LevelingManager.java): new `xpGainCapEnabled` field loaded from `default.xp_gain_cap_enabled` (defaults to `true`). When `false`, the per-kill XP gain cap in `grantXp` is bypassed entirely.
- [`leveling.yml`](src/main/resources/leveling.yml): new `xp_gain_cap_enabled: true` key.

### Death XP Penalty Reduction (`Balance`)

- [`leveling.yml`](src/main/resources/leveling.yml): `death_xp_penalty.current_xp_percent` reduced from `15` → `12.5`; `death_xp_penalty.max_xp_percent` from `5` → `2.5`.

### Dungeon Damage Scaling Normalisation (`Balance`)

All dungeon world settings now use a uniform `Damage.Per_Level` of `0.05`, down from the previous per-file values:

| World setting | Before | After |
|---|---|---|
| `shiva-dungeons.json` (both tiers) | `0.3` | `0.05` |
| `endgame-dungeons.json` (all 3 entries) | `0.4` | `0.05` |
| `major-dungeons.json` (all 4 entries) | `0.04` | `0.05` |
| `default.json` | `0.015` | `0.03` |

Default world defense scaling also retuned: `At_Positive_Max_Difference` `0.8` → `0.5`, `Above_Positive_Max_Difference` `0.9` → `0.8`.

### Global Mob Damage Scaling Adjustment (`Balance`)

- `global.json` `Damage_Max_Difference`: `At_Negative_Max_Difference` raised from `0.5` → `0.75`, `Below_Negative_Max_Difference` from `0.25` → `0.5`. Under-leveled mobs now deal 75% at the cap instead of 50%, and 50% beyond instead of 25%.

### Gates Nav Button — Classpath Guard (`Fixed Gate Nav Notif`)

- [`NavUIHelper`](src/main/java/com/airijko/endlessleveling/ui/NavUIHelper.java):
  - New `isEndlessDungeonsPresent()` method performs a `Class.forName` classpath check for `com.airijko.endlessleveling.EndlessDungeonsAndGates`.
  - `openGatesGui()` now returns `false` early when `EndlessDungeons` is not loaded, instead of dispatching `/gate` and letting `CommandManager` send "Command not found!" to the player.
  - When the gate button is clicked and the addon is absent, partner servers show `"Gates are disabled."` (via `PartnerConsoleGuard` check); non-partner servers show the existing Patreon upsell link.
  - Casing fix: `"Gates are Disabled."` → `"Gates are disabled."`.

### Tracked Wave Gate Snapshot (`Tracked Gates Improvements`)

- [`TrackedWaveGateSnapshot`](src/main/java/com/airijko/endlessleveling/api/gates/TrackedWaveGateSnapshot.java): new `expiryAtEpochMillis` field added to the record, exposing the gate's absolute expiry time to downstream consumers (e.g. gate tracker UI, addon APIs).

### Config Restructuring (`permission nodes`)

- [`config.yml`](src/main/resources/config.yml): `force_builtin_*` and `enable_builtin_*` blocks moved from bottom to top of file for visibility. New `necromancer_summons` entry added to `debug_sections` documentation comment.

### Version

- `gradle.properties`: `7.6.1` → `7.7.0`.

### Files Changed

```
gradle.properties                                                  |  2 +-
manifest.json                                                      |  2 +-
api/gates/TrackedWaveGateSnapshot.java                             |  +1
augments/MobAugmentExecutor.java                                   | +50
classes/ClassManager.java                                          | +92 (3 new methods, 2 resolution guards)
commands/ (17 admin commands)                                      | +8 / -5 each (OperatorHelper guard)
commands/classes/ClassChooseCommand.java                           | +18 (2 validation blocks)
commands/xpstats/ (5 files)                                        | new (carried from 7.6.1 permission rebase)
leveling/LevelingManager.java                                      | +10 / -3
managers/LoggingManager.java                                       | +33 / -3
mob/MobDamageScalingSystem.java                                    | +19
mob/MobLevelingSystem.java                                         | +2 / -1
passives/type/ArmyOfTheDeadPassive.java                            | +115 / -28
systems/ArmyOfTheDeadDeathSystem.java                              | +30 / -3
ui/ClassesUIPage.java                                              | +24 (canSecondary guard, UI handler guard)
ui/NavUIHelper.java                                                | +34 / -7
util/OperatorHelper.java                                           | +16
config.yml                                                         | +24 / -20 (restructured + necromancer_summons doc)
leveling.yml                                                       | +3 / -2
world-settings/default.json                                        | +3 / -3
world-settings/endgame-dungeons.json                               | +3 / -3
world-settings/global.json                                         | +2 / -2
world-settings/major-dungeons.json                                 | +4 / -4
world-settings/shiva-dungeons.json                                 | +2 / -2
```

## 2026-04-09 — 7.6.1 (compared to 7.6.0)

An analytics and stability patch on top of 7.6.0. The headline is a full **XP Stats** tracking system with per-player hourly/daily analytics, a global leaderboard, exploit-detection flagging, and an admin panel — all accessible from a new nav button and `/xpstats` command. Also fixes stale race/class ID resolution on login, the `addswap` console guard, Shiva dungeon over-tuning, footer navbar sizing, and compresses branding images by ~90 %.

### Highlights

- New **XP Stats** system — rolling 24-hour and 7-day XP tracking per profile with momentum scoring and prestige history.
- New **XP Stats Leaderboard** — top-100 rankings by XP/24h, XP/7d, Total XP, and Momentum, with gold/silver/bronze podium rows.
- New **XP Stats Admin** panel — operator-only view with a flagged-players exploit-detection tab (momentum > 3.0 or XP/24h > 500k).
- New **XP Stats** nav button in the footer navbar; all footer `@BottomNavBar` containers made auto-width.
- Stale race/class IDs are now nullified on login when the ID no longer exists in the server registry.
- `addswap` console guard fixed — players could not run the command because the console-only check blocked everyone.
- Shiva dungeon mob scaling reduced (HP per level `0.1` → `0.075`, damage per level `0.4` → `0.3`).
- Global mob damage scaling now penalises under-leveled mobs; same-level mobs deal exactly `1.0×` damage; positive-side caps reduced (`3.0×` → `1.5×` at cap, `5.0×` → `3.0×` beyond).
- Branding PNGs compressed from ~3.2 MB total to ~226 KB.

### XP Stats System (`New System`)

#### Data Model

- New [`XpStatsData`](src/main/java/com/airijko/endlessleveling/xpstats/XpStatsData.java) — per-profile analytics container. Tracks `totalXp` (lifetime), a rolling `hourly[24]` array (UTC hour buckets), a rolling `daily[7]` array (UTC day buckets), cursor indices, and a `prestigeHistory` list.
- `recordXpGain(amount)` rotates stale buckets via `rotateBuckets()`, then adds to totalXp, current hourly, and current daily slots. Negative/zero amounts are ignored.
- `getMomentum()` = `xp24h / avg(other 6 daily slots)`. Returns `0.0` when xp24h is zero; capped at `999.0` when the weekly baseline is zero.
- `recordPrestige(level)` appends a timestamped `PrestigeEvent` record.
- Composite cache key: [`XpStatsKey`](src/main/java/com/airijko/endlessleveling/xpstats/XpStatsKey.java) `record(UUID, int profileIndex)`.

#### Manager

- New [`XpStatsManager`](src/main/java/com/airijko/endlessleveling/xpstats/XpStatsManager.java) — ConcurrentHashMap cache with per-UUID `ReentrantLock` double-checked locking.
- Persisted as JSON at `playerdata/<uuid>/xpstats/<profileIndex>_stats.json`. Atomic write via `.tmp` + `Files.move(ATOMIC_MOVE)`.
- `saveAll()` flushes all dirty entries; `saveAllForPlayer(uuid)` and `evict(uuid)` for disconnect cleanup.
- `loadAllEntries()` merges in-memory cache with a disk scan of offline player files (used by leaderboard).

#### Leaderboard Service

- New [`XpStatsLeaderboardService`](src/main/java/com/airijko/endlessleveling/xpstats/XpStatsLeaderboardService.java) — enriches entries with player name, profile name, prestige, and level from `PlayerDataManager`. Sorts by `LeaderboardType` enum: `XP_24H`, `XP_7D`, `TOTAL_XP`, `MOMENTUM`.
- `getFlaggedPlayers(momentumThreshold, xp24hThreshold)` returns entries where momentum > threshold **or** xp24h > threshold.

#### Autosave

- New [`XpStatsAutosaveSystem`](src/main/java/com/airijko/endlessleveling/systems/XpStatsAutosaveSystem.java) — ticking system that flushes all dirty stats to disk every **300 seconds** (5 minutes).

#### Integration Hooks

- [`LevelingManager`](src/main/java/com/airijko/endlessleveling/leveling/LevelingManager.java): `grantXp` and `adjustRawXp` call `XpStatsManager.recordXpGain`; `prestige` calls `recordPrestige`.
- [`PlayerDataListener`](src/main/java/com/airijko/endlessleveling/listeners/PlayerDataListener.java): pre-loads active profile stats on connect; saves and evicts on disconnect.
- [`ProfileSelectSubCommand`](src/main/java/com/airijko/endlessleveling/commands/profile/ProfileSelectSubCommand.java): saves current profile stats and loads new profile stats on profile switch.
- [`EndlessLevelingShutdownCoordinator`](src/main/java/com/airijko/endlessleveling/shutdown/EndlessLevelingShutdownCoordinator.java): `saveAll()` during shutdown, `clearRuntimeState()` during cleanup.

### XP Stats Commands (`New Commands`)

- New [`/xpstats`](src/main/java/com/airijko/endlessleveling/commands/xpstats/XpStatsCommand.java) (alias `/xps`) — opens the XP Stats UI page. Player-only.
- `/xpstats top` — opens the global leaderboard UI with tab buttons for each sort type.
- `/xpstats profiles` — chat output listing all of the sender's profiles ranked by xp24h.
- `/xpstats profile <slot>` — chat output with detailed per-profile stats: totalXp, xp24h, xp7d, momentum, non-zero hourly buckets, and full prestige history.
- `/xpstats admin` — operator-only, opens the admin UI panel with leaderboard and flagged-players tabs.

### XP Stats UI (`New UI`)

- New [`XpStatsUIPage`](src/main/java/com/airijko/endlessleveling/ui/XpStatsUIPage.java) — tabbed page with My Stats / Leaderboard / Admin LB / Admin Flagged tabs (admin tabs gated by operator access).
  - **My Stats**: summary row (totalXp, xp24h, xp7d, momentum), hourly XP bars (non-zero hours only), daily breakdown (all 7 days), prestige history.
  - **Leaderboard**: top 100 entries; clicking the tab again cycles sort through XP_24H → XP_7D → TOTAL_XP → MOMENTUM.
  - **Admin Flagged**: entries with momentum > 3.0 or xp24h > 500,000.
- New [`XpStatsLeaderboardUIPage`](src/main/java/com/airijko/endlessleveling/ui/XpStatsLeaderboardUIPage.java) — standalone leaderboard page with dedicated sort-type tab buttons.
- New [`XpStatsAdminUIPage`](src/main/java/com/airijko/endlessleveling/ui/XpStatsAdminUIPage.java) — standalone admin panel with Refresh button.
- UI templates: [`XpStatsPage.ui`](src/main/resources/Common/UI/Custom/Pages/XpStats/XpStatsPage.ui), `XpStatsLeaderboardPage.ui`, `XpStatsAdminPage.ui`, `XpStatsRow.ui`, `XpStatsRowFirst.ui` (gold), `XpStatsRowSecond.ui` (silver), `XpStatsRowThird.ui` (bronze), `XpStatsHourlyBar.ui`, `XpStatsDailyBar.ui`, `XpStatsPrestigeEntry.ui`.

### TopNavBar — XP Stats Button (`UI`)

- [`TopNavBar.ui`](src/main/resources/Common/UI/Custom/Pages/Nav/TopNavBar.ui): Leaderboards and the new **XP Stats** button moved from the top primary row into the `@BottomNavBar` footer row. Footer is now: Leaderboards / XP Stats / Addons / Support / Settings.
- [`NavUIHelper`](src/main/java/com/airijko/endlessleveling/ui/NavUIHelper.java): new `#NavXpStats` / `#NavXpStatsLabel` bindings, selected-style logic, and `nav:xpstats` action wiring that opens `XpStatsUIPage`.

### Footer NavBar Auto-Width (`UI Fix`)

- All 15 pages' `@BottomNavBar` container changed from `Anchor: (Width: 480, Height: 92)` → `Anchor: (Height: 92)` so the footer auto-sizes to fit the additional XP Stats button.

### Stale Race/Class ID Resolution (`Fixed Login`)

- [`PlayerDataManager`](src/main/java/com/airijko/endlessleveling/player/PlayerDataManager.java): after resolving a race or class ID on login, the resolved ID is now checked against the live registry (`raceManager.getRace()` / `classManager.getClass()`). If the entry no longer exists — e.g. a removed race/class mod — the ID is nullified so the player can re-select via the emergency-swap mechanism instead of being stuck on a phantom selection.

### AddClassSwap Console Guard Fix (`Fixed Command`)

- [`AddClassSwapCommand`](src/main/java/com/airijko/endlessleveling/commands/classes/AddClassSwapCommand.java): the `PartnerConsoleGuard` check was missing a `!senderIsPlayer` gate, so it rejected **all** senders (including players) when no partner addon was loaded. Now only console senders are checked.

### Mob Damage Level-Difference Scaling — Below-Level Penalty (`Balance`)

`global.json` / `Mob_Scaling.Damage_Max_Difference` now penalises under-leveled mobs and reins in over-leveled mobs.

**Why it was a problem:** Before 7.6.1, the `Damage_Max_Difference` block only had the two positive-side keys. The negative side (mob below the player) was simply absent, so the engine defaulted `At_Negative_Max_Difference` to `1.0×` and the lerp ran from `1.0×` up to `3.0×` across the full range. This meant a same-level mob dealt **200%** of its raw base damage, and a mob anywhere in the negative range still dealt 100–200% — even a mob far below the player dealt full unscaled damage.

**The scaling system (unchanged):** `Mob_Level_Scaling_Difference.Range = 15` defines the cap. When `mob level − player level` is within `[−15, +15]` the damage multiplier is linearly interpolated between `At_Negative_Max_Difference` and `At_Positive_Max_Difference`. Beyond either end of the range, the flat `Below_*` / `Above_*` clamp is used.

**Full damage % at every 5-level step:**

| Level difference (mob − player) | Before (7.6.0) | After (7.6.1) | Reduction |
|---|---|---|---|
| **< −15** (far under-leveled, flat clamp) | **100%** *(defaulted)* | **25%** | −75% |
| **−15** (at negative cap) | **100%** | **50%** | −50% |
| **−10** | **133%** | **67%** | −50% |
| **−5** | **167%** | **83%** | −50% |
| **0** (same level) | **200%** | **100%** | −50% |
| **+5** | **233%** | **117%** | −50% |
| **+10** | **267%** | **133%** | −50% |
| **+15** (at positive cap) | **300%** | **150%** | −50% |
| **> +15** (far over-leveled, flat clamp) | **500%** | **300%** | −40% |

> Lerp formula: `multiplier = atNeg + (diff + range) / (range × 2) × (atPos − atNeg)`  
> Before: `lerp(1.0, 3.0)` — After: `lerp(0.5, 1.5)`

**Key takeaways:**
- **Same-level mobs** previously dealt **200%** damage — double their raw stat value — because the lerp midpoint between the default `1.0` and `3.0` landed there. They now deal **100%**, i.e. exactly what their stats say.
- **Under-leveled mobs** drop to **50%** at −15 levels and **25%** beyond that. A level 1 mob hitting a level 100 player goes from full-parity damage to trivially weak, as intended.
- **Over-leveled mobs** are also much less spikey: 300% at the cap instead of 500% beyond it, removing the main source of single-hit kills from high-level elites.
- The **50% reduction is consistent across the entire lerp range** (−15 to +15), which means the curve shape is the same — only the scale changed.

### Shiva Dungeon Scaling Reduction (`Balance`)

- `shiva-dungeons.json`: Tier ≤3 and Tier >3 entries both adjusted:
  - HP per level: `0.1` → `0.075`
  - Damage per level: `0.4` → `0.3`

### Branding Image Compression (`Assets`)

- `EndlessBanner.png`: 1,053 KB → 98 KB
- `EndlessBanner2.png`: 709 KB → 70 KB
- `EndlessLeveling.png`: 1,506 KB → 59 KB
- Copies under `Pages/Images/` synced to match.

### Version

- `gradle.properties`: `7.6.0` → `7.6.1`.
- `manifest.json`: synced to match.

### Files Changed

```
gradle.properties                                                  |  2 +-
manifest.json                                                      |  2 +-
EndlessLeveling.java                                               | +19
commands/CommandRegistrar.java                                     | +11 / -2
commands/classes/AddClassSwapCommand.java                          |  1 fix
commands/profile/ProfileSelectSubCommand.java                      | +11
commands/xpstats/XpStatsCommand.java                               | new
commands/xpstats/XpStatsAdminSubCommand.java                       | new
commands/xpstats/XpStatsProfileSubCommand.java                     | new
commands/xpstats/XpStatsProfilesSubCommand.java                    | new
commands/xpstats/XpStatsTopSubCommand.java                         | new
leveling/LevelingManager.java                                      | +19
listeners/PlayerDataListener.java                                  | +13
player/PlayerDataManager.java                                      | +12
shutdown/EndlessLevelingShutdownCoordinator.java                   | +14
systems/XpStatsAutosaveSystem.java                                 | new
ui/NavUIHelper.java                                                | +12
ui/XpStatsAdminUIPage.java                                         | new
ui/XpStatsLeaderboardUIPage.java                                   | new
ui/XpStatsUIPage.java                                              | new
xpstats/XpStatsData.java                                          | new
xpstats/XpStatsKey.java                                           | new
xpstats/XpStatsLeaderboardService.java                            | new
xpstats/XpStatsManager.java                                       | new
Nav/TopNavBar.ui                                                   | +74 / -15
XpStats/XpStatsPage.ui                                             | new
XpStats/XpStatsLeaderboardPage.ui                                  | new
XpStats/XpStatsAdminPage.ui                                        | new
XpStats/XpStatsRow.ui                                              | new
XpStats/XpStatsRowFirst.ui                                         | new
XpStats/XpStatsRowSecond.ui                                        | new
XpStats/XpStatsRowThird.ui                                         | new
XpStats/XpStatsHourlyBar.ui                                        | new
XpStats/XpStatsDailyBar.ui                                         | new
XpStats/XpStatsPrestigeEntry.ui                                    | new
Profile/ProfilePage.ui                                             |  1 fix
Profile/ProfilePagePartner.ui                                      |  1 fix
+ 13 more .ui pages (BottomNavBar auto-width fix)                  |  1 fix each
Common/Images/Branding/EndlessBanner.png                           | compressed
Common/Images/Branding/EndlessBanner2.png                          | compressed
Common/Images/Branding/EndlessLeveling.png                         | compressed
Pages/Images/EndlessBanner.png                                     | compressed
Pages/Images/EndlessBanner2.png                                    | compressed
world-settings/shiva-dungeons.json                                 | +4 / -4
```

## 2026-04-09 — 7.6.0 (compared to 7.5.0)

A balance, safety, and QoL pass on top of 7.5.0. The headlines are a new death XP penalty system, a per-kill XP gain cap, automatic friendly-mob blacklisting via the Attitude API, clearer attribute labels in the Skills UI, and a teleport-safety rewrite for the login safe-spawn path.

### Highlights

- New **death XP penalty** — players lose a percentage of current XP and max XP on death. Cannot lose a level or prestige.
- New **per-kill XP gain cap** — prevents exploitatively large single-kill payouts. Scales with level.
- New **Blacklist_Friendly_Mobs** world setting — automatically prevents leveling/XP from any mob whose `Role.defaultPlayerAttitude` is not `HOSTILE`, replacing the old 90+ keyword blacklist.
- New **Gain_XP_From_Blacklisted_Mob** toggle — when `false`, killing a blacklisted mob gives zero XP.
- Skills UI attribute labels made more descriptive (e.g. `PHYS DMG` → `PHYSICAL DMG`, `CRIT RATE` → `CRITICAL RATE`).
- TopNavBar reworked: Addons moved to footer, Leaderboards promoted to primary nav row.
- Dungeon cards cleaned up: removed redundant title labels, added author attribution.
- Safe-login teleport hardened against in-flight teleport collisions and same-world reposition bugs.
- Mob stat scaling rebalanced across default, endgame, major, and shiva dungeon world settings.

### Death XP Penalty (`New System`)

- New [`PlayerDeathXpPenaltySystem`](src/main/java/com/airijko/endlessleveling/systems/PlayerDeathXpPenaltySystem.java) — a `DeathSystems.OnDeathSystem` that fires `LevelingManager.applyDeathXpPenalty` on any player death.
- Penalty formula: `(current_xp_percent / 100) * currentXp + (max_xp_percent / 100) * xpForNextLevel`. XP is clamped at 0 — the player cannot lose a level or prestige from this.
- Configurable in `leveling.yml` under `death_xp_penalty`:
  - `enabled: true`
  - `current_xp_percent: 15` (% of current XP lost)
  - `max_xp_percent: 5` (% of max XP for the level lost)
- Wired into `EndlessLeveling.onEnable` alongside `XpEventSystem`.

### Per-Kill XP Gain Cap (`New Feature`)

- `LevelingManager.grantXp` now enforces `getXpGainCap(level)` before level-up checks. Formula: `xp_gain_cap_base + xp_gain_cap_per_level * max(0, level - xp_gain_cap_threshold)`.
- Defaults: base 40,000 XP, +520/level above level 100. At level 100 the cap is 40k, at level 200 it's 92k, at level 600 it's 300k.
- Configurable in `leveling.yml` under `default`: `xp_gain_cap_base`, `xp_gain_cap_per_level`, `xp_gain_cap_threshold`.

### Friendly Mob Blacklist (`Mob Leveling`)

- New `Blacklist_Friendly_Mobs` world setting (default: `true` in `global.json`). When enabled, any NPC whose `Role.getWorldSupport().getDefaultPlayerAttitude()` is not `HOSTILE` is treated as blacklisted — no level tag, no scaled stats.
- New `Gain_XP_From_Blacklisted_Mob` world setting (default: `false` in `global.json`). When `false`, `XpEventSystem` skips XP grant entirely for blacklisted mobs.
- The old 90+ keyword `Blacklist_Mob_Types.keywords` list in `default.json` has been removed — `Blacklist_Friendly_Mobs` replaces it with a single engine-backed check. Only `Totem` remains in the global keyword blacklist for edge cases.
- `MobLevelingManager`: new `isBlacklistFriendlyMobsEnabled`, `isGainXpFromBlacklistedMobEnabled`, and `isEntityFriendlyMob` methods. `isEntityBlacklisted` now checks friendly-mob status after the type blacklist.
- `XpEventSystem.onComponentAdded` now early-returns with zero XP when the mob is blacklisted and `Gain_XP_From_Blacklisted_Mob` is false.

### Skills UI — Clearer Attribute Labels (`Made attributes more clear`)

- [`SkillsUIPage`](src/main/java/com/airijko/endlessleveling/ui/SkillsUIPage.java) attribute display units updated:
  - Strength: `PHYS DMG` → `PHYSICAL DMG`
  - Sorcery: `MAG DMG` → `MAGICAL DMG`
  - Haste: `SPEED` → `MOVEMENT SPEED`
  - Precision: `CRIT RATE` → `CRITICAL RATE`
  - Ferocity: `CRIT DMG` → `CRITICAL DMG`
  - Discipline: `XP` → `XP BONUS`

### TopNavBar — Leaderboards Promoted (`UI`)

- [`TopNavBar.ui`](src/main/resources/Common/UI/Custom/Pages/Nav/TopNavBar.ui): the primary nav row is now Profile / Skills / Augments / Races / Classes / Gates / Dungeons / **Leaderboards**. Addons moved to the footer secondary navbar alongside Support and Settings.
- `NavAddons` button/icon/label renamed to `NavLeaderboards` throughout.

### Dungeon Cards — Author Attribution (`UI`)

- Endgame dungeon cards (Frozen Dungeon, Swamp Dungeon, Void Golem Realm) now show `By Lewaii` instead of a duplicate title + instance name.
- Major dungeon cards (Azaroth, Katherina, Baron) now show `Major Dungeons by MAJOR76` instead of a duplicate title + version string.
- All cards simplified from a two-label (title + subtitle) layout to a single attribution label.

### Safe-Login Teleport Hardening (`Fixed Player Teleport`)

- [`PlayerDataListener`](src/main/java/com/airijko/endlessleveling/listeners/PlayerDataListener.java): the safe-spawn teleport (for players logged in to unloaded chunks) now:
  1. Checks the entity's `Archetype` for an existing `Teleport` or `PendingTeleport` component. If one is present (e.g. death-eject from a gate instance), the safe-login teleport is skipped entirely — adding a second `Teleport` triggered a duplicate `JoinWorld` cycle that desynchronised the `teleportId` counter and disconnected the player.
  2. Uses the in-place `Teleport.createForPlayer(spawnTransform)` variant (no world arg) for same-world repositions, avoiding the full drain → add → ClientReady cycle that the world-bearing variant triggers even for same-world teleports.

### HUD Crash Fix (`Fixed Leaderboards`)

- [`HudRefreshSystem`](src/main/java/com/airijko/endlessleveling/systems/HudRefreshSystem.java): movement-based HUD refresh now uses `EntityRefUtil.tryGetComponent` instead of a direct `store.getComponent` call. The direct call throws `IllegalStateException` / `IndexOutOfBoundsException` when the entity slot has been recycled mid-tick (world transition, death respawn), crashing the world thread.

### API Additions

- [`EndlessLevelingAPI.adjustRawXp(uuid, delta)`](src/main/java/com/airijko/endlessleveling/api/EndlessLevelingAPI.java) — directly adjusts a player's XP pool without applying personal bonuses or triggering XP grant listeners. Used by the Endless Marriage even-split system.
- [`EndlessLevelingAPI.isInParty(uuid)`](src/main/java/com/airijko/endlessleveling/api/EndlessLevelingAPI.java) — checks whether a player is currently in a party.
- `LevelingManager.getBaseXpForPrestige(prestigeLevel)` — returns the effective base XP for a given prestige level.

### World Settings — Mob Stat Rebalance

- `default.json`: mob HP per level `0.075` → `0.05`, mob damage per level `0.035` → `0.015`.
- `major-dungeons.json`: damage per level `0.035` → `0.04` across all tier entries (Tier ≤3 boss `0.025` → `0.03`).
- `shiva-dungeons.json`: damage per level `0.035` → `0.4` across all tier entries.
- `endgame-dungeons.json`: minor scaling adjustments to match the new baseline.
- `global.json`: new `Blacklist_Friendly_Mobs: true`, `Gain_XP_From_Blacklisted_Mob: false`, and `Blacklist_Mob_Types: { keywords: ["Totem"] }`.

### Config Defaults

- `leveling.yml` base XP default: `50` → `100`.
- Prestige base XP increase: `10` → `20`.
- Multiplier exponent default: `1.5` → `1.75` (parsed from formula fallback only; actual expression still `^1.8`).

### Version

- `gradle.properties`: `7.5.0` → `7.6.0`.
- `manifest.json`: synced to match.

### Files Changed

```
gradle.properties                                                  |  2 +-
manifest.json                                                      |  2 +-
leveling.yml                                                       | +22 / -2
world-settings/default.json                                        | +6 / -100
world-settings/global.json                                         | +8
world-settings/endgame-dungeons.json                               | +6 / -6
world-settings/major-dungeons.json                                 | +10 / -10
world-settings/shiva-dungeons.json                                 | +6 / -6
api/EndlessLevelingAPI.java                                        | +25
leveling/LevelingManager.java                                      | +160 / -17
leveling/MobLevelingManager.java                                   | +36 / -4
leveling/XpEventSystem.java                                        | +8
listeners/PlayerDataListener.java                                  | +28 / -3
systems/HudRefreshSystem.java                                      | +9 / -3
systems/PlayerDeathXpPenaltySystem.java                            | +47 (new)
ui/NavUIHelper.java                                                | +3
ui/SkillsUIPage.java                                               | +12 / -12
Nav/TopNavBar.ui                                                   | +59 / -59
Profile/ProfilePage.ui                                             | +10 / -10
Profile/ProfilePagePartner.ui                                      | +10 / -10
Skills/SkillsPage.ui (new copy)                                    | +1516
Dungeons/Cards/Endgame/*.ui                                        | +8 / -24
Dungeons/Cards/Major/*.ui                                          | +10 / -30
```

## 2026-04-08 — 7.5.0 (compared to 7.4.0)

A UI-focused follow-up to 7.4 that introduces a new top navbar layout, ports every Endless Leveling page over to it, and fixes the `/priest` command so it actually opens the church menu instead of dumping help text.

> Note: the version-bump commit ([`3578d7c`](../) "7.6.0 Update") is mislabeled — both `gradle.properties` and `manifest.json` actually move from **7.4.0 → 7.5.0**.

### Highlights

- New **TopNavBar** UI layout (Profile / Skills / Augments / Races / Classes / Gates / Dungeons / Addons + Support / Settings footer).
- Every main page (Profile, Skills, Augments, Races, Classes, Class Paths, Race Paths, Dungeons, Addons, Leaderboards, Settings, Support, Augments) ported from `LeftNavPanel` to `TopNavBar`.
- `/priest` with no subcommand now opens the church menu GUI directly (previously printed plain-text help).
- New "Gates" nav button — clicking it dispatches `/gate` as the player so the EndlessDungeons addon's `GateListUIPage` opens inline.
- New `MultipleHUD.ui` shim for [Buuz135/MultipleHUD](https://hytale.com/) compatibility, injected into the jar at the case-sensitive `Common/UI/Custom/HUD/` path.

### TopNavBar Rework (`UI Rework` x2)

- New [`TopNavBar.ui`](../src/main/resources/Common/UI/Custom/Pages/Nav/TopNavBar.ui) (~559 lines) — defines `@TopNavBar`, `@BottomNavBar`, `@ObjectiveContainer`, and `@PanelTitleStyle` symbols. Each nav button is a tile-patch border + solid fill + click-state outline + icon + label.
- New nav icons under [`Pages/Nav/Icons/`](../src/main/resources/Common/UI/Custom/Pages/Nav/Icons/): `NavProfileIcon.png`, `NavSkillsIcon.png`, `NavAugmentsIcon@2x.png`, `NavRacesIcon.png`, `NavClassesIcon.png`, `NavGatesIcon@2x.png`, `NavDungeonsIcon@2x.png`, `NavAddonsIcon.png`, `NavSettingsIcon.png`, `NavSupportIcon@2x.png`.
- [`NavUIHelper`](../src/main/java/com/airijko/endlessleveling/ui/NavUIHelper.java) reworked:
  - New `pageUsesTopNavBar(pageResourcePath)` autodetects which layout a page uses by scanning its resource text for `TopNavBar.ui`.
  - `applyNavVersion` now branches: top-nav pages set `#Nav<Name>Label.Text` widgets, legacy pages set `#Nav<Name>.Text` widgets directly.
  - `applySelectedNavStyle` and `bindNavEvents` both gained a `topNav` mode that only touches the selectors that actually exist in `TopNavBar.ui` — eliminates "target element not found" errors when a top-nav page tries to bind legacy buttons.
  - `applyBrandingEnforcement` skips the `#NavHeader` / `#NavSubHeader` writes on top-nav pages (those widgets only live in `LeftNavPanel`).
- Pages converted to `TopNavBar` + `@ObjectiveContainer` (centered 1000x700 layout matching the EndlessMarriage panel sizing): `ProfilePage.ui`, `ProfilePagePartner.ui`, `SkillsPage.ui` (moved from `Pages/` to `Pages/Skills/`), `AugmentsPage.ui`, `RacesPage.ui`, `RacePathsPage.ui`, `ClassesPage.ui`, `ClassPathsPage.ui`, `DungeonsPage.ui`, `LeaderboardsPage.ui`, `AddonsPage.ui`, `SettingsPage.ui` (both copies), `SupportPage.ui`.
- `SkillsPage.ui` stat row heights tightened from `100` → `95` for all 11 stat panels (Life Force, Strength, Precision, Haste, Flow, Defense, Sorcery, Ferocity, Stamina, Discipline).

### `/priest` GUI Fix (`Fixed Background for Priest`)

- [`PriestCommand.execute`](../src/main/java/com/airijko/endlessleveling/commands/classes/PriestCommand.java) no longer dumps a plain-text command list. It now:
  1. Verifies the class system is enabled and player data is loadable.
  2. Confirms the sender is a Priest via `PriestClassCheck.isPriest`.
  3. Opens [`PriestMenuPage`](../src/main/java/com/airijko/endlessleveling/ui/PriestMenuPage.java) on the world thread.
- Added `Common/Priest/Images/ObjectivePanelContainer.png` so the priest menu uses the same panel background as the rest of the mod.

### Gates Button Wiring

- New `openGatesGui(playerRef)` in `NavUIHelper`. Clicking the Gates nav button dispatches `/gate` through `CommandManager.get().handleCommand(playerRef, "gate")` so the EndlessDungeons addon (which registers that command) opens its `GateListUIPage` for the player. Falls back to a friendly "Gates UI is not available right now." message if the addon isn't loaded.
- The 7.4 placeholder ("Gates UI is coming soon.") is gone.

### MultipleHUD Compatibility Shim

- New file `src/main/extra-resources/multiplehud-shim/MultipleHUD.ui` injected into the jar at `Common/UI/Custom/HUD/MultipleHUD.ui` via a custom `shadowJar { from(...) into 'Common/UI/Custom/HUD' }` block in [`build.gradle`](../build.gradle).
- The shim is staged outside `src/main/resources` because the existing `Common/UI/Custom/Hud/` (mixed-case) folder is indistinguishable from `HUD/` on Windows NTFS — the gradle injection adds the entry directly to the jar so the case-sensitive runtime path is preserved on Linux servers.

### Version

- `gradle.properties`: `7.4.0` → `7.5.0`.
- `manifest.json`: `7.4.0` → `7.5.0`.

## 2026-04-08 — 7.4 (compared to 7.3.4)

### Highlights

- New **Bard** class with playable in-world music and a Bard Music UI page.
- **Priest** rework: church setup/management commands and a full church-building flow.
- Major UI memory leak fixes via a new `SafeUICommandBuilder` wrapper.
- Necromancer "Army of the Dead" passive overhaul plus haste system fixes.
- Removed the NameplateBuilder compatibility layer (no longer maintained).
- External-mod API additions: `ELNotificationType`, pre-teleport listeners, combat tags.
- New community addon entries (Roguetale, Mermaids) added to the in-game Addons UI.

### New Class — Bard

- New `bard` class tier set: `bard`, `bard_elite`, `bard_exalted`, `bard_legendary`, `bard_master`.
- New commands: `/bard`, `/bard play`, `/bard song`, `/bard music`, `/bard stop` and a `BardClassCheck` gate so only Bards can use them.
- New `BardSongRegistry` and `BardMusicPage` UI for browsing and triggering songs.
- Bundled assets: `Common/Sounds/Bard/bridal_chorus.ogg`, `BardMusicPage.ui`, `SongRow.ui`, `ObjectivePanelContainer.png`, and `SFX_EL_Bard_BridalChorus.json`.
- Bard reworked again in a follow-up commit alongside the Priest rework (`Bard and Priest Rework`).

### Priest / Church System

- New `ChurchManager` (~600 LOC) plus admin and player-facing commands:
  - `/priest`, `/priest setup`, `/priest info`, `/priest undo`, `/priest admin`, `/priest admin remove`.
  - `PriestClassCheck` restricts the commands to Priests.
- Follow-up commit "Allow Churches to be setup" enables players to actually configure their churches end-to-end.
- Updated `shiva-dungeons.json` world settings to support the new flow.

### Necromancer & Combat Fixes

- `ArmyOfTheDeadPassive` rewritten (~480 LOC of changes) and `ArmyOfTheDeadDeathSystem` updated to fix lingering ghoul / death issues.
- `MobAugmentExecutor` and `MobDamageScalingSystem` hardened (~234 / ~82 LOC) to fix augment-driven crashes and bad damage scaling.
- `MovementHasteSystem` fix for haste stacking issues introduced after 7.3.4.
- `BuffingAuraPassive`, `PartyBuffingAuraPassive`, `PartyHealingDistributor`, `PartyShieldingAuraPassive`, `ShieldingAuraPassive` all touched as part of the buffing/aura cleanup.
- New player-side toggles in `SettingsUIPage` and `PlayerSettings.ui` for the related effects.

### Race & Class Stability

- "Race Swap Fix" — `ClassManager` and `RaceManager` cleaned up to fix race swap edge cases.
- Class/race definitions and `VersionRegistry` synced after the Bard/Priest work.

### UI Memory-Leak Sweep (`Fixed UI Memory Leaks`)

- Introduced `SafeUICommandBuilder` (~440 LOC) and routed every page through it:
  - `AddonsUIPage`, `AugmentsChoosePage`, `AugmentsUIPage`, `ClassPathsUIPage`, `ClassesUIPage`, `DungeonsUIPage`, `LeaderboardsUIPage`, `PlayerHud`, `ProfileUIPage`, `RacePathsUIPage`, `RacesUIPage`, `SettingsUIPage`, `SkillsUIPage`, `SupportUIPage`.
- Fixed severe `SkillsUIPage` warnings around skill points (unintended log spam).

### NameplateBuilder Removed (`removed nameplatebuilder compat`)

- Deleted `NameplateBuilderCompatibility.java` (~1,050 LOC) and all wiring in `EndlessLeveling`, `EndlessLevelingAPI`, `MobLevelingSystem`, `ArmyOfTheDeadPassive`, and `PlayerNameplateSystem`.
- Removed the dependency from `manifest.json`.

### External Mod API (7.3.5 + 7.3.6)

- New [`ELNotificationType`](../src/main/java/com/airijko/endlessleveling/api/ELNotificationType.java) for notification routing across mods.
- `EndlessLevelingAPI` additions:
  - **Pre-teleport listeners** — `addPreTeleportListener` / `removePreTeleportListener` / `notifyPreTeleportListeners`. Lets external mods clean up transient ECS state (mounts, particles) right before a player is moved between worlds.
  - **Combat tags** — `markInCombat`, `isInCombat(uuid, windowMs)`, `clearCombatTag` for shared combat-state tracking.
  - Additional null guards and defensive copies across `AugmentExecutor`, `MobAugmentAnnouncer`, `CombatHookProcessor`, `LevelingManager`, `XpEventSystem`, `DungeonTierJoinNotificationListener`, `PlayerDataListener`, `PassiveManager`, `HealingAuraPassive`, `PartyMendingAuraPassive`, `SkillManager`, `PassiveRegenSystem`.

### Addons UI

- New community addon cards: **Roguetale** (Zbeve) and **Mermaids** (Siren), with their own filter section in the Addons page.
- New community addon section/spacer in `AddonsPage.ui`, plus localization keys (`ui.addons.roguetale.*`, `ui.addons.mermaids.*`) translated across all 9 supported languages.

## 2026-03-11

### Augment Balance Pass

Compared old augments (`old augments`) against current `src/main/resources/augments`.

- Total augment files compared: `50`
- Modified files: `18`
- Added files: `0`
- Removed files: `0`

### Changed Values

- `arcane_instability.yml` `passives.buffs.sorcery_bonus_high.value`: `0.5` -> `0.4`
- `bloodthirster.yml` `passives.healthy_state.bonus_damage.value`: `1.0` -> `0.8`
- `brute_force.yml` `passives.brute_force.sorcery_multiplier`: `1.75` -> `1.6`
- `brute_force.yml` `passives.brute_force.strength_multiplier`: `1.75` -> `1.6`
- `burn.yml` `passives.aura_burn.radius_health_scaling.health_per_block`: `175` -> `200`
- `drain.yml` `passives.bonus_damage_on_hit.value`: `0.075` -> `0.1`
- `drain.yml` `passives.bonus_damage_on_hit.cooldown`: `4.0` -> `1.0`
- `executioner.yml` `passives.bonus_damage_on_hit.value`: `3.0` -> `2.5`
- `first_strike.yml` `passives.bonus_damage_on_hit.value`: `2.0` -> `1.75`
- `frozen_domain.yml` `passives.aura_frozen_domain.radius_health_scaling.health_per_block`: `175` -> `200`
- `giant_slayer.yml` `passives.bonus_damage_vs_hp_ratio.max_ratio`: `10.0` -> `8.0`
- `giant_slayer.yml` `passives.bonus_damage_vs_hp_ratio.max_value`: `2.0` -> `1.8`
- `goliath.yml` `passives.buffs.sorcery.value`: `0.1` -> `0.075`
- `goliath.yml` `passives.buffs.strength.value`: `0.1` -> `0.075`
- `mana_infusion.yml` `passives.buffs.sorcery_from_mana.value`: `0.25` -> `0.2`
- `protective_bubble.yml` `passives.immunity_bubble.cooldown`: `25` -> `30`
- `raid_boss.yml` `passives.buffs.bonus_damage.value`: `0.2` -> `0.15`
- `rebirth.yml` `passives.heal_on_trigger.value`: `0.8` -> `0.5`
- `tank_engine.yml` `passives.stacking_health.percent_cap`: removed (old `1.0`)
- `tank_engine.yml` `passives.stacking_health.max_health_multiplier_at_max_stacks`: added (new `2.0`)
- `titans_might.yml` `passives.buffs.strength_from_max_health.value`: `0.2` -> `0.15`
- `titans_might.yml` `passives.debuffs.haste.value`: `-0.1` -> `-0.15`
- `titans_wisdom.yml` `description`: `Your immense vitality fuels your magical power.` -> `Gain Sorcery based on your total max Health, but lose some Haste.`
- `titans_wisdom.yml` `passives.health_to_sorcery.scaling_stat`: removed (old `max_health`)
- `titans_wisdom.yml` `passives.health_to_sorcery.conversion_percent`: removed (old `0.2`)
- `titans_wisdom.yml` `passives.buffs.sorcery_from_max_health.scaling_stat`: added (new `max_health`)
- `titans_wisdom.yml` `passives.buffs.sorcery_from_max_health.conversion_percent`: added (new `0.15`)
- `titans_wisdom.yml` `passives.debuffs.haste.value`: added (new `-0.15`)
- `undying_rage.yml` `passives.bonus_damage.max_bonus_ferocity`: `150` -> `125`

## 2026-03-01

### Highlights

- Added a full augment progression flow with tiered unlock milestones.
- Expanded mob blacklist configuration to support exact IDs, keywords, and wildcard matching.
- Added support for no-default race/class setups using `"None"`.
- Improved UI handling for unselected race/class states.

### Augments

Augments are a player-bound progression layer. They work similarly to race/class passives in mechanics (passive-style bonuses and scaling), but they are attached to each player profile after selection from unlock offers.

#### Unlock Milestones

- ELITE: levels [10], [25]
- MYTHIC: levels [50]

#### Available Augments

##### ELITE

- Arcane Instability
- Arcane Mastery
- Blood Frenzy
- Critical Guard
- Drain
- Executioner
- First Strike
- Fortress
- Mana Infusion
- Overdrive
- Predator
- Rebirth
- Soul Reaver
- Supersonic
- Taunt
- Titan's Might
- Vampirism
- Wither

##### MYTHIC

- Blood Echo
- Blood Surge
- Giant Slayer
- Raging Momentum
- Reckoning
- Sniper's Reach
- Undying Rage
- Vampiric Strike

### Config & Gameplay Changes

#### Mob Blacklist

- `Mob_Leveling.Blacklist_Mob_Types` now supports:
  - `ids` for exact ID matching
  - `keywords` for keyword/substring matching
  - wildcard `*` patterns in both (examples: `Undead_*`, `*_Horse`, `*fish*`)
- Added more blacklisted mob IDs in `leveling.yml`.

#### Race/Class Defaults

- You can now set these to `"None"`:
  - `default_race`
  - `default_primary_class`
  - `default_secondary_class`
- When set to `"None"`, new players start with no default race/class selected.

### Fixes

- Fixed profile page null-value issues when race is unselected.
- Updated HUD behavior so unselected race is shown as "No Race" instead of unknown.
