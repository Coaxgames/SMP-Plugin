# Galaxy SMP Plugin – Roadmap

Living document. Source of ideas: [IdeasOnPaper.md](./IdeasOnPaper.md). Update the status columns and "Open Questions" as we build.

## 1. Target Platform

- **Server:** Paper 26.3. Plugin declares `api-version: '26.3'`.
- **Java:** 25+ (Paper 26.3 runtime requirement). **Build:** Gradle + `paper-api` (`compileOnly`); use `paperweight-userdev` only if NMS is needed (avoid).
- **Config/data:** `config.yml` (tunables), `data.yml`/SQLite (item ownership, ritual state, cooldowns, crafted flags).
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
| Mass Orb | Infinitely craftable, only 1 held per player; Slowness 4; anvil-like fall damage scaling with height; Feather Falling 15 effect | Middle: heavy core; sides: netherite blocks; top/bottom: blue ice; corners: gold blocks |

### 2.3 Global rules

1. Legendary items can be crafted **once ever** server-wide (exceptions: Galaxy String, Kuiper Ball, Mass Orb). A server reset or administrative removal does not allow another craft; preserve the craft record across resets.
2. The vanilla mace recipe is disabled. Only the two custom mace recipes are allowed, and each custom mace type can be crafted once ever. Track each recipe's crafted state separately in this plugin; a server reset or administrative removal does not reset either flag.
3. Legendary items are unbreakable and protected from loss. On player death, the item remains at the death location unless destroyed by hazards such as lava, the void, or cactus; a hazard-destroyed item is returned to (0,0) as a reclaimable block. An item left to despawn is also returned there.
4. Curse of Vanishing (COV) is prohibited; items cannot be enchanted or disenchanted (anvil, grindstone, and enchanting table blocked).
5. Cannot be stored in ender chests or any container (chests, barrels, shulkers, hoppers, droppers, item frames, armor stands, bundles, etc.).
6. A player may hold only **one** legendary item at a time (exceptions above).
7. Crafting triggers a **15 min ritual**, except for Galaxy String, Kuiper Ball, and Mass Orb. The item does not exist in the world until the timer ends. An animation appears above the crafting table, then the item drops there. Every 3 min, chat reveals the crafter's coordinates and the crafted item's name.

## 3. Technical Design

### 3.1 Item identity
- Each legendary = `ItemStack` with `PersistentDataContainer` key `galaxy:item_id` (+ unique `galaxy:uuid` for tracking), custom model data / `item_model` component, `unbreakable`, hidden enchants/attributes via data components, lore via Adventure.
- Central `LegendaryItem` interface + registry: `id`, `createItem()`, `recipe()`, `onEvent hooks`, `cooldown`, `maxCrafts`.
- Ability logic is implemented as listeners per item (one class per item) sharing helpers (`CooldownManager`, `HitCounter`, `DamageCap`).

### 3.2 Recipes
- Register shaped recipes using exact choices for fixed custom ingredients such as Galaxy String. Player-head slots must match the Head Drop PDC marker, not a fixed display name or full `ItemStack` snapshot.
- Remove/disable the vanilla mace recipe and register only the Star Mace and Collapsing Star Mace recipes. Enforce and persist a separate one-craft flag for each custom mace type.
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
- **Damage caps:** `EntityDamageByEntityEvent` – `setDamage(min(final, cap))` (cap computed from `getFinalDamage()` and armor; test vs prot 4 diamond).
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
- [ ] `LegendaryItem` registry + PDC identification
- [ ] Item builder (unbreakable, hidden flags, lore, model)
- [ ] `CooldownManager`, config-driven values
- [ ] Persistent data store (craft-once flags, ownership)
- [ ] Admin commands: `/galaxy give|reset|list|locate`

### Phase 2 – Rules Engine
- [ ] Craft-once enforcement
- [ ] Container/enderchest block
- [ ] One-item-per-player limit
- [ ] Anti-destroy (fire/lava/void/cactus/explosion/despawn)
- [ ] Despawn → (0,0) block + reclaim
- [ ] No enchant/disenchant/COV
- [ ] Death/logout handling

### Phase 3 – Ritual System
- [ ] 15 min ritual on craft, boss-bar/announcement
- [ ] Coordinate-axis reveal every 3 min (X or Z); confirm audience and message format
- [ ] Persistence over restarts

### Phase 4 – Simple Items (first playable)
- [ ] Galaxy String, Kuiper Ball, Mass Orb
- [ ] Astral Sword, Void Sword (combo effect)
- [ ] Solstice Chestplate, Lunar Leggings

### Phase 5 – Complex Abilities
- [ ] Star Mace, Collapsing Star Mace
- [ ] Nebula Spear
- [ ] Wormhole Trident (rifts)
- [ ] Celestial Bow
- [ ] Cosmic Crown, Singularity Boots, Nova Shield

### Phase 6 – Polish & Balance
- [ ] Particles/sounds/lore per item
- [ ] Recipe viewer (`/galaxy recipes`) and resource-pack-free textures (item_model optional)
- [ ] Config for all numbers; balance testing vs prot 4 diamond
- [ ] Anti-exploit pass (dupe via crafter, shulker, bundle, portal, dispenser)
- [ ] Docs + release

## 5. Open Questions / Conflicts to Resolve

1. **Singularity Boots recipe** still has an undecided corner ingredient.

## 6. Changelog

- 2026-10-22: Initial roadmap drafted from IdeasOnPaper.md; updated from answers in OpenQuestions.md and reviewed for consistency.
