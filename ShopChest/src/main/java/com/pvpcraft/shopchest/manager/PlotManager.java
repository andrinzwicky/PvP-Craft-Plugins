package com.pvpcraft.shopchest.manager;

import com.pvpcraft.shopchest.model.Plot;
import com.pvpcraft.shopchest.util.RankUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Loads, stores and queries shop plots. Backed by plots.yml. */
public class PlotManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Plot> plots = new HashMap<>();

    // Transient WorldEdit-style selection state per player.
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public PlotManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    // --- selection -------------------------------------------------------

    public void setPos1(UUID player, Location loc) {
        pos1.put(player, loc);
    }

    public void setPos2(UUID player, Location loc) {
        pos2.put(player, loc);
    }

    public Location getPos1(UUID player) {
        return pos1.get(player);
    }

    public Location getPos2(UUID player) {
        return pos2.get(player);
    }

    public boolean hasSelection(UUID player) {
        return pos1.containsKey(player) && pos2.containsKey(player);
    }

    public void clearSelection(UUID player) {
        pos1.remove(player);
        pos2.remove(player);
    }

    // --- plot CRUD -------------------------------------------------------

    public Plot getPlot(String id) {
        return plots.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return plots.containsKey(id.toLowerCase());
    }

    public Collection<Plot> getPlots() {
        return plots.values();
    }

    public void addPlot(Plot plot) {
        plots.put(plot.getId().toLowerCase(), plot);
        save();
    }

    public Plot removePlot(String id) {
        Plot removed = plots.remove(id.toLowerCase());
        if (removed != null) {
            save();
        }
        return removed;
    }

    /** First plot whose cuboid contains the location, or null. */
    public Plot getPlotAt(Location loc) {
        for (Plot plot : plots.values()) {
            if (plot.contains(loc)) {
                return plot;
            }
        }
        return null;
    }

    public List<Plot> getPlotsOwnedBy(UUID uuid) {
        List<Plot> result = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.isOwner(uuid)) {
                result.add(plot);
            }
        }
        return result;
    }

    public List<Plot> getPlotsWithMember(UUID uuid) {
        List<Plot> result = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.isMember(uuid)) {
                result.add(plot);
            }
        }
        return result;
    }

    /**
     * Whether {@code player} may build, break and configure shop chests within
     * {@code plot}. The check order mirrors the plot permission hierarchy: a staff
     * rank bypass first, then the plot owner, then an invited member; everyone else
     * is denied. A {@code null} plot grants nothing here — the caller decides what
     * happens outside any plot (normally: vanilla behaviour, no restriction).
     */
    public boolean canModify(Player player, Plot plot) {
        if (RankUtil.hasBypass(player)) {
            return true;
        }
        if (plot == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        return plot.isOwner(uuid) || plot.isMember(uuid);
    }

    // --- persistence -----------------------------------------------------

    public void load() {
        plots.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("plots");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            try {
                String world = sec.getString("world", "world");
                int x1 = sec.getInt("pos1.x");
                int y1 = sec.getInt("pos1.y");
                int z1 = sec.getInt("pos1.z");
                int x2 = sec.getInt("pos2.x");
                int y2 = sec.getInt("pos2.y");
                int z2 = sec.getInt("pos2.z");
                String ownerStr = sec.getString("owner");
                UUID owner = (ownerStr == null || ownerStr.isEmpty()) ? null : UUID.fromString(ownerStr);
                Plot plot = new Plot(id, world, x1, y1, z1, x2, y2, z2, owner);
                for (String m : sec.getStringList("members")) {
                    try {
                        plot.getMembers().add(UUID.fromString(m));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                plots.put(id.toLowerCase(), plot);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid plot '" + id + "'", ex);
            }
        }
        plugin.getLogger().info("Loaded " + plots.size() + " plot(s).");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Plot plot : plots.values()) {
            String base = "plots." + plot.getId();
            cfg.set(base + ".world", plot.getWorld());
            cfg.set(base + ".pos1.x", plot.getMinX());
            cfg.set(base + ".pos1.y", plot.getMinY());
            cfg.set(base + ".pos1.z", plot.getMinZ());
            cfg.set(base + ".pos2.x", plot.getMaxX());
            cfg.set(base + ".pos2.y", plot.getMaxY());
            cfg.set(base + ".pos2.z", plot.getMaxZ());
            cfg.set(base + ".owner", plot.getOwner() == null ? "" : plot.getOwner().toString());
            List<String> members = new ArrayList<>();
            for (UUID m : plot.getMembers()) {
                members.add(m.toString());
            }
            cfg.set(base + ".members", members);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save plots.yml", ex);
        }
    }
}
