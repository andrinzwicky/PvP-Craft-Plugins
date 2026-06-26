package com.pvpcraft.rankmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

/**
 * Optional, fully decoupled bridge to a separately developed ClanSystem plugin.
 *
 * <p>RankManager does <b>not</b> depend on ClanSystem at compile time. ClanSystem
 * publishes a player's clan tag by setting two Bukkit metadata values on the
 * {@link Player}:
 * <ul>
 *   <li>{@code clan_tag}   – the short tag text shown in brackets, e.g. {@code "ACE"}</li>
 *   <li>{@code clan_color} – the tag color, either a hex string ({@code "#ff5555"})
 *       or a vanilla color name ({@code "red"}); optional, defaults to white.</li>
 * </ul>
 *
 * <p>If ClanSystem is absent the metadata is simply never set, so every method
 * here returns an empty component and the clan tag is skipped silently. Every
 * lookup is wrapped defensively so a misbehaving clan plugin can never crash
 * RankManager.
 */
public final class ClanHook {

    private ClanHook() {
    }

    /** A player's clan tag plus its color, or {@code null} when no clan is set. */
    public record ClanTag(String tag, TextColor color) {
    }

    /**
     * Reads the clan tag from the player's metadata, or {@code null} if ClanSystem
     * is absent / the player has no clan. Never throws.
     */
    public static ClanTag read(Player player) {
        try {
            String tag = firstString(player, "clan_tag");
            if (tag == null || tag.isBlank()) {
                return null;
            }
            TextColor color = parseColor(firstString(player, "clan_color"));
            return new ClanTag(tag.trim(), color);
        } catch (Exception ignored) {
            // A faulty clan plugin must never break the nametag or chat.
            return null;
        }
    }

    /**
     * The over-head nametag suffix: " [Tag]" (note the leading space) in the clan
     * color, or {@link Component#empty()} when the player has no clan.
     */
    public static Component nametagSuffix(Player player) {
        ClanTag clan = read(player);
        if (clan == null) {
            return Component.empty();
        }
        return Component.text(" [" + clan.tag() + "]", clan.color());
    }

    /**
     * The chat clan tag: " [Tag]" (leading space) in the clan color, or
     * {@link Component#empty()} when the player has no clan. The leading space lets
     * the configured chat format place the tag right after the player name without
     * leaving a dangling space when no clan is present.
     */
    public static Component chatTag(Player player) {
        return nametagSuffix(player);
    }

    private static String firstString(Player player, String key) {
        if (!player.hasMetadata(key)) {
            return null;
        }
        List<MetadataValue> values = player.getMetadata(key);
        if (values.isEmpty()) {
            return null;
        }
        // Prefer a value set by an actually-enabled plugin; fall back to the first.
        for (MetadataValue value : values) {
            if (value.getOwningPlugin() != null && value.getOwningPlugin().isEnabled()) {
                String s = value.asString();
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return values.get(0).asString();
    }

    /** Parses a hex ({@code #rrggbb}) or named color; white if blank/unparseable. */
    private static TextColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return NamedTextColor.WHITE;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(value);
            if (hex != null) {
                return hex;
            }
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase());
        return named != null ? named : NamedTextColor.WHITE;
    }
}
