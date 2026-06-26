package com.pvpcraft.shopchest.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Holds the plugin's persistent-data keys for tagged items. */
public final class Keys {

    public final NamespacedKey configurator;
    public final NamespacedKey selector;
    public final NamespacedKey configuratorRecipe;

    public Keys(Plugin plugin) {
        this.configurator = new NamespacedKey(plugin, "configurator");
        this.selector = new NamespacedKey(plugin, "selector");
        this.configuratorRecipe = new NamespacedKey(plugin, "configurator_recipe");
    }
}
