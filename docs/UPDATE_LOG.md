# Endless Leveling - Update Log

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
