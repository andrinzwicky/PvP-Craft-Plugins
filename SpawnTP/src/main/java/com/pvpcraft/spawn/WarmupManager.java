package com.pvpcraft.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks pending /spawn warmups and per-player cooldowns. A warmup counts down
 * once per second and teleports on completion; movement or damage cancels it.
 */
public final class WarmupManager {

    private final SpawnTP plugin;

    /** Players currently counting down to a teleport. */
    private final Map<UUID, CountdownTask> pending = new HashMap<>();
    /** When (epoch millis) each player may use /spawn again. */
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public WarmupManager(SpawnTP plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(UUID id) {
        return pending.containsKey(id);
    }

    /** Remaining cooldown in milliseconds, or 0 if the player may teleport now. */
    public long cooldownRemainingMillis(UUID id) {
        Long until = cooldownUntil.get(id);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /** Starts the countdown for a player. Caller must check cooldown/pending first. */
    public void begin(Player player) {
        CountdownTask task = new CountdownTask(player);
        pending.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    /** Cancels a pending warmup and tells the player why (reason message key). */
    public void cancel(Player player, String reasonKey) {
        CountdownTask task = pending.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (reasonKey != null) {
                plugin.msg(player, reasonKey);
            }
        }
    }

    /** Aborts a pending warmup without a message (e.g. on quit). Keeps cooldown. */
    public void abort(UUID id) {
        CountdownTask task = pending.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Whether the player has moved to a different horizontal block than where the
     * warmup started. Looking around and jumping in place do not count.
     */
    public boolean hasMovedFromStart(UUID id, Location to) {
        CountdownTask task = pending.get(id);
        if (task == null || to == null) {
            return false;
        }
        return to.getBlockX() != task.startX || to.getBlockZ() != task.startZ;
    }

    private void finishTeleport(Player player) {
        // Remove from pending BEFORE teleporting so the teleport's own move event
        // isn't mistaken for the player walking away.
        pending.remove(player.getUniqueId());

        Location spawn = plugin.mainSpawn();
        if (spawn == null) {
            plugin.msg(player, "no-spawn-world");
            return;
        }
        player.teleport(spawn);
        if (plugin.cooldownSeconds() > 0) {
            cooldownUntil.put(player.getUniqueId(),
                    System.currentTimeMillis() + plugin.cooldownSeconds() * 1000L);
        }
        plugin.msg(player, "teleported");
    }

    /** Per-second countdown that teleports the player when it reaches zero. */
    private final class CountdownTask extends BukkitRunnable {

        private final UUID id;
        private final int startX;
        private final int startZ;
        private int remaining;

        CountdownTask(Player player) {
            this.id = player.getUniqueId();
            Location loc = player.getLocation();
            this.startX = loc.getBlockX();
            this.startZ = loc.getBlockZ();
            this.remaining = plugin.warmupSeconds();
        }

        @Override
        public void run() {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                pending.remove(id);
                cancel();
                return;
            }
            if (remaining <= 0) {
                cancel();
                finishTeleport(player);
                return;
            }
            plugin.actionBarCountdown(player, remaining);
            remaining--;
        }
    }
}
