package com.pvpcraft.dimensioncontrol.listener;

import com.pvpcraft.dimensioncontrol.DimensionControl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Enforces disabled dimensions:
 *  - blocks nether portal travel and nether portal ignition,
 *  - blocks end portal entry,
 *  - and bounces players who log in inside a disabled dimension back to spawn.
 */
public final class PortalListener implements Listener {

    private final DimensionControl plugin;

    public PortalListener(DimensionControl plugin) {
        this.plugin = plugin;
    }

    /** Blocks travel through nether/end portals while the target is disabled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        TeleportCause cause = event.getCause();
        if (cause == TeleportCause.NETHER_PORTAL && !plugin.isNetherEnabled()) {
            event.setCancelled(true);
            plugin.send(event.getPlayer(), plugin.netherDisabledMessage());
        } else if (cause == TeleportCause.END_PORTAL && !plugin.isEndEnabled()) {
            event.setCancelled(true);
            plugin.send(event.getPlayer(), plugin.endDisabledMessage());
        }
    }

    /** Blocks lighting a new nether portal while the Nether is disabled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.FIRE && !plugin.isNetherEnabled()) {
            event.setCancelled(true);
            Entity creator = event.getEntity();
            if (creator instanceof Player player) {
                plugin.send(player, plugin.netherDisabledMessage());
            }
        }
    }

    /** Moves players who join inside a now-disabled dimension back to the overworld. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World.Environment env = player.getWorld().getEnvironment();
        boolean blocked = (env == World.Environment.NETHER && !plugin.isNetherEnabled())
                || (env == World.Environment.THE_END && !plugin.isEndEnabled());
        if (!blocked) {
            return;
        }
        Location spawn = plugin.overworldSpawn();
        if (spawn != null) {
            player.teleport(spawn);
            plugin.send(player, env == World.Environment.NETHER
                    ? plugin.netherDisabledMessage()
                    : plugin.endDisabledMessage());
        }
    }
}
