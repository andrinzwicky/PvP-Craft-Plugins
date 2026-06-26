package com.pvpcraft.spawnelytra.zone;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads, stores and persists launch {@link Zone}s in the plugin's config.yml.
 * Zone names are matched case-insensitively. Both spherical and cuboid zones are
 * supported; the {@code type} field decides which (missing = sphere for
 * backwards compatibility with older configs).
 */
public final class ZoneManager {

    private final JavaPlugin plugin;
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public ZoneManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads all zones from config.yml. */
    public void load() {
        zones.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                continue;
            }
            Zone zone = readZone(name, sec);
            if (zone != null) {
                zones.put(name.toLowerCase(Locale.ROOT), zone);
            }
        }
    }

    private Zone readZone(String name, ConfigurationSection sec) {
        String world = sec.getString("world", "world");
        String type = sec.getString("type", "sphere");
        if (type.equalsIgnoreCase("cuboid")) {
            return new CuboidZone(name, world,
                    sec.getDouble("min-x"), sec.getDouble("min-y"), sec.getDouble("min-z"),
                    sec.getDouble("max-x"), sec.getDouble("max-y"), sec.getDouble("max-z"));
        }
        return new SphereZone(name, world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                sec.getDouble("radius", 10.0));
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    /** The first zone containing {@code loc}, or {@code null} if none does. */
    public Zone zoneAt(Location loc) {
        for (Zone zone : zones.values()) {
            if (zone.contains(loc)) {
                return zone;
            }
        }
        return null;
    }

    /** Creates or replaces a spherical zone centred on {@code loc} and saves. */
    public void setSphereZone(String name, Location loc, double radius) {
        save(new SphereZone(name, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), radius));
    }

    /** Creates or replaces a cuboid zone from two corner points and saves. */
    public void setCuboidZone(String name, Location a, Location b) {
        save(CuboidZone.of(name, a.getWorld().getName(), a, b));
    }

    private void save(Zone zone) {
        zones.put(zone.name().toLowerCase(Locale.ROOT), zone);
        // Clear any previous data so switching a zone's shape leaves no stale keys.
        plugin.getConfig().set("zones." + zone.name(), null);
        ConfigurationSection sec = plugin.getConfig().createSection("zones." + zone.name());
        zone.write(sec);
        plugin.saveConfig();
    }

    /** Removes a zone (if present) and saves the config. */
    public boolean removeZone(String name) {
        Zone removed = zones.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        plugin.getConfig().set("zones." + removed.name(), null);
        plugin.saveConfig();
        return true;
    }
}
