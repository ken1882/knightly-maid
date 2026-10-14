# Changelog

All notable changes to Knightly Maid. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.2] - 2026-10-14

### Fixed

- Dragon Survival spin attacks no longer damage maids carried on the dragon.
- Red fox scroll no longer accumulates stale maid positions after teleports or fast movement.
- On login, stale red fox scroll entries are cleaned up, keeping only the latest entry per unloaded maid.
- Touhou Little Maid Spell now properly re-registers maids after teleports or fast movement, preventing Ender Pocket, spell book, and spell data loss.

## [0.1.1] - 2026-10-12

### Added

- Maids now follow their owner across dimensions, including portals, waypoint teleports, and `/tp`, whether on foot, riding a horse, or carried with Touhou Little Maid's saddle. Config: `followAcrossDimensions` (default: `true`).
- Common config `config/knightly_maid-common.toml`, also editable in game with config screen mods such as Configured.
- `verboseLogging` option (default `false`): logs seating, teleports, dimension changes, blocked dismounts and saddle transfers to `latest.log`. When a maid leaves a seat on her own, the stack trace shows what made her.

### Fixed

- A maid seated on a dragon was left behind after a long teleport within a dimension (waypoints, Waystones, `/tp`), frozen where the dragon had been, and could be lost when that chunk unloaded. The seat now follows the dragon.
- A seated maid was thrown off right after any teleport. Touhou Little Maid makes maids near their owner leave their vehicle for 3s after the owner leaves one, and counts every teleport as leaving a vehicle. She now stays seated during that time.
- A seated maid was put down in the old dimension when the dragon changed dimension. She now follows the dragon and is seated again.
- If cross-dimension transfer fails, the maid is restored to the original location; if that also fails, full recovery data is logged.
- Crash recovery now keeps failed seated-maid transfers for retry on next login and avoids duplicating maids already loaded elsewhere.
- Horses ridden by maids no longer pass through portals without them.

### Changed

- README rewritten in Chinese and English.

## [0.1.0] - 2026-10-10

### Added

- A maid can sit on a Dragon Survival dragon player's back (saddle on the maid), through an invisible seat entity on the dragon's
  mounting bone. She stays seated while the dragon flies, takes no fall or suffocation damage while seated, and is put down next to the
  dragon when they turn human, log out, die or change dimension.
- Crash safety: maids seated on dragons are saved separately and put down next to their dragon on the next login.
- A maid can ride and steer her own horse (tag `knightly_maid:maid_mounts`), following her owner at a gallop and being teleported along
  when too far. Saddle transfers between the player (TLM's carry or a dragon's back) and the horse.
- Mounted combat: melee and close-range spells ride to the target; bows, crossbows, danmaku, tridents, TACZ guns and far spells kite like
  a skeleton. Her horse does not rear when hit.
- YSM models sit at the right height on the seat.
- Customization: entity type tags `knightly_maid:maid_mounts` and `knightly_maid:maid_carriers`, data map `knightly_maid:mount_settings`
  (`seat_offset`, `speed_modifier`), with datapack, KubeJS and Java examples.
