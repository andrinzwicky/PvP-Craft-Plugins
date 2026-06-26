package com.pvpcraft.shopchest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Builds the plugin's tagged items (configurator, selector) and GUI icons. */
public final class ItemUtil {

    private ItemUtil() {
    }

    /** Build the "Shop Konfigurator" item, tagged in its persistent data container. */
    public static ItemStack configurator(Keys keys, Material material, int customModelData) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Msg.mm("<gold>⚙ Shop Konfigurator").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Msg.mm("<gray>Rechtsklick auf eine Truhe").decoration(TextDecoration.ITALIC, false),
                    Msg.mm("<gray>um einen Shop einzurichten.").decoration(TextDecoration.ITALIC, false),
                    Msg.mm("<dark_gray>⚠ Nur auf Shop-Plots nutzbar.").decoration(TextDecoration.ITALIC, false)
            ));
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            meta.getPersistentDataContainer().set(keys.configurator, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isConfigurator(Keys keys, ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(keys.configurator, PersistentDataType.BYTE);
    }

    /** Build the WorldEdit-style plot selection wand. */
    public static ItemStack selector(Keys keys, Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Shop Plot Selektor")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Msg.mm("<gray>Linksklick</gray> <dark_gray>=</dark_gray> <yellow>Position 1</yellow>").decoration(TextDecoration.ITALIC, false),
                    Msg.mm("<gray>Rechtsklick</gray> <dark_gray>=</dark_gray> <yellow>Position 2</yellow>").decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(keys.selector, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isSelector(Keys keys, ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(keys.selector, PersistentDataType.BYTE);
    }

    /** Build a non-italic named GUI icon with lore. */
    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> cleaned = new ArrayList<>();
            for (Component line : lore) {
                cleaned.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(cleaned);
        });
        return item;
    }
}
