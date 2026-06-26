package com.pvpcraft.clansystem.listener;

import com.pvpcraft.clansystem.ClanSystem;
import com.pvpcraft.clansystem.Messages;
import com.pvpcraft.clansystem.gui.InviteGuiHolder;
import com.pvpcraft.clansystem.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Handles clicks in the /clan accept invite menu: clicking an entry joins that clan. */
public final class InviteGuiListener implements Listener {

    private final ClanSystem plugin;

    public InviteGuiListener(ClanSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof InviteGuiHolder gui)) {
            return;
        }
        event.setCancelled(true);

        // Only act on clicks inside the menu itself.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(gui.getInventory())) {
            return;
        }
        String clanKey = gui.slotToClanKey().get(event.getRawSlot());
        if (clanKey == null) {
            return;
        }

        player.closeInventory();
        join(player, clanKey);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InviteGuiHolder) {
            event.setCancelled(true);
        }
    }

    /** Re-validates the invite (it may have expired or been revoked) and joins. */
    private void join(Player player, String clanKey) {
        if (plugin.getClanManager().getClan(player.getUniqueId()) != null) {
            Messages.send(player, Messages.ALREADY_IN);
            return;
        }
        Clan clan = plugin.getClanManager().getClanByKey(clanKey);
        if (clan == null) {
            Messages.send(player, Messages.NO_INVITE);
            return;
        }
        if (!plugin.getInviteManager().hasValidInvite(player.getUniqueId(), clanKey)) {
            Messages.send(player, plugin.getInviteManager().hasAnyInvite(player.getUniqueId(), clanKey)
                    ? Messages.INVITE_EXPIRED : Messages.NO_INVITE);
            return;
        }

        plugin.getInviteManager().consume(player.getUniqueId(), clanKey);
        plugin.getClanManager().addMember(clan, player.getUniqueId());
        plugin.getDisplayManager().update(player);
        Messages.send(player, Messages.JOINED, Messages.p("name", clan.name()));

        for (UUID uuid : clan.allMembers()) {
            if (uuid.equals(player.getUniqueId())) {
                continue;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Messages.send(online, "<gray><player> ist dem Clan beigetreten.",
                        Messages.p("player", player.getName()));
            }
        }
    }
}
