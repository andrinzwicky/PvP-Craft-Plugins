package com.pvpcraft.watch;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * State of a single watch session: who is watching, who is being watched, and
 * the watcher's location and gamemode from before the watch started (so they
 * can be restored on /unwatch, logout or shutdown).
 *
 * <p>Only the {@code target} is mutable: a watcher may re-run /watch to switch
 * to a new target while keeping their original restore data.
 */
public final class WatchSession {

    private final UUID watcher;
    private UUID target;
    private final Location originalLocation;
    private final GameMode originalGameMode;

    public WatchSession(UUID watcher, UUID target, Location originalLocation, GameMode originalGameMode) {
        this.watcher = watcher;
        this.target = target;
        this.originalLocation = originalLocation;
        this.originalGameMode = originalGameMode;
    }

    public UUID getWatcher() {
        return watcher;
    }

    public UUID getTarget() {
        return target;
    }

    public void setTarget(UUID target) {
        this.target = target;
    }

    /** Location to restore the watcher to (may be {@code null} if its world is unloaded). */
    public Location getOriginalLocation() {
        return originalLocation;
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }
}
