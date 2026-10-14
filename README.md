**語言 / Language:**
[中文](#中文版本) | [English](#english-version)

---

<a id="中文版本"></a>
# 🐉『騎士女僕』Knightly Maid 🐎

車萬女僕（Touhou Little Maid，TLM）的附屬模組：讓女僕坐在龍之生存（Dragon Survival，DS）龍形態玩家的背上，或是騎馬跟隨主人。

女僕工作的戰鬥行為兼容以下模組，僅在安裝時生效

| 模組 | 用途 |
|---|---|
| Dragon Survival | 坐在龍形態玩家的背上並協助戰鬥 |
| Touhou Little Maid Spell | 遠程法術會在馬背上放風箏；近戰法術（`maidspell:spell_combat_melee`）直接騎馬衝上去。無代碼引用。 |
| TACZ | 槍械工作模式下的女僕會騎射放風箏 |

## 使用方式

!!! 建議適時備份女僕資料以免發生不測 !!!

- 安裝 DS 且玩家處於龍形態時，手持鞍右鍵你的女僕，讓她騎你。
  - 下車：手持鞍右鍵（建議蹲下按來降低墜落傷害）任意位置。與 TLM 的公主抱模式時放下操作相同。
  - 龍的身體必須符合 DS 的騎乘規則（`DragonRidingHandler.dragonIsRideable`）才能讓女僕騎。沒有體型限制。
  - 當你變回人形、退出遊戲或死亡時，她也會自動下車。
  - 傳送時她會繼續坐在你背上，同維度或跨維度都可以（例如傳送點、Waystones、`/tp`、傳送門）。
  - 如果遊戲在她坐在你背上時意外停止且你沒有正常退出（例如崩潰），下次登入時她會被放在你身邊。
- 正在跟隨你的女僕（步行或騎馬）會在你切換維度時一起跟過來。TLM 本身只在同一維度內跟隨。可在設定中關閉（`followAcrossDimensions`）。
- 讓女僕騎馬
  - **上馬：** 抱著你的女僕（TLM 的鞍抱起，或在你龍背上），手持鞍右鍵一匹沒有騎手的馬類坐騎。她會騎上去。
  - **跟隨：** 她獨自騎馬時會操控馬匹，策馬追隨你。戰鬥中她會騎馬衝向近戰目標，對遠程目標則像骷髏一樣風箏（保持距離並繞圈）。
  如果你離得太遠，馬（連同她）會被傳送到你身邊。
  - **抱起：** 手持鞍右鍵馬或馬上的女僕。她會回到 TLM 的抱起狀態，如果你是龍則回到你背上。
  - **下馬：** 潛行 + 右鍵目標女僕（TLM 的常規下馬方式）。
  - **跨維度：** 你切換維度時（傳送門、傳送點、`/tp` 等），她會連同馬一起跟過來。
  - 如果轉移失敗，你會收到訊息"現在無法把女僕移到那裡"，而女僕將會留在原處。沒有可轉移物件時的鞍點擊保留原版互動（例如給馬裝鞍或騎馬）。


### 支援的坐騎種類

標籤 `knightly_maid:maid_mounts`：馬、驢、騾、骷髏馬、殭屍馬（可以添加更多，請參閱[自訂](#zh-customizing)）。
女僕操控馬匹不需要坐騎身上有鞍具；幼年馬和骷髏陷阱馬則無法騎乘。被她騎上的野馬仍會保持野生狀態，但在她操控時不會把她甩下來（被生物操控的坐騎，原版會關閉其移動目標）。

## 實作原理

只要女僕的載具是 `Player`，TLM 就會把她抱在懷裡：其定位 Mixin、渲染器旋轉以及 `isRidingPlayer` 動畫標誌都會檢查這一點。
本模組在兩者之間放了一個看不見的座位實體（龍 ← `knightly_maid:dragon_seat` ← 女僕），這樣女僕騎的是普通載具，使用她的坐姿和騎乘 AI。

| 功能 | 說明 | 位置 |
|---|---|---|
| 鞍具交互 | 透過 TLM 的 `InteractMaidEvent` 處理，使用 HIGH 優先權，會比 TLM 原本的鞍處理更早執行。騎乘時同樣會關閉家模式。 | `MountMaidEvents#mountDragon` |
| 放下女僕 | 對空氣使用鞍可讓女僕下來。TLM 原本的 `CancelSaddleMaidEvent` 只檢查第一個乘客是否為女僕，但這裡第一個乘客是座位。 | `MountMaidEvents#ejectMaid` |
| 座位位置 | 座位跟隨龍的 `MountingBone`，也就是 DS 放置玩家騎手的位置，並跟隨龍的身體朝向。 | `DragonSeat#getVehicleAttachmentPoint`、`DragonSeat#tick` |
| 防止飛行中跳車 | 龍離地時禁止女僕自行下來，避免 TLM 的 AI、日程或跟隨任務讓她跳車。龍在地面時則維持 TLM 原本行為，但主人離開載具後的 3 秒內除外：TLM 的 `MaidFollowOwnerVehicleTask` 會讓附近的女僕跟著下車，而 TLM 把每次傳送也當成離開載具。 | `MountMaidEvents#keepMaidSeated` |
| 墜落保護 | 女僕若在空中離開座位，下一次落地不會受到摔落傷害。 | `MountMaidEvents#protectDroppedMaid` |
| 傷害保護 | 座位不會把龍的墜落傷害傳給女僕；龍把女僕帶進方塊時，女僕也不會受到窒息傷害；揹著她的龍也傷不到她（例如 Dragon Survival 的飛行旋轉攻擊）。 | `DragonSeat#causeFallDamage`、`MountMaidEvents#protectRidingMaid` |
| 座位清理 | 女僕下車、龍型態失效、退出遊戲、死亡或切換維度時，座位會把女僕放到龍旁邊並移除自己。 | `DragonSeat#tick`、`DragonSeat#removeVehicle` |
| YSM 模型修正 | YSM 模型原本會讓女僕坐得過高，因此座位會將 YSM 女僕降低約 0.6 格。TLM 坐姿與馬匹不做此修正。模型作者也可提供 `vehicle$knightly_maid:dragon_seat` 專用動畫。 | `DragonSeat#getPassengerAttachmentPoint` |
| 崩潰／存檔保護 | 原版不會可靠保存玩家載具上的女僕，因此模組會另外記錄正在騎龍的女僕。龍下次載入時，會把女僕恢復到龍旁邊，避免女僕遺失。恢復失敗時保留記錄，下次登入再試；若她已在世界中則不再恢復，以免重複。 | `SeatedMaids`、`MountMaidEvents#restoreSeatedMaid` |
| 重新追蹤的女僕 | NeoForge 在實體只是暫時停止追蹤時（例如傳送或高速飛行把她帶進尚未載入的區塊）會當成離開世界處理，重新追蹤時卻不會當成加入世界。TLM 因此會多一筆紅狐卷軸的過期位置記錄；車萬女僕法術則會把她移出女僕登記（末影口袋看不到她）、釋放法術書，並在 100 tick 後刪除她的法術資料。模組會在她重新被追蹤時清除這些記錄，並重新執行車萬女僕法術的加入處理。登入時也會清理舊存檔中的過期記錄：每位女僕只保留最新一筆，目前已載入的女僕則全部清除。 | `MaidRetrackFix`、`MaidSpellCompat` |
| 傳送跟隨 | 乘客只有在自己所在的區塊仍在運算實體時才會跟著載具移動。長距離傳送後座位會留在原地，區塊卸載時女僕也不會被保存，因此座位離龍超過 16 格時，模組會在下一個 tick 開始時把座位和女僕移到龍身上。 | `MountMaidEvents#followTeleportedDragon` |
| 跨維度跟隨 | 原版在玩家切換維度時會放下乘客。模組會在下一個 tick 用 `/tp` 相同的方式（`Entity#teleportTo`）把女僕傳送到龍身邊，再讓她坐回座位。TLM 的 `EntityMaid#changeDimension`（傳送門）不會真的切換維度，所以不使用它；延後一個 tick 是為了避開觸發傳送的模組的呼叫堆疊，否則錨定核心會清空女僕資料。若新維度拒絕加入她（例如其他模組取消加入），會把她放回原維度原處；連原處也被拒絕時，會在日誌記錄她的完整資料以便手動恢復。 | `MountMaidEvents#rememberTravellingMaid`、`MountMaidEvents#followDragonToDimension` |
| 騎馬跟隨主人 | 女僕是馬的控制乘客，所以實際尋路由馬負責。模組會讓馬跟隨主人，距離太遠時直接傳送到主人附近。跟隨半徑至少為 16 格；家模式下停用。 | `DriveMountToOwnerTask`、`KnightlyMaidExtension` |
| 鞍具交互 | 對馬或騎馬中的女僕使用鞍，可在適用時進行騎乘轉移，並取代 TLM 原本的鞍具與 Shift 下馬處理。 | `HorseEvents#transferWithSaddle`、`HorseEvents#interactRidingMaid` |
| 防止自行下馬 | 騎馬後女僕會保持在馬上，避免被 TLM 的跟隨、乘船、恐慌或日程 AI 強制下馬。只允許主人操作、死亡／移除，以及原版水下自動下馬。 | `HorseEvents#keepMaidOnMount` |
| 傷害保護 | 騎馬時不會承受馬傳遞的摔落傷害，也不會受到窒息傷害。 | `MountMaidEvents#protectRidingMaid` |
| 防止馬揚蹄 | 女僕的馬受傷時不會觸發原版揚蹄，避免戰鬥中停住約 1 秒。 | `HorseEvents#preventRearing` |
| 不單獨穿越傳送門 | 女僕操控馬時，馬不會穿過傳送門：原版會帶著乘客一起切換維度，但 TLM 的女僕不會真的切換維度，馬會獨自抵達。 | `HorseEvents#keepMountWithMaid` |
| 跟隨主人跨維度 | TLM 的女僕只在同一維度內跟隨主人，也不能穿過傳送門。主人切換維度時，模組會記下正在跟隨的女僕（不在家模式，且步行、操控自己的坐騎或被主人抱著），並在下一個 tick 用與龍背跨維度相同的方式移過去：步行的女僕用 TLM 的跟隨傳送找主人旁邊的空位；騎馬的女僕會連馬一起移動，再坐回馬上。 | `MountMaidEvents#rememberFollowingMaids`、`MountMaidEvents#followOwnerToDimension` |
| 近戰移動 | 戰鬥時補上騎馬專用移動 AI。近戰會騎近目標；近戰法術則使用 TLM 的遠程接近邏輯。 | `KnightlyMaidExtension`、`WhileDriving` |
| 遠程風箏 | 弓、弩、槍械、三叉戟、遠程法術等模式會自動保持距離：太遠就接近，6 格內就後退到約 10 格，其他情況繞著目標移動。若路徑受限，會改變方向；無法找到合適位置時則返回主人或家的中心。 | `MountedKiteTask` |
| 騎乘速度 | AI 速度會依馬本身的移動速度修正，使女僕騎馬時接近玩家騎乘的疾馳速度，而不是普通步行速度。 | `MaidMounts#gallopSpeed` |

<a id="zh-customizing"></a>
## 自訂

資料包、KubeJS 以及其他模組可以新增自己的載具。現成範例（資料包、KubeJS 腳本、Java 資料生成）位於[examples/](examples/README.md)。

| 介面 | Id | 意義 |
|---|---|---|
| 實體類型標籤 | `knightly_maid:maid_mounts` | 她自己操控的坐騎。任意生物都可以，但她只能操控接受生物作為控制乘客的生物（`Mob#getControllingPassenger`）。 |
| 實體類型標籤 | `knightly_maid:maid_carriers` | 她透過座位坐在其上、由其他人移動的實體。抱著她時，手持鞍點擊該實體；再次手持鞍點擊該實體（或她）可把她帶回。 `minecraft:player` 會讓每個玩家都能把她背在背上。預設為空。 |
| 資料映射 | `knightly_maid:mount_settings` | 按類型設定：`seat_offset` `[x, y, z]`（載體：從腳底算起，隨身體旋轉，x 向左、y 向上、z 向前）和 `speed_modifier`（坐騎）。會同步到客戶端。 |

| 部份 | 位置 |
|---|---|
| 載體檢查（龍形態玩家或標籤）和座位放置：偏移在載體放置座位之後應用，因此對自行放置乘客的載體同樣有效 | `DragonSeat#canCarry`、`DragonSeat#rideTick` |
| 座位使她不會成為載體的控制乘客（原版 `Mob` 會把控制權交給作為第一個乘客的生物），之後騎上的玩家仍然排在前面 | `DragonSeat` |
| 保存：在玩家身上時由 `SeatedMaids` 保存；其他載體與任何乘客一樣，連同座位和她一起保存 | `DragonSeat#saveAsPassenger` |
| 與載具之間的鞍轉移 | `HorseEvents#find`、`HorseEvents#interactRidingMaid` |
| 資料映射類型 | `MountSettings` |

## 設定

`config/knightly_maid-common.toml`，也可以用 Configured 等設定介面模組在遊戲中修改。

| 選項 | 預設 | 說明 |
|---|---|---|
| `followAcrossDimensions` | `true` | 主人切換維度時，帶上正在跟隨的女僕（步行或騎馬）。坐在龍背上的女僕不受此選項影響，總會一起過去。 |
| `verboseLogging` | `false` | 在 `latest.log`（INFO 等級）記錄模組對乘坐中女僕的處理：乘坐、傳送、跨維度、被攔截的下車、鞍具轉移等。女僕自行下車時會附上呼叫堆疊，方便找出是誰讓她下車。用於排查問題，日誌可能很多。 |

## 已知限制

- 騎乘位置主要由客戶端渲染，DS 只會把騎手位置同步到服務端。因此服務端仍使用原版座位位置，也就是龍的碰撞箱頂部。玩家畫面看起來正常，但女僕在服務端的碰撞箱與彈射物發射點，可能會有約 1 格的偏差。
- 第一次離開終界（播放片尾字幕）不經過切換維度的流程，女僕會被留在終界。
- 關閉 `followAcrossDimensions` 時，跟隨的女僕（包括騎馬的）會留在原維度等待。把馬單獨傳送到其他維度的方式（例如 Waystones 傳送拴繩上的生物）會讓她和馬分開。

## 專案建置

使用 GraalVM 21。請參閱 `../AGENTS.md` 中的 "Build (Minecraft 1.21.1)" 一節。

```powershell
$env:JAVA_HOME = 'D:\minecraft\graalvm-community-openjdk-21.0.2+13.1'
.\gradlew.bat build
```

輸出：`build/libs/knightly-maid-neoforge-mc1.21.1-0.1.2.jar`。

`libs/` 存放 DS、TLM 和車萬女僕法術的 jar，僅用於編譯（DS 和車萬女僕法術是可選模組，但編譯 `DragonCompat` 與 `MaidSpellCompat` 仍需要它們）。該目錄被 git 忽略；請從整合包的 `mods/` 資料夾複製：

- `DragonSurvival-1.21.1-v2.0.71-25.09.2026-all.jar`
- `touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar`
- `touhou_little_maid_spell-1.21.1-1.9.0-neoforge.jar`

## 授權

MIT，見 [LICENSE](LICENSE)。

<hr>

<a id="english-version"></a>
# 🐉『Knightly Maid』🐎

An add-on for Touhou Little Maid (TLM): your maid can sit on the back of a Dragon Survival (DS) dragon player, or ride her own horse and follow her owner.

The maid's work and combat behaviour is compatible with these mods, active only when they are installed:

| Mod | Used for |
|---|---|
| Dragon Survival | Sitting on a dragon player's back (and fight) |
| Touhou Little Maid Spell | Far spells kite on horseback; close-range spells (`maidspell:spell_combat_melee`) ride straight in. No code reference. |
| TACZ | In gun work mode the maid kites, shooting from horseback |

## How to use

!!! Back up your maids' data from time to time, just in case !!!

- With DS installed and you're in dragon form, right click your maid with a saddle to have her ride on your back.
  - Dismount: right click anywhere with the saddle (sneaking while clicking is recommended to reduce fall damage). Same as putting her down from TLM's princess carry.
  - The dragon's body must be rideable by DS's rules (`DragonRidingHandler.dragonIsRideable`) for the maid to ride it. There is no size limit.
  - She is also put down next to you automatically if you turn human, log out or die.
  - She stays on your back when you teleport, within a dimension or to another one (e.g. waypoints, Waystones, `/tp`, portals).
  - If the game stops while she sits on your back without you logging out (e.g. a crash), she is put down next to you when you next log in.
- Maids following you (on foot or on a horse) come along when you change dimension. TLM itself only lets them follow within a dimension. Can be turned off in the config (`followAcrossDimensions`).
- Putting your maid on a horse
  - **Mount:** carry your maid (TLM's saddle carry, or on your dragon's back), hold a saddle and right click a horse-family mount with no rider. She rides it.
  - **Follow:** while she rides alone she steers the horse and gallops after you. In combat she rides to melee targets, and kites ranged targets like a skeleton (keeps her distance and circles). If you get too far, the horse (with her) is teleported next to you.
  - **Pick up:** right click the horse, or the maid on it, with a saddle. She goes back to TLM's carry, or onto your back if you are a dragon.
  - **Dismount:** shift + right click her (TLM's usual dismount).
  - **Other dimensions:** when you change dimension (portals, waypoints, `/tp`, ...) she comes along with her horse.
  - If a transfer fails you get the message "The maid cannot be moved there right now" and she stays where she was. A saddle click with nothing to transfer keeps the vanilla interaction (e.g. saddling or riding the horse).


### Supported mounts

Tag `knightly_maid:maid_mounts`: horse, donkey, mule, skeleton horse, zombie horse (more can be added, see [Customizing](#customizing)).
The mount does not need a saddle for her to steer it; baby horses and skeleton trap horses cannot be ridden. A wild horse she rides stays wild, but does not buck her off while she steers (vanilla switches off the movement goals of a mount steered by a mob).

## How it works

TLM carries a maid in its arms whenever her vehicle is a `Player`: its positioning Mixin, renderer rotation and `isRidingPlayer` animation flag all check for that.
This mod puts an invisible seat entity in between (dragon ← `knightly_maid:dragon_seat` ← maid), so the maid rides a normal vehicle and uses her sitting pose and riding AI.

| Feature | Description | Where |
|---|---|---|
| Saddle interaction | Handled through TLM's `InteractMaidEvent` at HIGH priority, before TLM's own saddle handler. Turns off home mode, like riding does in TLM. | `MountMaidEvents#mountDragon` |
| Putting the maid down | A saddle used in the air puts the maid down. TLM's own `CancelSaddleMaidEvent` only checks for a maid as the first passenger, which is the seat here. | `MountMaidEvents#ejectMaid` |
| Seat position | The seat follows the dragon's `MountingBone`, where DS seats player riders, and faces the dragon's body direction. | `DragonSeat#getVehicleAttachmentPoint`, `DragonSeat#tick` |
| No jumping off in flight | While the dragon is off the ground the maid cannot leave on her own, so TLM's AI, schedule or follow tasks cannot make her jump off. On the ground TLM's behaviour is kept, except for 3 s after the owner leaves a vehicle: TLM's `MaidFollowOwnerVehicleTask` then makes nearby maids leave theirs, and TLM counts every teleport as leaving a vehicle. | `MountMaidEvents#keepMaidSeated` |
| Fall protection | A maid who leaves the seat in mid-air takes no fall damage on her next landing. | `MountMaidEvents#protectDroppedMaid` |
| Damage protection | The seat does not pass the dragon's fall damage on to the maid, she takes no suffocation damage when the dragon carries her into blocks, and the dragon carrying her cannot hurt her (e.g. Dragon Survival's flight spin attack). | `DragonSeat#causeFallDamage`, `MountMaidEvents#protectRidingMaid` |
| Seat cleanup | When the maid dismounts, or the dragon is no longer in dragon form, logs out, dies or changes dimension, the seat puts the maid down next to the dragon and removes itself. | `DragonSeat#tick`, `DragonSeat#removeVehicle` |
| YSM model fix | YSM models would make the maid sit too high, so the seat lowers a maid with a YSM model by about 0.6 blocks. Not done in TLM's sitting pose or on horses. Model authors can also provide a dedicated `vehicle$knightly_maid:dragon_seat` animation. | `DragonSeat#getPassengerAttachmentPoint` |
| Crash / save protection | Vanilla does not reliably save a maid riding on a player, so the mod keeps its own record of maids riding dragons. When the dragon next loads in, the maid is restored next to them, so she is never lost. If that fails the record is kept and retried on the next login; if she is already in the world she is not restored again, so she is not duplicated. | `SeatedMaids`, `MountMaidEvents#restoreSeatedMaid` |
| Maids tracked again | When an entity only stops being tracked for a moment (e.g. a teleport or a fast flight carries her into a chunk that is not loaded yet), NeoForge handles it like leaving the world, but not like joining when tracking starts again. So TLM keeps one more stale red fox scroll entry each time, and Touhou Little Maid Spell drops her from its maid registry (the Ender Pocket no longer lists her), releases her spell books and deletes her spell data 100 ticks later. Once she is tracked again the mod clears those entries and runs Touhou Little Maid Spell's join handling again. On login it also cleans up older saves: only the newest entry of each maid is kept, and none for maids that are loaded right now. | `MaidRetrackFix`, `MaidSpellCompat` |
| Following teleports | A passenger only moves with its vehicle while its own chunk is entity-ticking. After a long teleport the seat would stay behind, and the maid would not be saved when that chunk unloads. So when the seat is more than 16 blocks from the dragon, the mod moves the seat and the maid onto the dragon at the start of the next tick. | `MountMaidEvents#followTeleportedDragon` |
| Following to another dimension | Vanilla drops a player's passengers when the player changes dimension. On the next tick the mod teleports the maid to the dragon the way `/tp` does (`Entity#teleportTo`) and seats her again. TLM's `EntityMaid#changeDimension` (portals) does not actually change dimension, so it is not used. Waiting a tick keeps the move out of the call stack of the mod that teleported the dragon, which the Anchor Core would otherwise answer by wiping the maid's data. If the new dimension refuses her (e.g. another mod cancels her joining), she is put back where she was; if that is refused too, her full data is logged so she can be restored by hand. | `MountMaidEvents#rememberTravellingMaid`, `MountMaidEvents#followDragonToDimension` |
| Following the owner on horseback | The maid is the horse's controlling passenger, so the horse does the actual pathfinding. The mod makes the horse follow the owner, and teleports it next to the owner when too far. The follow radius is at least 16 blocks; off in home mode. | `DriveMountToOwnerTask`, `KnightlyMaidExtension` |
| Saddle interaction | A saddle used on a horse or on a riding maid transfers her where applicable, replacing TLM's own saddle and shift dismount handling. | `HorseEvents#transferWithSaddle`, `HorseEvents#interactRidingMaid` |
| No dismounting on her own | Once on a horse the maid stays on it, so TLM's follow, boat boarding, panic or schedule AI cannot force her off. Only owner actions, death / removal, and vanilla's underwater dismount are allowed. | `HorseEvents#keepMaidOnMount` |
| Damage protection | While riding a horse she takes no fall damage passed on by the horse, and no suffocation damage. | `MountMaidEvents#protectRidingMaid` |
| No rearing | The maid's horse does not rear (vanilla) when hurt, so it does not stall for about a second in combat. | `HorseEvents#preventRearing` |
| No portals without her | While she drives her horse, it does not go through portals: vanilla takes the passengers along, but TLM's maid does not actually change dimension, so the horse would arrive alone. | `HorseEvents#keepMountWithMaid` |
| Following the owner to another dimension | TLM's maids follow their owner only within a dimension and cannot use portals. When the owner changes dimension, the mod notes the maids following them (not in home mode; on foot, driving their own mount, or carried by the owner) and moves them on the next tick the same way as from a dragon's back: maids on foot get a free spot next to the owner through TLM's follow teleport; a maid on her horse is moved with it and seated again. | `MountMaidEvents#rememberFollowingMaids`, `MountMaidEvents#followOwnerToDimension` |
| Melee movement | Adds mounted movement AI in combat. Melee rides up to the target; close-range spells use TLM's ranged approach logic. | `KnightlyMaidExtension`, `WhileDriving` |
| Ranged kiting | Bow, crossbow, gun, trident, far spell and other ranged modes keep their distance automatically: ride closer when too far, back off to about 10 blocks when within 6, otherwise circle the target. Changes direction when the path is blocked; with no suitable spot she rides back to the owner or the home center. | `MountedKiteTask` |
| Riding speed | AI speed is adjusted to each horse's own movement speed, so a maid on horseback gallops at about the speed of a player riding it, instead of walking. | `MaidMounts#gallopSpeed` |

## Customizing

Datapacks, KubeJS and other mods can add their own vehicles. Ready-made examples (datapack, KubeJS script, Java datagen) are in [examples/](examples/README.md).

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

## Config

`config/knightly_maid-common.toml`, also editable in game with a config screen mod such as Configured.

| Option | Default | Meaning |
|---|---|---|
| `followAcrossDimensions` | `true` | Bring the owner's following maids (on foot or on their horse) along when the owner changes dimension. Maids seated on a dragon always come along, whatever this option says. |
| `verboseLogging` | `false` | Logs what the mod does with seated and riding maids to `latest.log` (INFO level): seating, teleports, dimension changes, blocked dismounts, saddle transfers. When a maid leaves a seat on her own the stack trace is included, to show what made her. For troubleshooting; can be noisy. |

## Known limits

- The riding position mostly comes from client-side rendering, and DS only syncs rider positions to the server for player riders. So the server still uses the vanilla seat position, the top of the dragon's hitbox. It looks right on players' screens, but the maid's server-side hitbox and projectile origin may be about 1 block off.
- The first exit from the End (end credits) does not go through the dimension change, so the maid is left in the End.
- With `followAcrossDimensions` off, following maids (also on horses) wait in the old dimension. Moving the horse to another dimension on its own (e.g. Waystones' transport of leashed mobs) separates them.

## Build

Uses GraalVM 21. See the "Build (Minecraft 1.21.1)" section in `../AGENTS.md`.

```powershell
$env:JAVA_HOME = 'D:\minecraft\graalvm-community-openjdk-21.0.2+13.1'
.\gradlew.bat build
```

Output: `build/libs/knightly-maid-neoforge-mc1.21.1-0.1.2.jar`.

`libs/` holds the DS, TLM and Touhou Little Maid Spell jars, used for compiling only (DS and Touhou Little Maid Spell are optional, but still needed to compile `DragonCompat` and `MaidSpellCompat`). It is git-ignored; copy them from the modpack's `mods/` folder:

- `DragonSurvival-1.21.1-v2.0.71-25.09.2026-all.jar`
- `touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar`
- `touhou_little_maid_spell-1.21.1-1.9.0-neoforge.jar`

## License

MIT, see [LICENSE](LICENSE).
