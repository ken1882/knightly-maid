# Knightly Maid: examples

Reference only. These files are not part of the mod.

| Folder | For | How to use |
|---|---|---|
| `datapack/knightly_maid_example/` | Pack makers | Copy into `saves/<world>/datapacks/` (or a global datapack mod), then `/reload` |
| `kubejs/` | KubeJS packs | Merge into the instance's `kubejs/` folder |
| `java/` | Mod developers | Datagen for your own entities |

## What you can add

| Interface | Id | Meaning |
|---|---|---|
| Entity type tag | `knightly_maid:maid_mounts` | Mounts she rides alone and drives herself, following you and fighting. Only mobs that accept a mob as controlling passenger work (horses do, most custom mounts that only allow players don't). |
| Entity type tag | `knightly_maid:maid_carriers` | Entities she sits on while someone else moves. Carrying her, saddle-click the entity. Adding `minecraft:player` makes every player carry her on their back. |
| Data map | `knightly_maid:mount_settings` | Optional per-type settings, see below |

`mount_settings` fields (all optional):

| Field | Type | Used for |
|---|---|---|
| `seat_offset` | `[x, y, z]` | Carriers: where she sits, from the carrier's feet, turned with its body (x left, y up, z forward). Without it the carrier places her itself. Dragon Survival dragons use their mounting bone on the client. |
| `speed_modifier` | number > 0 | Mounts: walk target speed while she drives. Without it she matches a player riding it. |

Notes:

- A carrier decides how many passengers it takes (`canAddPassenger`). If it is full, the saddle click fails with a message.
- Entities from mods that are not installed: use `"required": false` in tags, and `neoforge:conditions` in the data map (see the example JSON). Otherwise the game logs an error and skips them.
- The offsets in these examples are rough. Tune them in game.
