# EndlessLeveling Public API

> **This repository is reference-only.** The Java file is a stub of the public
> API surface — method bodies are elided and the code does not compile.
> For the live runtime, depend on the EndlessLeveling plugin JAR.

External guide for mods that want to read EndlessLeveling state or add their
own bonuses, augments, races, classes, and gate/dungeon bridges.

---

## Table of contents

1. [Entry point](#1-entry-point)
2. [Common example](#2-common-example)
3. [API categories](#3-api-categories)
   - [Player snapshot + attributes](#player-snapshot--attributes)
   - [Level / XP helpers](#level--xp-helpers)
   - [Mob level overrides](#mob-level-overrides)
   - [Runtime world settings](#runtime-world-settings)
   - [Augments / races / classes](#augments--races--classes)
   - [Archetype passive sources](#archetype-passive-sources)
   - [Listeners (events)](#listeners-events)
   - [Combat tag](#combat-tag)
   - [Nameplate + notification control](#nameplate--notification-control)
   - [Entity stat + XP multipliers](#entity-stat--xp-multipliers)
   - [Gate bridges + instance dungeons](#gate-bridges--instance-dungeons)
4. [Integration tips](#4-integration-tips)
5. [Calculation cheat sheet](#5-calculation-cheat-sheet)

---

## 1) Entry point

```java
import com.airijko.endlessleveling.api.EndlessLevelingAPI;

var api = EndlessLevelingAPI.get();
```

Singleton. Safe from the normal server thread. Read-only queries return safe
defaults (0 / null / passed-in fallback) when EL is not fully loaded.

---

## 2) Common example

```java
UUID uuid = playerRef.getUuid();
var api = EndlessLevelingAPI.get();

// Snapshot
var snap = api.getPlayerSnapshot(uuid);
if (snap != null) {
    System.out.println("Lv " + snap.level() + " race " + snap.raceId());
}

// Skill numbers
int strengthLevel = api.getSkillAttributeLevel(uuid, SkillAttributeType.STRENGTH);
double strengthBonus = api.getSkillAttributeBonus(uuid, SkillAttributeType.STRENGTH);

// Combined base (race) + skill, no gear/buffs
double baseHealth = api.getCombinedAttribute(uuid, SkillAttributeType.LIFE_FORCE, 0.0);

// Add your own health on top (e.g., from another mod)
double externalHealth = 50.0;
var life = api.getAttributeBreakdown(uuid, SkillAttributeType.LIFE_FORCE, externalHealth, 0.0);
double totalHealth = life.total();
```

---

## 3) API categories

### Player snapshot + attributes

```java
// Full snapshot record
var snap = api.getPlayerSnapshot(uuid);
// -> PlayerSnapshot { uuid, playerName, level, xp, skillPoints, raceId,
//                     primaryClassId, secondaryClassId,
//                     skillLevels(Map), xpGainMultiplier }

int haste = snap.skillLevels().getOrDefault(SkillAttributeType.HASTE, 0);

// Single attribute level
int strLvl = api.getSkillAttributeLevel(uuid, SkillAttributeType.STRENGTH);

// Additive bonus (race + skill, no gear)
double strBonus = api.getSkillAttributeBonus(uuid, SkillAttributeType.STRENGTH);
double finalDamage = baseDamage * (1.0 + (strBonus / 100.0));

// Combined base (race) + skill (no gear/buffs)
double hp = api.getCombinedAttribute(uuid, SkillAttributeType.LIFE_FORCE, 0.0);
double stamina = api.getCombinedAttribute(uuid, SkillAttributeType.STAMINA, 0.0);

// Attribute breakdown with your own additive bonus
double externalHp = 75.0;
var life = api.getAttributeBreakdown(uuid, SkillAttributeType.LIFE_FORCE, externalHp, 0.0);
// life.raceBase(), life.skillBonus(), life.externalBonus(), life.total()

String uiLine = String.format("HP: base %.0f + skill %.0f + gear %.0f = %.0f",
        life.raceBase(), life.skillBonus(), life.externalBonus(), life.total());

// Convenience overload: no external bonus
var cleanLife = api.getAttributeBreakdown(uuid, SkillAttributeType.LIFE_FORCE, 0.0);
```

Per-point config values from `config.yml`:

```java
double perPointFlow = api.getSkillAttributeConfigValue(SkillAttributeType.FLOW);
double manaFromFlow = perPointFlow * api.getSkillAttributeLevel(uuid, SkillAttributeType.FLOW);
```

---

### Level / XP helpers

```java
int level = api.getPlayerLevel(uuid);
double xp = api.getPlayerXp(uuid);
int prestige = api.getPlayerPrestigeLevel(uuid);

int cap = api.getLevelCap();                   // global
int personalCap = api.getLevelCap(uuid);       // prestige-aware
double xpToNext = api.getXpForNextLevel(level);
double personalXpToNext = api.getXpForNextLevel(uuid, level);

// Progress bar (prestige-aware)
double pct = personalXpToNext == Double.POSITIVE_INFINITY
        ? 1.0
        : Math.min(1.0, xp / personalXpToNext);
```

IDs:

```java
String raceId = api.getRaceId(uuid);
String primary = api.getPrimaryClassId(uuid);
String secondary = api.getSecondaryClassId(uuid);
```

Granting XP:

```java
// Respects EL bonuses + cap, fires xpGrantListeners
api.grantXp(playerUuid, 250);

// Raw adjust (skips bonuses, skips listeners) — used by marriage split
api.adjustRawXp(playerUuid, 100);

// Divide XP evenly among party members within 30 blocks of the killer
api.grantSharedXpInRange(killerUuid, mobXpTotal, 30.0);

// Party membership check
boolean grouped = api.isInParty(playerUuid);
```

---

### Mob level overrides

Priority: per-entity > gate > area > world fixed > world-settings fallback.

```java
// Flat level 20 for everything in world "dungeon_world"
api.registerMobWorldLevelOverride("dungeon-world", "dungeon_world", 20, 20);

// Radius override: level 5-10 inside (100,100) radius 50 in overworld
api.registerMobAreaLevelOverride("starter-dungeon", "overworld", 100, 100, 50, 5, 10);

// Gate override (evaluated before area overrides)
api.registerMobWorldGateLevelOverride("g1", "el_gate_x_y", 12, 18);
// with boss offset (replaces Mob_Overrides.Level_From_Range_Max_Offset)
api.registerMobWorldGateLevelOverride("g1", "el_gate_x_y", 12, 18, 3);

// Dynamic FIXED Level_Source override (keeps scaling pipeline)
api.registerMobWorldFixedLevelOverride("zone-a", "world_a", 10, 15);

// Gate scaling override (mirrors world-settings JSON schema)
api.registerGateScalingOverride("g1", Map.of(
        "Scaling", Map.of("Health", Map.of(/* ... */)),
        "Mob_Overrides", Map.of(/* per-mob-type */)));

// Per-entity pin (safe across worlds — keyed by (store, index))
api.setMobEntityLevelOverride(bossRef, 25);

// Cleanup
api.removeMobAreaLevelOverride("starter-dungeon");
api.removeMobGateLevelOverride("g1");
api.removeGateScalingOverride("g1");
api.removeMobWorldFixedLevelOverride("zone-a");
api.clearMobAreaLevelOverrides();
api.clearMobGateLevelOverrides();
api.clearMobWorldFixedLevelOverrides();
api.clearMobEntityLevelOverride(bossRef);
api.clearAllMobEntityLevelOverrides();
```

> **Dungeon example.** Trash between levels 12–16 inside radius 80 at (512, 512);
> boss fixed at 22. These overrides bypass `Level_Source` in `leveling.yml`;
> they apply first and are not re-scaled.

Mob blacklist (runtime, substring match, case-insensitive):

```java
api.addMobBlacklistEntry("trork_peon");
api.hasMobBlacklistEntry("TROrk_peon");   // true — normalized match
api.removeMobBlacklistEntry("trork_peon");
api.clearRuntimeMobBlacklist();
Set<String> view = api.getRuntimeMobBlacklistView();  // hot-path view
```

Query whether EL has leveled a mob:

```java
Integer lvl = api.getEntityMobLevel(ref, store, cmdBuf);
if (lvl != null) { /* EL processed this entity */ }
```

Managed summon check (Army of the Dead) — excluded from mob leveling:

```java
if (api.isEntityManagedSummon(ref, store, cmdBuf)) return;
if (api.isEntityManagedSummonByUuid(entityUuid)) return;  // lightweight variant
```

---

### Runtime world settings

Programmatic merge layer on top of world-settings JSON. Call `reloadWorldSettings()`
afterwards for level/XP multiplier changes to take effect.

```java
// Level range for a world
api.setWorldLevelRange("dungeon_world", 20, 30);

// Pin a specific mob type to a fixed level (ID normalized internally)
api.setWorldMobLevel("dungeon_world", "trork_elite", 25);

// Global XP multiplier per-world (use "default" for overworld)
api.setWorldXpMultiplier("dungeon_world", 1.5);

api.clearWorldOverrides("dungeon_world");
api.clearAllWorldOverrides();
api.reloadWorldSettings();

// XP world blacklist (immediate, no reload needed)
api.addXpBlacklistedWorld("creative_*");
api.removeXpBlacklistedWorld("creative_*");
api.clearXpBlacklistedWorlds();
```

---

### Augments / races / classes

Definitions can be registered by external mods and survive EL reloads
(file-backed + API-registered are merged).

```java
// Lookup
AugmentDefinition aug = api.getAugmentDefinition("crit_mastery");
RaceDefinition race = api.getRaceDefinition("stormborn");
CharacterClassDefinition clazz = api.getClassDefinition("tempestblade");

Collection<RaceDefinition> allRaces = api.getRaceDefinitions();
Collection<CharacterClassDefinition> allClasses = api.getClassDefinitions();
```

**Augment registration.** Metadata via `AugmentDefinition` is required; YAML
optional for external mods. A custom factory is required for hook-driven
behavior (`OnHit`, `OnCrit`, passive stat logic).

```java
var definition = new AugmentDefinition(
        "crit_mastery",
        "Crit Mastery",
        PassiveTier.RARE,
        PassiveCategory.PASSIVE_STAT,
        false,
        "Gain critical strike chance.",
        Map.of("buffs", Map.of("precision", Map.of("value", 0.20))));

// Data-only (falls back to YamlAugment unless a factory is also registered)
api.registerAugment(definition);

// With custom Java behavior
api.registerAugment(definition, MyCritMasteryAugment::new);

// Override existing id
api.registerAugment(definition, MyCritMasteryAugment::new, true);

// Cleanup
api.unregisterAugment("crit_mastery");
```

**Race / class registration.** Definition-level — become first-class in the
EndlessLeveling UI, lookups, and passive aggregation. Custom gameplay logic
for those races/classes lives in your own plugin keyed off
`api.getRaceId(uuid)` / `api.getPrimaryClassId(uuid)` etc.

```java
var customRace = new RaceDefinition(
        "stormborn", "Stormborn", "A race attuned to storm magic.",
        "Ingredient_Life_Essence", null, 1.0, true,
        Map.of(SkillAttributeType.SORCERY, 12.0),
        List.of(), List.of(),
        RaceAscensionDefinition.baseFallback("stormborn"));
api.registerRace(customRace);
api.registerRace(customRace, true);   // replace existing

var customClass = new CharacterClassDefinition(
        "tempestblade", "Tempestblade", "A mobile melee caster.",
        "Striker", "fighter", true, "Weapon_Sword_T1",
        Map.of("sword", 1.2),
        List.of(), List.of(),
        RaceAscensionDefinition.baseFallback("tempestblade"));
api.registerClass(customClass);

api.unregisterRace("stormborn");
api.unregisterClass("tempestblade");
```

**Augment snapshot + temp apply.** Capture and restore a player's selections
around a scoped effect (minigame, arena, boss fight):

```java
Map<String, String> saved = api.snapshotAugments(uuid);
api.applyTempAugment(uuid, "crit_mastery");
// ... scoped effect ...
api.restoreAugments(uuid, saved);
```

---

### Archetype passive sources

Provide context-dependent passives (seasonal, guild, achievement-based) by
implementing `ArchetypePassiveSource`:

```java
class SeasonalBonusSource implements ArchetypePassiveSource {
    @Override
    public void collect(PlayerData playerData,
            EnumMap<ArchetypePassiveType, StackAccumulator> totals,
            EnumMap<ArchetypePassiveType, List<RacePassiveDefinition>> grouped) {
        if (isWinterSeason()) {
            StackAccumulator acc = totals.computeIfAbsent(
                ArchetypePassiveType.FEROCITY,
                k -> new StackAccumulator(PassiveStackingStyle.ADDITIVE));
            acc.addValue(10.0);   // +10 ferocity
        }
    }
}

var src = new SeasonalBonusSource();
api.registerArchetypePassiveSource(src);
api.unregisterArchetypePassiveSource(src);
```

Called during every snapshot query. Use `StackAccumulator` for safe stacking.
Return early if not applicable (player not in your guild, etc.). Multiple
sources stack additively into the final snapshot.

---

### Listeners (events)

All listeners run on the server thread. Keep handlers fast and non-blocking.

```java
// XP grant (adjusted / post-bonus)
api.addXpGrantListener((uuid, xp) -> { /* ... */ });

// Level-up (per level crossed)
api.addLevelUpListener(e -> System.out.println(e.oldLevel() + "→" + e.newLevel()));

// Prestige gain
api.addPrestigeListener(e -> { /* ... */ });

// Mob kill (keyed by normalized mob type id)
api.addMobKillListener(e -> { /* ... */ });

// Outlander Bridge cleared all waves
api.addOutlanderBridgeCompletedListener(e -> { /* ... */ });

// Rifts & Raids wave-gate cleared final wave
api.addWaveGateCompletedListener(e -> { /* ... */ });

// Pre-teleport cleanup hook (player UUID)
api.addPreTeleportListener(uuid -> cleanupMyMountComponent(uuid));

// Mob post-process (after MobLevelingSystem assigns a level)
api.registerMobPostProcessListener((ref, store, cmdBuf, level) -> { /* ... */ });

// Damage event (credited to a player by the EL damage tracker)
api.registerDamageEventListener((uuid, worldId, amt, source, crit) -> { /* ... */ });
```

Corresponding `remove*` / `unregister*` methods exist for every listener.

**Leaderboard events** (damage-meter):

```java
String eventId = api.startLeaderboardEvent(worldId, "Dungeon Run #3");
// ...
LeaderboardSnapshot snap = api.stopLeaderboardEventForWorld(worldId);
// or by id:
api.stopLeaderboardEvent(eventId);
api.getLeaderboardSnapshot(eventId);
PlayerDamageStats stats = api.getPlayerDamageStats(playerUuid);
```

---

### Combat tag

Shared "player in combat" state across damage-meter HUD, passive regen gating,
haymaker out-of-combat bonus, etc.

```java
api.markInCombat(uuid);
boolean inCombat = api.isInCombat(uuid);                // default window 5000 ms
boolean inCombat10s = api.isInCombat(uuid, 10_000L);
long lastHit = api.getLastCombatMs(uuid);
api.clearCombatTag(uuid);                               // e.g. on disconnect

// Constant
long w = EndlessLevelingAPI.DEFAULT_COMBAT_WINDOW_MS;   // 5000L
```

---

### Nameplate + notification control

```java
// Mob nameplates
api.setMobNameplatesEnabled(false);
api.resetMobNameplatesEnabled();
boolean on = api.areMobNameplatesEnabled();

// Player nameplates
api.setPlayerNameplatesEnabled(true);
api.resetPlayerNameplatesEnabled();
api.arePlayerNameplatesEnabled();

// Per-entity nameplate prefix (prepended before [Lv.X] tag)
api.setEntityNameplatePrefix(entityIndex, "[S] ");
api.getEntityNameplatePrefix(entityIndex);
api.removeEntityNameplatePrefix(entityIndex);

// World mob rank tier (gate mods register on instance spawn; elite mobs read)
api.registerWorldMobRankTier("el_gate_foo_bar", MobRankTier.A);
MobRankTier tier = api.getWorldMobRankTier("el_gate_foo_bar");
api.unregisterWorldMobRankTier("el_gate_foo_bar");

// Suppress specific EL notifications
api.suppressNotification(ELNotificationType.LEVEL_UP_TITLE);
api.unsuppressNotification(ELNotificationType.LEVEL_UP_TITLE);
api.isNotificationSuppressed(ELNotificationType.XP_GAIN);
api.clearNotificationSuppressions();

// Chat + command prefix override
api.setCommandPrefix("/rl hub");       // changes "Use /lvl..." → "Use /rl hub..."
api.setChatPrefix("[MyBrand] ");       // replaces "[EndlessLeveling] "
api.setCommandPrefix(null);            // reset
api.setChatPrefix(null);

// Skill attribute visibility (hidden attrs cannot receive points)
api.hideSkillAttribute(SkillAttributeType.DISCIPLINE);
api.showSkillAttribute(SkillAttributeType.DISCIPLINE);
api.isSkillAttributeHidden(SkillAttributeType.DISCIPLINE);
api.getHiddenSkillAttributes();
```

Complete `ELNotificationType` list:

| Type | Source |
|---|---|
| `LEVEL_UP_TITLE` | Level-up title splash |
| `LEVEL_UP_SKILL_POINTS` | "You gained X skill points!" popup |
| `XP_GAIN` | XP-gain popup |
| `UNSPENT_SKILL_POINTS` | Login reminder for unspent points |
| `AUGMENT_AVAILABILITY` | "You have augments available" chat |
| `AUGMENT_TRIGGERED` | Augment proc chat |
| `PRESTIGE_AVAILABLE` | Prestige-available title + chat |
| `PASSIVE_TRIGGERED` | Passive trigger chat (Swiftness, Adrenaline, ...) |
| `PASSIVE_LEVEL_UP` | "Swiftness is now level 3" chat |
| `PASSIVE_REGEN` | Passive regen popup |
| `CRITICAL_HIT` | Critical-hit popup |
| `LUCK_DOUBLE_DROP` | Luck double-drop chat |
| `MOB_AUGMENT_ANNOUNCE` | Mob augment announce chat |
| `DUNGEON_TIER_JOIN` | Tier join info chat |

`MobRankTier`: `E < D < C < B < A < S`. Helpers: `letter()`, `defaultColor()`
(hex), `strength()` (ordinal), `fromLetter(String)` (case-insensitive parse).

---

### Entity stat + XP multipliers

```java
// XP reward multiplier for a specific entity (single-source, doesn't stack)
api.setEntityXpMultiplier(entityIndex, 2.0);
api.clearEntityXpMultiplier(entityIndex);
double cur = api.getEntityXpMultiplier(entityIndex);   // 1.0 default

// Named multiplicative health modifier (stacks with EL scaling)
api.applyHealthModifier(ref, store, cmdBuf, "ELITE_HEALTH_SCALE", 1.5f);
api.removeHealthModifier(ref, store, cmdBuf, "ELITE_HEALTH_SCALE");
```

**Swing-heal / lifesteal integration.** External lifesteal weapons deposit
potential-heal from their own `OnHit` path. Folded into the attacker's swing
pipeline so it's counted by Blood Surge / Blood Echo and scaled by
`HEALING_BONUS`. Do **not** also add HP on the stat map — that double-counts.

```java
api.contributeSwingHeal(attackerUuid, 15.0);  // call from YOUR on-hit
```

Internal: `drainPendingSwingHeal(uuid)` is called by the combat pipeline at
swing finalize.

---

### Gate bridges + instance dungeons

External dungeon/wave-gate mods register bridges so EL can invoke them through
its gate lifecycle. Read-only consumers fetch them by type.

```java
// Dungeon gates
api.registerDungeonGateLifecycleBridge(myBridge, true);
DungeonGateLifecycleBridge b = api.getDungeonGateLifecycleBridge();

// Wave gates (Rifts & Raids)
api.registerWaveGateRuntimeBridge(runtime, true);
api.registerWaveGateSessionBridge(session, true);
api.registerWaveGateSessionExecutorBridge(exec, true);
api.getWaveGateSessionBridge();

// Dungeon wave gate (bundled)
api.registerDungeonWaveGateBridge(bridge, true);

// Routing bridge (instance world ↔ gate mapping)
api.registerGateInstanceRoutingBridge(router, true);
api.getGateInstanceRoutingBridge();

// Content providers (spawn tables, wave pools)
api.registerDungeonGateContentProvider(provider, true);     // alias: registerGateContentProvider
api.registerWaveGateContentProvider(provider, true);        // alias: registerWaveContentProvider

// Instance dungeons (definition-level, routing-template aware)
api.registerInstanceDungeon(def, true);
api.getInstanceDungeon("frozen_dungeon");
api.getInstanceDungeons();
api.getInstanceDungeonByBlockId("block_id");
api.getInstanceDungeonByRoutingTemplate("tpl_name");
api.getInstanceDungeonByWorldName("el_gate_foo_bar");

// World-name / group-id builders (mirror EL's internal conventions)
String worldName = api.buildInstanceDungeonWorldName("frozen_dungeon", "el_gate:foo");
String groupId = api.buildInstanceDungeonGroupId("el_gate:foo", "frozen_dungeon");
```

Generic manager registry (used internally by most of the above; exposed for
custom cross-mod integrations):

```java
api.registerManager("my-mod.foo", myManager, true);
Object m = api.getManager("my-mod.foo");
api.unregisterManager("my-mod.foo", myManager);
```

Marriage convenience (delegates via reflection to a manager registered under
key `"marriage"`):

```java
api.isMarried(uuid);
UUID spouse = api.getSpouseUuid(uuid);
api.isNearSpouse(uuid);
api.isCoupleOnlyParty(uuid);   // true when party = {self, spouse} only
```

Auto-allocate guards (skill points ARE granted, but auto-spending is gated):

```java
api.addAutoAllocateGuard(uuid -> !inPvpZone(uuid));
int spent = api.applyPendingAutoAllocate(uuid);
boolean allowed = api.isAutoAllocateAllowed(uuid);
api.removeAutoAllocateGuard(myGuard);
```

---

## 4) Integration tips

- Missing data returns safe defaults (0, null, or your fallback).
- All queries are read-only and safe from the normal server thread.
- Cache per-tick; values are cheap but repeated map lookups add up.
- Prefer player-specific progression overloads when prestige matters:
  `getLevelCap(uuid)`, `getXpForNextLevel(uuid, level)`.
- `getLevelCap(uuid)` falls back to the global cap when player data is unavailable.
- `getAttributeBreakdown(uuid, type, fallback)` is the simplest way to query
  clean race + skill totals with no external flat bonus.
- `getRaceDefinitions()` / `getClassDefinitions()` return snapshot copies —
  safe to iterate without mutating EL internals.
- External augment / race / class registrations survive EL reloads
  (file-backed + API-registered are merged).

---

## 5) Calculation cheat sheet

**Skill bonus (any attribute).**
```
bonus = level * perPointValue + innateRaceBonus
```
`perPointValue` from `config.yml` via `getSkillAttributeConfigValue(type)`.
Additive — no multipliers at this layer.

**Combined attribute (no gear/buffs).**
```
total = raceBase + skillBonus
```
With `AttributeBreakdown`: `total = raceBase + skillBonus + externalBonus`.
With percent buffs: `totalPercentBuffed = total * (1 + percentBuff / 100)`.

**Passives stacking.** Sources: race + primary class (100%) + secondary class
(scaled, default 50%). Same passive type combines by `stackingStyle`:

| Style | Formula |
|---|---|
| `ADDITIVE` | `total = a + b + c` |
| `DIMINISHING` | `total = 1 - (1-a)*(1-b)*(1-c)` |
| `UNIQUE` | `total = max(a, b, c)` |

Passives can tag a damage layer; tags in the same layer combine before damage
is applied.

**Health / Stamina / Flow (mana).** Use `getCombinedAttribute` for race+skill
max before other buffs.
```
base            = raceBase + skillBonus
basePlusFlat    = base + externalFlat
final           = basePlusFlat * (1 + externalPercent / 100)
```

**Strength damage.**
```
finalDamage = baseDamage * (1 + strengthBonus / 100)
            * (1 + externalPercent / 100)
```

**Sorcery (spell damage).** Same stacking as strength.
```
finalSpellDamage = baseSpellDamage * (1 + sorceryBonus / 100)
                 * (1 + externalPercent / 100)
```

**Full damage order (weapon + crit + passives).**
1. Start with incoming event damage (weapon hit).
2. Attribute scale — staff → sorcery, else → strength.
   `damageAttr = baseDamage * (1 + attrBonus / 100)`
3. Critical. If proc: `damageCrit = damageAttr * (1 + ferocity / 100)`.
4. Passive bonuses (First Strike, Berzerker, Retaliation, Executioner).
   Each computes bonusDamage vs `damageCrit`; stored as percent or flat in the
   BONUS layer; tags in the same layer combine via `stackingStyle`.
   `damageBonusLayer = damageCrit * (1 + sumPercents) + sumFlat`
5. Class weapon multiplier: `finalDamage = damageBonusLayer * weaponMultiplier`.
6. Life steal heals off `finalDamage` (does not change damage).

**Haste (move speed).** Use the haste level (via `skillLevels` or
`getSkillAttributeLevel`) as your multiplier input; EL clamps to the
movement min/max. If you add speed buffs:
```
requested = (1 + hasteBonus) * (1 + yourPercentBuff / 100)   // then clamp
```

**Defense (damage reduction).**
```
perPointDefense = getSkillAttributeConfigValue(DEFENSE)
reduction       = clamp(perPointDefense * level + raceBonus, 0, defenseCap)
finalDamage     = incomingDamage * (1 - reduction / 100)
```

**Precision / Ferocity.** Precision → crit chance. Ferocity → crit damage.
```
critChance     = baseChance + precision * perPointChance
critMultiplier = baseCrit   + ferocity  * perPointFerocity
```

**Discipline XP bonus.**
```
xpGainMultiplier = api.getSkillAttributeBonus(uuid, DISCIPLINE) / 100 + 1.0
finalXp          = rawXp * xpGainMultiplier * (1 + externalXpPercent / 100)
```

**XP to next level.**
```
xpNeeded(level) = baseXp * ((ln(level) + 1) * sqrt(level))^multiplier
```
`baseXp`, `multiplier` from `config.yml`. Helper: `api.getXpForNextLevel(level)`.

**Prestige-aware progression.** When your mod shows progression for players
whose cap / XP curve changes with prestige:
```java
int prestige = api.getPlayerPrestigeLevel(uuid);
int cap      = api.getLevelCap(uuid);
double need  = api.getXpForNextLevel(uuid, level);
```

**Level cap.** `api.getLevelCap()` returns the configured maximum; XP beyond
cap is ignored.
