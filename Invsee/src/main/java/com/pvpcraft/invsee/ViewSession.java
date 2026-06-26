package com.pvpcraft.invsee;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * One viewer's open view of another player's inventory or enderchest.
 *
 * <p>Enderchest views and the player's own inventory are opened live, so no
 * write-back is needed. Inventory views use a mirrored 45-slot GUI (so armor and
 * offhand can be shown); read-only mirrors are refreshed on a repeating task to
 * stay live, and editable mirrors are written back to the target on each change.
 */
public final class ViewSession {

    private final UUID targetId;
    private final boolean editable;
    private BukkitTask refreshTask;

    public ViewSession(UUID targetId, boolean editable) {
        this.targetId = targetId;
        this.editable = editable;
    }

    /** The player whose container is being viewed. */
    public UUID targetId() {
        return targetId;
    }

    /** {@code true} for the {@code +} commands (full interaction). */
    public boolean editable() {
        return editable;
    }

    /** Repeating live-refresh task for read-only mirrors; {@code null} otherwise. */
    public BukkitTask refreshTask() {
        return refreshTask;
    }

    public void setRefreshTask(BukkitTask refreshTask) {
        this.refreshTask = refreshTask;
    }
}
