package com.pvpcraft.watch;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all active watch sessions and the leash / boundary logic.
 *
 * <p>The leash "center" is always the target's <em>current</em> location, so as
 * the target moves the allowed area follows them. The watcher may roam freely
 * inside {@link #RADIUS} blocks of the target; crossing the boundary pulls them
 * back, and a world change makes them follow.
 */
public final class WatchManager {

    /** Maximum distance (blocks) the watcher may be from the target. */
    public static final double RADIUS = 100.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    /** Distance the watcher is placed at when pulled back (just inside the boundary). */
    private static final double PULL_BACK = 98.0;

    private final Watch plugin;
    private final WatchStorage storage;

    /** Active sessions of currently-online watchers, keyed by watcher UUID. */
    private final Map<UUID, WatchSession> sessions = new HashMap<>();

    public WatchManager(Watch plugin, WatchStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public boolean isWatching(UUID watcher) {
        return sessions.containsKey(watcher);
    }

    public WatchSession getSession(UUID watcher) {
        return sessions.get(watcher);
    }

    public Collection<WatchSession> sessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Starts watching, or — if the watcher already has a session — switches the
     * target while keeping the original restore data. Sets the watcher to
     * spectator and teleports them to the target.
     *
     * @return {@code true} if a new session was started, {@code false} if an
     *         existing session merely switched target.
     */
    public boolean start(Player watcher, Player target) {
        WatchSession existing = sessions.get(watcher.getUniqueId());
        if (existing != null) {
            existing.setTarget(target.getUniqueId());
            storage.save(existing);
            watcher.teleport(target.getLocation());
            return false;
        }

        WatchSession session = new WatchSession(
                watcher.getUniqueId(),
                target.getUniqueId(),
                watcher.getLocation().clone(),
                watcher.getGameMode());
        sessions.put(watcher.getUniqueId(), session);
        storage.save(session);

        watcher.setGameMode(GameMode.SPECTATOR);
        watcher.teleport(target.getLocation());
        return true;
    }

    /** Ends an online watcher's session and restores their gamemode + location. */
    public void stopAndRestore(Player watcher) {
        WatchSession session = sessions.remove(watcher.getUniqueId());
        if (session == null) {
            return;
        }
        restore(watcher, session);
        storage.remove(watcher.getUniqueId());
    }

    /**
     * Watcher logged out while watching: drop the in-memory session but keep the
     * stored entry so {@link #handleWatcherJoin(Player)} can restore them later.
     */
    public void handleWatcherQuit(UUID watcher) {
        sessions.remove(watcher);
    }

    /**
     * Watcher (re)joined: if they have a stored session, restore their saved
     * gamemode and location and clear the entry. Does nothing otherwise.
     */
    public void handleWatcherJoin(Player watcher) {
        WatchSession stored = storage.load(watcher.getUniqueId());
        if (stored == null) {
            return;
        }
        sessions.remove(watcher.getUniqueId());
        restore(watcher, stored);
        storage.remove(watcher.getUniqueId());
    }

    /**
     * Target logged out: end every session pointed at them, restoring each
     * online watcher and notifying them.
     */
    public void handleTargetQuit(UUID target) {
        Iterator<Map.Entry<UUID, WatchSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WatchSession> entry = it.next();
            WatchSession session = entry.getValue();
            if (!target.equals(session.getTarget())) {
                continue;
            }
            it.remove();
            storage.remove(entry.getKey());
            Player watcher = Bukkit.getPlayer(entry.getKey());
            if (watcher != null && watcher.isOnline()) {
                restore(watcher, session);
                Messages.send(watcher, Messages.TARGET_LEFT_SERVER);
            }
        }
    }

    /**
     * Periodic enforcement: keep each watcher within range of their (moving)
     * target, follow cross-world moves, and end sessions whose target has gone
     * offline. The {@code PlayerMoveEvent} handles instant boundary clamping;
     * this catches movement caused by the <em>target</em> moving and edge cases.
     */
    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, WatchSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WatchSession> entry = it.next();
            Player watcher = Bukkit.getPlayer(entry.getKey());
            if (watcher == null || !watcher.isOnline()) {
                continue; // offline watcher is handled on quit/join
            }
            WatchSession session = entry.getValue();
            Player target = Bukkit.getPlayer(session.getTarget());
            if (target == null || !target.isOnline()) {
                it.remove();
                storage.remove(entry.getKey());
                restore(watcher, session);
                Messages.send(watcher, Messages.TARGET_LEFT_SERVER);
                continue;
            }

            Location wl = watcher.getLocation();
            Location tl = target.getLocation();
            if (wl.getWorld() == null || !wl.getWorld().equals(tl.getWorld())) {
                watcher.teleport(tl); // follow into the target's world
            } else if (wl.distanceSquared(tl) > RADIUS_SQ) {
                watcher.teleport(pullBack(wl, tl));
            }
        }
    }

    /**
     * If {@code to} is outside the radius of {@code targetLoc} (same world),
     * returns a clamped location just inside the boundary; otherwise {@code null}.
     */
    public Location clampToRadius(Location to, Location targetLoc) {
        if (to == null || to.getWorld() == null || targetLoc.getWorld() == null) {
            return null;
        }
        if (!to.getWorld().equals(targetLoc.getWorld())) {
            return null; // cross-world is handled by tick()
        }
        if (to.distanceSquared(targetLoc) <= RADIUS_SQ) {
            return null;
        }
        return pullBack(to, targetLoc);
    }

    private Location pullBack(Location watcherLoc, Location targetLoc) {
        Vector dir = watcherLoc.toVector().subtract(targetLoc.toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize().multiply(PULL_BACK);
        Location pull = targetLoc.clone().add(dir);
        pull.setYaw(watcherLoc.getYaw());
        pull.setPitch(watcherLoc.getPitch());
        return pull;
    }

    /** Restores a watcher's saved gamemode and (if available) location. */
    private void restore(Player watcher, WatchSession session) {
        watcher.setGameMode(session.getOriginalGameMode());
        Location loc = session.getOriginalLocation();
        if (loc != null && loc.getWorld() != null) {
            watcher.teleport(loc);
        }
    }

    /**
     * On disable, best-effort restore of online watchers so nobody is left stuck
     * in spectator. Stored entries are intentionally kept so a restart still
     * restores the correct location on the watcher's next join.
     */
    public void shutdownRestore() {
        for (WatchSession session : new ArrayList<>(sessions.values())) {
            Player watcher = Bukkit.getPlayer(session.getWatcher());
            if (watcher != null && watcher.isOnline()) {
                restore(watcher, session);
            }
        }
        sessions.clear();
    }
}
