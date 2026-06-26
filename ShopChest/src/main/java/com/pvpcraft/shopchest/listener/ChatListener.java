package com.pvpcraft.shopchest.listener;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.gui.ShopGui;
import com.pvpcraft.shopchest.manager.SessionManager;
import com.pvpcraft.shopchest.model.Plot;
import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;

/** Captures chat input for price entry and member invitations. */
public class ChatListener implements Listener {

    private final ShopChestPlugin plugin;

    public ChatListener(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.sessions().hasPendingChat(uuid)) {
            return;
        }
        event.setCancelled(true);
        SessionManager.ChatRequest request = plugin.sessions().consumePendingChat(uuid);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Hop back to the main thread for all Bukkit interaction.
        Bukkit.getScheduler().runTask(plugin, () -> process(player, request, message));
    }

    private void process(Player player, SessionManager.ChatRequest request, String message) {
        if (message.equalsIgnoreCase("abbrechen") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(Msg.prefixed("<gray>Abgebrochen."));
            if (request.type() == SessionManager.ChatRequestType.PRICE) {
                reopenSetup(player, request.shopChest());
            }
            return;
        }

        switch (request.type()) {
            case PRICE -> handlePrice(player, request, message);
            case INVITE_MEMBER -> handleInvite(player, message);
        }
    }

    private void handlePrice(Player player, SessionManager.ChatRequest request, String message) {
        int price;
        try {
            price = Integer.parseInt(message);
        } catch (NumberFormatException ex) {
            player.sendMessage(Msg.prefixed("<red>Ungültige Zahl: <white>" + message));
            reopenSetup(player, request.shopChest());
            return;
        }
        if (price <= 0) {
            player.sendMessage(Msg.prefixed("<red>Der Preis muss mindestens 1 sein."));
            reopenSetup(player, request.shopChest());
            return;
        }
        Shop shop = plugin.shops().getShop(request.shopChest());
        if (shop == null) {
            player.sendMessage(Msg.prefixed("<red>Der Shop existiert nicht mehr."));
            return;
        }
        shop.setPrice(price);
        plugin.shops().save();
        player.sendMessage(Msg.prefixed("<green>Preis gesetzt: <gold><price>x <currency>",
                Placeholder.unparsed("price", String.valueOf(price)),
                Placeholder.component("currency",
                        new org.bukkit.inventory.ItemStack(shop.getCurrency()).effectiveName())));
        reopenSetup(player, request.shopChest());
    }

    private void handleInvite(Player player, String name) {
        UUID uuid = player.getUniqueId();
        List<Plot> ownedPlots = plugin.plots().getPlotsOwnedBy(uuid);
        if (ownedPlots.isEmpty()) {
            player.sendMessage(Msg.prefixed("<red>Du besitzt kein Plot, zu dem du einladen könntest."));
            return;
        }

        OfflinePlayer target = resolvePlayer(name);
        if (target == null || target.getUniqueId().equals(uuid)) {
            player.sendMessage(Msg.prefixed("<red>Spieler nicht gefunden: <white>" + name));
            return;
        }

        for (Plot plot : ownedPlots) {
            plot.getMembers().add(target.getUniqueId());
            // Mirror the new member into the matching WorldGuard region "plot_<id>".
            plugin.worldGuard().addMember(plot, target.getUniqueId());
        }
        plugin.plots().save();
        player.sendMessage(Msg.prefixed("<green><name> kann jetzt deine Shops sehen.",
                Placeholder.unparsed("name", target.getName() == null ? name : target.getName())));

        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(Msg.prefixed("<green>Du wurdest zu den Shops von <gold><name></gold> eingeladen. <gray>Nutze /shop.",
                    Placeholder.unparsed("name", player.getName())));
        }
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached;
        }
        return null;
    }

    private void reopenSetup(Player player, org.bukkit.Location chest) {
        Shop shop = plugin.shops().getShop(chest);
        if (shop != null && player.isOnline()) {
            ShopGui.openSetup(plugin, player, shop);
        }
    }
}
