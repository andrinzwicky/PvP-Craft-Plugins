package com.pvpcraft.shopchest.manager;

import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.InventoryUtil;
import com.pvpcraft.shopchest.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Central shop logic: stock queries, the purchase flow and income collection. */
public class ShopService {

    private final ShopManager shops;

    public ShopService(ShopManager shops) {
        this.shops = shops;
    }

    /** Live inventory of the shop chest (full double-chest inventory if applicable), or null. */
    public Inventory getChestInventory(Shop shop) {
        Location loc = shop.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return null;
        }
        Block block = loc.getBlock();
        if (block.getState() instanceof Chest chest) {
            return chest.getInventory();
        }
        return null;
    }

    /** Current stock (count of the sale item) in the chest, or -1 if the chest is unavailable. */
    public int getStock(Shop shop) {
        if (!shop.isConfigured()) {
            return 0;
        }
        Inventory inv = getChestInventory(shop);
        if (inv == null) {
            return -1;
        }
        return InventoryUtil.countSimilar(inv, shop.getSaleItem());
    }

    /**
     * Highest number of units a buyer could take given the current stock. One unit
     * is one purchase: {@code saleItem.getAmount()} items for {@code price} currency.
     */
    public int maxUnitsInStock(Shop shop) {
        if (!shop.isConfigured()) {
            return 0;
        }
        int stock = getStock(shop);
        if (stock <= 0) {
            return 0;
        }
        int perUnit = Math.max(1, shop.getSaleItem().getAmount());
        return stock / perUnit;
    }

    /**
     * Attempt to purchase {@code units} units from the shop, sending the appropriate
     * MiniMessage feedback to the buyer. One unit is {@code saleItem.getAmount()} items
     * for {@code price} currency. Returns true on a successful purchase.
     */
    public boolean purchase(Player buyer, Shop shop, int units) {
        units = Math.max(1, units);

        if (!shop.isActive() || !shop.isConfigured()) {
            buyer.sendMessage(Msg.mm("<gray>⚠ Dieser Shop ist momentan inaktiv."));
            return false;
        }

        Inventory chestInv = getChestInventory(shop);
        if (chestInv == null) {
            buyer.sendMessage(Msg.mm("<red>✖ Dieser Shop hat keinen Vorrat mehr."));
            return false;
        }

        ItemStack sale = shop.getSaleItem();
        int perUnit = sale.getAmount();
        int needItems = perUnit * units;
        int stock = InventoryUtil.countSimilar(chestInv, sale);
        if (stock < needItems) {
            buyer.sendMessage(Msg.mm("<red>✖ Dieser Shop hat keinen Vorrat mehr."));
            return false;
        }

        Material currency = shop.getCurrency();
        int totalPrice = shop.getPrice() * units;
        int have = InventoryUtil.countMaterial(buyer.getInventory(), currency);
        if (have < totalPrice) {
            buyer.sendMessage(Msg.mm(
                    "<red>✖ Nicht genug <currency>. Benötigt: <required> | Vorhanden: <have>",
                    Placeholder.component("currency", new ItemStack(currency).effectiveName()),
                    Placeholder.unparsed("required", String.valueOf(totalPrice)),
                    Placeholder.unparsed("have", String.valueOf(have))
            ));
            return false;
        }

        // All checks passed — complete the exchange. The payment is recorded as a
        // counted income on the shop (not stored physically) for the owner to collect.
        InventoryUtil.removeMaterial(buyer.getInventory(), currency, totalPrice);
        InventoryUtil.removeSimilar(chestInv, sale, needItems);
        shop.addCollected(totalPrice);

        ItemStack give = sale.clone();
        give.setAmount(perUnit);
        for (int i = 0; i < units; i++) {
            Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(give.clone());
            for (ItemStack leftover : overflow.values()) {
                buyer.getWorld().dropItem(buyer.getLocation(), leftover);
            }
        }

        shops.save();

        Component currencyName = new ItemStack(currency).effectiveName();
        buyer.sendMessage(Msg.mm(
                "<green>✔ Kauf erfolgreich! <gold><amount>x <item></gold> für <gold><price>x <currency></gold>.",
                Placeholder.unparsed("amount", String.valueOf(needItems)),
                Placeholder.component("item", sale.effectiveName()),
                Placeholder.unparsed("price", String.valueOf(totalPrice)),
                Placeholder.component("currency", currencyName)
        ));
        return true;
    }

    /**
     * Pay out all collected income to the owner as currency items and reset the counter.
     * Returns the amount paid out (0 if there was nothing). Any items that don't fit the
     * owner's inventory are dropped at their feet so nothing is lost.
     */
    public int collectEarnings(Player owner, Shop shop) {
        int amount = shop.getCollected();
        if (amount <= 0) {
            return 0;
        }
        Material currency = shop.getCurrency();
        int maxStack = Math.max(1, currency.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            Map<Integer, ItemStack> overflow = owner.getInventory().addItem(new ItemStack(currency, give));
            for (ItemStack leftover : overflow.values()) {
                owner.getWorld().dropItem(owner.getLocation(), leftover);
            }
            remaining -= give;
        }
        shop.setCollected(0);
        shops.save();
        return amount;
    }
}
