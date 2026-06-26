package com.pvpcraft.spawnelytra;

import com.pvpcraft.spawnelytra.command.SpawnElytraCommand;
import com.pvpcraft.spawnelytra.listener.ElytraListeners;
import com.pvpcraft.spawnelytra.selection.SelectionManager;
import com.pvpcraft.spawnelytra.zone.ZoneManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SpawnElytra turns configurable "launch zones" into elytra launch pads. When a
 * player walks into a zone their real chestplate is stashed away and replaced
 * with a uniquely tagged Elytra that is locked into the chestplate slot. Once
 * they glide and land again the Elytra is removed and the original chestplate is
 * handed back. Flights survive reconnects via UUID-based state in players.yml.
 *
 * <p>Tagging uses a {@link NamespacedKey} ("spawn_elytra_id") on the item's
 * {@link org.bukkit.persistence.PersistentDataContainer}; no scoreboards, lore
 * parsing or NMS are involved, so the plugin is compatible with
 * {@code -Dpaper.disablePluginRemapping=true}.
 */
public final class SpawnElytra extends JavaPlugin {

    /** Prefix prepended to every chat message. */
    public static final String PREFIX = "<gray>[<aqua>SpawnElytra</aqua><gray>]</gray> ";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private NamespacedKey elytraKey;
    private ZoneManager zones;
    private LaunchManager launches;
    private SelectionManager selection;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.elytraKey = new NamespacedKey(this, "spawn_elytra_id");
        this.zones = new ZoneManager(this);
        this.zones.load();
        this.launches = new LaunchManager(this, elytraKey, zones);
        this.selection = new SelectionManager(new NamespacedKey(this, "spawn_elytra_wand"));

        getServer().getPluginManager().registerEvents(
                new ElytraListeners(this, zones, launches, selection), this);

        PluginCommand command = getCommand("spawnelytra");
        if (command != null) {
            SpawnElytraCommand executor = new SpawnElytraCommand(this, zones, launches, selection);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'spawnelytra' missing from plugin.yml!");
        }

        // Restore any players that were mid-flight across a reload.
        getServer().getOnlinePlayers().forEach(launches::restoreOnJoin);

        getLogger().info("SpawnElytra enabled with " + zones.all().size() + " launch zone(s).");
    }

    @Override
    public void onDisable() {
        if (launches != null) {
            launches.persistAllOnDisable();
        }
    }

    public ZoneManager zones() {
        return zones;
    }

    public LaunchManager launches() {
        return launches;
    }

    public SelectionManager selection() {
        return selection;
    }

    // --- messaging -------------------------------------------------------

    /** Sends a prefixed MiniMessage line. */
    public void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(PREFIX + miniMessage));
    }

    /** Sends a MiniMessage line without the prefix (e.g. list entries). */
    public void sendRaw(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(miniMessage));
    }
}
