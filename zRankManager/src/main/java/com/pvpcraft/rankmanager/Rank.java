package com.pvpcraft.rankmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/**
 * Immutable definition of a rank loaded from config.yml.
 *
 * @param id               lowercase config key (e.g. "owner"); used for ability lookups
 * @param display          text shown inside the [ ] brackets
 * @param colors           one hex color (solid) or several (gradient)
 * @param priority         tab list sort order, lowest = top
 * @param permission       permission node that marks this rank
 * @param isDefault        true if this is the rank new players receive
 * @param extraPermissions additional permission nodes granted on top of the
 *                         rank's built-in abilities (from config "extra-permissions")
 */
public record Rank(String id,
                   String display,
                   List<String> colors,
                   int priority,
                   String permission,
                   boolean isDefault,
                   List<String> extraPermissions) {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Builds the tab list component "[Display] Playername" with the rank's color
     * or gradient applied across the ENTIRE string as one continuous gradient.
     */
    public Component formatTabName(String playerName) {
        return MM.deserialize(colorize("[" + display + "] " + playerName));
    }

    /**
     * The over-head nametag prefix: "[Display] " (note the trailing space) with the
     * rank gradient applied. Used as a scoreboard team prefix so the in-world name
     * above the player's skin reads "[Rank] Playername".
     */
    public Component nametagPrefix() {
        return MM.deserialize(colorize("[" + display + "] "));
    }

    /** The bracketed, gradient-colored "[Display]" used in the chat format. */
    public Component chatPrefix() {
        return MM.deserialize(colorize("[" + display + "]"));
    }

    /** A small colored preview of just the rank name, used by /rank list. */
    public Component coloredDisplay() {
        return MM.deserialize(colorize(display));
    }

    /**
     * Wraps {@code text} in the rank's MiniMessage color: a single {@code <color>}
     * tag for a solid rank, or a continuous {@code <gradient>} for two or more
     * colors. The same wrapping is reused for the tab name, nametag and chat.
     */
    private String colorize(String text) {
        if (colors.size() <= 1) {
            String color = colors.isEmpty() ? "#ffffff" : colors.get(0);
            return "<color:" + color + ">" + text + "</color>";
        }
        return "<gradient:" + String.join(":", colors) + ">" + text + "</gradient>";
    }

    /**
     * Scoreboard team name used to control tab list sort order. Teams are sorted
     * alphabetically, so we pad the priority to keep numeric order. Truncated to
     * the 16-char team-name limit.
     */
    public String teamName() {
        String name = String.format("rm_%03d_%s", priority, id);
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
