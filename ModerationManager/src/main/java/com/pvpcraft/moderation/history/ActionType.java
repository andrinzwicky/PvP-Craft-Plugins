package com.pvpcraft.moderation.history;

import java.util.Locale;

/**
 * The kind of moderation action recorded in a player's history. New action
 * types (e.g. MUTE, TEMPBAN) can be added here and they will automatically be
 * understood by the history storage and the {@code /warns} display.
 */
public enum ActionType {

    WARN("Verwarnung", "#ffcc00"),
    MUTE("Stummschaltung", "#55ddff"),
    UNMUTE("Entstummung", "#55ff99"),
    KICK("Kick", "#ff8800"),
    BAN("Bann", "#ff3333");

    private final String display;
    private final String color; // hex color used when rendering the entry

    ActionType(String display, String color) {
        this.display = display;
        this.color = color;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    /** Parses a stored type id, falling back to WARN for unknown values. */
    public static ActionType fromId(String id) {
        if (id == null) {
            return WARN;
        }
        try {
            return ActionType.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return WARN;
        }
    }
}
