package com.pvpcraft.shopchest.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Helpers for counting / removing items from inventories. */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    /** Count items in the inventory matching the template via {@link ItemStack#isSimilar}. */
    public static int countSimilar(Inventory inventory, ItemStack template) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Count items in the inventory of the given material (meta ignored). */
    public static int countMaterial(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Remove up to {@code amount} items matching the template (via isSimilar) from the
     * inventory. Returns the amount actually removed.
     */
    public static int removeSimilar(Inventory inventory, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                inventory.setItem(i, null);
            }
        }
        return amount - remaining;
    }

    /** Remove up to {@code amount} items of the given material (meta ignored). */
    public static int removeMaterial(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                inventory.setItem(i, null);
            }
        }
        return amount - remaining;
    }
}
