package com.pvpcraft.baseprotect;

import com.pvpcraft.baseprotect.command.BaseCommand;
import com.pvpcraft.baseprotect.integration.WorldGuardService;
import com.pvpcraft.baseprotect.manager.BaseManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BaseProtect - WorldGuard-backed player bases.
 *
 * <p>Bases are protected via WorldGuard regions named {@code base_<id>} (a
 * namespace strictly separate from ShopChest's {@code plot_} regions).
 * BaseProtect keeps its own owner/leader/member hierarchy in {@code bases.yml}
 * and mirrors every role into the region's member list so the protection flags
 * never apply to base members.
 */
public class BaseProtectPlugin extends JavaPlugin {

    private BaseManager baseManager;
    private WorldGuardService worldGuard;

    @Override
    public void onEnable() {
        this.worldGuard = new WorldGuardService(this);
        this.worldGuard.initialize();

        this.baseManager = new BaseManager(this);
        this.baseManager.load();

        registerCommand("base", new BaseCommand(this));

        getLogger().info("BaseProtect enabled.");
    }

    @Override
    public void onDisable() {
        if (baseManager != null) {
            baseManager.save();
        }
        getLogger().info("BaseProtect disabled.");
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

    public BaseManager bases() {
        return baseManager;
    }

    public WorldGuardService worldGuard() {
        return worldGuard;
    }
}
