package com.pvpcraft.clansystem.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as the /clan accept invite menu and maps slots to clan keys. */
public final class InviteGuiHolder implements InventoryHolder {

    /** raw slot index -> clan key of the invite shown there. */
    private final Map<Integer, String> slotToClanKey = new HashMap<>();
    private Inventory inventory;

    public Map<Integer, String> slotToClanKey() {
        return slotToClanKey;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
