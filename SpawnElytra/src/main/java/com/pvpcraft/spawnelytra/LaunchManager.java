package com.pvpcraft.spawnelytra;

import com.pvpcraft.spawnelytra.zone.ZoneManager;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns all flight state: starting launches, locking the Elytra into the
 * chestplate slot, detecting landings, restoring the saved chestplate, and
 * persisting everything per-UUID in players.yml so flights survive reconnects.
 */
public final class LaunchManager {

    /** Search radius (blocks, each axis) for dropped items and containers. */
    private static final double RADIUS = 200.0;
    /** Safety cap for the landing watcher (ticks) so it can never run forever. */
    private static final int LANDING_TIMEOUT_TICKS = 20 * 60; // 60 seconds
    /** Player-inventory slot index of the chestplate. */
    public static final int CHESTPLATE_SLOT = 38;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SpawnElytra plugin;
    private final NamespacedKey key;
    private final ZoneManager zones;

    private final File playersFile;
    private final YamlConfiguration playersCfg;

    /** Online players that currently have an active launch. */
    private final Map<UUID, LaunchData> active = new HashMap<>();
    /** Player UUID -> block locations of containers they recently opened. */
    private final Map<UUID, Set<Location>> openedContainers = new HashMap<>();
    /** Players that currently have an active landing watcher running. */
    private final Set<UUID> watching = new HashSet<>();

    public LaunchManager(SpawnElytra plugin, NamespacedKey key, ZoneManager zones) {
        this.plugin = plugin;
        this.key = key;
        this.zones = zones;
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
        this.playersCfg = YamlConfiguration.loadConfiguration(playersFile);
    }

    /** In-memory + on-disk state for a single player's flight. */
    public static final class LaunchData {
        final UUID elytraId;
        ItemStack savedChestplate; // may be null (no chestplate when launched)
        boolean wasGliding;

        LaunchData(UUID elytraId, ItemStack savedChestplate, boolean wasGliding) {
            this.elytraId = elytraId;
            this.savedChestplate = savedChestplate;
            this.wasGliding = wasGliding;
        }
    }

    // --- item tagging ----------------------------------------------------

    /** Returns the spawn-elytra id stamped on the stack, or {@code null}. */
    public UUID taggedId(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ELYTRA || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Whether the stack is any tagged Spawn Elytra. */
    public boolean isSpawnElytra(ItemStack stack) {
        return taggedId(stack) != null;
    }

    private boolean matches(ItemStack stack, UUID target) {
        UUID id = taggedId(stack);
        return id != null && id.equals(target);
    }

    private ItemStack createElytra(UUID id) {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.displayName(MM.deserialize("<gray>Spawn Elytra")
                .decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        elytra.setItemMeta(meta);
        return elytra;
    }

    // --- state queries (used by listeners) -------------------------------

    public boolean hasActiveLaunch(UUID playerId) {
        return active.containsKey(playerId);
    }

    /** Whether the stack is something that would equip into the chest slot. */
    public boolean isChestArmor(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Material type = stack.getType();
        return type == Material.ELYTRA || type.name().endsWith("_CHESTPLATE");
    }

    /** Whether the stack is exactly the active player's tagged Spawn Elytra. */
    public boolean isElytraFor(ItemStack stack, UUID playerId) {
        LaunchData data = active.get(playerId);
        return data != null && matches(stack, data.elytraId);
    }

    /**
     * Safety net for the (already-blocked) case where some other chestplate ends
     * up equipped: strips the displaced Elytra copies, re-equips the tagged
     * Elytra and hands the intruding chestplate back to the player.
     */
    public void revertChestplate(Player player, ItemStack equipped) {
        UUID pid = player.getUniqueId();
        if (!active.containsKey(pid)) {
            return;
        }
        ItemStack returned = (equipped != null && !equipped.getType().isAir())
                ? equipped.clone() : null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            LaunchData data = active.get(pid);
            if (data == null || !player.isOnline()) {
                return;
            }
            PlayerInventory inv = player.getInventory();
            removeFromInventory(player, data.elytraId);
            inv.setChestplate(createElytra(data.elytraId));
            if (returned != null) {
                inv.addItem(returned).values().forEach(left ->
                        player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            player.updateInventory();
        });
    }

    // --- launching -------------------------------------------------------

    /**
     * Begins a launch: stashes the player's chestplate, equips a fresh tagged
     * Elytra in its place, and persists the state.
     */
    public void launch(Player player) {
        UUID pid = player.getUniqueId();
        if (active.containsKey(pid)) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack chestplate = inv.getChestplate();
        boolean hadChestplate = chestplate != null && !chestplate.getType().isAir();

        UUID elytraId = UUID.randomUUID();
        LaunchData data = new LaunchData(elytraId,
                hadChestplate ? chestplate.clone() : null, false);
        active.put(pid, data);

        inv.setChestplate(createElytra(elytraId));
        player.updateInventory();
        savePlayerData(pid, data);

        plugin.send(player, "<green>Launch! <gray>Spring und gleite – deine Rüstung bekommst du bei der Landung zurück.");
    }

    // --- glide / landing -------------------------------------------------

    /** Records that the player is actively gliding (gliding=true fired). */
    public void markGliding(Player player) {
        LaunchData data = active.get(player.getUniqueId());
        if (data != null && !data.wasGliding) {
            data.wasGliding = true;
            savePlayerData(player.getUniqueId(), data);
        }
    }

    /**
     * Called when gliding stops. Landing only completes once the player has
     * actually glided AND is back on the ground, so we poll for the ground.
     */
    public void onGlideStop(Player player) {
        LaunchData data = active.get(player.getUniqueId());
        if (data == null || !data.wasGliding) {
            return;
        }
        watchForLanding(player);
    }

    private void watchForLanding(Player player) {
        UUID pid = player.getUniqueId();
        if (!active.containsKey(pid) || !watching.add(pid)) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(pid);
                if (p == null || !p.isOnline() || !active.containsKey(pid)) {
                    stop();
                    return;
                }
                if (p.isGliding()) {
                    stop(); // took off again; wait for the next glide stop
                    return;
                }
                if (p.isOnGround()) {
                    stop();
                    completeLanding(p);
                    return;
                }
                if (++ticks > LANDING_TIMEOUT_TICKS) {
                    stop();
                }
            }

            private void stop() {
                watching.remove(pid);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Decides what happens once the player is confirmed on the ground after
     * gliding. Landing inside a launch zone keeps the Elytra (so they can take
     * off again); only a landing outside every zone removes it.
     */
    private void completeLanding(Player player) {
        LaunchData data = active.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (zones.zoneAt(player.getLocation()) != null) {
            // Inside a launch zone: keep the Elytra, allow another glide.
            data.wasGliding = false;
            savePlayerData(player.getUniqueId(), data);
            ensureElytraEquipped(player, data);
            return;
        }
        land(player);
    }

    /** Re-equips the tagged Elytra if an active player is missing it. */
    public void ensureElytraEquipped(Player player) {
        LaunchData data = active.get(player.getUniqueId());
        if (data != null) {
            ensureElytraEquipped(player, data);
        }
    }

    private void ensureElytraEquipped(Player player, LaunchData data) {
        if (!matches(player.getInventory().getChestplate(), data.elytraId)) {
            player.getInventory().setChestplate(createElytra(data.elytraId));
            player.updateInventory();
        }
    }

    /**
     * Completes a flight: removes every copy of the tagged Elytra, hands the
     * saved chestplate back, and clears the player's stored data.
     */
    public void land(Player player) {
        UUID pid = player.getUniqueId();
        LaunchData data = active.remove(pid);
        if (data == null) {
            return;
        }
        removeFromInventory(player, data.elytraId);
        removeFromDroppedItems(player.getWorld(), player.getLocation(), data.elytraId);
        removeFromContainers(player, data.elytraId);

        if (data.savedChestplate != null) {
            player.getInventory().setChestplate(data.savedChestplate);
        }
        player.updateInventory();
        clearPlayerData(pid);

        plugin.send(player, "<aqua>Gelandet! <gray>Deine Rüstung ist zurück.");
    }

    /**
     * Handles a death while a launch is active. The tagged Spawn Elytra must never
     * survive a death (it is deleted, not dropped or kept); the player's real saved
     * chestplate takes its place — dropping at the death location on a normal death,
     * or going back into the chest slot when the inventory is kept. Either way the
     * stored flight state is wiped immediately (memory <i>and</i> players.yml) so a
     * later landing can no longer restore a pre-death chestplate and duplicate it.
     *
     * @param drops         the live death-drops list ({@code event.getDrops()});
     *                      mutated in place. Empty when the inventory is kept.
     * @param keepInventory {@code event.getKeepInventory()}
     */
    public void handleDeath(Player player, List<ItemStack> drops, boolean keepInventory) {
        UUID pid = player.getUniqueId();
        LaunchData data = active.remove(pid);
        if (data == null) {
            return;
        }
        // Stop any in-flight landing watcher / container tracking for this player.
        watching.remove(pid);
        openedContainers.remove(pid);

        // (a) The tagged Elytra is deleted in every case: strip it from the drops.
        drops.removeIf(this::isSpawnElytra);

        if (keepInventory) {
            // Inventory is preserved, so the Elytra is still equipped on the player:
            // remove it and put the real chestplate (or nothing) back in the slot.
            removeFromInventory(player, data.elytraId);
            player.getInventory().setChestplate(data.savedChestplate); // null clears it
            player.updateInventory();
        } else if (data.savedChestplate != null) {
            // (b) Normal death: the saved chestplate drops at the death location.
            drops.add(data.savedChestplate);
        }

        // (c) Forget everything about this flight, in memory and on disk.
        clearPlayerData(pid);
    }

    // --- removal helpers -------------------------------------------------

    private void removeFromInventory(Player player, UUID target) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (matches(storage[i], target)) {
                storage[i] = null;
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        if (matches(inv.getChestplate(), target)) {
            inv.setChestplate(null);
        }
        if (matches(inv.getItemInOffHand(), target)) {
            inv.setItemInOffHand(null);
        }
    }

    private void removeFromDroppedItems(World world, Location around, UUID target) {
        for (Entity entity : world.getNearbyEntities(around, RADIUS, RADIUS, RADIUS)) {
            if (entity instanceof Item item && matches(item.getItemStack(), target)) {
                item.remove();
            }
        }
    }

    private void removeFromContainers(Player player, UUID target) {
        Set<Location> locations = openedContainers.remove(player.getUniqueId());
        if (locations == null) {
            return;
        }
        Location origin = player.getLocation();
        World world = origin.getWorld();
        for (Location loc : locations) {
            if (loc.getWorld() == null || !loc.getWorld().equals(world)
                    || loc.distanceSquared(origin) > RADIUS * RADIUS) {
                continue;
            }
            BlockState state = loc.getBlock().getState();
            if (!(state instanceof Container container)) {
                continue;
            }
            Inventory inv = container.getInventory();
            ItemStack[] contents = inv.getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                if (matches(contents[i], target)) {
                    contents[i] = null;
                    changed = true;
                }
            }
            if (changed) {
                inv.setContents(contents);
            }
        }
    }

    // --- container tracking ----------------------------------------------

    /** Caches the block location(s) of a container the player just closed. */
    public void rememberContainer(Player player, Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            addContainerLocation(player, locationOf(doubleChest.getLeftSide()));
            addContainerLocation(player, locationOf(doubleChest.getRightSide()));
        } else if (holder instanceof Container && inventory.getLocation() != null) {
            addContainerLocation(player, inventory.getLocation());
        }
    }

    private Location locationOf(InventoryHolder side) {
        return side instanceof Chest chest ? chest.getLocation() : null;
    }

    private void addContainerLocation(Player player, Location loc) {
        if (loc != null) {
            openedContainers
                    .computeIfAbsent(player.getUniqueId(), k -> new HashSet<>())
                    .add(loc.toBlockLocation());
        }
    }

    // --- join / quit / persistence ---------------------------------------

    /** Restores a player's flight from players.yml when they (re)join. */
    public void restoreOnJoin(Player player) {
        UUID pid = player.getUniqueId();
        LaunchData data = readPlayerData(pid);
        if (data == null) {
            return;
        }
        active.put(pid, data);
        // Make sure the tagged Elytra is back in the chestplate slot.
        if (!matches(player.getInventory().getChestplate(), data.elytraId)) {
            player.getInventory().setChestplate(createElytra(data.elytraId));
            player.updateInventory();
        }
        plugin.send(player, "<gray>Dein Flug wurde wiederhergestellt – lande, um deine Rüstung zurückzubekommen.");
    }

    /** Persists a player's flight on quit (chestplate stays stashed). */
    public void handleQuit(Player player) {
        UUID pid = player.getUniqueId();
        LaunchData data = active.remove(pid);
        if (data != null) {
            savePlayerData(pid, data);
        }
        openedContainers.remove(pid);
        watching.remove(pid);
    }

    /** On disable, flush every active flight to disk so nothing is lost. */
    public void persistAllOnDisable() {
        for (Map.Entry<UUID, LaunchData> entry : active.entrySet()) {
            savePlayerData(entry.getKey(), entry.getValue());
        }
    }

    private void savePlayerData(UUID pid, LaunchData data) {
        String path = "players." + pid + ".";
        playersCfg.set(path + "elytra-id", data.elytraId.toString());
        playersCfg.set(path + "gliding", data.wasGliding);
        playersCfg.set(path + "chestplate", data.savedChestplate); // null clears it
        savePlayersFile();
    }

    private LaunchData readPlayerData(UUID pid) {
        ConfigurationSection sec = playersCfg.getConfigurationSection("players." + pid);
        if (sec == null) {
            return null;
        }
        String rawId = sec.getString("elytra-id");
        if (rawId == null) {
            return null;
        }
        UUID elytraId;
        try {
            elytraId = UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ItemStack chestplate = sec.getItemStack("chestplate");
        boolean gliding = sec.getBoolean("gliding", false);
        return new LaunchData(elytraId, chestplate, gliding);
    }

    private void clearPlayerData(UUID pid) {
        playersCfg.set("players." + pid, null);
        savePlayersFile();
    }

    private void savePlayersFile() {
        try {
            playersCfg.save(playersFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", ex);
        }
    }
}
