package com.pvpcraft.clansystem.gui;

import com.pvpcraft.clansystem.ClanSystem;
import com.pvpcraft.clansystem.Messages;
import com.pvpcraft.clansystem.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Builds and opens the chest menu listing a player's pending clan invites. */
public final class InviteGui {

    /** GUI title, in MiniMessage. */
    public static final String TITLE = "<gold>Clan-Einladungen";

    private static final int MAX_SLOTS = 54;

    private InviteGui() {
    }

    /**
     * Opens a chest menu showing one clickable entry per pending invite. The clan
     * list must be non-empty; the caller handles the "no invites" case.
     */
    public static void open(ClanSystem plugin, Player player, List<Clan> clans) {
        int rows = Math.min(6, Math.max(1, (clans.size() + 8) / 9));
        int size = rows * 9;

        InviteGuiHolder holder = new InviteGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, size, Messages.mm(TITLE));
        holder.setInventory(inv);

        int slot = 0;
        for (Clan clan : clans) {
            if (slot >= size || slot >= MAX_SLOTS) {
                break;
            }
            inv.setItem(slot, icon(plugin, clan));
            holder.slotToClanKey().put(slot, clan.key());
            slot++;
        }

        player.openInventory(inv);
    }

    /** A paper item named with the clan's colored name and tag. */
    private static ItemStack icon(ClanSystem plugin, Clan clan) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        Component name = Messages.mm("<color:" + clan.colorHex() + "><name></color>",
                        Placeholder.unparsed("name", clan.name()))
                .append(Component.space())
                .append(plugin.getDisplayManager().clanTag(clan))
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);
        meta.lore(List.of(Messages.mm("<gray>Klicke zum Beitreten.")
                .decoration(TextDecoration.ITALIC, false)));

        item.setItemMeta(meta);
        return item;
    }
}
