package com.pvpcraft.watch;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * UUID-based persistence of active watch sessions in {@code watch.yml}. The
 * file is the source of truth for "this watcher is mid-watch and still needs
 * their location/gamemode restored" — used to recover cleanly when a watcher
 * logs out (or the server restarts) while watching.
 *
 * <pre>
 * sessions:
 *   &lt;watcher-uuid&gt;:
 *     target: &lt;target-uuid&gt;
 *     gamemode: SURVIVAL
 *     world: world
 *     x: 0.0
 *     y: 64.0
 *     z: 0.0
 *     yaw: 0.0
 *     pitch: 0.0
 * </pre>
 */
public final class WatchStorage {

    private final Watch plugin;
    private final File file;
    private FileConfiguration config;

    public WatchStorage(Watch plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "watch.yml");
    }

    public void init() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Konnte den Datenordner nicht erstellen.");
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    /** Persists (or overwrites) the restore data for one watcher. */
    public void save(WatchSession session) {
        String base = "sessions." + session.getWatcher();
        config.set(base + ".target", session.getTarget().toString());
        config.set(base + ".gamemode", session.getOriginalGameMode().name());

        Location l = session.getOriginalLocation();
        if (l != null && l.getWorld() != null) {
            config.set(base + ".world", l.getWorld().getName());
            config.set(base + ".x", l.getX());
            config.set(base + ".y", l.getY());
            config.set(base + ".z", l.getZ());
            config.set(base + ".yaw", l.getYaw());
            config.set(base + ".pitch", l.getPitch());
        }
        write();
    }

    /** Removes the stored entry for a watcher (after they have been restored). */
    public void remove(UUID watcher) {
        config.set("sessions." + watcher, null);
        write();
    }

    /** Reconstructs the stored session for a watcher, or {@code null} if none. */
    public WatchSession load(UUID watcher) {
        String base = "sessions." + watcher;
        if (!config.contains(base)) {
            return null;
        }
        try {
            UUID target = UUID.fromString(config.getString(base + ".target"));
            GameMode gamemode = GameMode.valueOf(config.getString(base + ".gamemode", "SURVIVAL"));

            String worldName = config.getString(base + ".world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            Location loc = null;
            if (world != null) {
                loc = new Location(
                        world,
                        config.getDouble(base + ".x"),
                        config.getDouble(base + ".y"),
                        config.getDouble(base + ".z"),
                        (float) config.getDouble(base + ".yaw"),
                        (float) config.getDouble(base + ".pitch"));
            }
            return new WatchSession(watcher, target, loc, gamemode);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Ungueltiger watch.yml-Eintrag fuer " + watcher, ex);
            return null;
        }
    }

    /** All watcher UUIDs currently stored. */
    public Set<UUID> watchers() {
        ConfigurationSection section = config.getConfigurationSection("sessions");
        if (section == null) {
            return Collections.emptySet();
        }
        Set<UUID> out = new HashSet<>();
        for (String key : section.getKeys(false)) {
            try {
                out.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // skip malformed key
            }
        }
        return out;
    }

    private void write() {
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Konnte watch.yml nicht speichern.", ex);
        }
    }
}
