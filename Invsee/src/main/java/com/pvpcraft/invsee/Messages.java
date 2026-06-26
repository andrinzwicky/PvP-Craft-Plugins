package com.pvpcraft.invsee;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Central MiniMessage templates and a small send helper. Every message carries
 * the {@code [Invsee]} prefix so the plugin name is always visible.
 */
public final class Messages {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    /** Prefix prepended to every message. */
    public static final String PREFIX = "<dark_gray>[<gold>Invsee<dark_gray>] ";

    public static final String NO_PERMISSION =
            PREFIX + "<red>✗ Du hast keine Berechtigung.";
    public static final String NOT_FOUND =
            PREFIX + "<red>✗ Spieler nicht gefunden.";
    public static final String OFFLINE =
            PREFIX + "<red>✗ Dieser Spieler ist offline.";
    public static final String USAGE =
            PREFIX + "<gray>➤ Verwendung: <gold>/<command> <player>";
    public static final String OPENED_INVENTORY =
            PREFIX + "<gray>➤ Inventar von <gold><player><gray> geöffnet.";
    public static final String OPENED_ENDERCHEST =
            PREFIX + "<gray>➤ Enderchest von <gold><player><gray> geöffnet.";
    public static final String TARGET_LEFT =
            PREFIX + "<red>⚠ <player> hat den Server verlassen.";

    private Messages() {
    }

    /** Deserializes {@code template} (with optional resolvers) and sends it. */
    public static void send(CommandSender to, String template, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(template, resolvers));
    }

    /** Convenience resolver for the {@code <player>} placeholder. */
    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }
}
