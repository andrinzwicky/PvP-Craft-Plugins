package com.pvpcraft.rankmanager.listener;

import com.pvpcraft.rankmanager.RankManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final RankManager plugin;

    public PlayerListener(RankManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.trackOpState(player);
        // Assigns default rank if none stored, grants Owner if op, sets tab list.
        plugin.reapply(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPermissionManager().clear(player);
        plugin.getTabListManager().removeFromSortTeam(player);
        plugin.untrackOpState(player.getUniqueId());
    }
}
