package com.pvpcraft.clansystem;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * Resolves a user-supplied color (a MiniMessage color name like {@code red} or a
 * hex code like {@code #ff0000}) into a canonical hex string.
 *
 * <p>RankManager reads the clan color from player metadata and expects a hex
 * string, so every color stored or displayed by ClanSystem is normalised to hex
 * up front. That keeps the metadata contract ("clan_color" -> hex) trivially
 * satisfied.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * Parses the given input into a {@link TextColor}, or returns {@code null} if
     * it is neither a valid hex code nor a known named color. Accepts an optional
     * leading {@code #} and surrounding {@code < >} (so {@code <red>} works too).
     */
    public static TextColor parse(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.startsWith("<") && s.endsWith(">") && s.length() > 2) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }

        // Hex: "#rrggbb" or "rrggbb".
        String hexBody = s.startsWith("#") ? s.substring(1) : s;
        if (hexBody.length() == 6 && hexBody.chars().allMatch(ColorUtil::isHexDigit)) {
            return TextColor.fromHexString("#" + hexBody.toLowerCase(Locale.ROOT));
        }

        // Named color, e.g. "red", "dark_aqua".
        return NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
    }

    /** Convenience: parses to a canonical hex string, or {@code null} if invalid. */
    public static String toHex(String input) {
        TextColor color = parse(input);
        return color == null ? null : color.asHexString();
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
