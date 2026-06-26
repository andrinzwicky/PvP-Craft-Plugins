package com.pvpcraft.clansystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Central place for all user-facing text. Everything is MiniMessage and sent as
 * an Adventure {@link Component}. Placeholders are inserted as <em>unparsed</em>
 * so player- or clan-supplied text can never inject MiniMessage tags.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Messages() {
    }

    /** Deserialize a raw MiniMessage string with the given resolvers. */
    public static Component mm(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers);
    }

    public static void send(CommandSender to, String raw, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(raw, resolvers));
    }

    /** Unparsed placeholder, e.g. {@code p("name", clan.name())}. */
    public static TagResolver p(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    // --- Spec messages -------------------------------------------------------
    public static final String CLAN_CREATED   = "<green>✔ Clan <gold><name></gold> wurde erstellt.";
    public static final String ALREADY_IN     = "<red>✗ Du bist bereits in einem Clan.";
    public static final String NOT_IN         = "<red>✗ Du bist in keinem Clan.";
    public static final String INVITED        = "<green>✔ <player> wurde eingeladen.";
    public static final String JOINED         = "<green>✔ Du bist dem Clan <gold><name></gold> beigetreten.";
    public static final String KICKED         = "<red>✗ <player> wurde aus dem Clan entfernt.";
    public static final String NO_PERMISSION  = "<red>✗ Du hast keine Berechtigung.";

    // --- Additional messages -------------------------------------------------
    public static final String PLAYER_ONLY    = "<red>✗ Nur Spieler können diesen Befehl verwenden.";
    public static final String UNKNOWN_SUB    = "<red>✗ Unbekannter Unterbefehl. Nutze <yellow>/clan</yellow> für Hilfe.";
    public static final String NAME_TAKEN     = "<red>✗ Ein Clan mit diesem Namen existiert bereits.";
    public static final String NAME_INVALID   = "<red>✗ Ungültiger Clan-Name (2-16 Zeichen, nur Buchstaben/Zahlen/_).";
    public static final String DISBANDED      = "<red>✗ Der Clan <gold><name></gold> wurde aufgelöst.";
    public static final String LEFT           = "<yellow>Du hast den Clan <gold><name></gold> verlassen.";
    public static final String OWNER_CANT_LEAVE = "<red>✗ Als Owner musst du den Clan auflösen oder die Leitung übergeben.";
    public static final String PLAYER_NOT_FOUND = "<red>✗ Spieler nicht gefunden oder nicht online.";
    public static final String TARGET_IN_CLAN = "<red>✗ <player> ist bereits in einem Clan.";
    public static final String TARGET_NOT_IN_CLAN = "<red>✗ <player> ist nicht in deinem Clan.";
    public static final String CANNOT_TARGET_SELF = "<red>✗ Du kannst das nicht mit dir selbst tun.";
    public static final String INVITE_RECEIVED = "<green>✔ Du wurdest in den Clan <gold><name></gold> eingeladen. Nutze <yellow>/clan accept</yellow>.";
    public static final String NO_INVITE      = "<red>✗ Du hast keine offene Einladung von diesem Clan.";
    public static final String NO_INVITES     = "<red>✗ Du hast keine offenen Einladungen.";
    public static final String INVITE_EXPIRED = "<red>✗ Die Einladung ist abgelaufen.";
    public static final String PROMOTED       = "<green>✔ <player> wurde zum Leader befördert.";
    public static final String DEMOTED        = "<yellow><player> wurde zum Member degradiert.";
    public static final String NOT_A_MEMBER_ROLE = "<red>✗ <player> kann nicht in diese Rolle versetzt werden.";
    public static final String TAG_TOO_LONG   = "<red>✗ Der Clan-Tag darf maximal 10 Zeichen lang sein.";
    public static final String TAG_EMPTY      = "<red>✗ Der Clan-Tag darf nicht leer sein.";
    // TAG_SET / COLOR_SET are built inline in the command because they embed the
    // validated hex color directly into the MiniMessage string.
    public static final String COLOR_INVALID  = "<red>✗ Ungültige Farbe. Nutze einen Farbnamen (z.B. red) oder einen Hex-Code (z.B. #ff0000).";
    public static final String USAGE_HEADER   = "<gold>— Clan-Befehle —";
}
