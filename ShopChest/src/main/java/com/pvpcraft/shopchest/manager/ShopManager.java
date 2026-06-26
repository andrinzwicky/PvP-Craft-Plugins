package com.pvpcraft.shopchest.manager;

import com.pvpcraft.shopchest.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Loads, stores and queries shops. Backed by shops.yml. */
public class ShopManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Shop> shops = new HashMap<>();

    public ShopManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    public Shop getShop(Location loc) {
        return shops.get(Shop.key(loc));
    }

    public boolean isShop(Location loc) {
        return shops.containsKey(Shop.key(loc));
    }

    /**
     * Resolve the shop for a clicked block, accounting for double chests: a shop
     * may be registered on either half. Returns null if neither half is a shop.
     */
    public Shop getShopByBlock(Block block) {
        Shop direct = getShop(block.getLocation());
        if (direct != null) {
            return direct;
        }
        Location partner = doubleChestPartner(block);
        if (partner != null) {
            return getShop(partner);
        }
        return null;
    }

    /** Location of the other half of a double chest, or null for single / non-chest. */
    public Location doubleChestPartner(Block block) {
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
            return null;
        }
        Inventory inv = chest.getInventory();
        if (inv instanceof DoubleChestInventory dci) {
            InventoryHolder left = dci.getLeftSide().getHolder();
            InventoryHolder right = dci.getRightSide().getHolder();
            Location self = block.getLocation();
            for (InventoryHolder holder : new InventoryHolder[]{left, right}) {
                if (holder instanceof org.bukkit.block.Chest c) {
                    Location loc = c.getLocation();
                    if (loc.getBlockX() != self.getBlockX()
                            || loc.getBlockY() != self.getBlockY()
                            || loc.getBlockZ() != self.getBlockZ()) {
                        return loc;
                    }
                } else if (holder instanceof DoubleChest dc) {
                    // Fallback: shouldn't normally happen via getLeftSide/getRightSide.
                    return null;
                }
            }
        }
        return null;
    }

    public Collection<Shop> getShops() {
        return shops.values();
    }

    public List<Shop> getShopsOwnedBy(UUID uuid) {
        List<Shop> result = new ArrayList<>();
        for (Shop shop : shops.values()) {
            if (uuid.equals(shop.getOwner())) {
                result.add(shop);
            }
        }
        return result;
    }

    public List<Shop> getShopsOnPlot(String plotId) {
        List<Shop> result = new ArrayList<>();
        for (Shop shop : shops.values()) {
            if (plotId.equalsIgnoreCase(shop.getPlotId())) {
                result.add(shop);
            }
        }
        return result;
    }

    public void addShop(Shop shop) {
        shops.put(shop.key(), shop);
        save();
    }

    public Shop removeShop(Location loc) {
        Shop removed = shops.remove(Shop.key(loc));
        if (removed != null) {
            save();
        }
        return removed;
    }

    /** Remove every shop belonging to a (deleted) plot. */
    public void removeShopsOnPlot(String plotId) {
        boolean changed = shops.values().removeIf(s -> plotId.equalsIgnoreCase(s.getPlotId()));
        if (changed) {
            save();
        }
    }

    // --- persistence -----------------------------------------------------

    public void load() {
        shops.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("shops");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                String worldName = sec.getString("world");
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Shop '" + key + "' references unknown world '" + worldName + "', skipping.");
                    continue;
                }
                Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
                UUID owner = UUID.fromString(sec.getString("owner"));
                String plotId = sec.getString("plot", "");
                Shop shop = new Shop(loc, owner, plotId);
                shop.setSaleItem(sec.getItemStack("saleItem"));
                shop.setPrice(sec.getInt("price", 1));
                Material currency = Material.matchMaterial(sec.getString("currency", "DIAMOND"));
                shop.setCurrency(currency == null ? Material.DIAMOND : currency);
                shop.setActive(sec.getBoolean("active", false));
                shop.setCollected(sec.getInt("collected", 0));
                shops.put(shop.key(), shop);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid shop '" + key + "'", ex);
            }
        }
        plugin.getLogger().info("Loaded " + shops.size() + " shop(s).");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Shop shop : shops.values()) {
            Location loc = shop.getLocation();
            String base = "shops." + shop.key().replace(':', '_');
            cfg.set(base + ".world", loc.getWorld().getName());
            cfg.set(base + ".x", loc.getBlockX());
            cfg.set(base + ".y", loc.getBlockY());
            cfg.set(base + ".z", loc.getBlockZ());
            cfg.set(base + ".owner", shop.getOwner().toString());
            cfg.set(base + ".plot", shop.getPlotId());
            cfg.set(base + ".saleItem", shop.getSaleItem());
            cfg.set(base + ".price", shop.getPrice());
            cfg.set(base + ".currency", shop.getCurrency().name());
            cfg.set(base + ".active", shop.isActive());
            cfg.set(base + ".collected", shop.getCollected());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shops.yml", ex);
        }
    }
}
