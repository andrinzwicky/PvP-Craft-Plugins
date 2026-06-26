package com.pvpcraft.invsee;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every open Invsee view, keyed by the viewer's UUID. A viewer can have
 * at most one open view at a time, so the viewer UUID is a sufficient key.
 */
public final class SessionManager {

    private final Map<UUID, ViewSession> sessions = new HashMap<>();

    public void open(UUID viewerId, ViewSession session) {
        sessions.put(viewerId, session);
    }

    public ViewSession get(UUID viewerId) {
        return sessions.get(viewerId);
    }

    public void close(UUID viewerId) {
        ViewSession removed = sessions.remove(viewerId);
        if (removed != null && removed.refreshTask() != null) {
            removed.refreshTask().cancel();
        }
    }

    /** Snapshot of the current sessions, safe to iterate while mutating. */
    public Map<UUID, ViewSession> snapshot() {
        return new HashMap<>(sessions);
    }
}
