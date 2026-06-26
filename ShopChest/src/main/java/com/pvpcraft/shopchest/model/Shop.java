package com.pvpcraft.shopchest.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** A configured shop bound to a single chest block. */
public class Shop {

    private final Location location;
    private final UUID owner;
    private final String plotId;

    /** The item sold; its amount is the quantity handed over per purchase. */
    private ItemStack saleItem;
    /** Number of currency items required per purchase. */
    private int price;
    /** Currency item material. */
    private Material currency;
    private boolean active;

    /**
     * Collected income, counted in currency items (not stored physically). Buyers'
     * payments add to this; the owner cashes it out via the setup GUI, which resets it.
     */
    private int collected;

    public Shop(Location location, UUID owner, String plotId) {
        this.location = location;
        this.owner = owner;
        this.plotId = plotId;
        this.saleItem = null;
        this.price = 1;
        this.currency = Material.DIAMOND;
        this.active = false;
        this.collected = 0;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getPlotId() {
        return plotId;
    }

    public ItemStack getSaleItem() {
        return saleItem;
    }

    public void setSaleItem(ItemStack saleItem) {
        this.saleItem = saleItem;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = Math.max(1, price);
    }

    public Material getCurrency() {
        return currency;
    }

    public void setCurrency(Material currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Collected income, counted in currency items awaiting collection by the owner. */
    public int getCollected() {
        return collected;
    }

    public void setCollected(int collected) {
        this.collected = Math.max(0, collected);
    }

    /** Add to the collected income counter. */
    public void addCollected(int amount) {
        this.collected = Math.max(0, this.collected + amount);
    }

    /** A shop can only be active once it has a sale item configured. */
    public boolean isConfigured() {
        return saleItem != null && saleItem.getType() != Material.AIR;
    }

    /** Stable key for a block location: "world:x:y:z". */
    public static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public String key() {
        return key(location);
    }
}
