# Galaxy SMP Plugin – Roadmap v2

Living document copied from [ROADMAP.md](./ROADMAP.md), including its existing local changes. Source of ideas: [IdeasOnPaper.md](./IdeasOnPaper.md). The Phase 5 and 6 correction checklists below track the newer owner feedback and supersede conflicting older descriptions. Checked implementation items are not a claim of in-game verification.

## Mandatory Progress Tracking

- Whenever a roadmap checklist item is completed, change its checkbox to `[x]` in the same work session, after verifying the implementation. Never leave completed work unchecked or mark incomplete work as done.
- Update [Changed.md](./Changed.md) in the same work session with a human-readable account of completed work, tests, workflow/configuration changes, decisions, and any setup or groundwork that affects later phases.
- Keep these records current automatically; do not wait for the user to remind you.

## 1. Target Platform

- **Server:** Paper 26.3. Plugin declares `api-version: '26.3'`.
- **Java:** 25+ (Paper 26.3 runtime requirement). **Build:** Gradle + `paper-api` (`compileOnly`); use `paperweight-userdev` only if NMS is needed (avoid).
- **Config/data:** `config.yml` (tunables; legacy per-item carry settings are retained for compatibility but no longer control the limit), `data.yml`/SQLite (item ownership, ritual state, cooldowns, crafted flags).
- **Packaging:** single shaded-free jar, `paper-plugin.yml` or `plugin.yml`.
- **Plugins:** Galaxy SMP and the required Head Drop dependency only. Nebula Spear uses the server's native spear/Lunge behavior; no additional lunge plugin is required.

## 2. Idea Summary

### 2.1 Legendary items (one craft each, protected, unbreakable, not enchantable)

| Item | Base | Key Ability / Rules | Recipe (from notes) |
|---|---|---|---|
| Nebula Spear | Netherite spear | Damage + cooldown, unbreakable; every 5 lunges → thrust (riptide-like velocity), max 4 hearts vs prot 4 diamond; comes with Lunge III, no other enchants; lunge count persists through item swaps; 5s CD | Corners: player head; top: trident; middle: netherite spear; bottom: redstone block; sides: gold blocks |
| Star Mace | Mace | Wind Burst starts at I and rises by one on damaging mace smash hits to V; resets after 20s without use or any miss, but only a miss at V starts a 30s cooldown; Density II; damage cap 7 hearts | Corners: heads; top: nether star; sides: wither skulls; bottom: netherite; middle: heavy core |
| Collapsing Star Mace | Mace | Breach 3 + Wind Burst 2; damage cap 7 hearts; one spark per full heart of actual mace damage, plus one spark per consecutive hit after the first; each spark deals 1 health point of splash damage | Sides: heavy cores; corners: heads; middle: nether star; top: deepslate coal ore; bottom: breeze rod |
| Astral Sword | Netherite sword | Sharpness 6, Looting 3, no Fire Aspect; each hit multiplies sword speed by 1.35, up to four hits within 10 seconds from the first; resets for a 30-second effect cooldown without blocking attacks | Corners: heads; top+middle: netherite ingot; bottom: breeze rod; sides: diamond blocks |
| Void Sword | Netherite sword | Unbreakable, Sharpness V, Looting III; 4-hit combo → Slowness I + Wither I for 10s | Corners: heads; middle: netherite sword; edges: wither roses |
| Celestial Bow | Bow | Power V, Punch II, Infinity, no Flame; fully charged → splash damage + lingering potion/cloud of Wither or Poison | Left-middle end rod, bottom blaze rod, top breeze rod, left corners heads, right column Galaxy String x3, middle end crystal |
| Wormhole Trident | Trident | Every 5 Riptides creates a pair of reusable rifts; the exit is placed 20 blocks forward in the creator's look direction when opened, and both close after 50s; non-owners passing through either end take 2 hearts + Wither I for 20s | Top/bottom: trident; corners: heads; middle: netherite ingot; left/right: wither skeleton skulls |
| Cosmic Crown | Netherite helmet | Max-enchant stats except vanilla Thorns, unbreakable; retaliates every 2 hits taken; boosted absorption (gapple 4 hearts, notch apple 10 hearts) | Middle: netherite helmet; sides: heads; corners: notch apples; top: axolotl bucket; bottom: silence armor trim |
| Singularity Boots | Netherite boots | Protection IV and Depth Strider III; no fall damage; fall ≥block 50 → creeper-sized explosion on impact | Middle: netherite boots; corners: Corrupt Ender Pearls; edges: breeze rods; top/bottom: heads |
| Solstice Chestplate | Netherite chestplate | Protection IV; once per Minecraft day at tick 6000: Speed 3, Strength 2, Resistance 1 for 5 min | Middle: netherite chestplate; top/bottom: end crystal; left/right: nether stars; corners: heads |
| Lunar Leggings | Netherite leggings | Protection IV and Swift Sneak III; at tick 13500: Speed 1, Strength 1, Resistance 2 for 5 min; unbreakable | Middle: netherite leggings; edges: respawn anchors; corners: heads |
| Nova Shield | Shield | Two fully charged axe critical hits disable blocking for 10s; totem rescue only in offhand, 10-minute rescue cooldown | Middle: totem; top/bottom: netherite blocks; sides+top corners: same type of planks; bottom corners: heads |

### 2.2 Craftable / repeatable items

| Item | Rules | Recipe |
|---|---|---|
| Galaxy String | Plain crafting component, no ability | Top: blaze rod; middle: breeze rod; bottom: end rod |
| Kuiper Ball | Freeze and wind-charge-like burst; 1.5s cooldown, configurable radius/power and player/mob targeting; recipe yields 10 | Corners: snowballs; center and middle-left/middle-right: powdered snow; top-middle/bottom-middle: breeze charges |
| Mass Orb | Infinitely craftable, stack limit 1; Slowness IV; direct fall-slam deals 0.25 hearts per block, capped at 5 hearts; holder takes reduced fall damage; 2-minute cooldown after a slam | Middle: heavy core; sides: netherite blocks; top/bottom: blue ice; corners: gold blocks |
| Corrupt Ender Pearl | Custom Enderman drop at 5%; Singularity Boots ingredient; stacks to 8; 15% chance per landing to give the thrower Wither II for 10 seconds | Uses the supplied CrackEnderPearl model and texture; in-game verification remains |

Ordinary and Corrupt Ender Pearls have a maximum of 8 per stack, with no total per-player pearl limit. Both use a shared 1.5-second cooldown; in-game verification remains.

### 2.3 Global rules

1. Non-operators can craft each legendary item **once ever** server-wide; operator copies bypass the craft quota. Galaxy String, Kuiper Ball, Mass Orb and Corrupt Ender Pearls are repeatable. A server reset or administrative removal does not allow another non-operator craft; preserve the craft record across resets.
2. The vanilla mace recipe is disabled. Only the two custom mace recipes are allowed, and each custom mace type can be crafted once ever. Track each recipe's crafted state separately in this plugin; a server reset or administrative removal does not reset either flag.
3. Legendary items are unbreakable and protected from loss. On player death, the item remains at the death location unless destroyed by hazards such as lava, the void, or cactus; a hazard-destroyed item is returned to (0,0) as a reclaimable block. An item left to despawn is also returned there.
4. Curse of Vanishing (COV) is prohibited; items cannot be enchanted or disenchanted (anvil, grindstone, and enchanting table blocked).
5. Cannot be stored in ender chests or any container (chests, barrels, shulkers, hoppers, droppers, item frames, armor stands, bundles, etc.).
6. Crafting triggers a **3 min ritual**, except for Galaxy String, Kuiper Ball, and Mass Orb. The item does not exist in the world until the timer ends. An animation appears above the crafting table, then the item drops there. Every 3 min, chat reveals the crafter's coordinates and the crafted item's name.
7. Each non-operator may carry at most **two legendary items in total**, including both maces. For example, a chestplate and a weapon or both different maces fill the limit. Usables and repeatable ingredients, such as Kuiper Balls, Mass Orbs, Galaxy String and Corrupt Ender Pearls, do not count. Operators are exempt from the player and server-wide limits.
8. For non-operators, there may be **one Star Mace and one Collapsing Star Mace server-wide**, independently; both count toward the player's two-legendary limit. Operator copies are exempt. Once a non-operator's mace has been made, another non-operator cannot craft another of that type. The current separate craft records do not by themselves prove all acquisition paths enforce the server-wide item cap.
9. When a limit blocks crafting, show no crafting-table output and prevent recipe-book autofill from enabling the craft. If an operator drops a mace while that mace's non-operator slot is occupied, immediately return it to the operator and log the operator and coordinates. If that slot is free, a non-operator may pick up the dropped operator mace.

## 3. Technical Design

### 3.1 Item identity
- Each legendary = `ItemStack` with `PersistentDataContainer` key `galaxy:item_id` (+ unique `galaxy:uuid` for tracking), custom model data / `item_model` component, `unbreakable`, hidden enchants/attributes via data components, lore via Adventure.
- Central `LegendaryItem` interface + registry: `id`, `createItem()`, `recipe()`, `onEvent hooks`, `cooldown`, `maxCrafts`.
- Ability logic is implemented as listeners per item (one class per item) sharing helpers (`CooldownManager`, `HitCounter`, `DamageCap`). `HitCounter` stores expiring per-player hit sequences; `DamageCap` computes a bounded final-damage value. Event adapters must verify Paper's final damage after applying a cap, including armor and protection modifiers.

### 3.2 Recipes
- Register shaped recipes using exact choices for fixed custom ingredients such as Galaxy String. Player-head slots must match the Head Drop PDC marker, not a fixed display name or full `ItemStack` snapshot.
- Head Drop (`HeadDrop`) 3.1.0 or newer is a required server plugin (`plugin.yml` hard dependency and startup version check). Configure its `drop.placement: item` so recipe heads are usable as items; for block placement, require `head.persist_lore_on_placement: true`.
- Remove/disable the vanilla mace recipe and register only the Star Mace and Collapsing Star Mace recipes. Each type has its own independent, permanent server-wide one-craft limit; the owner also requires no more than one of each mace present server-wide. Verify all acquisition paths against that cap.
- Recipe heads come from [Head Drop](https://modrinth.com/plugin/head-drop). Its 3.1.0 source tags generated heads with plugin-owned persistent data: `headdrop:dropped` as a byte value of `1` (`DROPPED_KEY = "dropped"`). Validate that marker in crafting events rather than matching player-specific display names or lore. Configure `drop.placement: item` so deaths produce recipe-usable item heads; if heads are placed as blocks, Head Drop can preserve the marker through placement and return it when broken when `head.persist_lore_on_placement` is enabled.
- Craft-once enforcement in `PrepareItemCraftEvent` (deny result) and `CraftItemEvent` (atomically record in the data store; handle shift-click and automated crafting).
- On successful craft, consume ingredients and start the ritual; do not create the item until the 3-minute timer completes. Show the animation over the crafting table and drop the item there at completion.

### 3.3 Protection systems
| Rule | Events / approach |
|---|---|
| Unbreakable | `unbreakable` component; cancel `PlayerItemDamageEvent` |
| Hazard / despawn recovery | If a dropped legendary is destroyed by a hazard or is about to despawn, return it to (0,0) in reclaimable block form; implement protection/handling for lava, void, cactus, and other agreed hazards |
| Death | Let the item remain at the death location. If a hazard destroys it, return it to (0,0) in reclaimable block form |
| No containers | `InventoryClickEvent`, `InventoryDragEvent`, `InventoryMoveItemEvent`, `InventoryPickupItemEvent`, hotbar-swap, shift-click, bundles, `PlayerDropItem` into frames/stands; check `InventoryType` ≠ PLAYER/CRAFTING |
| Two legendary items total | Enforce for non-operators across crafting, grants, pickup, inventory movement, ritual delivery, reclaim and join/restart recovery; exclude operators, repeatable usables and ingredients |
| No (dis)enchant | `PrepareAnvilEvent`, `PrepareGrindstoneEvent`, `PrepareItemEnchantEvent`, `EnchantItemEvent`, Mending/XP |

### 3.4 Abilities (implementation notes)
- **Damage caps:** `EntityDamageByEntityEvent` – compute the bounded value from `getFinalDamage()` and verify the applied result after armor/protection modifiers; do not assume `setDamage()` directly sets final damage. Test against prot 4 diamond.
- **Combo counters:** per-attacker tracking for Void Sword, Star Mace progression and Cosmic Crown. Void Sword hits do not expire; its fourth hit applies effects to that hit's target. Star Mace progression resets on a missed swing or after 20 seconds without use.
- **Lunge/Riptide counting:** use the native spear Lunge enchantment already confirmed working on the existing server; implement Nebula Spear's fifth-successful-lunge trigger using the Paper API available in the target setup. No third-party lunge integration is expected. Count trident Riptides for Wormhole Trident.
- **Rift (Wormhole):** `Location` + radius, particle task, `BukkitScheduler`/Folia-safe scheduler; 50s lifetime; entry check by proximity.
- **Time-based buffs (Solstice/Lunar):** Solstice at tick 6000; Lunar at tick 13500, once per Minecraft day.
- **Nova Shield:** count fully charged axe crits, disable after two for 10 seconds, and trigger its totem effect only in offhand; rescue cooldown is 10 minutes.
- **Singularity Boots:** `EntityDamageEvent` FALL cancel, track fall distance, `world.createExplosion`.
- **Celestial Bow:** `EntityShootBowEvent` (force == 1) + `ProjectileHitEvent` → `AreaEffectCloud`.
- **Kuiper Ball / Mass Orb:** custom projectile (snowball PDC) and held-item passive effects with fall-damage tick logic.
- **Absorption boost (Crown):** `EntityPotionEffectEvent` / `PlayerItemConsumeEvent` – replace absorption amplifier/amount.
- **Reveal coords:** broadcast scheduled task; track which axis revealed; persist across restarts.

### 3.5 Persistence
- Store: `item_id`, `crafted` flag, current owner UUID, last known location, ritual start time, ritual reveals count, cooldowns.
- Reload-safe: restart resumes rituals, rebuilds spawn-block markers.

## 4. Roadmap

### Phase 0 – Project Setup
- [x] Gradle project, Paper API 26.3, `plugin.yml`, main class, config loader
- [x] Debug/test server setup and run script (use the existing server and run script)
- [x] Decide package layout (`com.coaxgames.smp` root; `item`, `ability`, `listener`, `ritual`, `storage`, `util`)

### Phase 1 – Core Framework
- [x] `LegendaryItem` registry + PDC identification (`galaxy:item_id` and per-item `galaxy:uuid`)
- [x] Programmatic item builder API (unbreakable, hidden flags, lore, model, Adventure-component names, configurable two-color gradient names); the in-game admin builder/editor is tracked in Phase 7
- [x] `CooldownManager`, config-driven values (`cooldowns.default-seconds` and per-item overrides; remaining cooldowns persist)
- [x] Persistent data store primitives for craft-once flags, ownership, and last known locations (crafting and player lifecycle integration follows in Phases 2–3)
- [x] Admin command surface includes `/galaxy version` for the running plugin version and `/galaxy give|reset|list|locate` (`reset` clears cooldowns only; craft-once history is permanent; item operations become populated as definitions are registered)
- [x] Shared `HitCounter` and `DamageCap` helpers for upcoming item abilities

### Phase 2 – Rules Engine
- [x] Provide central registration for keyed recipes and stamp recipe outputs with item identity; declare each item's exact recipe in its item phase (Phases 4–5), with repeatable recipes kept outside legendary craft-once enforcement
- [x] Expose per-definition craft-once and loss/storage-protection policies; reject shaped recipes that hard-code a player-head ItemStack instead of validating the Head Drop marker at craft time
- [x] Require Head Drop (`HeadDrop`) 3.1.0+ in `plugin.yml`; validate player-head recipe ingredients using `headdrop:dropped` as byte value `1`
- [x] Disable the vanilla mace recipe; enforce independent, permanent craft records by item ID, allowing one of each custom mace type when their definitions are added in Phase 5
- [x] Enforce craft-once limits in prepare/craft events and Paper's automated Crafter event; atomically persist the count, item UUID, owner when known, and location
- [x] Block legendary storage/transfers through containers and ender chests, hopper/dropper movement, bundles, shulker boxes, item frames, armor stands, inventory click/drag/hotbar-swap, and player/non-player pickups
- [x] Apply a shared two-legendary carrying limit for non-operators, counting pending rituals and excluding operators and repeatable usables/ingredients; live verification remains open.
- [x] Prevent item damage, enchanting, disenchanting, Curse of Vanishing, and relevant anvil/grindstone/enchanting-table/Mending/XP routes
- [x] Keep ordinary death drops at the death location; recover lava/fire/void/cactus/explosion/despawn losses into a protected, reclaimable barrel block at world X/Z (0,0)
- [x] Track owner and last-known location across craft, pickup, drop, death, logout, recovery, and administrative grant

### Phase 2.1 – Integration Tests
- [x] Add automated tests for Head Drop marker-byte validation/version gating, Paper/plugin metadata, per-item gradient config coverage, independent mace craft records, ownerless automation-item persistence, and Phase 1 persistence behavior; `gradlew test build` passes
- [x] **Owner local test:** On the owner's Paper 26.3 server, verify Galaxy SMP loads with Head Drop 3.1.0+ and refuses missing/older versions
- [x] **Owner local test:** Configure Head Drop `drop.placement: item`; if testing block placement instead, enable `head.persist_lore_on_placement`; verify generated item heads carry the `headdrop:dropped` byte marker and normal player skulls are rejected by matching recipes
- [ ] **Owner local test:** Verify Phase 4 shaped recipes' exact ingredients and output identity, including Head Drop marker acceptance and rejection of ordinary player heads; verify repeatable outputs and stack size
- [ ] **Owner local test:** Verify the vanilla mace recipe is absent and Star Mace/Collapsing Star Mace each have an independent one-ever server-wide craft record
- [ ] **Owner local test:** Verify normal crafting, shift-click, repeated attempts, Crafter automation, restart persistence, and administrative grants against craft-once records
- [ ] **Owner local test:** Exercise container protections: chest/barrel/shulker/ender chest, hopper/dropper, bundle, crafting grid, item frame, armor stand, shift-click, drag, number-key/offhand swap, and container open/break/explosion paths
- [ ] **Owner local test:** Verify the two-legendary total across crafting, pickup, inventory movement, join/restart recovery, reclaim and `/galaxy give`; verify both maces count, usables do not, and removal frees carrying capacity without resetting craft history.
- [ ] **Owner local test:** Verify durability, enchanting table, anvil, grindstone, Curse of Vanishing, Mending, and XP repair are blocked for identified items
- [ ] **Owner local test:** Verify death drops stay at the death location; lava, fire, cactus, void, explosions, and despawn create a protected reclaim barrel at world X/Z (0,0), and claiming it restores the same item UUID
- [ ] Re-run the relevant recipe, protection, lifecycle, and persistence tests whenever later phases add item definitions or change event handling

**Phase 2.1 status:** The owner confirmed the Paper 26.3/Head Drop version and Head Drop marker/config checks passed locally. The remaining owner-local gameplay checks listed above are explicitly waiting. Phase 4 recipe/output checks are available under Phase 4; Star Mace and Collapsing Star Mace recipe checks are available under Phase 5. No test server is requested or expected.

### Phase 3 – Ritual System
- [x] Start the configured 3-minute ritual after a successful craft-once legendary craft, consume ingredients only after the ritual record is saved, and keep the item nonexistent until completion; repeatable exceptions remain immediate
- [x] Show an updating boss bar to the crafter, animate above the crafting location, and drop the completed item there when the timer ends
- [x] Announce the configured-gradient item name and crafter coordinates globally every 3 minutes, with coordinates formatted as `(X, Y, Z)` and a configurable message template; the audience/message placeholder is now implemented as global broadcast with an editable template (see Open Question 1)
- [x] Persist ritual item identity, crafter and table locations, start/completion times, reveal count, craft quota reservation, owner, cooldowns, and reclaimable spawn-block markers
- [x] Resume active rituals after restart, persist reveal progress before broadcasting to avoid duplicate announcements, reconcile an already-dropped item before retrying completion, and rebuild reclaimable spawn-block markers on load
- [x] Add automated data-store coverage for ritual restart/reveal/completion, craft quota rollback after a failed ritual start, and reclaim-marker persistence; `gradlew test build` passes
- [ ] **Owner local test:** Verify a legendary craft consumes exactly the required ingredients/remainders and gives no item before the configured deadline; test manual crafting, shift-click, and Crafter automation
- [ ] **Owner local test:** Verify the crafter boss bar, crafting-location animation, global reveal name/coordinates/message, completion drop location, and that Phase 4 repeatable recipes remain immediate
- [ ] **Owner local test:** Restart during an active ritual and at/after its deadline; verify one output and no repeated reveal, confirm unloaded-world/chunk recovery, and exercise craft cancellation/failure without losing ingredients or craft allowance

**Phase 3 status:** Implementation and repository tests are complete. The three live Paper checks remain unchecked for owner verification; no live server test has been claimed.

### Phase 4 – Simple Items (first playable)
The checked entries below describe the earlier implementation, not acceptance of the newer corrections in Phase 5.
- [x] Galaxy String: shaped, repeatable recipe; plain crafting component, no ability or craft quota; stack-compatible item identity with no unique-instance UUID
- [x] Kuiper Ball: shaped recipe yields 10, custom stack maximum 64; marked thrown snowballs freeze eligible living targets in a configurable velocity burst; cooldown, radius, strength, vertical impulse, and player/mob/item/projectile targeting are host-configurable; repeatable with normal stack merging and no per-player carrying limit
- [x] Mass Orb: repeatable recipe, stack size 1, cannot be placed as a block, Slowness IV while held, and direct fall-slams on living targets at 0.25 hearts per block fallen, capped at 5 hearts before normal damage mitigation; the holder is exempt from slam damage but retains the existing configurable fall-damage reduction
- [x] Astral Sword: recipe, Sharpness VI/Looting III without Fire Aspect, no-enchant protection, and sword-only stacking attack speed with a 10-second window and 30-second effect cooldown
- [x] Void Sword: recipe, Unbreakable/Sharpness IV/Looting III, no-enchant protection, and Slowness I plus Wither I for 10 seconds after four hits
- [ ] **Owner local test:** Verify the Void Sword applies Slowness I and Wither I for 10 seconds to the target of every fourth hit, without a time limit between hits.
- [x] Solstice Chestplate: register Protection IV; grant Speed III, Strength II, Resistance I for five minutes when world time crosses tick 13,500; persist daily trigger
- [x] Lunar Leggings: register Protection IV and Unbreakable; grant Speed I, Strength I, Resistance II for five minutes at world tick 13,500; active remaining duration pauses over clean shutdown and resumes after restart
- [x] Add automated tests for daily-effect persistence and Mass Orb damage formula/cap placeholder; `gradlew test build` passes
- [ ] **Owner local test:** Verify all seven shaped recipes' exact ingredients and outputs; Head Drop heads work and ordinary player heads are rejected
- [ ] **Owner local test:** Verify the supplied animated Galaxy String texture on new and existing strings, Galaxy String cannot be placed while ordinary string can, Galaxy String and Kuiper Ball stacking, Kuiper Ball output of 10/stack size 64, multiple Kuiper Balls/Mass Orbs remain carried without being dropped or capped, and repeatable crafts do not start rituals or consume craft-once quota
- [ ] **Owner local test:** Verify Astral speed compounds 1.35x per hit up to four hits during 10 seconds from the first, resets for 30 seconds without blocking attacks, and never affects another weapon. Verify Void Sword effects and duration.
- [ ] **Owner local test:** Verify Kuiper Ball freezes all eligible living burst targets; new and existing Mass Orbs stack to 1 and cannot be placed while ordinary Heavy Cores can; Mass Orb lore shows enemy slam and holder fall-damage caps in hearts; direct slams at 10, 20 and 40+ blocks cap at 5 hearts, trigger the two-minute cooldown on use, and preserve target selection, Slowness IV, and the holder's reduced fall damage
- [ ] **Owner local test:** Wear Solstice Chestplate at tick 6000 and Lunar Leggings at tick 13500, check effect amplifiers/duration and once-per-day behavior plus the plugin grant log, restart during an active effect, and decide Lunar missed-trigger catch-up behavior
- [ ] Verify Phase 4 items on Paper 26.3, including protection/enchant rules, craft/ritual lifecycle, persistence and recovery; no texture pack is required for mechanics testing

**Phase 4 status:** Item definitions, recipes, abilities, configurable placeholders and repository tests are implemented. Live Paper gameplay checks remain unchecked for owner verification. The holder's Mass Orb fall-damage reduction and Lunar catch-up behavior remain open for balance/owner decisions.

### Phase 5 – Complex Abilities
- [x] Register separate Star Mace and Collapsing Star Mace recipes with their specified base enchantments and independent one-ever craft allowances
- [x] Add automated coverage for mace damage capping, Star Mace hit/miss progression and cooldown conditions, damage-based Collapsing Star sparks, and confirm the existing datastore test covers independent permanent craft records
- [ ] **Owner local test:** Verify each mace recipe, Head Drop head-marker requirement, output enchantments/unbreakable status, and separate permanent craft-once records; confirm the vanilla mace recipe is unavailable
- [ ] **Owner local test:** Star Mace progression works per owner confirmation; verify reset to I after 20 seconds without use or on a miss, only a miss at V starts cooldown, swinging during cooldown does not reset its indicator or duration, blocked attacks and native Wind Burst during cooldown, and the seven-heart damage ceiling against unarmored and Protection IV diamond targets.
- [ ] **Owner local test:** Verify Collapsing Star spark count from damage and consecutive hits, larger spark displays (including lethal hits), one-health-point splash damage per spark to nearby entities, streak resets, three-hit cooldown, blocked attacks and native Wind Burst during cooldown, and configured radius. Spark damage now bypasses and restores each victim's damage-immunity timer; verify in game.
- [x] Nebula Spear: register its recipe with native Lunge III only; count the target Paper API's successful `EntityLungeEvent`s and apply the fifth-lunge thrust and four-heart damage ceiling; default cooldown is 5 seconds with a separate hotbar indicator
- [x] Wormhole Trident: count successful Riptides and create two visible, reusable rift endpoints on every fifth; close both 50 seconds after opening, and apply two hearts plus Wither I for 20 seconds to non-owners on each passage
- [x] Celestial Bow: register its recipe with Power V, Punch II, and Infinity without Flame; tag fully charged shots, apply configurable splash damage, and create a configurable lingering cloud on impact (currently defaults to a 20-second lifetime; final balance is deferred to Phase 6)
- [x] Cosmic Crown: register its recipe with helmet enchantments except vanilla Thorns and unbreakable protection; retaliate every two damage hits and boost absorption from all sources, with golden apples set to four hearts and enchanted golden apples to ten hearts
- [x] Singularity Boots: register Protection IV and a recipe requiring Corrupt Ender Pearls in the four corners, cancel fall damage, and create a creeper-sized explosion after a fall of at least 50 blocks
- [x] Register the Nova Shield recipe and persisted totem-style rescue cooldown
- [x] Implement Nova Shield hit qualification, ten-second disable and offhand-only rescue; the same-type plank recipe is implemented locally. All still need in-game verification.
- [x] Add validated configuration placeholders for undecided balance values; retain unresolved owner decisions without blocking implementation or filling unspecified ingredients
- [x] Add repository tests for Phase 5 damage caps, Star Mace progression and Collapsing Star spark scaling, plus configuration defaults
- [ ] **Owner local test:** Verify all Phase 5 recipes, Head Drop head-marker requirements, Corrupt Ender Pearl ingredients, output identity/enchants (including Lunge III and Power V/Punch II/Infinity without Flame), and the independent one-ever craft records; confirm vanilla mace remains unavailable.
- [ ] **Owner local test:** Verify Mace damage caps/cooldowns/sparks; Nebula's fifth-successful-Lunge thrust after switching away and back, five-second cooldown and hotbar indicator; Wormhole's fifth-Riptide rift/passage/lifetime without anti-cheat setbacks; and Celestial Bow's charged-shot splash/cloud.
- [ ] **Owner local test:** Verify Cosmic Crown's every-two-hit retaliation and absorption increases, Singularity Boots fall immunity/50-block explosion, and Nova Shield's two-axe-hit disable/restore and lethal-damage rescue/cooldown.
- [ ] Verify all Phase 5 abilities against Protection IV diamond and test configured cooldowns, effect durations, persistence and exploit resistance.

#### Owner-feedback checklist
**Key:** ✅ implemented locally, needs in-game testing · ⬜ still open

##### Rift
1. ✅ Matching entrance and exit.
2. ✅ Reusable travel in both directions.
3. ✅ Wither I and two hearts of damage for non-owners.

##### Cooldowns and UI
1. ✅ The same vanilla Ender Pearl-style cooldown sweep is implemented for Kuiper Ball, Mass Orb, both maces and Nova Shield using separate custom-item cooldown groups; in-game verification remains.
2. ✅ Kuiper Ball: **1.5-second cooldown on throw attempt**; in-game verification remains.
3. ✅ Mass Orb: **two-minute cooldown starts when a slam damages a target**; in-game verification remains.
4. ✅ Both maces and the Nova Shield use separate custom-item cooldown groups rather than material-wide markers. Verify the indicator and ordinary-item isolation in game.

##### Nova Shield
1. ✅ Require two blocked axe critical hits.
2. ✅ Require full axe recharge for each counted hit.
3. ✅ Disable Nova Shield blocking for 10 seconds after the second qualifying hit; its cooldown group does not include ordinary shields. In-game verification remains.
4. ✅ Totem rescue only when the Nova Shield is in the offhand.
5. ✅ Recipe accepts matching planks of any wood type; rejects mixed types and logs. In-game verification remains.

##### Items and abilities
1. ✅ Swift Sneak III on new Lunar Leggings and upgrade of existing identified leggings. In-game verification remains.
2. ✅ Kuiper Ball settings are host-configurable: freeze duration, cooldown, radius, strength, vertical boost/minimum, and booleans for players, mobs, dropped items, and projectiles. Players/mobs default on; dropped items/projectiles default off. In-game verification remains.
3. ✅ Void Sword Sharpness V, including an upgrade for older swords.
4. ✅ Remove Fire Aspect and Flame from legendary weapons; existing identified items are cleaned on player-inventory refresh or pickup. In-game verification remains.
5. ✅ Solstice Chestplate at tick 6000.
6. ✅ Lunar Leggings remain at tick 13500.
7. ⬜ Collapsing Star Mace growing sparks and splash damage: local implementation and automated coverage exist; verify the complete mechanic in game before closing.
8. ✅ Mass Orb fall-scaled slam damage.
9. ⬜ Star Mace progression confirmed working by owner; verify 20-second inactivity and misses reset to I, only a miss at V starts cooldown, and attacks are blocked during cooldown.
10. ✅ Depth Strider III on newly created Singularity Boots; in-game verification remains.

##### Items, drops and limits
1. ✅ Corrupt Ender Pearl: 5% Enderman drop, boots ingredient, maximum stack size of 8, and a 15% chance per landing to give the thrower Wither II for 10 seconds. Uses the supplied CrackEnderPearl model and texture; in-game verification remains.
2. ✅ Fix repeatable-item untracked-drop error spam.
3. ✅ Retest nearby compatible repeatable custom items and ordinary Ender Pearls clumping into one ground entity containing up to 128 items. A periodic scan now handles full stacks and items that meet after their initial spawn; pickup must respect inventory stack limits.
4. ✅ Verify legendary limit behavior after OP inventory removal, including freed player capacity and permanent mace craft records.
5. ✅ Verify Celestial Bow requires Galaxy String; the code already checks the slots.
6. ✅ Verify ordinary and Corrupt Ender Pearl stacks on Paper: loot, drops, inventory changes, creative and other acquisition paths must cap at 8 without a per-player total limit. Verify both share the 1.5-second use cooldown.
7. ✅ Two legendary items total per non-operator, counting both maces and pending rituals but excluding repeatable usables and ingredients; operators are exempt. In-game verification remains.
8. ✅ Independent persistent Star and Collapsing Star mace slots for non-operators; operator copies are exempt. Crafting, grants, pickup and reclaim use the tracker. In-game and acquisition-path verification remains.
9. ✅ Hide crafting output and block recipe-book autofill when a limit is reached; return an operator's dropped mace if its non-operator slot is occupied and log operator and coordinates. A non-operator can claim an operator's dropped mace when that slot is free. In-game verification remains.

##### Updates
1. ⬜ Verify matching release/plugin/pack versions and restart-free pack-only updates after installing the content-comparing updater; a running plugin's reported version changes only on restart.

These statuses describe local implementation only; none of the ✅ items are claimed as verified in game. Changes remain local and unpushed.

**Phase 5 status:** The owner-feedback checklist above is authoritative for corrected mechanics and current status. Earlier local code and repository tests do not change an item marked ⬜ to complete; no in-game verification is claimed.

**Nova Shield testing:** Verify full versus partial axe recharge, critical versus ordinary attacks, missed/unblocked hits, both consecutive qualifying hits, the complete 10-second disable and restoration, vanilla shield-disable interactions, and lethal damage with the shield in the offhand, main hand and inventory. Verify the vanilla totem pop overlay when the offhand shield rescues the player. The old `phase-five.nova-shield.disable-seconds` setting is ignored; the requested disable duration is fixed at 10 seconds.

**Resource-pack status:** The build generates the resource pack and its SHA-1 sidecar for release. Galaxy sends its own pack on join and updates, without reacting to other packs. Another plugin can remove Galaxy's pack; player acceptance still requires owner testing on the server.

### Phase 6 – Item Lifecycle, Limits & Update Corrections

- [ ] **Celestial Bow cloud balance:** Finalize the cloud effect, lifetime and effect duration/strength. Current configurable placeholders default to Wither, a 20-second cloud lifetime, and a 5-second effect at amplifier 0.
- [ ] **Ground clumping retest:** The owner reported Kuiper Balls still stacking like vanilla. Nearby eligible items are now rechecked every second, including full stacks and already-loaded items, rather than only around spawn or on vanilla merge events. Verify 128 total in one ground entity and partial pickup on Paper.
- [ ] **Repeatable drop errors:** Investigate and fix unknown-ID/untracked-drop spam for usable repeatable items such as Kuiper Balls; do not require unique UUID tracking for items that do not need it. Preserve legitimate diagnostics for genuinely untracked protected items.
- [x] **Dropped-item buildup:** Add Clumps-inspired ground-item merging for compatible repeatable custom items and ordinary Ender Pearls, with up to 128 items represented by one entity. Partial pickup leaves the remainder on the ground, and inventory stack limits remain unchanged. Preserve protected legendary items separately.
- [ ] **Owner local test:** Verify ground clumping and partial player/hopper pickup with full and partially full inventories, pearl stacks of 8, mixed item metadata, item ownership, despawn and chunk reload.
- [ ] **OP removal and limits:** Verify the implemented two-legendary total, separate non-operator mace slots and operator exemptions in game, including bench output and recipe-book autofill, grants, removal, pickup, inventory edits, operator drops and restart. Removing an item frees carrying capacity but does not erase permanent craft-once records.
- The owner confirmed the test server has no old data. No legacy migration or reset is necessary.
- [ ] **Resource-pack-only updates:** Verify a pack-only update does not enforce a restart unless the plugin itself changes, or implement the requested warning alternative. The pack service resends packs independently; the separate plugin updater's version-triggered restart needs investigation.
- [ ] **Update warnings:** Verify actual plugin-update restart warnings, including 2-minute and 30-second notices, and distinguish pack refreshes from plugin restarts.
- [ ] Add regression tests and owner-local checks for drop handling, entity buildup, item limits and pack-only versus plugin updates.

### Phase 7 – Polish & Balance
- [ ] Add an in-game item/recipe builder and editor for registered items, restricted to server operators/admins only; support editing recipe ingredients/shapes, item display name and gradient, lore, stats, and configurable ability values, with validated persistent saves and a safe way to review/apply changes without granting non-operators access
- [ ] Add distinct particles, sounds, and player-facing lore for every legendary and repeatable item; use each item's configured gradient display name in death and interaction messages
- [ ] Show a clean, readable hover tooltip for every registered item that presents all relevant item stats and abilities (including enchantments, damage/effect values, cooldowns, triggers, and special rules); keep the tooltip accurate to the item's configured behavior
- [ ] Add `/galaxy recipes` covering every registered recipe; verify readable item models/textures without requiring a resource pack (`item_model` remains optional)
- [x] Generate a separate versioned Minecraft 26.3 resource pack from the embedded Blockbench models/textures, assign item models to Astral Sword, Void Sword, and Collapsing Star Mace, and attach the ZIP and SHA-1 sidecar to releases
- [ ] Move balance-sensitive values into validated config, including cooldowns, damage/effect caps, combo windows, durations, ritual settings, and projectile/rift behavior
- [ ] Run balance tests against Protection IV diamond and verify all exact damage caps, armor interactions, and effect durations
- [ ] Complete a dupe/transfer exploit pass for crafter, shulker, bundle, portals, dispensers, containers, item frames/armor stands, death, reconnect, and concurrent crafting/recovery
- [ ] Test clean install, upgrade, data reload, plugin/server restart, and preservation of permanent craft-once records
- [ ] Document setup/dependencies (including Head Drop configuration), item recipes, admin commands, open design decisions, and recovery behavior
- [ ] Build the plugin jar and publish a release with generated notes and the versioned artifact

## 5. Open Questions / Conflicts to Resolve

1. **Ritual reveal — [REQUIRED: Phase 3].** The item name must use that item's own configured gradient in the global reveal, and coordinates must be formatted in parentheses as `(X, Y, Z)`. Audience and the remaining exact message format are undecided; use placeholders for those details. Reuse the same configured item-name component for death and interaction messages (Phase 7).
2. **Lunar Leggings catch-up — [REQUIRED: Phase 4].** The trigger is confirmed as world time tick 13,500 in the 24,000-tick day, with a five-minute effect. Persist and resume an effect that was active at restart. Still decide whether a day whose tick 13,500 passed while the plugin/server was offline should grant the missed effect on startup, or wait for the next day's trigger. “Catch-up” here means only this missed-trigger decision, not resuming an already-active effect.
3. **Mass Orb fall-damage formula — [OWNER VERIFICATION IN PHASE 4].** Direct fall-slams deal 0.25 hearts per block fallen to living targets, capped at 5 hearts before normal damage mitigation. The holder takes no slam damage but still takes reduced fall damage; the existing configurable Feather Falling XV reduction remains provisional.
4. **Corrupt Ender Pearl — [IMPLEMENTED LOCALLY; OWNER VERIFICATION IN PHASE 5].** Endermen have a 5% chance to drop the custom Corrupt Ender Pearl, required in all four Singularity Boots recipe corners. Each landing has a 15% chance to give the thrower Wither II for 10 seconds. The supplied CrackEnderPearl model and texture are bound to the custom item; ordinary pearls remain vanilla.
5. **Celestial Bow cloud balance — [REQUIRED: Phase 6].** The current configurable Wither/Poison effect, cloud lifetime (20-second default), and effect duration/strength are placeholders pending final balance decisions; retain the settings until then.
6. **Collapsing Star Mace sparks — [OWNER VERIFICATION IN PHASE 5].** Local code and automated tests cover damage-based and consecutive-hit sparks with one health point of splash damage per spark. The correction stays open until the complete behavior is verified in game.
7. **Wormhole Trident rift — [IMPLEMENTED; OWNER VERIFICATION IN PHASE 5].** The trigger, paired reusable endpoints, opponent damage/effect on each passage, and 50-second lifetime beginning on opening are implemented. The exit is placed using the creator's facing direction at opening, up to the configured 20 blocks; obstructed or overlapping exits are rejected. Interaction radius is a configurable placeholder until owner review.
8. **Nebula Spear lunge — [OWNER VERIFICATION IN PHASE 5].** The spear uses native Lunge III and thrusts after five successful lunges, with a default five-second cooldown and hotbar indicator. The counter is player-based and should survive switching away and back; the owner reported this fails in game, so retest that exact sequence.
9. **Cosmic Crown thorns — [OWNER VERIFICATION IN PHASE 5].** The Crown retaliates against the attacker on every second damaging hit. Vanilla Thorns is removed from the Crown so it cannot trigger independently; verify in game.
10. **Mace limits — [IMPLEMENTED LOCALLY; OWNER VERIFICATION REQUIRED].** There are two custom mace types, with a server-wide maximum of one Star Mace and one Collapsing Star Mace for non-operators. Each has an independent permanent craft record, and both count toward the non-operator player's two-legendary total. Operators are exempt. This is not an open question.
11. **Astral Sword attack-speed balance — [REQUIRED: Phase 7].** Verify the requested 1.35x compounded speed per hit, up to four hits, and review balance in game.
12. **Star Mace progression — [OWNER VERIFICATION IN PHASE 5].** Only damaging vanilla mace smash hits advance Wind Burst; ordinary hits do not. Wind Burst resets to I after 20 seconds without use or on any miss; only a miss at V starts the 30-second cooldown. A successful hit at V does not start the cooldown. Verify the corrected behavior in game.
13. **Cooldown UI — [IMPLEMENTED LOCALLY; OWNER VERIFICATION IN PHASE 5].** All five custom items use the same vanilla Ender Pearl-style sweep with separate cooldown groups. Ordinary snowballs, heavy cores, maces and shields do not share these groups. Persisted Kuiper, Mass Orb and mace cooldowns restore their markers on login; verify in game.
14. **Swift Sneak — [IMPLEMENTED LOCALLY; OWNER VERIFICATION IN PHASE 5].** Lunar Leggings receive Swift Sneak III; existing identified leggings are upgraded on inventory refresh or pickup. Kuiper Ball settings remain under `phase-four.kuiper-ball`.
15. **Dropped repeatables and limits — [OWNER VERIFICATION IN PHASE 6].** The owner chose ground clumps of up to 128 items, unpacked according to inventory stack limits. The owner has confirmed the two-legendary player limit and separate one-of-each-mace server limits; removal must not silently overturn permanent craft-once records.
16. **Operator cooldown toggle — [IMPLEMENTED LOCALLY; OWNER VERIFICATION REQUIRED].** `/cooldown` toggles the operator's own cooldowns and `/cooldown status` reports the setting. Cooldowns default to enabled and the preference persists across restarts; non-operators always have cooldowns enabled. Previously active persisted cooldowns resume if still running when re-enabled. Verify item abilities, indicators, persistence, and non-operator enforcement in game.
17. **Solstice Chestplate textures — [IMPLEMENTED LOCALLY; OWNER VERIFICATION REQUIRED].** The supplied animated item texture and static worn armor texture are bound to the chestplate. Existing items refresh on inventory refresh or pickup; verify both appearances in game.


## 6. Changelog

- 2026-11-11: Bound the supplied CrackEnderPearl model and texture to the Corrupt Ender Pearl; ordinary pearls are unchanged. In-game verification remains.
- 2026-11-11: Changed ground clumping to recheck nearby eligible items every second, including full stacks and items already loaded. Added merge and partial-pickup regression tests; owner-reported Kuiper Ball clumping still needs an in-game retest.
- 2026-11-11: Set the Celestial Bow cloud lifetime default to 20 seconds and moved final cloud effect/lifetime/strength balance from Phase 5 to Phase 6. Values remain configurable placeholders pending final decisions.
- 2026-11-11: Added Ender Pearl-style cooldown sweeps for Kuiper Ball and Mass Orb and moved both maces and Nova Shield to separate custom-item cooldown groups. Restore persisted markers on login; in-game indicator and isolation checks remain.
- 2026-11-11: Corrected Star Mace so its 30-second cooldown starts only on a miss, including at Wind Burst V; a successful hit at V no longer starts a cooldown. In-game verification remains.
- 2026-11-11: Removed Fire Aspect from Astral Sword and Flame from Celestial Bow. Existing identified protected items have these enchants and their generated lore removed on player-inventory refresh or pickup; live verification remains.
- 2026-11-11: Nova Shield now counts only blocked, fully charged player axe critical hits, prevents vanilla premature disable, disables blocking for 10 seconds after two qualifying hits, and rescues only from the offhand. Live combat verification remains.
- 2026-11-11: Added Clumps-inspired ground clumping for compatible repeatable items and Ender Pearls, storing up to 128 items per ground entity with partial pickup and unchanged inventory stack limits. Automated merge arithmetic is tested; live Paper validation remains.
- 2026-11-11: Added Depth Strider III to new Singularity Boots. Applied the eight-per-stack pearl metadata to loot, spawned drops and player inventory changes, with creative checks; no player-wide pearl cap. Server-side gameplay and other acquisition paths need verification. Removed the unnecessary Corrupt Pearl legacy-migration task.
- 2026-11-11: Implemented shared non-operator legendary limits, independent non-operator mace tracking, operator exemptions, blocked recipe-book clicks and output, and logged operator mace-drop cancellation. Owner confirmed the test server has no old data; no migration is needed. Automated tests/build pass; live verification is pending.
- 2026-11-11: Clarified that operators bypass both limits; documented blocked bench output/recipe-book autofill and the requested logged return of operator-dropped maces when the matching non-operator slot is occupied. The later entry above records their implementation.
- 2026-11-11: Confirmed one of each mace server-wide and two legendary items total per player, with both maces counting and usables excluded. Removed the repeated mace question and marked enforcement beyond existing per-item and craft-once checks as outstanding.
- 2026-11-11: Set newly created Corrupt Ender Pearls to a maximum stack size of 8. Corrected the cooldown UI checklist to distinguish enforced cooldowns from visible markers and recorded the mace implementations as awaiting in-game verification.
- 2026-11-11: Added Swift Sneak III to Lunar Leggings, the 5% Corrupt Ender Pearl drop and boots recipe, and Nova Shield recipes requiring matching planks; custom pearl model and in-game verification remain pending.
- 2026-11-11: Implemented the Mass Orb 20-second cooldown, starting on an attempted fall-slam, and the Kuiper 1.5-second throw-attempt cooldown with configurable entity targeting. Documented that cooldown indicators use the vanilla Ender Pearl sweep.
- 2026-11-11: Exposed all Kuiper Ball effect parameters in `config.yml`, including freeze duration, cooldown, burst radius/strength, vertical impulse, and target-category booleans; documented host configuration and validated numeric values.
- 2026-11-11: Replaced the Phase 5–6 owner-feedback checklist with the owner's latest status key. ✅ entries are local-only and still need in-game testing; ⬜ entries remain open. No changes were pushed.
- 2026-11-10: Created roadmap v2 from the existing roadmap; added the complete owner-feedback correction checklists to Phases 5 and 6, moved polish and balance to Phase 7, and retained owner-local tests and unresolved decisions.
- 2026-10-22: Initial roadmap drafted from IdeasOnPaper.md; updated from answers in OpenQuestions.md and reviewed for consistency.
- 2026-10-22: Expanded Phases 1–6 to trace the implementation against Sections 2–3.5; recorded remaining design decisions instead of guessing.
- 2026-10-25: Applied owner decisions to Open Questions 1–11 and affected Phase 3–5 requirements; retained unresolved questions with required-phase tags and explicit placeholders.
- 2026-10-25: Clarified Nebula Spear uses the native Lunge behavior on the existing Galaxy SMP + Head Drop server; no additional plugin integration is required. Phase 5 still needs to implement and verify successful-lunge counting against the target Paper API.
- 2026-10-25: Recorded each custom mace type's independent permanent server-wide one-craft allowance. The later owner confirmation above resolves the former “first mace” question.
- 2026-10-25: Implemented the Phase 2 rules engine, made Head Drop 3.1.0+ a required dependency, and added Phase 2.1 automated/server integration test checklists.



added line for Extra push
