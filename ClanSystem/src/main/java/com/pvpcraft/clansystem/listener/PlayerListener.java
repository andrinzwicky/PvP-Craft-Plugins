package com.pvpcraft.clansystem.listener;

import com.pvpcraft.clansystem.ClanSystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final ClanSystem plugin;

    public PlayerListener(ClanSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // RankManager (also MONITOR) sets its "[Rank] Player" tab name during this
        // event. Metadata is written immediately; the tab name is reapplied one
        // tick later so we read RankManager's name back and append the clan tag.
        plugin.getDisplayManager().handleJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Metadata is per-online-player and is dropped automatically when the
        // player object is unloaded, but clear it explicitly to be tidy.
        plugin.getDisplayManager().clearMetadata(event.getPlayer());
        // Tidy up the fallback nametag team (no-op when RankManager owns the team).
        plugin.getDisplayManager().removeNametag(event.getPlayer());
    }
}
