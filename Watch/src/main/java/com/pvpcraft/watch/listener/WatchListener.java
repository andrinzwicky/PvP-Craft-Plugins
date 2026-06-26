package com.pvpcraft.watch.listener;

import com.pvpcraft.watch.Watch;
import com.pvpcraft.watch.WatchManager;
import com.pvpcraft.watch.WatchSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Wires the watch sessions into player lifecycle and movement events:
 * <ul>
 *   <li>instant boundary clamping while a watcher moves,</li>
 *   <li>ending a watch when the target leaves,</li>
 *   <li>keeping a session stored when a watcher leaves, and</li>
 *   <li>restoring a watcher's gamemode + location when they rejoin.</li>
 * </ul>
 */
public final class WatchListener implements Listener {

    private final Watch plugin;
    private final WatchManager manager;

    public WatchListener(Watch plugin, WatchManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        WatchSession session = manager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        // Only react to actual position changes, not look-only movement.
        if (to == null
                || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player target = Bukkit.getPlayer(session.getTarget());
        if (target == null || !target.isOnline()) {
            return; // tick() ends the session
        }
        Location clamped = manager.clampToRadius(to, target.getLocation());
        if (clamped != null) {
            event.setTo(clamped);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // If the leaver is a watcher, keep their stored session for rejoin restore.
        if (manager.isWatching(uuid)) {
            manager.handleWatcherQuit(uuid);
        }
        // If the leaver is being watched, end those sessions and restore watchers.
        manager.handleTargetQuit(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Restore one tick later so the join/teleport settles first.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                manager.handleWatcherJoin(player);
            }
        });
    }
}
