# Galaxy SMP Plugin – Roadmap

Living document. Source of ideas: [IdeasOnPaper.md](./IdeasOnPaper.md). Update the status columns and "Open Questions" as we build.

## Mandatory Progress Tracking

- Whenever a roadmap checklist item is completed, change its checkbox to `[x]` in the same work session, after verifying the implementation. Never leave completed work unchecked or mark incomplete work as done.
- Update [Changed.md](./Changed.md) in the same work session with a human-readable account of completed work, tests, workflow/configuration changes, decisions, and any setup or groundwork that affects later phases.
- Keep these records current automatically; do not wait for the user to remind you.

## 1. Target Platform

- **Server:** Paper 26.3. Plugin declares `api-version: '26.3'`.
- **Java:** 25+ (Paper 26.3 runtime requirement). **Build:** Gradle + `paper-api` (`compileOnly`); use `paperweight-userdev` only if NMS is needed (avoid).
- **Config/data:** `config.yml` (tunables, including per-item player carrying caps), `data.yml`/SQLite (item ownership, ritual state, cooldowns, crafted flags).
- **Packaging:** single shaded-free jar, `paper-plugin.yml` or `plugin.yml`.

## 2. Idea Summary

### 2.1 Legendary items (one craft each, protected, unbreakable, not enchantable)

| Item | Base | Key Ability / Rules | Recipe (from notes) |
|---|---|---|---|
| Nebula Spear | Netherite spear | Damage + cooldown, unbreakable; every 5 lunges → thrust (riptide-like velocity), max 4 hearts vs prot 4 diamond; comes with Lunge, no other enchants; attribute swapping ineffective; 45s CD | Corners: player head; top: trident; middle: netherite spear; bottom: redstone block; sides: gold blocks |
| Star Mace | Mace | 1st mace on server; Wind Burst 3, Density 2; after 2 hits 30s CD; damage cap 7 hearts; no enchant/disenchant | Corners: heads; top: nether star; sides: wither skulls; bottom: netherite; middle: heavy core |
| Collapsing Star Mace | Mace | Mace perks + sparks if hit > 5 hearts; each spark = tiny explosion (cap 1 heart); dmg cap 7 hearts; Breach 3 + Wind Burst 2 | Sides: heavy cores; corners: heads; middle: nether star; top: deepslate coal ore; bottom: breeze rod |
| Astral Sword | Netherite sword | Sharpness 6, Looting 3, Fire Aspect; no enchanting; 4-hit combo →Faster attack speed for 30 seconds | Corners: heads; top+middle: netherite ingot; bottom: breeze rod; sides: diamond blocks |
| Void Sword | Netherite sword | Unbreakable, Sharp 4, Looting 3; 4-hit combo → Slowness 1 + Wither 1 for 10s | Corners: heads; middle: netherite sword; edges: wither roses |
| Celestial Bow | Bow | Fully charged → splash damage + lingering potion/cloud of Wither or Poison | Left-middle end rod, bottom blaze rod, top breeze rod, left corners heads, right column Galaxy String x3, middle end crystal |
| Wormhole Trident | Trident | Every 5 Riptides creates a rift; when its creator passes through, it launches them 20 blocks forward in their look direction; rift remains for 50s after the creator passes through; opponents passing through take 2 hearts + Wither I for 20s | Top/bottom: trident; corners: heads; middle: netherite ingot; left/right: wither skeleton skulls |
| Cosmic Crown | Netherite helmet | Max-enchant stats, unbreakable; thorns every 2 hits taken; boosted absorption (gapple 4 hearts, notch apple 10 hearts) | Middle: netherite helmet; sides: heads; corners: notch apples; top: axolotl bucket; bottom: silence armor trim |
| Singularity Boots | Netherite boots | No fall damage; fall ≥block 50 → creeper-sized explosion on impact | Middle: netherite boots; corners: TBD item; edges: breeze rods; top/bottom: heads |
| Solstice Chestplate | Netherite chestplate | Once per Minecraft day at 13,500 ticks (right before midnight): Speed 3, Strength 2, Resistance 1 for 5 min | Middle: netherite chestplate; top/bottom: end crystal; left/right: nether stars; corners: heads |
| Lunar Leggings | Netherite leggings | At midnight: Speed 1, Strength 1, Resistance 2 for 5 min; unbreakable | Middle: netherite leggings; edges: respawn anchors; corners: heads |
| Nova Shield | Shield | After 2 axe hits, becomes unusable for the shield-disable timer; automatically usable again when the timer expires. On death grants totem effect, 10 min cooldown | Middle: totem; top/bottom: netherite blocks; sides+top corners: pale oak; bottom corners: heads |

### 2.2 Craftable / repeatable items

| Item | Rules | Recipe |
|---|---|---|
| Galaxy String | Plain crafting component, no ability | Top: blaze rod; middle: breeze rod; bottom: end rod |
| Kuiper Ball | Freezing 30s + wind-charge-like burst (thrown snowball); recipe yields 10, stacks to 64 | Corners: snowballs; center and middle-left/middle-right: powdered snow; top-middle/bottom-middle: breeze charges |
| Mass Orb | Infinitely craftable; Slowness 4; anvil-like fall damage scaling with height; Feather Falling 15 effect | Middle: heavy core; sides: netherite blocks; top/bottom: blue ice; corners: gold blocks |

### 2.3 Global rules

1. Legendary items can be crafted **once ever** server-wide (exceptions: Galaxy String, Kuiper Ball, Mass Orb). A server reset or administrative removal does not allow another craft; preserve the craft record across resets.
2. The vanilla mace recipe is disabled. Only the two custom mace recipes are allowed, and each custom mace type can be crafted once ever. Track each recipe's crafted state separately in this plugin; a server reset or administrative removal does not reset either flag.
3. Legendary items are unbreakable and protected from loss. On player death, the item remains at the death location unless destroyed by hazards such as lava, the void, or cactus; a hazard-destroyed item is returned to (0,0) as a reclaimable block. An item left to despawn is also returned there.
4. Curse of Vanishing (COV) is prohibited; items cannot be enchanted or disenchanted (anvil, grindstone, and enchanting table blocked).
5. Cannot be stored in ender chests or any container (chests, barrels, shulkers, hoppers, droppers, item frames, armor stands, bundles, etc.).
6. Crafting triggers a **15 min ritual**, except for Galaxy String, Kuiper Ball, and Mass Orb. The item does not exist in the world until the timer ends. An animation appears above the crafting table, then the item drops there. Every 3 min, chat reveals the crafter's coordinates and the crafted item's name.

## 3. Technical Design

### 3.1 Item identity
- Each legendary = `ItemStack` with `PersistentDataContainer` key `galaxy:item_id` (+ unique `galaxy:uuid` for tracking), custom model data / `item_model` component, `unbreakable`, hidden enchants/attributes via data components, lore via Adventure.
- Central `LegendaryItem` interface + registry: `id`, `createItem()`, `recipe()`, `onEvent hooks`, `cooldown`, `maxCrafts`.
- Ability logic is implemented as listeners per item (one class per item) sharing helpers (`CooldownManager`, `HitCounter`, `DamageCap`). `HitCounter` stores expiring per-player hit sequences; `DamageCap` computes a bounded final-damage value. Event adapters must verify Paper's final damage after applying a cap, including armor and protection modifiers.

### 3.2 Recipes
- Register shaped recipes using exact choices for fixed custom ingredients such as Galaxy String. Player-head slots must match the Head Drop PDC marker, not a fixed display name or full `ItemStack` snapshot.
- Head Drop (`HeadDrop`) 3.1.0 or newer is a required server plugin (`plugin.yml` hard dependency and startup version check). Configure its `drop.placement: item` so recipe heads are usable as items; for block placement, require `head.persist_lore_on_placement: true`.
- Remove/disable the vanilla mace recipe and register only the Star Mace and Collapsing Star Mace recipes. Each custom mace type has its own independent, permanent server-wide one-craft limit: one Star Mace and one Collapsing Star Mace may each be crafted. “First mace on the server” remains an open question (see Open Question 11).
- Recipe heads come from [Head Drop](https://modrinth.com/plugin/head-drop). Its 3.1.0 source tags generated heads with plugin-owned persistent data: `headdrop:dropped` as a byte value of `1` (`DROPPED_KEY = "dropped"`). Validate that marker in crafting events rather than matching player-specific display names or lore. Configure `drop.placement: item` so deaths produce recipe-usable item heads; if heads are placed as blocks, Head Drop can preserve the marker through placement and return it when broken when `head.persist_lore_on_placement` is enabled.
- Craft-once enforcement in `PrepareItemCraftEvent` (deny result) and `CraftItemEvent` (atomically record in the data store; handle shift-click and automated crafting).
- On successful craft, consume ingredients and start the ritual; do not create the item until the 15-minute timer completes. Show the animation over the crafting table and drop the item there at completion.

### 3.3 Protection systems
| Rule | Events / approach |
|---|---|
| Unbreakable | `unbreakable` component; cancel `PlayerItemDamageEvent` |
| Hazard / despawn recovery | If a dropped legendary is destroyed by a hazard or is about to despawn, return it to (0,0) in reclaimable block form; implement protection/handling for lava, void, cactus, and other agreed hazards |
| Death | Let the item remain at the death location. If a hazard destroys it, return it to (0,0) in reclaimable block form |
| No containers | `InventoryClickEvent`, `InventoryDragEvent`, `InventoryMoveItemEvent`, `InventoryPickupItemEvent`, hotbar-swap, shift-click, bundles, `PlayerDropItem` into frames/stands; check `InventoryType` ≠ PLAYER/CRAFTING |
| One at a time | `EntityPickupItemEvent`, `InventoryClickEvent`, `PlayerJoinEvent` check |
| No (dis)enchant | `PrepareAnvilEvent`, `PrepareGrindstoneEvent`, `PrepareItemEnchantEvent`, `EnchantItemEvent`, Mending/XP |

### 3.4 Abilities (implementation notes)
- **Damage caps:** `EntityDamageByEntityEvent` – compute the bounded value from `getFinalDamage()` and verify the applied result after armor/protection modifiers; do not assume `setDamage()` directly sets final damage. Test against prot 4 diamond.
- **Combo counters:** per-attacker map with timeout (Void Sword 4 hits, Star Mace 2 hits, Cosmic Crown 2 hits taken).
- **Lunge/Riptide counting:** implement the spear/lunge behavior already confirmed working on the existing server; verify the implementation hook against that server setup. Count trident Riptides for Wormhole Trident.
- **Rift (Wormhole):** `Location` + radius, particle task, `BukkitScheduler`/Folia-safe scheduler; 50s lifetime; entry check by proximity.
- **Time-based buffs (Solstice/Lunar):** grant the Solstice Chestplate effects once per Minecraft day when `world.getTime()` reaches 13,500 ticks; Lunar Leggings timing remains to be specified.
- **Nova Shield:** track axe hits and disable the shield after 2; re-enable it when the shield-disable timer expires. Handle its totem effect on death with a 10 min cooldown.
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
- [x] Item builder (unbreakable, hidden flags, lore, model, Adventure-component names, configurable two-color gradient names)
- [x] `CooldownManager`, config-driven values (`cooldowns.default-seconds` and per-item overrides; remaining cooldowns persist)
- [x] Persistent data store primitives for craft-once flags, ownership, and last known locations (crafting and player lifecycle integration follows in Phases 2–3)
- [x] Admin command surface: `/galaxy give|reset|list|locate` (`reset` clears cooldowns only; craft-once history is permanent; item operations become populated as definitions are registered)
- [x] Shared `HitCounter` and `DamageCap` helpers for upcoming item abilities

### Phase 2 – Rules Engine
- [x] Provide central registration for keyed recipes and stamp recipe outputs with item identity; declare each item's exact recipe in its item phase (Phases 4–5), with repeatable recipes kept outside legendary craft-once enforcement
- [x] Expose per-definition craft-once and loss/storage-protection policies; reject shaped recipes that hard-code a player-head ItemStack instead of validating the Head Drop marker at craft time
- [x] Require Head Drop (`HeadDrop`) 3.1.0+ in `plugin.yml`; validate player-head recipe ingredients using `headdrop:dropped` as byte value `1`
- [x] Disable the vanilla mace recipe; enforce independent, permanent craft records by item ID, allowing one of each custom mace type when their definitions are added in Phase 5
- [x] Enforce craft-once limits in prepare/craft events and Paper's automated Crafter event; atomically persist the count, item UUID, owner when known, and location
- [x] Block legendary storage/transfers through containers and ender chests, hopper/dropper movement, bundles, shulker boxes, item frames, armor stands, inventory click/drag/hotbar-swap, and player/non-player pickups
- [x] Configure the per-player carrying cap independently for each item ID; default to one per item, with zero meaning unlimited
- [x] Prevent item damage, enchanting, disenchanting, Curse of Vanishing, and relevant anvil/grindstone/enchanting-table/Mending/XP routes
- [x] Keep ordinary death drops at the death location; recover lava/fire/void/cactus/explosion/despawn losses into a protected, reclaimable barrel block at world X/Z (0,0)
- [x] Track owner and last-known location across craft, pickup, drop, death, logout, recovery, and administrative grant

### Phase 2.1 – Integration Tests
- [x] Add automated tests for Head Drop marker-byte validation/version gating, Paper/plugin metadata, per-item gradient config coverage, independent mace craft records, ownerless automation-item persistence, and Phase 1 persistence behavior; `gradlew test build` passes
- [ ] **Owner local test:** On the owner's Paper 26.3 server, verify Galaxy SMP loads with Head Drop 3.1.0+ and refuses missing/older versions
- [ ] **Owner local test:** Configure Head Drop `drop.placement: item`; if testing block placement instead, enable `head.persist_lore_on_placement`; verify generated item heads carry the `headdrop:dropped` byte marker and normal player skulls are rejected by matching recipes
- [ ] **Deferred until Phases 4–5 add recipes:** Verify each shaped recipe's exact ingredients and output identity; verify the vanilla mace recipe is absent and each custom mace can be crafted once independently
- [ ] **Owner local test:** Verify normal crafting, shift-click, repeated attempts, Crafter automation, restart persistence, and administrative grants against craft-once records
- [ ] **Owner local test:** Exercise container protections: chest/barrel/shulker/ender chest, hopper/dropper, bundle, crafting grid, item frame, armor stand, shift-click, drag, number-key/offhand swap, and container open/break/explosion paths
- [ ] **Owner local test:** Verify per-item carrying caps at default, custom values, and zero/unlimited through crafting, pickup, inventory movement, join/restart recovery, reclaim, and `/galaxy give`; verify distinct item types have independent caps
- [ ] **Owner local test:** Verify durability, enchanting table, anvil, grindstone, Curse of Vanishing, Mending, and XP repair are blocked for identified items
- [ ] **Owner local test:** Verify death drops stay at the death location; lava, fire, cactus, void, explosions, and despawn create a protected reclaim barrel at world X/Z (0,0), and claiming it restores the same item UUID
- [ ] Re-run the relevant recipe, protection, lifecycle, and persistence tests whenever later phases add item definitions or change event handling

**Phase 2.1 deferrals:** The owner will run the Paper/Head Drop, crafting, inventory, protection, and restart checks locally; no test server is requested or expected to be provided. Exact recipe/output checks remain deferred until item definitions and recipes are added in Phases 4–5. Leave these items unchecked until the owner confirms those tests pass.

### Phase 3 – Ritual System
- [ ] Start a 15-minute ritual after a successful legendary craft, consume ingredients, and keep the legendary item nonexistent until completion; repeatable exceptions have no ritual
- [ ] Show the ritual boss bar/announcement and animation above the crafting table; drop the completed item at that table when the timer ends
- [ ] Announce the item name and crafter coordinates every 3 minutes; render each item's configured gradient name in global announcements and place coordinates in parentheses as `(X, Y, Z)`; audience and remaining message format use placeholders until decided (see Open Question 1)
- [ ] Persist item ID, craft/ritual start time, table location, reveal count/axis progress, owner, cooldowns, and reclaimable spawn-block markers
- [ ] Resume active rituals after restart without duplicate items or announcements; rebuild reclaimable spawn-block markers on load
- [ ] Test cancellation, shutdown/restart, unloaded worlds/chunks, and a craft completed exactly at the ritual deadline

### Phase 4 – Simple Items (first playable)
- [ ] Galaxy String: implement its shaped recipe as a plain, repeatable crafting component with no ability; it must not consume a craft-once quota and must remain stackable
- [ ] Kuiper Ball: recipe yields 10, stack size 64; thrown snowball freezes for 30 seconds and creates the wind-charge-like burst; it must not consume a craft-once quota and its stacks must merge normally
- [ ] Mass Orb: repeatable recipe; it must not consume a craft-once quota or receive legendary-only loss/storage protection; its carrying cap is configurable per item ID (default one, zero unlimited); Slowness IV and height-scaled, anvil-like fall damage with the Feather Falling XV effect; use a documented formula placeholder until confirmed
- [ ] Astral Sword: implement its recipe, Sharpness VI/Looting III/Fire Aspect, prohibit further enchanting, and grant a placeholder 1.65x attack-speed multiplier for 30 seconds after a four-hit combo
- [ ] Void Sword: implement its recipe, Unbreakable/Sharpness IV/Looting III, prohibit further enchanting, and apply Slowness I plus Wither I for 10 seconds after a four-hit combo
- [ ] Solstice Chestplate: implement its recipe and grant Speed III, Strength II, and Resistance I for five minutes once per Minecraft day when world time reaches 13,500 ticks
- [ ] Lunar Leggings: implement its recipe, remain Unbreakable, and grant Speed I, Strength I, and Resistance II for five minutes when world time reaches tick 13,500 of the 24,000-tick day; persist and resume the remaining effect duration across restarts
- [ ] Verify each item identity, recipe, global protection rule, cooldown/effect duration, and restart behavior

### Phase 5 – Complex Abilities
- [ ] Star Mace: implement its recipe as one custom mace type, Wind Burst III/Density II, seven-heart damage cap, and 30-second cooldown after two hits; retain its independent one-ever craft allowance
- [ ] Collapsing Star Mace: implement its recipe as a separate custom mace type, Breach III/Wind Burst II, seven-heart damage cap, and a spark/tiny-explosion response when a hit exceeds five hearts (each spark capped at one heart); retain its independent one-ever craft allowance
- [ ] Nebula Spear: implement its recipe, Lunge-only enchantment, 45-second cooldown, every-fifth-lunge thrust, and four-heart maximum damage against Protection IV diamond; verify the confirmed server lunge hook and resist attribute swapping
- [ ] Wormhole Trident: implement its recipe and count every fifth Riptide to create a rift; launch its creator 20 blocks forward on passage, keep the rift for 50 seconds after creator passage, and apply two hearts plus Wither I for 20 seconds to opponents who pass through
- [ ] Celestial Bow: implement its recipe; on a fully charged shot, apply splash damage and create a lingering Wither-or-Poison cloud on impact using clearly labeled configurable placeholders until the effect and values are decided
- [ ] Cosmic Crown: implement its recipe, maximum-enchant stats and unbreakable protection; trigger thorns every two hits taken and boost absorption to four hearts for golden apples and ten hearts for enchanted golden apples
- [ ] Singularity Boots: implement the agreed recipe, cancel fall damage, and create a creeper-sized explosion after a fall of at least 50 blocks
- [ ] Nova Shield: implement its recipe; disable it after two axe hits for the shield-disable timer and restore it automatically; grant the totem effect on death with a 10-minute cooldown
- [ ] Verify damage caps against Protection IV diamond and test cooldowns, effect durations, combo reset windows, persistence, and exploit resistance for every ability

### Phase 6 – Polish & Balance
- [ ] Add distinct particles, sounds, and player-facing lore for every legendary and repeatable item; use each item's configured gradient display name in death and interaction messages
- [ ] Add `/galaxy recipes` covering every registered recipe; verify readable item models/textures without requiring a resource pack (`item_model` remains optional)
- [ ] Move balance-sensitive values into validated config, including cooldowns, damage/effect caps, combo windows, durations, ritual settings, and projectile/rift behavior
- [ ] Run balance tests against Protection IV diamond and verify all exact damage caps, armor interactions, and effect durations
- [ ] Complete a dupe/transfer exploit pass for crafter, shulker, bundle, portals, dispensers, containers, item frames/armor stands, death, reconnect, and concurrent crafting/recovery
- [ ] Test clean install, upgrade, data reload, plugin/server restart, and preservation of permanent craft-once records
- [ ] Document setup/dependencies (including Head Drop configuration), item recipes, admin commands, open design decisions, and recovery behavior
- [ ] Build the plugin jar and publish a release with generated notes and the versioned artifact

## 5. Open Questions / Conflicts to Resolve

1. **Ritual reveal — [REQUIRED: Phase 3].** The item name must use that item's own configured gradient in the global reveal, and coordinates must be formatted in parentheses as `(X, Y, Z)`. Audience and the remaining exact message format are undecided; use placeholders for those details. Reuse the same configured item-name component for death and interaction messages (Phase 6).
2. **Lunar Leggings catch-up — [REQUIRED: Phase 4].** The trigger is confirmed as world time tick 13,500 in the 24,000-tick day, with a five-minute effect. Persist and resume an effect that was active at restart. Still decide whether a day whose tick 13,500 passed while the plugin/server was offline should grant the missed effect on startup, or wait for the next day's trigger. “Catch-up” here means only this missed-trigger decision, not resuming an already-active effect.
3. **Mass Orb fall-damage formula — [REQUIRED: Phase 4].** The required behavior is height-scaled, anvil-like fall damage, with Feather Falling XV. The remaining implementation detail is the exact damage formula (how height maps to damage) and any maximum cap; keep these as explicit placeholders rather than revisiting the requested behavior.
4. **Singularity Boots recipe — [REQUIRED: Phase 5].** The corner ingredient is still undecided. Keep the recipe visibly incomplete until supplied; do not choose an ingredient.
5. **Celestial Bow effect — [REQUIRED: Phase 5].** The final cloud effect (Wither or Poison), duration, and strength are undecided. Use explicit configurable placeholders until chosen; do not treat either option as final.
6. **Collapsing Star Mace sparks — [REQUIRED: Phase 5].** Keep the specified trigger (a hit exceeding five hearts) and one-heart maximum damage per spark. The explosion size/radius and whether one hit may produce multiple sparks remain undecided.
7. **Wormhole Trident rift — [REQUIRED: Phase 5].** Keep the specified five-Riptide trigger, 20-block creator launch, opponent damage/effect, and 50-second post-creator-passage lifetime. Interaction radius remains undecided; confirm whether the 50 seconds begin at creator passage or creation before implementation.
8. **Nebula Spear lunge integration — [REQUIRED: Phase 5 verification].** The lunge behavior is already confirmed working on the existing server; this is not a request to redesign it. Before implementation, inspect that server's plugin/API integration and identify the reliable event or callback that signals a successful lunge so counting does not rely on guessed movement/velocity. Ask for owner input only if the running setup cannot be inspected.
9. **Cosmic Crown thorns — [REQUIRED: Phase 5].** Keep the specified trigger of every two hits taken. Damage amount and target/retaliation rule remain undecided; use a clearly labeled placeholder until agreed.
10. **“First mace on the server” — [REQUIRED: Phase 5].** The server should allow one of each custom mace type: one Star Mace and one Collapsing Star Mace, each with a separate permanent server-wide craft record. Keep the “first mace on the server” question open for the server owner: does the Star Mace need to be the first mace of any kind to exist/be crafted on the server, or is it only the first custom mace type introduced by this plugin? Do not add a scan for existing mace items or block either custom type's independent craft allowance unless the owner confirms the broader rule.
11. **Astral Sword attack-speed balance — [REQUIRED: Phase 6].** Use a temporary 1.65x attack-speed multiplier for 30 seconds after the four-hit combo. Keep the final value open for balance review; clarify whether “1.65 boost” is intended as a multiplier if testing exposes any ambiguity.

## 6. Changelog

- 2026-10-22: Initial roadmap drafted from IdeasOnPaper.md; updated from answers in OpenQuestions.md and reviewed for consistency.
- 2026-10-22: Expanded Phases 1–6 to trace the implementation against Sections 2–3.5; recorded remaining design decisions instead of guessing.
- 2026-10-25: Applied owner decisions to Open Questions 1–11 and affected Phase 3–5 requirements; retained unresolved questions with required-phase tags and explicit placeholders.
- 2026-10-25: Corrected the mace rule: each custom mace type has its own permanent server-wide one-craft allowance, and kept the “first mace on the server” interpretation open for the server owner.
- 2026-10-25: Implemented the Phase 2 rules engine, made Head Drop 3.1.0+ a required dependency, and added Phase 2.1 automated/server integration test checklists.
