# Galaxy SMP Plugin – Roadmap

Living document. Source of ideas: [IdeasOnPaper.md](./IdeasOnPaper.md). Update the status columns and "Open Questions" as we build.

## 1. Target Platform

- **Server:** Paper 1.21.11+ (Bukkit/Spigot API is a subset; Paper is strongly recommended for `ItemStack` data components, Adventure text, async schedulers, and better events). Plugin declares `api-version: '1.21.11'`.
- **Java:** 21. **Build:** Gradle (Kotlin DSL) + `paper-api` (`compileOnly`), `paperweight-userdev` only if NMS is needed (avoid).
- **Config/data:** `config.yml` (tunables), `data.yml`/SQLite (item ownership, ritual state, cooldowns, crafted flags).
- **Packaging:** single shaded-free jar, `paper-plugin.yml` or `plugin.yml`.

## 2. Idea Summary

### 2.1 Legendary items (one craft each, unlosable, unbreakable, not enchantable)

| Item | Base | Key Ability / Rules | Recipe (from notes) |
|---|---|---|---|
| Nebula Spear | Netherite spear | Damage + cooldown, unbreakable; every 5 lunges → thrust (riptide-like velocity), max 4 hearts vs prot 4 diamond; comes with Lunge, no other enchants; attribute swapping ineffective; 45s CD | Corners: player head; top: trident; middle: netherite spear; bottom: redstone block; sides: gold blocks |
| Star Mace | Mace | 2nd mace on server; Wind Burst 3, Density 2; 30s CD after 2 hits; damage cap 7 hearts; no enchant/disenchant | Corners: heads; top: nether star; sides: wither skulls; bottom: netherite; middle: heavy core |
| Collapsing Star Mace | Mace | Mace perks + sparks if hit > 5 hearts; each spark = tiny explosion (cap 1 heart); dmg cap 7 hearts; Breach 3 + Wind Burst 2 | Sides: heavy cores; corners: heads; middle: nether star; top: deepslate coal ore; bottom: breeze rod |
| Astral Sword | Netherite sword | Sharpness 6, Looting 3, Fire Aspect; no enchanting | Corners: heads; top+middle: netherite ingot; bottom: breeze rod; sides: diamond blocks |
| Void Sword | Netherite sword | Unbreakable, Sharp 4, Looting 3, Breach 1; 4-hit combo → Slowness 1 + Wither 1 for 10s | Corners: heads; middle: netherite sword; edges: wither roses |
| Celestial Bow | Bow | Fully charged → splash damage + lingering cloud (Wither or Instant Damage 1) | Left-middle end rod, bottom blaze rod, top breeze rod, left corners heads, right column Galaxy String x3, middle end crystal |
| Wormhole Trident | Trident | Every 5 riptides → rift launches user 20 blocks forward; rift lasts 50s; enemies passing take 2 hearts + Wither 1 for 20s | Top/bottom: trident; corners: heads; middle: netherite ingot; left/right: wither skeleton skulls |
| Cosmic Crown | Netherite helmet | Max-enchant stats, unbreakable; thorns every 2 hits taken; boosted absorption (gapple 4 hearts, notch apple 10 hearts) | Middle: netherite helmet; sides: heads; corners: notch apples; top: axolotl bucket; bottom: silence armor trim |
| Singularity Boots | Netherite boots | No fall damage; fall ≥ 50 blocks → creeper-sized explosion on impact | Middle: netherite boots; corners: TBD item; edges: breeze rods; top/bottom: heads |
| Solstice Chestplate | Netherite chestplate | At noon: Speed 3, Strength 2, Resistance 1 for 5 min | Middle: netherite chestplate; top/bottom: end crystal; left/right: nether stars; corners: heads |
| Lunar Leggings | Netherite leggings | At midnight: Speed 1, Strength 1, Resistance 2 for 5 min; unbreakable | Middle: netherite leggings; edges: respawn anchors; corners: heads |
| Nova Shield | Shield | Withstands 2 axe hits before disabled; on death grants totem effect, 10 min CD | Middle: totem; top/bottom: netherite blocks; sides+top corners: pale oak; bottom corners: heads |

### 2.2 Craftable / repeatable items

| Item | Rules | Recipe |
|---|---|---|
| Galaxy String | Plain crafting component, no ability | Top: blaze rod; middle: breeze rod; bottom: end rod |
| Kuiper Ball | Freezing 30s + wind-charge-like burst (thrown snowball); recipe yields 10, stacks to 64 | Notes: "snowball all corners: powdered snow in middle middle left and right, breeze charge middle top and middle bottom" – ambiguous layout, confirm (likely corners: snowball; middle + left/right: powdered snow; top/bottom: breeze charge) |
| Mass Orb | Infinitely craftable, only 1 held per player; Slowness 4; anvil-like fall damage scaling with height; Feather Falling 15 effect | Middle: heavy core; sides: netherite blocks; top/bottom: blue ice; corners: gold blocks |

### 2.3 Global rules

1. Legendary items are crafted **once** server-wide (exceptions: Galaxy String, Kuiper Ball, Mass Orb).
2. Unbreakable, cannot be destroyed or lost (lava, fire, cactus, void, explosions, despawn).
3. No Curse of Vanishing (COV); cannot be enchanted/disenchanted (anvil, grindstone, table blocked).
4. Cannot be stored in ender chests or any container (chests, barrels, shulkers, hoppers, droppers, item frames, armor stands, bundles, etc.).
5. A player may hold only **one** legendary item at a time (exceptions above).
6. Crafting triggers a **15 min ritual**; every 3 min reveals one coordinate (X or Z) of the crafter.
7. Despawned/abandoned item teleports to world spawn (0,0) as a block form; right-click to reclaim as item.

## 3. Technical Design

### 3.1 Item identity
- Each legendary = `ItemStack` with `PersistentDataContainer` key `galaxy:item_id` (+ unique `galaxy:uuid` for tracking), custom model data / `item_model` component, `unbreakable`, hidden enchants/attributes via data components, lore via Adventure.
- Central `LegendaryItem` interface + registry: `id`, `createItem()`, `recipe()`, `onEvent hooks`, `cooldown`, `maxCrafts`.
- Ability logic is implemented as listeners per item (one class per item) sharing helpers (`CooldownManager`, `HitCounter`, `DamageCap`).

### 3.2 Recipes
- Register `ShapedRecipe` with `RecipeChoice.ExactChoice` for components (Galaxy String, heads).
- "Player head" = any player head (`Material.PLAYER_HEAD`); decide if specific.
- Craft-once enforcement in `PrepareItemCraftEvent` (deny result) and `CraftItemEvent` (atomically record in the data store; handle shift-click and crafter blocks via `CrafterCraftEvent`).
- Ritual starts on craft; item delivered at ritual end or held until then (decide; see Open Questions).

### 3.3 Protection systems
| Rule | Events / approach |
|---|---|
| Unbreakable | `unbreakable` component; cancel `PlayerItemDamageEvent` |
| Not destroyed | `EntityDamageEvent` on item entity, `ItemDespawnEvent`, `EntityCombustEvent`, void handling; make item entities invulnerable |
| Despawn → spawn block | `ItemDespawnEvent`/void check → place marker block at (0,0) (or a custom `ItemDisplay`/barrel-like block entity) with PDC; `PlayerInteractEvent` restores |
| No containers | `InventoryClickEvent`, `InventoryDragEvent`, `InventoryMoveItemEvent`, `InventoryPickupItemEvent`, hotbar-swap, shift-click, bundles, `PlayerDropItem` into frames/stands; check `InventoryType` ≠ PLAYER/CRAFTING |
| One at a time | `EntityPickupItemEvent`, `InventoryClickEvent`, `PlayerJoinEvent` check |
| No (dis)enchant | `PrepareAnvilEvent`, `PrepareGrindstoneEvent`, `PrepareItemEnchantEvent`, `EnchantItemEvent`, Mending/XP |
| Death / logout | Keep on `PlayerDeathEvent` (keepInventory for that item) or drop+protect; quit handling |

### 3.4 Abilities (implementation notes)
- **Damage caps:** `EntityDamageByEntityEvent` – `setDamage(min(final, cap))` (cap computed from `getFinalDamage()` and armor; test vs prot 4 diamond).
- **Combo counters:** per-attacker map with timeout (Void Sword 4 hits, Star Mace 2 hits, Cosmic Crown 2 hits taken).
- **Lunge/Riptide counting:** spear lunge via `PlayerRiptideEvent`/spear attack events (verify available API in 1.21.11); trident riptide via `PlayerRiptideEvent`.
- **Rift (Wormhole):** `Location` + radius, particle task, `BukkitScheduler`/Folia-safe scheduler; 50s lifetime; entry check by proximity.
- **Time-based buffs (Solstice/Lunar):** periodic task checking `world.getTime()` windows (noon ≈ 6000, midnight ≈ 18000) while item worn; grant once per cycle.
- **Nova Shield:** track axe-disable count (`PlayerItemBreak`/`EntityDamageByEntityEvent` blocking); `EntityResurrectEvent` / manual totem effect on death with 10 min CD.
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
- [ ] Gradle project, Paper API 1.21.11, `plugin.yml`, main class, config loader
- [ ] Debug/test server setup and run script
- [ ] Decide package layout (`item`, `ability`, `listener`, `ritual`, `storage`, `util`)

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
- [ ] Coord reveal every 3 min (X then Z, alternating)
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

1. **Ownership:** Is "COV" Curse of Vanishing? (assumed yes).
2. **Death:** if the holder dies, does the item drop (then spawn-block rule) or stay on the player? Notes say "cannot be lost".
3. **Singularity Boots recipe** has an undecided corner item.
4. **Mace damage cap vs "2nd mace on server":** is the vanilla mace crafting disabled/limited? Star Mace and Collapsing Star Mace each "one craft".
5. **Conflicting item caps:** "Mass Orb only one held per person" vs "one item at a time excluding Mass Orb" – confirm they stack independently.
6. **Kuiper Ball recipe** is complete (yields 10, stacks to 64) but its layout wording is ambiguous; confirm the grid.
7. **Spear:** vanilla spear/lunge exists in 1.21.11; confirm "netherite spear" and "5 lunges" mechanics via API.
8. **"Player head" recipe ingredient:** any head or specific (e.g. crafted from killed players)?
9. **Ritual details:** does the item exist during the ritual, or is it granted at the end? Who sees coordinates (global broadcast)?
10. **Nova Shield "2 axe hits":** per-cooldown reset?
11. **Item "can only be crafted one time":** is it re-craftable if the server resets/admin removes it?
12. Solstice "peak of noon" – exact tick window and re-trigger rule (once per day?).

## 6. Changelog

- 2026-10-22: Initial roadmap drafted from IdeasOnPaper.md.
