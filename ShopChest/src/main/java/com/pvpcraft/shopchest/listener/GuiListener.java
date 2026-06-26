package com.pvpcraft.shopchest.listener;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.gui.ManageHolder;
import com.pvpcraft.shopchest.gui.PurchaseHolder;
import com.pvpcraft.shopchest.gui.SetupHolder;
import com.pvpcraft.shopchest.gui.ShopGui;
import com.pvpcraft.shopchest.manager.SessionManager;
import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Drives the Shop Setup GUI and the /shop management GUI. */
public class GuiListener implements org.bukkit.event.Listener {

    private final ShopChestPlugin plugin;

    public GuiListener(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof SetupHolder setup) {
            event.setCancelled(true);
            handleSetupClick(player, event, setup);
        } else if (holder instanceof ManageHolder manage) {
            event.setCancelled(true);
            handleManageClick(player, event, manage);
        } else if (holder instanceof PurchaseHolder purchase) {
            event.setCancelled(true);
            handlePurchaseClick(player, event, purchase);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof SetupHolder || holder instanceof ManageHolder
                || holder instanceof PurchaseHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof SetupHolder || holder instanceof ManageHolder) {
            // Keep any pending chat request (price/invite intentionally close the GUI).
            plugin.sessions().clearOpenSetup(event.getPlayer().getUniqueId());
        }
    }

    // --- Setup GUI -------------------------------------------------------

    private void handleSetupClick(Player player, InventoryClickEvent event, SetupHolder holder) {
        Shop shop = plugin.shops().getShop(holder.getChest());
        if (shop == null) {
            player.closeInventory();
            return;
        }

        // Picking the sale item from the player's own inventory.
        if (plugin.sessions().isAwaitingSaleItem(player.getUniqueId())
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())) {
            ItemStack picked = event.getCurrentItem();
            plugin.sessions().setAwaitingSaleItem(player.getUniqueId(), false);
            if (picked == null || picked.getType() == Material.AIR) {
                player.sendMessage(Msg.prefixed("<red>Kein Item ausgewählt."));
            } else {
                shop.setSaleItem(picked.clone());
                plugin.shops().save();
                player.sendMessage(Msg.prefixed("<green>Verkaufs-Item gesetzt: <gold><amount>x <item>",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                                "amount", String.valueOf(picked.getAmount())),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(
                                "item", picked.effectiveName())));
            }
            ShopGui.renderSetup(plugin, holder.getInventory(), shop);
            return;
        }

        // Only act on clicks inside the GUI itself.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(holder.getInventory())) {
            return;
        }

        switch (event.getRawSlot()) {
            case SetupHolder.SLOT_SALE_ITEM -> {
                plugin.sessions().setAwaitingSaleItem(player.getUniqueId(), true);
                player.sendMessage(Msg.prefixed("<yellow>Klicke nun ein Item in deinem Inventar an."));
            }
            case SetupHolder.SLOT_PRICE -> {
                plugin.sessions().setPendingChat(player.getUniqueId(),
                        new SessionManager.ChatRequest(SessionManager.ChatRequestType.PRICE, holder.getChest()));
                player.closeInventory();
                player.sendMessage(Msg.prefixed("<yellow>Gib den Preis (Anzahl) im Chat ein. <gray>(\"abbrechen\" zum Abbrechen)"));
            }
            case SetupHolder.SLOT_CURRENCY -> {
                cycleCurrency(shop, event.isRightClick());
                plugin.shops().save();
                ShopGui.renderSetup(plugin, holder.getInventory(), shop);
            }
            case SetupHolder.SLOT_STOCK -> {
                // Informational only.
            }
            case SetupHolder.SLOT_TOGGLE -> {
                if (!shop.isConfigured()) {
                    player.sendMessage(Msg.prefixed("<red>Setze zuerst ein Verkaufs-Item."));
                } else {
                    shop.setActive(!shop.isActive());
                    plugin.shops().save();
                    ShopGui.renderSetup(plugin, holder.getInventory(), shop);
                }
            }
            case SetupHolder.SLOT_DELETE -> {
                if (event.isShiftClick()) {
                    plugin.shops().removeShop(shop.getLocation());
                    player.closeInventory();
                    player.sendMessage(Msg.prefixed("<gray>Shop gelöscht."));
                } else {
                    player.sendMessage(Msg.prefixed("<red>Shift-Klick zum Bestätigen des Löschens."));
                }
            }
            case SetupHolder.SLOT_EARNINGS -> {
                int paid = plugin.shopService().collectEarnings(player, shop);
                if (paid <= 0) {
                    player.sendMessage(Msg.prefixed("<gray>Keine Einnahmen zum Abholen."));
                } else {
                    player.sendMessage(Msg.prefixed("<green>Einnahmen abgeholt: <gold><amount>x <currency></gold>.",
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                                    "amount", String.valueOf(paid)),
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(
                                    "currency", new org.bukkit.inventory.ItemStack(shop.getCurrency()).effectiveName())));
                }
                ShopGui.renderSetup(plugin, holder.getInventory(), shop);
            }
            default -> {
                // filler slots
            }
        }
    }

    private void cycleCurrency(Shop shop, boolean backwards) {
        List<Material> currencies = plugin.currencies();
        if (currencies.isEmpty()) {
            return;
        }
        int idx = currencies.indexOf(shop.getCurrency());
        if (idx < 0) {
            idx = 0;
        }
        int next = backwards ? (idx - 1 + currencies.size()) % currencies.size()
                : (idx + 1) % currencies.size();
        shop.setCurrency(currencies.get(next));
    }

    // --- Management GUI --------------------------------------------------

    private void handleManageClick(Player player, InventoryClickEvent event, ManageHolder holder) {
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(holder.getInventory())) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == ManageHolder.SLOT_INVITE
                && holder.getInventory().getItem(ManageHolder.SLOT_INVITE) != null) {
            plugin.sessions().setPendingChat(player.getUniqueId(),
                    new SessionManager.ChatRequest(SessionManager.ChatRequestType.INVITE_MEMBER, null));
            player.closeInventory();
            player.sendMessage(Msg.prefixed("<yellow>Gib den Namen des Spielers im Chat ein. <gray>(\"abbrechen\" zum Abbrechen)"));
            return;
        }

        Location loc = holder.getOwnedShops().get(slot);
        if (loc != null) {
            Shop shop = plugin.shops().getShop(loc);
            if (shop != null) {
                ShopGui.openSetup(plugin, player, shop);
            } else {
                player.sendMessage(Msg.prefixed("<red>Dieser Shop existiert nicht mehr."));
                ShopGui.openManagement(plugin, player);
            }
        }
    }

    // --- Purchase GUI (buyer) -------------------------------------------

    private void handlePurchaseClick(Player player, InventoryClickEvent event, PurchaseHolder holder) {
        Shop shop = plugin.shops().getShop(holder.getChest());
        if (shop == null) {
            player.closeInventory();
            player.sendMessage(Msg.prefixed("<red>Dieser Shop existiert nicht mehr."));
            return;
        }
        // Only react to clicks inside the GUI itself.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(holder.getInventory())) {
            return;
        }

        switch (event.getRawSlot()) {
            case PurchaseHolder.SLOT_MINUS_10 -> adjustAmount(plugin, holder, shop, -10);
            case PurchaseHolder.SLOT_MINUS_1 -> adjustAmount(plugin, holder, shop, -1);
            case PurchaseHolder.SLOT_PLUS_1 -> adjustAmount(plugin, holder, shop, 1);
            case PurchaseHolder.SLOT_PLUS_10 -> adjustAmount(plugin, holder, shop, 10);
            case PurchaseHolder.SLOT_CONFIRM -> {
                plugin.shopService().purchase(player, shop, holder.getAmount());
                player.closeInventory();
            }
            default -> {
                // item display / filler: ignore
            }
        }
    }

    private void adjustAmount(ShopChestPlugin plugin, PurchaseHolder holder, Shop shop, int delta) {
        holder.setAmount(holder.getAmount() + delta);
        ShopGui.renderPurchase(plugin, holder, shop);
    }
}
