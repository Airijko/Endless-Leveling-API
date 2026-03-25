# world-settings

Per-world mob leveling settings split into JSON bundles.

These files are not world generation data. They represent world-specific leveling, scaling, and mob override rules.

## Files

- `load-order.json`: explicit load order for settings bundles.
- `blacklisted-worlds.json`: world-id/name match rules that block all leveling and XP.
- `global.json`: `World_Overrides.Global` defaults.
- `default.json`: `World_Overrides.default` fallback.
- `major-dungeons.json`: major dungeon world overrides.
- `endgame-dungeons.json`: endgame dungeon world overrides.
- `shiva-dungeons.json`: tower of Shiva related world overrides.

## Notes

- Load order is defined in `load-order.json`.
- Runtime config is loaded directly from this `world-settings` folder using `load-order.json`.
