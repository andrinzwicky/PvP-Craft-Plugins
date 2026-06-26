package com.pvpcraft.shopchest.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks an inventory as the Shop Setup GUI and remembers which chest it edits. */
public class SetupHolder implements InventoryHolder {

    public static final int SLOT_SALE_ITEM = 0;
    public static final int SLOT_PRICE = 1;
    public static final int SLOT_CURRENCY = 2;
    public static final int SLOT_STOCK = 3;
    public static final int SLOT_TOGGLE = 4;
    public static final int SLOT_DELETE = 5;
    public static final int SLOT_EARNINGS = 6;
    public static final int SIZE = 9;

    private final Location chest;
    private Inventory inventory;

    public SetupHolder(Location chest) {
        this.chest = chest;
    }

    public Location getChest() {
        return chest;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
