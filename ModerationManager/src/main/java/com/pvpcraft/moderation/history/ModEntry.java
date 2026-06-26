package com.pvpcraft.moderation.history;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single immutable moderation record: who did what to whom, why, and when.
 * Serializes to/from a plain {@link Map} so it can live in a YAML list.
 */
public record ModEntry(ActionType type, String moderator, String reason, long time) {

    /** Converts this entry into a YAML-friendly map. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        map.put("moderator", moderator);
        map.put("reason", reason);
        map.put("time", time);
        return map;
    }

    /** Rebuilds an entry from a stored map; tolerant of missing fields. */
    public static ModEntry fromMap(Map<?, ?> map) {
        ActionType type = ActionType.fromId(asString(map.get("type")));
        String moderator = asString(map.get("moderator"));
        String reason = asString(map.get("reason"));
        long time = map.get("time") instanceof Number n ? n.longValue() : 0L;
        return new ModEntry(type, moderator == null ? "?" : moderator,
                reason == null ? "" : reason, time);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
