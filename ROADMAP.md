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
- **Plugins:** Galaxy SMP and the required Head Drop dependency only. Nebula Spear uses the server's native spear/Lunge behavior; no additional lunge plugin is required.

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
- **Lunge/Riptide counting:** use the native spear Lunge enchantment already confirmed working on the existing server; implement Nebula Spear's fifth-successful-lunge trigger using the Paper API available in the target setup. No third-party lunge integration is expected. Count trident Riptides for Wormhole Trident.
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
- [x] Programmatic item builder API (unbreakable, hidden flags, lore, model, Adventure-component names, configurable two-color gradient names); the in-game admin builder/editor is tracked in Phase 6
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
- [x] **Owner local test:** On the owner's Paper 26.3 server, verify Galaxy SMP loads with Head Drop 3.1.0+ and refuses missing/older versions
- [x] **Owner local test:** Configure Head Drop `drop.placement: item`; if testing block placement instead, enable `head.persist_lore_on_placement`; verify generated item heads carry the `headdrop:dropped` byte marker and normal player skulls are rejected by matching recipes
- [ ] **Owner local test:** Verify Phase 4 shaped recipes' exact ingredients and output identity, including Head Drop marker acceptance and rejection of ordinary player heads; verify repeatable outputs and stack size
- [ ] **Owner local test:** Verify the vanilla mace recipe is absent and Star Mace/Collapsing Star Mace each have an independent one-ever server-wide craft record
- [ ] **Owner local test:** Verify normal crafting, shift-click, repeated attempts, Crafter automation, restart persistence, and administrative grants against craft-once records
- [ ] **Owner local test:** Exercise container protections: chest/barrel/shulker/ender chest, hopper/dropper, bundle, crafting grid, item frame, armor stand, shift-click, drag, number-key/offhand swap, and container open/break/explosion paths
- [ ] **Owner local test:** Verify per-item carrying caps at default, custom values, and zero/unlimited through crafting, pickup, inventory movement, join/restart recovery, reclaim, and `/galaxy give`; verify distinct item types have independent caps
- [ ] **Owner local test:** Verify durability, enchanting table, anvil, grindstone, Curse of Vanishing, Mending, and XP repair are blocked for identified items
- [ ] **Owner local test:** Verify death drops stay at the death location; lava, fire, cactus, void, explosions, and despawn create a protected reclaim barrel at world X/Z (0,0), and claiming it restores the same item UUID
- [ ] Re-run the relevant recipe, protection, lifecycle, and persistence tests whenever later phases add item definitions or change event handling

**Phase 2.1 status:** The owner confirmed the Paper 26.3/Head Drop version and Head Drop marker/config checks passed locally. The remaining owner-local gameplay checks listed above are explicitly waiting. Phase 4 recipe/output checks are available under Phase 4; Star Mace and Collapsing Star Mace recipe checks are available under Phase 5. No test server is requested or expected.

### Phase 3 – Ritual System
- [x] Start the configured 15-minute ritual after a successful craft-once legendary craft, consume ingredients only after the ritual record is saved, and keep the item nonexistent until completion; repeatable exceptions remain immediate
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
- [x] Galaxy String: shaped, repeatable recipe; plain crafting component, no ability or craft quota; stack-compatible item identity with no unique-instance UUID
- [x] Kuiper Ball: shaped recipe yields 10, custom stack maximum 64; marked thrown snowballs freeze a direct hit for 30 seconds and create a configurable wind-charge-like velocity burst; repeatable with normal stack merging
- [x] Mass Orb: repeatable recipe with no legendary-only loss/storage protection; configurable per-item cap (default one, zero unlimited); Slowness IV while held; height-scaled fall damage and Feather Falling XV reduction use documented, configurable placeholders pending owner balance decision
- [x] Astral Sword: recipe, Sharpness VI/Looting III/Fire Aspect, no-enchant protection, and temporary 1.65x attack-speed multiplier for 30 seconds after four hits
- [x] Void Sword: recipe, Unbreakable/Sharpness IV/Looting III, no-enchant protection, and Slowness I plus Wither I for 10 seconds after four hits
- [x] Solstice Chestplate: recipe and Speed III, Strength II, Resistance I for five minutes when world time crosses tick 13,500; daily trigger is persisted
- [x] Lunar Leggings: recipe, Unbreakable, and Speed I, Strength I, Resistance II for five minutes at world tick 13,500; active remaining duration pauses over clean shutdown and resumes after restart
- [x] Add automated tests for daily-effect persistence and Mass Orb damage formula/cap placeholder; `gradlew test build` passes
- [ ] **Owner local test:** Verify all seven shaped recipes' exact ingredients and outputs; Head Drop heads work and ordinary player heads are rejected
- [ ] **Owner local test:** Verify Galaxy String and Kuiper Ball stacking, Kuiper Ball output of 10/stack size 64, and repeatable crafts do not start rituals or consume craft-once quota
- [ ] **Owner local test:** Verify Astral/Void four-hit combos, effect targets and durations, Astral attack-speed multiplier, and reset window
- [ ] **Owner local test:** Verify Kuiper Ball freeze and burst; Mass Orb cap, Slowness IV, fall-damage placeholder and Feather Falling XV behavior
- [ ] **Owner local test:** Wear Solstice Chestplate/Lunar Leggings at tick 13,500, check effect amplifiers/duration and once-per-day behavior, restart during an active effect, and decide Lunar missed-trigger catch-up behavior
- [ ] Verify Phase 4 items on Paper 26.3, including protection/enchant rules, craft/ritual lifecycle, persistence and recovery; no texture pack is required for mechanics testing

**Phase 4 status:** Item definitions, recipes, abilities, configurable placeholders and repository tests are implemented. Live Paper gameplay checks remain unchecked for owner verification. Mass Orb's formula/cap and Lunar catch-up behavior remain explicitly open for balance/owner decisions.

### Phase 5 – Complex Abilities
- [x] Star Mace: register its shaped recipe as one custom mace type, Wind Burst III/Density II, seven-heart damage ceiling, and 30-second cooldown after two successful hits; retain its independent one-ever craft allowance
- [x] Collapsing Star Mace: register its shaped recipe as a separate custom mace type, Breach III/Wind Burst II, seven-heart damage ceiling, and spark/tiny-explosion response when pre-cap final damage exceeds five hearts, with each spark capped at one heart; retain its independent one-ever craft allowance
- [x] Add configurable provisional spark-count/radius/damage settings; preserve the open questions about final explosion radius and whether a hit may create multiple sparks
- [x] Add automated coverage for mace damage capping/threshold and confirm the existing datastore test covers independent permanent craft records
- [ ] **Owner local test:** Verify each mace recipe, Head Drop head-marker requirement, output enchantments/unbreakable status, and separate permanent craft-once records; confirm the vanilla mace recipe is unavailable
- [ ] **Owner local test:** Verify each mace's two-hit 30-second cooldown and seven-heart damage ceiling against unarmored and Protection IV diamond targets
- [ ] **Owner local test:** Verify Collapsing Star Mace sparks trigger only above five hearts and each spark deals no more than one heart; review the provisional spark count and radius settings
- [ ] Nebula Spear: implement its recipe, native Lunge-only enchantment, 45-second cooldown, every-fifth-successful-lunge thrust, and four-heart maximum damage against Protection IV diamond; verify successful-lunge counting and resistance to attribute swapping on the existing Paper setup (Galaxy SMP + Head Drop only; no additional plugin)
- [ ] Wormhole Trident: implement its recipe and count every fifth Riptide to create a rift; launch its creator 20 blocks forward on passage, keep the rift for 50 seconds after creator passage, and apply two hearts plus Wither I for 20 seconds to opponents who pass through
- [ ] Celestial Bow: implement its recipe; on a fully charged shot, apply splash damage and create a lingering Wither-or-Poison cloud on impact using clearly labeled configurable placeholders until the effect and values are decided
- [ ] Cosmic Crown: implement its recipe, maximum-enchant stats and unbreakable protection; trigger thorns every two hits taken and boost absorption to four hearts for golden apples and ten hearts for enchanted golden apples
- [ ] Singularity Boots: implement the agreed recipe, cancel fall damage, and create a creeper-sized explosion after a fall of at least 50 blocks
- [ ] Nova Shield: implement its recipe; disable it after two axe hits for the shield-disable timer and restore it automatically; grant the totem effect on death with a 10-minute cooldown
- [ ] Verify damage caps against Protection IV diamond and test cooldowns, effect durations, combo reset windows, persistence, and exploit resistance for every ability

**Phase 5 status:** The first two checklist items—Star Mace and Collapsing Star Mace—are implemented and repository-tested. Their Paper gameplay checks remain unchecked for owner review. No later Phase 5 items have been started.

### Phase 6 – Polish & Balance
- [ ] Add an in-game item/recipe builder and editor for registered items, restricted to server operators/admins only; support editing recipe ingredients/shapes, item display name and gradient, lore, stats, and configurable ability values, with validated persistent saves and a safe way to review/apply changes without granting non-operators access
- [ ] Add distinct particles, sounds, and player-facing lore for every legendary and repeatable item; use each item's configured gradient display name in death and interaction messages
- [ ] Show a clean, readable hover tooltip for every registered item that presents all relevant item stats and abilities (including enchantments, damage/effect values, cooldowns, triggers, and special rules); keep the tooltip accurate to the item's configured behavior
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
6. **Collapsing Star Mace sparks — [REQUIRED: Phase 5].** Keep the specified trigger (a hit exceeding five hearts) and one-heart maximum damage per spark. The implemented count (one per qualifying hit) and radius are provisional config placeholders only; the final explosion size/radius and whether one hit may produce multiple sparks remain undecided.
7. **Wormhole Trident rift — [REQUIRED: Phase 5].** Keep the specified five-Riptide trigger, 20-block creator launch, opponent damage/effect, and 50-second post-creator-passage lifetime. Interaction radius remains undecided; confirm whether the 50 seconds begin at creator passage or creation before implementation.
8. **Nebula Spear lunge implementation — [REQUIRED: Phase 5].** The source notes specify a Netherite spear with the native Lunge enchantment, a thrust after five lunges, Riptide II-like velocity, a four-heart maximum against Protection IV diamond, no other enchants, and a 45-second cooldown. The owner has confirmed this native behavior already works on the existing server, which uses Galaxy SMP and Head Drop only. Do not add or assume another plugin, redesign the ability, or ask the owner to reconfirm these requirements. Implement fifth-successful-lunge counting against the target Paper API and verify it without relying on guessed movement/velocity.
9. **Cosmic Crown thorns — [REQUIRED: Phase 5].** Keep the specified trigger of every two hits taken. Damage amount and target/retaliation rule remain undecided; use a clearly labeled placeholder until agreed.
10. **“First mace on the server” — [REQUIRED: Phase 5].** The server should allow one of each custom mace type: one Star Mace and one Collapsing Star Mace, each with a separate permanent server-wide craft record. Keep the “first mace on the server” question open for the server owner: does the Star Mace need to be the first mace of any kind to exist/be crafted on the server, or is it only the first custom mace type introduced by this plugin? Do not add a scan for existing mace items or block either custom type's independent craft allowance unless the owner confirms the broader rule.
11. **Astral Sword attack-speed balance — [REQUIRED: Phase 6].** Use a temporary 1.65x attack-speed multiplier for 30 seconds after the four-hit combo. Keep the final value open for balance review; clarify whether “1.65 boost” is intended as a multiplier if testing exposes any ambiguity.

## 6. Changelog

- 2026-10-22: Initial roadmap drafted from IdeasOnPaper.md; updated from answers in OpenQuestions.md and reviewed for consistency.
- 2026-10-22: Expanded Phases 1–6 to trace the implementation against Sections 2–3.5; recorded remaining design decisions instead of guessing.
- 2026-10-25: Applied owner decisions to Open Questions 1–11 and affected Phase 3–5 requirements; retained unresolved questions with required-phase tags and explicit placeholders.
- 2026-10-25: Clarified Nebula Spear uses the native Lunge behavior on the existing Galaxy SMP + Head Drop server; no additional plugin integration is required. Phase 5 still needs to implement and verify successful-lunge counting against the target Paper API.
- 2026-10-25: Corrected the mace rule: each custom mace type has its own permanent server-wide one-craft allowance, and kept the “first mace on the server” interpretation open for the server owner.
- 2026-10-25: Implemented the Phase 2 rules engine, made Head Drop 3.1.0+ a required dependency, and added Phase 2.1 automated/server integration test checklists.
