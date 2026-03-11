# Endless Leveling - Update Log

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
