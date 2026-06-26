package com.pvpcraft.clansystem.listener;

import com.pvpcraft.clansystem.ClanSystem;
import com.pvpcraft.clansystem.display.DisplayManager;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Renders chat as "[Rank] Player [Tag]: message". The prefix is built once when
 * the event fires (the clan/rank lookups are concurrency-safe) and then reused
 * for every viewer by the renderer.
 */
public final class ChatListener implements Listener {

    private final ClanSystem plugin;

    public ChatListener(ClanSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        DisplayManager display = plugin.getDisplayManager();
        // Built now, on the async chat thread; ClanManager's indexes are
        // concurrent and the rank hook only reads, so this is safe.
        Component prefix = display.displayName(event.getPlayer());

        ChatRenderer renderer = (source, sourceDisplayName, message, viewer) ->
                prefix.append(Component.text(": ")).append(message);
        event.renderer(renderer);
    }
}
