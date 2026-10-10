# Knightly Maid

NeoForge 1.21.1 add-on that lets a Touhou Little Maid (TLM) maid ride on the back of a Dragon Survival (DS) dragon player,
and ride her own horse, following her owner (see [Horses](#horses)).
TLM is required, on both client and server. Everything else is optional and used when installed:

| Mod | Used for |
|---|---|
| Dragon Survival | Riding on a dragon player's back. All DS calls are in `DragonCompat`, only reached when DS is loaded (`DragonSeat.DRAGON_SURVIVAL`), so without DS its classes are never loaded and the saddle keeps TLM's normal carry. |
| Touhou Little Maid Spell | Far spells kite on horseback; close-range spells (task id `maidspell:spell_combat_melee`) ride straight in. No code reference. |
| TACZ | Guns kite on horseback, through TLM's own gun task (an `IRangedAttackTask`). No code reference. |

## How to use

As a dragon, right click your maid with a saddle. She sits on your back.

- To dismount her, right click with the saddle in the air (not aiming at a block that uses right click). Like TLM's carry mode,
  because a player's crosshair passes through their own passengers, so clicking the maid herself is not possible.
- She is also put down next to you automatically if you turn human, log out, die or change dimension.
- If the game stops while she sits on your back without you logging out (e.g. a crash), she is put down next to you when you next log in.
- Human players keep TLM's normal saddle behaviour (carrying the maid in their arms).
- The dragon's body must be rideable by DS's rules (`DragonRidingHandler.dragonIsRideable`). There is no size limit.

## How it works

TLM carries a maid in its arms whenever her vehicle is a `Player`: its positioning Mixin, renderer rotation and `isRidingPlayer` animation
flag all check for that. This mod puts an invisible seat entity in between (dragon ← `knightly_maid:dragon_seat` ← maid),
so the maid rides a normal vehicle and uses her sitting pose and riding AI.

| Part | Where |
|---|---|
| Saddle interaction, through TLM's API event `InteractMaidEvent` at HIGH priority (before TLM's saddle handler). Turns off home mode, like TLM's saddle does. | `MountMaidEvents#mountDragon` |
| Saddle used in the air ejects the maid. TLM's own handler (`CancelSaddleMaidEvent`) only checks for a maid as the first passenger, which is the seat here. | `MountMaidEvents#ejectMaid` |
| Seat on the dragon's `MountingBone` (where DS seats player riders), facing the dragon's body yaw | `DragonSeat#getVehicleAttachmentPoint`, `DragonSeat#tick` |
| While the dragon is off the ground, the maid cannot leave on her own. TLM's panic AI (non-combat modes, when hurt or near hostiles), schedule and follow-owner tasks would otherwise make her jump off. On the ground TLM's behaviour is kept. Uses NeoForge `EntityMountEvent`. | `MountMaidEvents#keepMaidSeated` |
| A maid released from the seat (e.g. ejected while flying, or the dragon logged out mid-air) takes no damage on her next landing. Uses the saved attachment `knightly_maid:fall_protection` and `LivingFallEvent`. | `MountMaidEvents#protectDroppedMaid` |
| The seat does not pass the dragon's fall damage on to the maid (vanilla passes a vehicle's fall damage to its passengers), and a seated maid takes no suffocation damage when the dragon carries her into blocks. | `DragonSeat#causeFallDamage`, `MountMaidEvents#protectRidingMaid` |
| Cleanup: the seat drops the maid next to the dragon and removes itself when she dismounts, the dragon is no longer a rideable dragon, logs out, dies or changes dimension | `DragonSeat#tick`, `DragonSeat#removeVehicle` |
| Crash safety: a passenger is only saved with her root vehicle, and a player's passengers are never loaded back, so a seated maid is in no save. The world data `knightly_maid_seated_maids` (overworld `data/`) collects the seated maids while the world saves, in the same pass as the chunks, and puts each down next to her dragon on their next login. | `SeatedMaids`, `MountMaidEvents#restoreSeatedMaid` |

There are no Mixins.

## Horses

### How to use

- **Put her on a horse:** carry your maid (TLM's saddle carry, or on your dragon's back), hold a saddle and right click a horse-family
  mount with no rider. She rides it.
- **She follows you:** while she rides alone she steers the horse and gallops after you. In combat she rides to melee targets, and kites ranged targets like a skeleton (keeps her distance and circles). If you get too far, the horse (with her) is
  teleported next to you.
- **Take her back:** right click the horse, or the maid on it, with a saddle. She goes back to TLM's carry, or onto your back if you are a
  dragon.
- **Let her off:** shift + right click her (TLM's usual dismount).
- If a transfer fails you get the message "The maid cannot be moved there right now" and she stays where she was. A saddle click with
  nothing to transfer keeps the vanilla interaction (e.g. saddling or riding the horse).

### Which horses

Tag `knightly_maid:maid_mounts`: horse, donkey, mule, skeleton horse, zombie horse (more can be added, see [Customizing](#customizing)). Any of them, whether yours, wild or another player's.
No saddle is needed for her to steer. Baby horses and skeleton trap horses are refused. A wild horse stays wild, but does not buck her
off while she steers (vanilla switches off the movement goals of a mount steered by a mob).

### How it works

| Part | Where |
|---|---|
| She is the horse's controlling passenger, so its navigation is hers. A core brain behaviour (TLM API `@LittleMaidExtension` / `addExtraMaidBrain`) sets her walk target to the owner, and teleports the horse near the owner when too far. Same formulas as TLM's `MaidFollowOwnerTask` (which does not run while she rides), with the radius raised to at least 16 (`MaidMounts#followRadius`): TLM's follow radius (`MaidNonHomeRange`, 8 by default) is far too tight for a galloping horse that keeps its distance from enemies. Not in home mode. | `DriveMountToOwnerTask`, `KnightlyMaidExtension` |
| Saddle clicks on the horse (`EntityInteract`) and on the maid (`InteractMaidEvent`, replacing TLM's saddle and shift dismount handlers while she rides). A click is only cancelled when a transfer applies, decided from state synced to both sides. | `HorseEvents#transferWithSaddle`, `HorseEvents#interactRidingMaid` |
| She stays on her horse. TLM would otherwise make her get off when the owner leaves a vehicle, board the owner's boat or camel, panic, or end work-point tasks on schedule. Only the owner's transfers (a scoped flag, `MountMaidEvents#runAsOwner`), death or removal, and vanilla's underwater dismount (so she can surface) are allowed. Mount attempts are cancelled before `startRiding` dismounts her, so she is never half on two vehicles. | `HorseEvents#keepMaidOnMount` |
| No fall damage passed on by the horse, no suffocation | `MountMaidEvents#protectRidingMaid` |
| Her horse does not rear when hurt. Vanilla rears on 1 in 3 hits for a second and cannot move meanwhile, which stalls it in fights. | `HorseEvents#preventRearing` |
| In combat (riding work activity), TLM's riding brain does not move her, so it is added and gated to a maid driving her mount. Melee: vanilla `SetWalkTargetFromAttackTargetIfTargetOutOfReach`, as TLM uses on foot. Close-range spells (`maidspell:spell_combat_melee`): TLM `MaidRangedWalkToTarget`. | `KnightlyMaidExtension`, `WhileDriving` |
| Kiting for every other ranged mode (bow, crossbow, danmaku, trident, TACZ guns, far spells). Out of the mode's range or sight (its own `canSee`): ride closer. Under 6 blocks: back off to 10, straight away or, if that leaves the leash, at ±45°/±90°/±135°. Otherwise circle the target by 25° steps, switching direction every 1–2 s like a skeleton (the other way at the edge of the leash). With no valid waypoint she rides back to the owner (home mode: the home center) rather than standing next to the target. New waypoint every 10 ticks, horse pathfinding at gallop speed. Waypoints outside the home area, or horizontally beyond the mounted follow radius − 2 from the owner, are skipped. TLM's strafing tasks are not reused: they turn the maid instead of the horse, strafe at a quarter of the speed and only cover bows and crossbows. | `MountedKiteTask` |
| Speed: AI movement goes at (modifier × speed)², so the modifier is `sqrt(0.98 / speed)` per horse, the same pace as when a player rides it (gallop instead of walk). | `MaidMounts#gallopSpeed` |

## Customizing

Datapacks, KubeJS and other mods can add their own entities. Ready-made examples (datapack, KubeJS script, Java datagen) are in
[examples/](examples/README.md).

| Interface | Id | Meaning |
|---|---|---|
| Entity type tag | `knightly_maid:maid_mounts` | Mounts she drives herself. Any mob, but she only steers mobs that accept a mob as controlling passenger (`Mob#getControllingPassenger`). |
| Entity type tag | `knightly_maid:maid_carriers` | Entities she sits on, through the seat, while someone else moves. Carrying her, saddle-click the entity; saddle-click it (or her) again to take her back. `minecraft:player` makes every player carry her on their back. Empty by default. |
| Data map | `knightly_maid:mount_settings` | Per type: `seat_offset` `[x, y, z]` (carriers: from the feet, turned with the body, x left, y up, z forward) and `speed_modifier` (mounts). Synced to clients. |

| Part | Where |
|---|---|
| Carrier check (dragon player or tag) and seat placement: the offset is applied after the carrier placed the seat, so it also works for carriers that place passengers their own way | `DragonSeat#canCarry`, `DragonSeat#rideTick` |
| The seat keeps her from being a carrier's controlling passenger (vanilla `Mob` gives control to a mob first passenger), and players who mount later still go first | `DragonSeat` |
| Saving: on a player she is saved by `SeatedMaids`; any other carrier saves the seat and her with it, like any passenger | `DragonSeat#saveAsPassenger` |
| Saddle transfers to and from carriers | `HorseEvents#find`, `HorseEvents#interactRidingMaid` |
| Data map type | `MountSettings` |

## Known limits

- **Bone position on the server.** The mounting bone position is only known on the client. DS syncs it to the server for player riders
  only, so on the server the seat uses vanilla seating (top of the dragon's hitbox). What players see is correct; the maid's server-side
  hitbox and projectile origin may be about a block off.
- **No bone tilt.** The maid follows the bone's position and the dragon's body yaw, but not the bone's tilt (DS tilts player riders).
- **Combat** is TLM's riding AI: ranged modes work, and melee only reaches enemies next to her. It runs only during her schedule's work
  time.
- **Horse teleport** needs a walkable spot within 3 blocks of the owner, so it fails while the owner flies. Like TLM, she waits if her chunks
  unload.
- Saddle-clicking an unsaddled horse while carrying the maid moves her onto it instead of equipping the saddle.

## Build

Uses GraalVM 21. See the "Build (Minecraft 1.21.1)" section in `../AGENTS.md`.

```powershell
$env:JAVA_HOME = 'D:\minecraft\graalvm-community-openjdk-21.0.2+13.1'
.\gradlew.bat build
```

Output: `build/libs/knightly-maid-neoforge-mc1.21.1-0.1.0.jar`.

`libs/` holds the DS and TLM jars, used for compiling only (DS is still needed to compile `DragonCompat`). It is git-ignored; copy them from the modpack's `mods/` folder:

- `DragonSurvival-1.21.1-v2.0.71-25.09.2026-all.jar`
- `touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar`

## License

MIT, see [LICENSE](LICENSE).
