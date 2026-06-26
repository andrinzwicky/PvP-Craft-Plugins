package com.pvpcraft.spawn.listener;

import com.pvpcraft.spawn.SpawnTP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Cancels a pending /spawn warmup when the player moves, takes damage or quits. */
public final class CancelListener implements Listener {

    private final SpawnTP plugin;

    public CancelListener(SpawnTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.warmups().isPending(player.getUniqueId())) {
            return;
        }
        // Only an actual change of horizontal block position counts as moving;
        // looking around and jumping in place are fine.
        if (plugin.warmups().hasMovedFromStart(player.getUniqueId(), event.getTo())) {
            plugin.warmups().cancel(player, "cancelled-move");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.warmups().isPending(player.getUniqueId())) {
            plugin.warmups().cancel(player, "cancelled-damage");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.warmups().abort(event.getPlayer().getUniqueId());
    }
}
