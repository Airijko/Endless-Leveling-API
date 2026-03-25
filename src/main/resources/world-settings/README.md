# world-settings

Per-world mob leveling settings split into JSON bundles.

These files are not world generation data. They represent world-specific leveling, scaling, and mob override rules.

## Files

- `blacklisted-worlds.json`: world-id/name match rules that block all leveling and XP.
- `global.json`: `World_Overrides.Global` defaults.
- `default.json`: `World_Overrides.default` world-id fallback entry.
- `major-dungeons.json`: major dungeon world overrides.
- `endgame-dungeons.json`: endgame dungeon world overrides.
- `shiva-dungeons.json`: tower of Shiva related world overrides.

## Notes

- Runtime config auto-loads all `.json` files in this folder.
- Merge precedence is fixed for core files: `blacklisted-worlds.json` -> `global.json` -> `default.json`.
- Any other `.json` files are loaded automatically after those core files; no registration step is required.
