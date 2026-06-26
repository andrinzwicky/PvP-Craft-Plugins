package com.pvpcraft.shopchest.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks an inventory as the buyer purchase GUI and tracks the selected amount. */
public class PurchaseHolder implements InventoryHolder {

    public static final int SIZE = 27;

    public static final int SLOT_ITEM = 4;
    public static final int SLOT_MINUS_10 = 9;
    public static final int SLOT_MINUS_1 = 10;
    public static final int SLOT_AMOUNT = 13;
    public static final int SLOT_PLUS_1 = 16;
    public static final int SLOT_PLUS_10 = 17;
    public static final int SLOT_CONFIRM = 22;

    private final Location chest;
    private int amount = 1;
    private Inventory inventory;

    public PurchaseHolder(Location chest) {
        this.chest = chest;
    }

    public Location getChest() {
        return chest;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
