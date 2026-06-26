package com.pvpcraft.invsee;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and syncs the 45-slot mirror GUI for a player's inventory: the 36
 * storage/hotbar slots plus the four armor slots and the offhand, with a few
 * filler panes rounding out the bottom row.
 *
 * <p>Slot layout (GUI slot -&gt; {@link PlayerInventory} index):
 * <pre>
 *   0..35  -> 0..35   hotbar (0-8) + main storage (9-35), identity mapping
 *   36     -> 39      helmet
 *   37     -> 38      chestplate
 *   38     -> 37      leggings
 *   39     -> 36      boots
 *   40     -> 40      offhand
 *   41..44 -> filler  (no mapping)
 * </pre>
 */
public final class InventoryMirror {

    public static final int SIZE = 45;

    /** GUI slot -> player inventory index; {@code -1} marks a filler slot. */
    private static final int[] TO_PLAYER = new int[SIZE];

    static {
        for (int i = 0; i <= 35; i++) {
            TO_PLAYER[i] = i;
        }
        TO_PLAYER[36] = 39; // helmet
        TO_PLAYER[37] = 38; // chestplate
        TO_PLAYER[38] = 37; // leggings
        TO_PLAYER[39] = 36; // boots
        TO_PLAYER[40] = 40; // offhand
        TO_PLAYER[41] = -1;
        TO_PLAYER[42] = -1;
        TO_PLAYER[43] = -1;
        TO_PLAYER[44] = -1;
    }

    private InventoryMirror() {
    }

    /** True if the given top-inventory raw slot is a non-interactive filler. */
    public static boolean isFiller(int rawSlot) {
        return rawSlot >= 0 && rawSlot < SIZE && TO_PLAYER[rawSlot] < 0;
    }

    /** Creates the mirror GUI, attaches its holder, fills fillers and content. */
    public static Inventory build(Player target, boolean editable) {
        MirrorHolder holder = new MirrorHolder(target.getUniqueId(), editable);
        Component title = Messages.MM.deserialize(
                "<dark_gray>Inventar von <gold><player>",
                Placeholder.unparsed("player", target.getName()));
        Inventory gui = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(gui);

        ItemStack filler = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (TO_PLAYER[slot] < 0) {
                gui.setItem(slot, filler);
            }
        }
        refresh(gui, target);
        return gui;
    }

    /** Copies the target's current items into the GUI's mapped slots. */
    public static void refresh(Inventory gui, Player target) {
        PlayerInventory source = target.getInventory();
        for (int slot = 0; slot < SIZE; slot++) {
            int index = TO_PLAYER[slot];
            if (index >= 0) {
                ItemStack item = source.getItem(index);
                gui.setItem(slot, item == null ? null : item.clone());
            }
        }
    }

    /** Writes the GUI's mapped slots back into the target's live inventory. */
    public static void writeBack(Inventory gui, Player target) {
        PlayerInventory dest = target.getInventory();
        for (int slot = 0; slot < SIZE; slot++) {
            int index = TO_PLAYER[slot];
            if (index >= 0) {
                ItemStack item = gui.getItem(slot);
                dest.setItem(index, item == null ? null : item.clone());
            }
        }
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }
}
