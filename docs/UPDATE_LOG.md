# Endless Leveling - Update Log

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
