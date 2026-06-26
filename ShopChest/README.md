# ShopChest

Plot-based chest shops for **Paper 1.21.11**. Admins assign plots to players;
plot owners place chests and turn them into shops that sell items to other
players. Purchases are instant on right-click.

No external dependencies. MiniMessage for all text. Builds with Gradle, no
`reobfJar`, Mojang-mapped, compatible with `-Dpaper.disablePluginRemapping=true`.

## Build

```bash
./gradlew build
```

Output: `build/libs/ShopChest-1.0.0.jar` — drop it into your server's `plugins/`.

Requires a JDK 21+ to build (compiled with `--release 21`, so JDK 25 works too).

## Permissions

| Node              | Default | Meaning                                  |
|-------------------|---------|------------------------------------------|
| `shopchest.admin` | op      | All `/shopplot` commands; also grants `/shopconfigurator` |

**Staff bypass** — holders of any of these RankManager nodes can manage/break
shops on any plot (checked purely via Bukkit permissions, so it degrades
gracefully if RankManager is absent):

- `rankmanager.rank.moderator`
- `rankmanager.rank.developer`
- `rankmanager.rank.admin`
- `rankmanager.rank.owner`

## Admin workflow

1. `/shopplot wand` — get the selection shovel (default `GOLDEN_SHOVEL`).
   - **Left-click** a block = Position 1
   - **Right-click** a block = Position 2
2. `/shopplot create <id>` — create a plot from the current selection.
3. `/shopplot assign <id> <player>` — assign the plot owner (the "purchase").
4. `/shopconfigurator give <player>` — give the **§6Shop Konfigurator**
   (players can also craft it, see below).

The Shop Konfigurator is **only** obtainable via crafting or this command.

`/shopconfigurator give` is restricted to `shopchest.admin` **or** the
Owner/Admin/Developer RankManager ranks (`rankmanager.rank.owner` /
`.admin` / `.developer`) — **Moderators are excluded**. The check is done in
code, so the rank nodes grant access even without the `shopchest.admin`
permission, and it falls back to `shopchest.admin` only when RankManager is
absent. Alias: `/shopconfig`.

## Crafting

The Shop Konfigurator is craftable — recipe registered on startup via the
Bukkit `ShapedRecipe` API:

```
I I I
I C I     I = IRON_INGOT   C = CHEST   → 1x Shop Konfigurator
I I I
```

Other admin commands: `/shopplot delete <id>`, `/shopplot list`,
`/shopplot info <id>`.

## Player workflow

1. On your plot, place a chest.
2. Hold the **Shop Konfigurator** and **right-click the chest** → opens the
   **Shop einrichten** GUI:
   - **Sale Item** — click, then click an item in your inventory (its stack
     amount becomes the quantity sold per purchase).
   - **Price** — click, type the amount in chat.
   - **Currency** — left/right-click to cycle (configurable list).
   - **Stock** — shows the current count of the sale item in the chest.
   - **Active/Inactive** — toggle (requires a sale item first).
   - **Delete** — shift-click to remove the shop.
3. Put the sale item into the chest as stock. **Right-click your own shop chest
   (without the configurator) to open it normally and restock.**
4. `/shop` — overview of all your shops; click one to edit. Use **Mitglied
   einladen** to let another player view (not edit) your shops via `/shop`.

## Purchase flow

Any other player right-clicks an active shop chest:

- Not enough currency → `✖ Nicht genug …`
- No stock → `✖ Dieser Shop hat keinen Vorrat mehr.`
- Inactive → `⚠ Dieser Shop ist momentan inaktiv.`
- Otherwise: currency is removed from the buyer, the sale item is handed over
  (dropped at the buyer's feet if the inventory is full), the stock is removed
  from the chest, and **the payment is deposited into the shop chest** so the
  owner collects earnings.

## Data storage

- `plugins/ShopChest/plots.yml` — plot id, world, pos1, pos2, owner UUID, members.
- `plugins/ShopChest/shops.yml` — chest location, owner UUID, plot id, sale item,
  price, currency, active state.

## Config (`config.yml`)

```yaml
configurator:
  material: COMPARATOR      # base item; swap the model via custom-model-data
  custom-model-data: 1001   # future-proof for a resource pack
selector:
  material: GOLDEN_SHOVEL
currencies:
  - DIAMOND
  - DIAMOND_BLOCK
  - ...
```
