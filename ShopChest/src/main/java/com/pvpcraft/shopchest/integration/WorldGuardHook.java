package com.pvpcraft.shopchest.integration;

import com.pvpcraft.shopchest.model.Plot;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Keeps WorldGuard region membership in sync with ShopChest plot ownership.
 *
 * <p>Each plot with id {@code <id>} maps to a WorldGuard region named
 * {@code plot_<id>}. Both the plot owner and its invited members are mirrored as
 * <em>members</em> of that region, by UUID.
 *
 * <p>WorldGuard is a soft dependency. If it is not installed (or fails to load)
 * every sync call becomes a silent no-op — the plugin never crashes. To keep the
 * WorldGuard classes from being loaded when the plugin is absent, all direct API
 * access lives in {@link #applyMembership} which is only ever reached while
 * {@link #enabled} is {@code true}.
 */
public class WorldGuardHook {

    private final Plugin plugin;
    private boolean enabled;

    public WorldGuardHook(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Detect WorldGuard once, on startup. Safe to call when WG is absent. */
    public void initialize() {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            enabled = false;
            plugin.getLogger().info("WorldGuard not found - region sync disabled.");
            return;
        }
        try {
            // Touch a core class to confirm the API is actually on the classpath.
            Class.forName("com.sk89q.worldguard.WorldGuard");
            enabled = true;
            plugin.getLogger().info("WorldGuard detected - plot region sync enabled.");
        } catch (Throwable t) {
            enabled = false;
            plugin.getLogger().warning("WorldGuard present but its API could not be loaded - region sync disabled.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Mirror {@code uuid} into the plot's region member list. No-op if WG is absent. */
    public void addMember(Plot plot, UUID uuid) {
        if (!enabled || plot == null || uuid == null) {
            return;
        }
        applyMembership(plot, uuid, true);
    }

    /** Remove {@code uuid} from the plot's region member list. No-op if WG is absent. */
    public void removeMember(Plot plot, UUID uuid) {
        if (!enabled || plot == null || uuid == null) {
            return;
        }
        applyMembership(plot, uuid, false);
    }

    /**
     * Mirror an ownership change: drop the previous owner, add the new one.
     * Either UUID may be {@code null} (no previous owner / no new owner).
     */
    public void syncOwnerChange(Plot plot, UUID oldOwner, UUID newOwner) {
        if (!enabled || plot == null) {
            return;
        }
        if (oldOwner != null && !oldOwner.equals(newOwner)) {
            applyMembership(plot, oldOwner, false);
        }
        if (newOwner != null) {
            applyMembership(plot, newOwner, true);
        }
    }

    /** The region name a plot maps to: {@code plot_<id>} (lower-cased). */
    public static String regionName(Plot plot) {
        return "plot_" + plot.getId().toLowerCase(Locale.ROOT);
    }

    // --- WorldGuard API access (only reached when enabled) ---------------

    private void applyMembership(Plot plot, UUID uuid, boolean add) {
        try {
            World world = Bukkit.getWorld(plot.getWorld());
            if (world == null) {
                plugin.getLogger().warning("Cannot sync region for plot '" + plot.getId()
                        + "': world '" + plot.getWorld() + "' is not loaded.");
                return;
            }

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions == null) {
                plugin.getLogger().warning("Cannot sync region for plot '" + plot.getId()
                        + "': no region manager for world '" + plot.getWorld() + "'.");
                return;
            }

            String name = regionName(plot);
            ProtectedRegion region = regions.getRegion(name);
            if (region == null) {
                plugin.getLogger().warning("WorldGuard region '" + name
                        + "' does not exist - skipping " + (add ? "add" : "remove")
                        + " of member " + uuid + ".");
                return;
            }

            DefaultDomain members = region.getMembers();
            if (add) {
                members.addPlayer(uuid);
            } else {
                members.removePlayer(uuid);
            }
        } catch (Throwable t) {
            // Never let a region-sync failure break the ShopChest operation.
            plugin.getLogger().warning("WorldGuard region sync failed for plot '"
                    + plot.getId() + "': " + t.getMessage());
        }
    }
}
