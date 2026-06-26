package com.pvpcraft.shopchest.gui;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.model.Plot;
import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.ItemUtil;
import com.pvpcraft.shopchest.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds and opens the Shop Setup GUI and the /shop management GUI. */
public final class ShopGui {

    private ShopGui() {
    }

    private static ItemStack filler() {
        return ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
    }

    // --- Setup GUI -------------------------------------------------------

    public static void openSetup(ShopChestPlugin plugin, Player player, Shop shop) {
        SetupHolder holder = new SetupHolder(shop.getLocation());
        Inventory inv = Bukkit.createInventory(holder, SetupHolder.SIZE, Msg.mm("<gold>Shop einrichten"));
        holder.setInventory(inv);
        renderSetup(plugin, inv, shop);

        for (int i = SetupHolder.SLOT_EARNINGS + 1; i < SetupHolder.SIZE; i++) {
            inv.setItem(i, filler());
        }

        plugin.sessions().setOpenSetup(player.getUniqueId(), shop.getLocation());
        plugin.sessions().setAwaitingSaleItem(player.getUniqueId(), false);
        player.openInventory(inv);
    }

    /** Repaint the live state of an open Setup GUI. */
    public static void renderSetup(ShopChestPlugin plugin, Inventory inv, Shop shop) {
        // Slot 1: sale item
        if (shop.isConfigured()) {
            ItemStack icon = shop.getSaleItem().clone();
            icon.editMeta(meta -> meta.lore(List.of(
                    Msg.mm("<gray>Verkauft: <white><amount>x</white> pro Kauf",
                            Placeholder.unparsed("amount", String.valueOf(shop.getSaleItem().getAmount())))
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    Msg.mm("<yellow>Klick</yellow><gray>, dann Item im Inventar wählen.")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            )));
            inv.setItem(SetupHolder.SLOT_SALE_ITEM, icon);
        } else {
            inv.setItem(SetupHolder.SLOT_SALE_ITEM, ItemUtil.icon(Material.BARRIER,
                    Msg.mm("<red>Kein Verkaufs-Item"),
                    List.of(Msg.mm("<gray>Klick, dann Item im Inventar wählen."))));
        }

        // Slot 2: price
        inv.setItem(SetupHolder.SLOT_PRICE, ItemUtil.icon(Material.GOLD_NUGGET,
                Msg.mm("<gold>Preis"),
                List.of(
                        Msg.mm("<gray>Aktuell: <white><price>x</white> <yellow><currency></yellow>",
                                Placeholder.unparsed("price", String.valueOf(shop.getPrice())),
                                Placeholder.component("currency", new ItemStack(shop.getCurrency()).effectiveName())),
                        Msg.mm("<yellow>Klick</yellow><gray>: Betrag im Chat eingeben.")
                )));

        // Slot 3: currency
        inv.setItem(SetupHolder.SLOT_CURRENCY, ItemUtil.icon(shop.getCurrency(),
                Msg.mm("<aqua>Währung"),
                List.of(
                        Msg.mm("<gray>Aktuell: <white><currency>",
                                Placeholder.component("currency", new ItemStack(shop.getCurrency()).effectiveName())),
                        Msg.mm("<yellow>Linksklick</yellow><gray>: nächste"),
                        Msg.mm("<yellow>Rechtsklick</yellow><gray>: vorherige")
                )));

        // Slot 4: stock
        int stock = plugin.shopService().getStock(shop);
        List<Component> stockLore = new ArrayList<>();
        if (stock < 0) {
            stockLore.add(Msg.mm("<gray>Truhe nicht geladen."));
        } else if (!shop.isConfigured()) {
            stockLore.add(Msg.mm("<gray>Kein Verkaufs-Item gesetzt."));
        } else {
            int perBuy = Math.max(1, shop.getSaleItem().getAmount());
            stockLore.add(Msg.mm("<gray>Im Lager: <white><count>",
                    Placeholder.unparsed("count", String.valueOf(stock))));
            stockLore.add(Msg.mm("<gray>Käufe möglich: <white><buys>",
                    Placeholder.unparsed("buys", String.valueOf(stock / perBuy))));
        }
        inv.setItem(SetupHolder.SLOT_STOCK, ItemUtil.icon(Material.CHEST, Msg.mm("<gold>Vorrat"), stockLore));

        // Slot 5: toggle
        boolean active = shop.isActive();
        inv.setItem(SetupHolder.SLOT_TOGGLE, ItemUtil.icon(
                active ? Material.LIME_DYE : Material.GRAY_DYE,
                active ? Msg.mm("<green>Aktiv") : Msg.mm("<red>Inaktiv"),
                List.of(
                        active ? Msg.mm("<gray>Shop ist aktiv und kaufbar.") : Msg.mm("<gray>Shop ist deaktiviert."),
                        shop.isConfigured() ? Msg.mm("<yellow>Klick</yellow><gray>: umschalten.")
                                : Msg.mm("<red>Erst ein Verkaufs-Item setzen.")
                )));

        // Slot 6: delete
        inv.setItem(SetupHolder.SLOT_DELETE, ItemUtil.icon(Material.TNT,
                Msg.mm("<red>Shop löschen"),
                List.of(Msg.mm("<gray>Entfernt diesen Shop dauerhaft."),
                        Msg.mm("<dark_red>Shift-Klick zum Bestätigen."))));

        // Slot 7: earnings (counted income, collected on click)
        int earnings = shop.getCollected();
        inv.setItem(SetupHolder.SLOT_EARNINGS, ItemUtil.icon(Material.SUNFLOWER,
                Msg.mm("<gold>Einnahmen abholen"),
                List.of(
                        Msg.mm("<gray>Gesammelt: <white><amount>x</white> <yellow><currency></yellow>",
                                Placeholder.unparsed("amount", String.valueOf(earnings)),
                                Placeholder.component("currency", new ItemStack(shop.getCurrency()).effectiveName())),
                        earnings > 0
                                ? Msg.mm("<yellow>Klick</yellow><gray>: alles auszahlen lassen.")
                                : Msg.mm("<dark_gray>Noch keine Einnahmen.")
                )));
    }

    // --- Management GUI --------------------------------------------------

    public static void openManagement(ShopChestPlugin plugin, Player player) {
        UUID uuid = player.getUniqueId();
        ManageHolder holder = new ManageHolder(uuid);
        Inventory inv = Bukkit.createInventory(holder, ManageHolder.SIZE, Msg.mm("<gold>Meine Shops"));
        holder.setInventory(inv);

        int slot = 0;

        // Owned shops (editable).
        for (Shop shop : plugin.shops().getShopsOwnedBy(uuid)) {
            if (slot >= ManageHolder.SLOT_INVITE) {
                break;
            }
            inv.setItem(slot, shopIcon(plugin, shop, true, false));
            holder.getOwnedShops().put(slot, shop.getLocation());
            slot++;
        }

        // Shops on plots where the player is a member (view only).
        for (Plot plot : plugin.plots().getPlotsWithMember(uuid)) {
            for (Shop shop : plugin.shops().getShopsOnPlot(plot.getId())) {
                if (slot >= ManageHolder.SLOT_INVITE) {
                    break;
                }
                if (uuid.equals(shop.getOwner())) {
                    continue;
                }
                inv.setItem(slot, shopIcon(plugin, shop, false, true));
                slot++;
            }
        }

        // Invite button (only useful for plot owners).
        if (!plugin.plots().getPlotsOwnedBy(uuid).isEmpty()) {
            inv.setItem(ManageHolder.SLOT_INVITE, ItemUtil.icon(Material.PLAYER_HEAD,
                    Msg.mm("<green>Mitglied einladen"),
                    List.of(
                            Msg.mm("<gray>Lädt einen Spieler ein, deine Shops"),
                            Msg.mm("<gray>im /shop-Menü zu sehen (nur ansehen)."),
                            Msg.mm("<yellow>Klick</yellow><gray>: Namen im Chat eingeben.")
                    )));
        }

        player.openInventory(inv);
    }

    // --- Purchase GUI (buyer) -------------------------------------------

    public static void openPurchase(ShopChestPlugin plugin, Player player, Shop shop) {
        PurchaseHolder holder = new PurchaseHolder(shop.getLocation());
        Inventory inv = Bukkit.createInventory(holder, PurchaseHolder.SIZE,
                Msg.mm("<gold>Shop — <item>",
                        Placeholder.component("item", shop.getSaleItem().effectiveName())));
        holder.setInventory(inv);
        renderPurchase(plugin, holder, shop);
        player.openInventory(inv);
    }

    /** Repaint the purchase GUI for the holder's current amount selection. */
    public static void renderPurchase(ShopChestPlugin plugin, PurchaseHolder holder, Shop shop) {
        Inventory inv = holder.getInventory();
        for (int i = 0; i < PurchaseHolder.SIZE; i++) {
            inv.setItem(i, filler());
        }

        ItemStack sale = shop.getSaleItem();
        int perUnit = sale.getAmount();
        int price = shop.getPrice();
        int maxUnits = Math.max(1, plugin.shopService().maxUnitsInStock(shop));
        int stock = plugin.shopService().getStock(shop);

        int amount = Math.min(Math.max(1, holder.getAmount()), maxUnits);
        holder.setAmount(amount);

        Component currencyName = new ItemStack(shop.getCurrency()).effectiveName();

        // Sale item with price/stock details.
        ItemStack icon = sale.clone();
        icon.editMeta(meta -> meta.lore(List.of(
                Msg.mm("<gray>Pro Einheit: <white><per>x</white>",
                                Placeholder.unparsed("per", String.valueOf(perUnit)))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Msg.mm("<gray>Preis pro Einheit: <gold><price>x</gold> <yellow><currency></yellow>",
                                Placeholder.unparsed("price", String.valueOf(price)),
                                Placeholder.component("currency", currencyName))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Msg.mm("<gray>Vorrat: <white><stock></white>",
                                Placeholder.unparsed("stock", stock < 0 ? "?" : String.valueOf(stock)))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        )));
        inv.setItem(PurchaseHolder.SLOT_ITEM, icon);

        inv.setItem(PurchaseHolder.SLOT_MINUS_10, ItemUtil.icon(Material.RED_STAINED_GLASS_PANE,
                Msg.mm("<red>− 10"), List.of(Msg.mm("<gray>Menge verringern."))));
        inv.setItem(PurchaseHolder.SLOT_MINUS_1, ItemUtil.icon(Material.PINK_STAINED_GLASS_PANE,
                Msg.mm("<red>− 1"), List.of(Msg.mm("<gray>Menge verringern."))));
        inv.setItem(PurchaseHolder.SLOT_PLUS_1, ItemUtil.icon(Material.LIME_STAINED_GLASS_PANE,
                Msg.mm("<green>+ 1"), List.of(Msg.mm("<gray>Menge erhöhen."))));
        inv.setItem(PurchaseHolder.SLOT_PLUS_10, ItemUtil.icon(Material.GREEN_STAINED_GLASS_PANE,
                Msg.mm("<green>+ 10"), List.of(Msg.mm("<gray>Menge erhöhen."))));

        int totalItems = perUnit * amount;
        int totalPrice = price * amount;
        inv.setItem(PurchaseHolder.SLOT_AMOUNT, ItemUtil.icon(Material.PAPER,
                Msg.mm("<gold>Menge: <white><amount>",
                        Placeholder.unparsed("amount", String.valueOf(amount))),
                List.of(
                        Msg.mm("<gray>Erhält: <white><items>x</white> <item>",
                                Placeholder.unparsed("items", String.valueOf(totalItems)),
                                Placeholder.component("item", sale.effectiveName())),
                        Msg.mm("<gray>Kosten: <gold><price>x</gold> <yellow><currency></yellow>",
                                Placeholder.unparsed("price", String.valueOf(totalPrice)),
                                Placeholder.component("currency", currencyName)),
                        Msg.mm("<dark_gray>Max: <white><max></white> Einheiten",
                                Placeholder.unparsed("max", String.valueOf(maxUnits)))
                )));

        inv.setItem(PurchaseHolder.SLOT_CONFIRM, ItemUtil.icon(Material.EMERALD,
                Msg.mm("<green>Kaufen"),
                List.of(
                        Msg.mm("<gray>Kauft <white><items>x</white> <item>",
                                Placeholder.unparsed("items", String.valueOf(totalItems)),
                                Placeholder.component("item", sale.effectiveName())),
                        Msg.mm("<gray>für <gold><price>x</gold> <yellow><currency></yellow>.",
                                Placeholder.unparsed("price", String.valueOf(totalPrice)),
                                Placeholder.component("currency", currencyName)),
                        Msg.mm("<yellow>Klick</yellow><gray>: bestätigen.")
                )));
    }

    private static ItemStack shopIcon(ShopChestPlugin plugin, Shop shop, boolean editable, boolean memberView) {
        Material iconMat = shop.isConfigured() ? shop.getSaleItem().getType() : Material.BARRIER;
        Component name = shop.isConfigured()
                ? shop.getSaleItem().effectiveName()
                : Msg.mm("<red>Nicht eingerichtet");

        List<Component> lore = new ArrayList<>();
        if (shop.isConfigured()) {
            lore.add(Msg.mm("<gray>Verkauft: <white><amount>x <item>",
                    Placeholder.unparsed("amount", String.valueOf(shop.getSaleItem().getAmount())),
                    Placeholder.component("item", shop.getSaleItem().effectiveName())));
        }
        lore.add(Msg.mm("<gray>Preis: <gold><price>x <currency>",
                Placeholder.unparsed("price", String.valueOf(shop.getPrice())),
                Placeholder.component("currency", new ItemStack(shop.getCurrency()).effectiveName())));
        int stock = plugin.shopService().getStock(shop);
        lore.add(Msg.mm("<gray>Vorrat: <white><stock>",
                Placeholder.unparsed("stock", stock < 0 ? "?" : String.valueOf(stock))));
        lore.add(shop.isActive() ? Msg.mm("<green>● Aktiv") : Msg.mm("<red>● Inaktiv"));
        lore.add(Msg.mm("<dark_gray>Plot: <plot>", Placeholder.unparsed("plot", shop.getPlotId())));
        if (editable) {
            lore.add(Msg.mm("<yellow>Klick</yellow><gray>: einrichten."));
        } else if (memberView) {
            lore.add(Msg.mm("<dark_gray>(nur ansehen)"));
        }
        return ItemUtil.icon(iconMat, name, lore);
    }
}
