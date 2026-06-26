package com.pvpcraft.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marks a custom 45-slot inventory as an Invsee mirror of a player's inventory
 * (the 36 storage slots plus armor and offhand). Lets the listener recognise the
 * GUI and find the player it mirrors, independent of the viewer-keyed session.
 */
public final class MirrorHolder implements InventoryHolder {

    private final UUID targetId;
    private final boolean editable;
    private Inventory inventory;

    public MirrorHolder(UUID targetId, boolean editable) {
        this.targetId = targetId;
        this.editable = editable;
    }

    public UUID targetId() {
        return targetId;
    }

    public boolean editable() {
        return editable;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
