package com.pvpcraft.dimensioncontrol;

import com.pvpcraft.dimensioncontrol.command.DimensionCommand;
import com.pvpcraft.dimensioncontrol.listener.PortalListener;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lets admins enable/disable the Nether and the End at runtime. While a
 * dimension is disabled, its portals are blocked and any player inside it is
 * moved to the overworld spawn. State lives in config.yml and survives restarts.
 */
public final class DimensionControl extends JavaPlugin {

    private boolean netherEnabled = true;
    private boolean endEnabled = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadState();

        DimensionCommand command = new DimensionCommand(this);
        PluginCommand pluginCommand = getCommand("dimension");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'dimension' missing from plugin.yml!");
        }

        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        getLogger().info("DimensionControl enabled (Nether="
                + netherEnabled + ", End=" + endEnabled + ").");
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** (Re)reads the enabled flags from config.yml. */
    public void loadState() {
        reloadConfig();
        netherEnabled = getConfig().getBoolean("nether-enabled", true);
        endEnabled = getConfig().getBoolean("end-enabled", true);
    }

    public boolean isNetherEnabled() {
        return netherEnabled;
    }

    public boolean isEndEnabled() {
        return endEnabled;
    }

    /** Enables/disables the Nether, persists it, and evacuates it if disabling. */
    public void setNetherEnabled(boolean enabled) {
        this.netherEnabled = enabled;
        getConfig().set("nether-enabled", enabled);
        saveConfig();
        if (!enabled) {
            evacuate(World.Environment.NETHER, netherDisabledMessage());
        }
    }

    /** Enables/disables the End, persists it, and evacuates it if disabling. */
    public void setEndEnabled(boolean enabled) {
        this.endEnabled = enabled;
        getConfig().set("end-enabled", enabled);
        saveConfig();
        if (!enabled) {
            evacuate(World.Environment.THE_END, endDisabledMessage());
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    public String netherDisabledMessage() {
        return getConfig().getString("nether-disabled-message", "The Nether is currently disabled.");
    }

    public String endDisabledMessage() {
        return getConfig().getString("end-disabled-message", "The End is currently disabled.");
    }

    /** Sends a message, interpreting legacy '&' colour codes. */
    public void send(CommandSender to, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        to.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    // ------------------------------------------------------------------
    // World helpers
    // ------------------------------------------------------------------

    /** Spawn location of the first NORMAL (overworld) world, or null if none. */
    public Location overworldSpawn() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world.getSpawnLocation();
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /** Teleports every player currently in {@code env} worlds to the overworld. */
    public void evacuate(World.Environment env, String message) {
        Location spawn = overworldSpawn();
        if (spawn == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != env) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                player.teleport(spawn);
                send(player, message);
            }
        }
    }
}
