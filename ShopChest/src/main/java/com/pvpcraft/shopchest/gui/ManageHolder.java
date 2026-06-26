package com.pvpcraft.shopchest.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Marks an inventory as the /shop management GUI and maps slots to shops. */
public class ManageHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_INVITE = 53;

    private final UUID viewer;
    /** slot index -> shop chest location (only for editable, owned shops). */
    private final Map<Integer, Location> ownedShops = new HashMap<>();
    private Inventory inventory;

    public ManageHolder(UUID viewer) {
        this.viewer = viewer;
    }

    public UUID getViewer() {
        return viewer;
    }

    public Map<Integer, Location> getOwnedShops() {
        return ownedShops;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
