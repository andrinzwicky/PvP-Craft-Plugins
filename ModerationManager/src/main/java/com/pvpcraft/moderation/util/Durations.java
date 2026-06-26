package com.pvpcraft.moderation.util;

import java.util.Locale;

/**
 * Parses human duration strings (e.g. {@code 30m}, {@code 2h}, {@code 1d},
 * {@code perm}) and formats remaining milliseconds back into German text.
 */
public final class Durations {

    /** Returned by {@link #parse} for a permanent duration. */
    public static final long PERMANENT = -1L;
    /** Returned by {@link #parse} when the input could not be parsed. */
    public static final long INVALID = -2L;

    private Durations() {
    }

    /**
     * Parses inputs like {@code 45s}, {@code 30m}, {@code 2h}, {@code 7d},
     * {@code 1w}, or {@code perm}/{@code permanent}/{@code 0}. A bare number
     * (no unit) is interpreted as minutes. Returns millis, {@link #PERMANENT},
     * or {@link #INVALID}.
     */
    public static long parse(String input) {
        if (input == null || input.isBlank()) {
            return INVALID;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("0")) {
            return PERMANENT;
        }

        char unit = s.charAt(s.length() - 1);
        long multiplier;
        String number;
        if (Character.isDigit(unit)) {
            multiplier = 60_000L; // bare number = minutes
            number = s;
        } else {
            number = s.substring(0, s.length() - 1);
            multiplier = switch (unit) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> -1L;
            };
        }
        if (multiplier < 0 || number.isEmpty()) {
            return INVALID;
        }
        try {
            long value = Long.parseLong(number);
            if (value <= 0) {
                return INVALID;
            }
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return INVALID;
        }
    }

    /**
     * Formats a positive duration in millis as compact German text using up to
     * two units, e.g. "2 Tage 3 Stunden" or "29 Minuten".
     */
    public static String format(long millis) {
        if (millis <= 0) {
            return "0 Sekunden";
        }
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        int units = 0;
        if (days > 0) {
            append(sb, days, "Tag", "Tage");
            units++;
        }
        if (hours > 0 && units < 2) {
            append(sb, hours, "Stunde", "Stunden");
            units++;
        }
        if (minutes > 0 && units < 2) {
            append(sb, minutes, "Minute", "Minuten");
            units++;
        }
        if (seconds > 0 && units < 2 && days == 0) {
            append(sb, seconds, "Sekunde", "Sekunden");
            units++;
        }
        if (sb.length() == 0) {
            return "wenige Sekunden";
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, long value, String singular, String plural) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(value).append(' ').append(value == 1 ? singular : plural);
    }
}
