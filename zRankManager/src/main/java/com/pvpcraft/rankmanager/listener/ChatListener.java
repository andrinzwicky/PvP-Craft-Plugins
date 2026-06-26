package com.pvpcraft.rankmanager.listener;

import com.pvpcraft.rankmanager.ClanHook;
import com.pvpcraft.rankmanager.Rank;
import com.pvpcraft.rankmanager.RankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Renders chat as "[Rank] Playername [ClanTag]: message" using Paper's
 * {@link AsyncChatEvent}. The exact layout is configurable via {@code chat.format}
 * in config.yml. The clan tag is shown only when ClanSystem publishes one (see
 * {@link ClanHook}); otherwise it collapses to nothing.
 */
public final class ChatListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DEFAULT_FORMAT = "<rank> <player><clan>: <message>";

    private final RankManager plugin;

    public ChatListener(RankManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Rank rank = plugin.getEffectiveRank(player);
        String format = plugin.getConfig().getString("chat.format", DEFAULT_FORMAT);

        // A ChatRenderer composes the final line per viewer. We ignore viewer
        // differences (everyone sees the same format) but use the renderer so we
        // play nicely with other chat plugins instead of overwriting the message.
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            TagResolver resolvers = TagResolver.resolver(
                    Placeholder.component("rank", rank.chatPrefix()),
                    Placeholder.component("player", Component.text(player.getName())),
                    Placeholder.component("clan", ClanHook.chatTag(player)),
                    // Pass the message as a component so player text is never parsed
                    // as MiniMessage (no formatting/tag injection from chat).
                    Placeholder.component("message", message));
            return MM.deserialize(format, resolvers);
        });
    }
}
