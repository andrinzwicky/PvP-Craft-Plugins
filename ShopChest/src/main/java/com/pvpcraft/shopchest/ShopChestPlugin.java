package com.pvpcraft.shopchest;

import com.pvpcraft.shopchest.command.ShopCommand;
import com.pvpcraft.shopchest.command.ShopConfiguratorCommand;
import com.pvpcraft.shopchest.command.ShopPlotCommand;
import com.pvpcraft.shopchest.listener.ChatListener;
import com.pvpcraft.shopchest.listener.GuiListener;
import com.pvpcraft.shopchest.listener.InteractListener;
import com.pvpcraft.shopchest.listener.ProtectionListener;
import com.pvpcraft.shopchest.integration.WorldGuardHook;
import com.pvpcraft.shopchest.manager.PlotManager;
import com.pvpcraft.shopchest.manager.SessionManager;
import com.pvpcraft.shopchest.manager.ShopManager;
import com.pvpcraft.shopchest.manager.ShopService;
import com.pvpcraft.shopchest.util.ItemUtil;
import com.pvpcraft.shopchest.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ShopChestPlugin extends JavaPlugin {

    private Keys keys;
    private PlotManager plotManager;
    private ShopManager shopManager;
    private ShopService shopService;
    private SessionManager sessionManager;
    private WorldGuardHook worldGuard;

    private List<Material> currencies;
    private Material configuratorMaterial;
    private int configuratorModelData;
    private Material selectorMaterial;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.keys = new Keys(this);
        loadSettings();

        this.plotManager = new PlotManager(this);
        this.shopManager = new ShopManager(this);
        this.shopService = new ShopService(shopManager);
        this.sessionManager = new SessionManager();
        this.worldGuard = new WorldGuardHook(this);
        this.worldGuard.initialize();

        plotManager.load();
        shopManager.load();

        registerConfiguratorRecipe();

        registerCommand("shopplot", new ShopPlotCommand(this));
        registerCommand("shopconfigurator", new ShopConfiguratorCommand(this));
        registerCommand("shop", new ShopCommand(this));

        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("ShopChest enabled.");
    }

    @Override
    public void onDisable() {
        if (keys != null) {
            Bukkit.removeRecipe(keys.configuratorRecipe);
        }
        if (plotManager != null) {
            plotManager.save();
        }
        if (shopManager != null) {
            shopManager.save();
        }
        getLogger().info("ShopChest disabled.");
    }

    /** Register the shaped crafting recipe for the Shop Konfigurator (8 iron ingots around a chest). */
    private void registerConfiguratorRecipe() {
        ItemStack result = ItemUtil.configurator(keys, configuratorMaterial, configuratorModelData);
        // Remove any stale recipe first so re-enabling / reloading doesn't throw.
        Bukkit.removeRecipe(keys.configuratorRecipe);
        ShapedRecipe recipe = new ShapedRecipe(keys.configuratorRecipe, result);
        recipe.shape("III", "ICI", "III");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('C', Material.CHEST);
        Bukkit.addRecipe(recipe);
    }

    private void loadSettings() {
        reloadConfig();
        configuratorMaterial = matOrDefault(getConfig().getString("configurator.material"), Material.COMPARATOR);
        configuratorModelData = getConfig().getInt("configurator.custom-model-data", 1001);
        selectorMaterial = matOrDefault(getConfig().getString("selector.material"), Material.GOLDEN_SHOVEL);

        currencies = new ArrayList<>();
        for (String name : getConfig().getStringList("currencies")) {
            Material mat = Material.matchMaterial(name);
            if (mat != null && mat.isItem()) {
                currencies.add(mat);
            } else {
                getLogger().warning("Unknown currency material in config: " + name);
            }
        }
        if (currencies.isEmpty()) {
            currencies.add(Material.DIAMOND);
            currencies.add(Material.DIAMOND_BLOCK);
        }
    }

    private Material matOrDefault(String name, Material def) {
        if (name == null) {
            return def;
        }
        Material mat = Material.matchMaterial(name);
        return (mat != null && mat.isItem()) ? mat : def;
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' missing from plugin.yml!");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            command.setExecutor(ce);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            command.setTabCompleter(tc);
        }
    }

    // --- accessors -------------------------------------------------------

    public Keys keys() {
        return keys;
    }

    public PlotManager plots() {
        return plotManager;
    }

    public ShopManager shops() {
        return shopManager;
    }

    public ShopService shopService() {
        return shopService;
    }

    public SessionManager sessions() {
        return sessionManager;
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public List<Material> currencies() {
        return currencies;
    }

    public Material configuratorMaterial() {
        return configuratorMaterial;
    }

    public int configuratorModelData() {
        return configuratorModelData;
    }

    public Material selectorMaterial() {
        return selectorMaterial;
    }
}
