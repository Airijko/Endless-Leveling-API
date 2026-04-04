# Mob Leveling, XP, and Luck Pipeline

## 1. Mob Level Resolution (`MobLevelingManager.resolveMobLevelForEntity`)

Called by `MobLevelingSystem` once per entity (on first tick after spawn). Priority order:

1. **Gate override** — if the store has a registered gate, pick level from the gate's tier `[min, max]` range (+ boss offset if applicable)
2. **Area override** — if the mob's world-position falls inside a registered `AreaOverride` rectangle
3. **Viewport-based position resolve** — sample the nearest active player's chunk viewport; look up the world config (zone/biome tiers) for that position → `PROXIMITY` or `MIXED` source mode
4. **Fallback** — if no viewport candidate, use config fallback level for the world; logs WARN if world is unidentifiable (`parseFixedLevelRange: no lock…`)

Result is clamped to configured `[min, max]` range for that world.

---

## 2. `MobLevelingSystem` Per-Entity Tick (`processEntity`)

Fires every ~0.75 s (15 world ticks at 20 TPS) via `DelayedSystem`.

### Tracking key
`resolveTrackingIdentity` extracts `UUIDComponent`:
- **UUID-backed**: key = `MSB ^ rotateLeft(LSB, 1)` — UUID only, **no** `storePart` (critical: `ref.getStore()` and the `store` callback param can be different Java objects)
- **Index-backed**: key = `(identityHashCode(store) << 32) | entityIndex` — store IS part of the key here since index-only is not globally unique

### State per entity (`EntityRuntimeState`)
| Field | Purpose |
|---|---|
| `appliedLevel` | Locked-in level once resolved; hot-path reuse on every subsequent tick |
| `lastRecordedResolvedLevel` | Guards redundant `recordEntityResolvedLevel` calls |
| `settledHealthLevel` | Level at which `applyHealthModifier` succeeded; re-runs if level changes |
| `lastHealthUpdateTick` | Last world tick the nameplate HP was refreshed |
| `lastSeenTimeMillis` | TTL for `pruneStaleEntities` (100 s) |

### Per-tick flow
```
resolveTrackingIdentity
→ getOrCreateEntityState
→ check blocked (world blacklisted / entity blacklisted) → clearHealthStateForBlockedEntity (level snapshot preserved)
→ check not-in-range → clearTrackedNameplateIfNeeded (level snapshot preserved)
→ DeathComponent present → clearTrackedNameplateIfNeeded (level snapshot preserved, XP will fire shortly)
→ resolveAndAssignLevelOnce (first tick only; sets appliedLevel)
→ recordEntityResolvedLevel (UUID key + index fallback key)
   └─ if no health snapshot yet → seed recordEntityMaxHealth with live max HP (floor before scaling)
→ applyMobAugments
→ applyHealthModifier (sets scaled max HP; writes recordEntityMaxHealth with scaled value)
→ applyNameplate / updateHealthNameplate
```

### Snapshot maps (`MobLevelingManager`)
| Map | Key | Content |
|---|---|---|
| `entityResolvedLevelSnapshots` | tracking key | Assigned level integer |
| `entityHealthCompositionSnapshots` | tracking key | `MobHealthCompositionSnapshot` (base, scaled, lifeForce, combined max) |
| `trueBaseHealthCache` | tracking key | Unmodified native base HP before scaling |
| `entityAssignedAugmentSnapshots` | tracking key | Set of active augment IDs |

Both UUID key and index-fallback key are written at the same time so lookups succeed regardless of which key is available at query time.

---

## 3. XP Pipeline (`XpEventSystem.onDeath`)

Fires when `DeathComponent` is added to a mob entity (non-player, non-blacklisted).

### Step-by-step
```
1. Resolve killer player UUID from XpKillCreditTracker
2. Guard: world XP blacklist, entity blacklist
3. Resolve mob level:
   a. getEntityResolvedLevelSnapshot(ref, store, commandBuffer)
      → resolveTrackingKey (UUID or index)
      → primary lookup; fallback to index key if UUID-keyed entry missing
   b. mobLvlSource = "snapshot" | "fallback"
   c. fallback → resolveMobLevel (position-based; logs XP-LevelDiag FINE)
4. Resolve max HP for XP base:
   a. cachedMaxHealth = getEntityMaxHealthSnapshot (scaled, post-augment value)
   b. liveMaxHealth = statMap.get(health).getMax() (current at death, may be near 0)
   c. maxHealthForXp = max(cached, live) to avoid undercounting when cache lags
   d. baseXP = max(1, maxHealthForXp)
5. applyMobKillXpRules(player, mobLevel, baseXP):
   a. Level gap check (XP_Level_Range): if |playerLvl - mobLvl| > xpMaxDifference → 0
   b. xpMultiplier from world/gate rule set
   c. killRulesXP = baseXP × multiplier
6. Personal bonus multiplier (additive stack):
   archetypeXpBonus + disciplineBonusPct + luckXpBonusPct → additiveMult = 1 + sum/100
   projectedPersonalXP = killRulesXP × additiveMult
7. Grant:
   - Party present → partyManager.handleMobKillXpGainInRange (distributes killRulesXP; each
     member applies their own personal bonuses inside partyManager)
   - Solo → levelingManager.addXp(uuid, killRulesXP)
     (addXp applies personal bonuses internally before crediting)
8. Register drops for luck double-drop window (if luckDoubleDropSystem present)
9. XpKillCreditTracker.clearTarget
```

### XP-Report log fields
```
target        entity index of killed mob
mobLvl        level used for XP math
mobLvlSource  "snapshot" (correct) | "fallback" (position-based, may be wrong)
sourceMaxHP   value used as XP base  (cached=X live=Y)
baseXP        = sourceMaxHP
killRulesXP   = baseXP × killRulesMult
additiveMult  = 1 + (archetypeBonus + disciplineBonus + luckBonus) / 100
projectedPersonalXP = killRulesXP × additiveMult
```

---

## 4. Luck Pipeline

### Luck value assembly (`PassiveManager.getLuckValue`)
```
totalLuck = innate (passive level × value_per_level)
          + archetype passive LUCK bonus
          + selected augment luck (e.g. Four Leaf Clover = +50%)
```
Config (`config.yml` under `passives.luck`):
- `base_value: 2.5`, `value_per_level: 2.5`, `max_level: 40`, `unlock_level: 20`
- Max innate = 40 × 2.5 = **100%** luck from passives alone

### Luck → XP bonus (`LevelingManager.getLuckXpBonusPercent`)
```
luck ≤ 100%  → xpBonusPct = luck          (1:1)
luck > 100%  → xpBonusPct = 100 + (luck - 100) × 2   (2:1 above cap)
```
Applied as additive multiplier: `additiveMult += luckXpBonusPct / 100`

### Luck → Drop bonus (`LuckDoubleDropSystem`)
On mob kill `registerMobKillLoot` opens a time-window claim against the mob's UUID.
When the mob's drops are actually collected (separate event), the system checks player luck and
may duplicate the item stack. Players can toggle proc notifications in settings.

---

## 5. Known Edge Cases & Fixes

| Symptom | Root cause | Fix |
|---|---|---|
| `mobLvlSource=fallback`, `primaryLvl=null idxLvl=null` (negative `primaryKey`) | All snapshot write/read methods guarded with `trackingKey < 0L`, treating the sentinel as "any negative long". UUID-derived keys (`MSB ^ rotateLeft(LSB,1)`) are random longs; ~50% of all NPC UUIDs produce negative keys and are silently rejected at `recordEntityResolvedLevel` | Changed all 6 guards from `< 0L` to `== -1L` (-1L is the only true sentinel for "null ref") |
| `mobLvlSource=fallback`, `primaryLvl=null idxLvl=null` (cleared before death) | `clearMobLevelingStateForBlockedEntity` called `clearMobLevelRuntimeStateForEntity` which wiped the UUID-keyed level snapshot when the store was briefly unidentifiable | Use `clearHealthStateForBlockedEntity` (preserves level snapshot) in the blocked path |
| `cached=-1, live=962` giving windfall XP from low-HP mob with high level | No health snapshot existed when mob died before `applyHealthModifier` ran; `liveMaxHealth` used raw native HP unscaled | Seed `recordEntityMaxHealth` with native max HP immediately on first `recordEntityResolvedLevel`; `applyHealthModifier` overwrites with scaled value |
| `primaryLvl=null idxLvl=null` (UUID correct, snapshot never found) | UUID tracking key included `System.identityHashCode(store)` which differed between `MobLevelingSystem` (`ref.getStore()`) and `MobLevelingManager` (`store` callback param) | Removed `storePart` from UUID-based keys; UUID alone is globally unique |
| Nameplate HP never updated | `Math.floorMod(worldTick, 5) == 0` almost never true when `DelayedSystem` fires at non-deterministic tick offsets | Per-entity `lastHealthUpdateTick` + world-tick delta ≥ `Nameplate_Update_Ticks` |

---

## 6. Debug Logging Sections

| Section key | Logger | What it shows |
|---|---|---|
| `mob_level_flow` | `MobLevelingSystem` | Per-entity level resolution, health settle phase |
| `mob_level_nameplate` | `MobLevelingSystem.nameplate` | Nameplate level changes (`[MOB_LEVEL_NAMEPLATE]`) |
| `mob_common_defense` | `MobLevelingSystem` | Defense stat application |
| XP-LevelDiag (FINE) | `XpEventSystem` | Fires when `mobLvlSource=fallback`; dumps primary/idx snapshot keys + levels |
| XP-Report (INFO) | `XpEventSystem` | Full XP calculation breakdown on every kill |
