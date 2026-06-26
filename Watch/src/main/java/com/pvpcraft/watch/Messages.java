package com.pvpcraft.watch;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Central MiniMessage templates and a small send helper. Every message carries
 * the {@code [Watch]} prefix so the plugin name is always visible.
 */
public final class Messages {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    /** Prefix prepended to every message. */
    public static final String PREFIX = "<dark_gray>[<gold>Watch<dark_gray>] ";

    public static final String PLAYER_ONLY =
            PREFIX + "<red>✗ Dieser Befehl kann nur von Spielern genutzt werden.";
    public static final String NO_PERMISSION =
            PREFIX + "<red>✗ Du hast keine Berechtigung.";
    public static final String USAGE =
            PREFIX + "<gray>➤ Verwendung: <gold>/watch <spieler>";
    public static final String NOT_FOUND =
            PREFIX + "<red>✗ Spieler nicht gefunden.";
    public static final String CANNOT_WATCH_SELF =
            PREFIX + "<red>✗ Du kannst dich nicht selbst beobachten.";
    public static final String TARGET_IS_WATCHING =
            PREFIX + "<red>✗ Dieser Spieler beobachtet gerade selbst jemanden.";
    public static final String CANNOT_WATCH_WHILE_SITTING =
            PREFIX + "<red>✗ Du kannst nicht beobachten während du sitzt/liegst/kriechst.";
    public static final String WATCH_STARTED =
            PREFIX + "<gray>Du beobachtest jetzt <gold><player><gray> (Radius 100 Blöcke).";
    public static final String WATCH_SWITCHED =
            PREFIX + "<gray>Du beobachtest jetzt <gold><player><gray>.";
    public static final String NOT_WATCHING =
            PREFIX + "<red>✗ Du beobachtest derzeit niemanden.";
    public static final String WATCH_STOPPED =
            PREFIX + "<gray>Beobachtung beendet.";
    public static final String TARGET_LEFT_SERVER =
            PREFIX + "<gray>Der beobachtete Spieler hat den Server verlassen.";

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
