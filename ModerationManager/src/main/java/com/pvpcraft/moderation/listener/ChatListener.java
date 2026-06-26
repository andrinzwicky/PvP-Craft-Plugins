package com.pvpcraft.moderation.listener;

import com.pvpcraft.moderation.ModerationManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Blocks public chat for muted players. */
public final class ChatListener implements Listener {

    private final ModerationManager plugin;

    public ChatListener(ModerationManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.mutes().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.sendMutedNotice(player);
        }
    }
}
