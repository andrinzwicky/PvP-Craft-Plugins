package com.pvpcraft.spawnelytra.selection;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the cuboid selection workflow: a tagged "selection wand" (a golden
 * shovel) lets admins pick two free corner points – left-click sets point 1,
 * right-click sets point 2 – exactly like WorldEdit's axe or ShopChest's golden
 * shovel. The picked points live in memory only; they are turned into a
 * {@link com.pvpcraft.spawnelytra.zone.CuboidZone} by {@code /spawnelytra setzone}.
 */
public final class SelectionManager {

    /** The material handed out as the selection wand. */
    public static final Material WAND_MATERIAL = Material.GOLDEN_SHOVEL;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final NamespacedKey wandKey;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public SelectionManager(NamespacedKey wandKey) {
        this.wandKey = wandKey;
    }

    /** Builds a fresh, uniquely tagged selection wand. */
    public ItemStack createWand() {
        ItemStack wand = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(MM.deserialize("<gold>SpawnElytra Auswahl-Tool")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MM.deserialize("<gray>Links-Klick: <white>Punkt 1")
                        .decoration(TextDecoration.ITALIC, false),
                MM.deserialize("<gray>Rechts-Klick: <white>Punkt 2")
                        .decoration(TextDecoration.ITALIC, false),
                MM.deserialize("<dark_gray>danach /spawnelytra setzone <name>")
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "wand");
        wand.setItemMeta(meta);
        return wand;
    }

    /** Whether {@code stack} is the tagged selection wand. */
    public boolean isWand(ItemStack stack) {
        if (stack == null || stack.getType() != WAND_MATERIAL || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .has(wandKey, PersistentDataType.STRING);
    }

    public void setPos1(UUID playerId, Location loc) {
        pos1.put(playerId, loc.clone());
    }

    public void setPos2(UUID playerId, Location loc) {
        pos2.put(playerId, loc.clone());
    }

    public Location pos1(UUID playerId) {
        return pos1.get(playerId);
    }

    public Location pos2(UUID playerId) {
        return pos2.get(playerId);
    }

    /** Whether both corner points are set and live in the same world. */
    public boolean hasCompleteSelection(UUID playerId) {
        Location a = pos1.get(playerId);
        Location b = pos2.get(playerId);
        return a != null && b != null && a.getWorld() != null
                && a.getWorld().equals(b.getWorld());
    }

    /** Drops the player's stored selection (e.g. on quit). */
    public void clear(UUID playerId) {
        pos1.remove(playerId);
        pos2.remove(playerId);
    }
}
