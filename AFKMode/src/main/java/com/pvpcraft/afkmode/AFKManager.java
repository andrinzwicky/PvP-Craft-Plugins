package com.pvpcraft.afkmode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all AFK state: who is AFK, where they are pinned, which floor block is
 * protected, the original tab-list name to restore, and the cached combat-end
 * timestamps used for the 3-minute activation rule.
 *
 * <p>All state is in-memory only, so it never survives a logout or restart &mdash;
 * exactly what the spec wants (a player is never still AFK after rejoining).
 */
public final class AFKManager {

    /** A player may only go AFK at least this long after their combat ended. */
    private static final long COMBAT_COOLDOWN_MILLIS = 3 * 60 * 1000L;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component AFK_SUFFIX =
            MM.deserialize("<gray> </gray><dark_gray>[</dark_gray><red>AFK</red><dark_gray>]</dark_gray>");

    private final AFKMode plugin;
    private final CombatHook combatHook;

    /** Per-AFK-player pinned location (position the player must stay at). */
    private final Map<UUID, Location> pinned = new ConcurrentHashMap<>();
    /** Per-AFK-player protected floor block (immune to break/explosion). */
    private final Map<UUID, Location> standBlock = new ConcurrentHashMap<>();
    /** Original tab-list name captured at AFK start, restored on stop. */
    private final Map<UUID, Component> originalTabName = new ConcurrentHashMap<>();
    /** Last known combat-end timestamp per player (kept fresh by the poll task). */
    private final Map<UUID, Long> lastCombatEnd = new ConcurrentHashMap<>();

    public AFKManager(AFKMode plugin, CombatHook combatHook) {
        this.plugin = plugin;
        this.combatHook = combatHook;
    }

    // --- queries ---------------------------------------------------------

    public boolean isAfk(Player player) {
        return pinned.containsKey(player.getUniqueId());
    }

    public boolean isAfk(UUID uuid) {
        return pinned.containsKey(uuid);
    }

    /** True if {@code block} is the protected floor block of any AFK player. */
    public boolean isStandBlock(Block block) {
        if (standBlock.isEmpty()) {
            return false;
        }
        Location loc = block.getLocation();
        for (Location protectedLoc : standBlock.values()) {
            if (sameBlock(protectedLoc, loc)) {
                return true;
            }
        }
        return false;
    }

    // --- activation ------------------------------------------------------

    /**
     * Tries to enable AFK. Returns {@code null} on success, otherwise a ready
     * MiniMessage error component (already including any remaining seconds).
     */
    public Component tryActivate(Player player) {
        UUID uuid = player.getUniqueId();
        if (pinned.containsKey(uuid)) {
            return MM.deserialize("<yellow>Du bist bereits im AFK-Modus. Nutze <white>/afkstopp</white> zum Beenden.");
        }

        long remaining = remainingCooldownMillis(player);
        if (remaining > 0) {
            long seconds = (remaining + 999) / 1000; // round up
            if (combatHook.isInCombat(player)) {
                return MM.deserialize("<red>Du bist im Kampf. AFK erst <white>" + seconds
                        + "s</white> nach Kampf-Ende moeglich.");
            }
            return MM.deserialize("<red>Noch <white>" + seconds
                    + "s</white> bis du in den AFK-Modus kannst (3 Min nach Kampf-Ende).");
        }

        activate(player);
        return null;
    }

    private void activate(Player player) {
        UUID uuid = player.getUniqueId();
        Location pin = player.getLocation().clone();
        pinned.put(uuid, pin);
        standBlock.put(uuid, pin.getBlock().getRelative(BlockFace.DOWN).getLocation());

        Component current = player.playerListName();
        if (current == null) {
            current = Component.text(player.getName());
        }
        originalTabName.put(uuid, current);
        player.playerListName(current.append(AFK_SUFFIX));

        player.sendMessage(MM.deserialize(
                "<green>AFK-Modus aktiviert.</green> <gray>Bewege dich (WASD/Springen), um ihn zu beenden.</gray>"));
    }

    /** Ends AFK for the player if active; restores their tab name. Quiet no-op otherwise. */
    public void deactivate(Player player, boolean announce) {
        UUID uuid = player.getUniqueId();
        if (pinned.remove(uuid) == null) {
            if (announce) {
                player.sendMessage(MM.deserialize("<yellow>Du bist nicht im AFK-Modus."));
            }
            return;
        }
        standBlock.remove(uuid);
        Component original = originalTabName.remove(uuid);
        if (player.isOnline()) {
            player.playerListName(original); // null restores the vanilla name
            if (announce) {
                player.sendMessage(MM.deserialize("<green>AFK-Modus beendet."));
            }
        }
    }

    /** Cleanup used on quit/death/disable: drop state without touching an offline player. */
    public void clear(UUID uuid) {
        pinned.remove(uuid);
        standBlock.remove(uuid);
        Component original = originalTabName.remove(uuid);
        // Only touch the tab name if we had actually modified it (i.e. the player
        // was AFK); otherwise leave RankManager's name untouched.
        if (original != null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                online.playerListName(original);
            }
        }
    }

    // --- position pinning ------------------------------------------------

    public Location getPinned(UUID uuid) {
        return pinned.get(uuid);
    }

    /**
     * Moves the pin (and protected floor block) to a new location. Used when an
     * admin/plugin teleports an AFK player &mdash; the player stays AFK and gets
     * pinned at the destination.
     */
    public void repin(UUID uuid, Location to) {
        if (!pinned.containsKey(uuid)) {
            return;
        }
        Location pin = to.clone();
        pinned.put(uuid, pin);
        standBlock.put(uuid, pin.getBlock().getRelative(BlockFace.DOWN).getLocation());
    }

    /**
     * Hard per-tick reset: teleports every AFK player who has drifted off their
     * pin back onto it, keeping their current look direction so head-turning
     * stays free. Driven by a 1-tick repeating task.
     */
    public void pinTick() {
        if (pinned.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Location> entry : pinned.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location pin = entry.getValue();
            Location at = player.getLocation();
            if (!pin.getWorld().equals(at.getWorld()) || pin.distanceSquared(at) > 1.0E-6) {
                Location target = new Location(
                        pin.getWorld(), pin.getX(), pin.getY(), pin.getZ(),
                        at.getYaw(), at.getPitch());
                player.teleport(target);
            }
        }
    }

    // --- combat / 3-minute rule ------------------------------------------

    /**
     * Per-second poll: caches each online player's combat-end timestamp while it
     * is still readable, so the 3-minute cooldown can be evaluated even after the
     * combat tag has been removed from AntiCombatLog.
     */
    public void combatPollTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long end = combatHook.combatEndMillis(player);
            if (end != null) {
                lastCombatEnd.merge(player.getUniqueId(), end, Math::max);
            }
        }
    }

    public void forgetCombat(UUID uuid) {
        lastCombatEnd.remove(uuid);
    }

    /**
     * @return milliseconds remaining until the player may go AFK (0 = allowed now).
     *         Covers both being in combat and the 3-minute post-combat cooldown.
     */
    private long remainingCooldownMillis(Player player) {
        long now = System.currentTimeMillis();

        // Latest known combat-end timestamp: the live value (future while tagged)
        // or the cached one captured by the poll task after the tag expired.
        Long live = combatHook.combatEndMillis(player);
        Long cached = lastCombatEnd.get(player.getUniqueId());
        Long end = max(live, cached);
        if (end != null) {
            lastCombatEnd.merge(player.getUniqueId(), end, Math::max);
        } else {
            return 0L; // no combat ever observed -> allowed
        }

        long allowedAt = end + COMBAT_COOLDOWN_MILLIS;
        return Math.max(0L, allowedAt - now);
    }

    public void shutdownAll() {
        for (UUID uuid : pinned.keySet().toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                deactivate(player, false);
            } else {
                clear(uuid);
            }
        }
        pinned.clear();
        standBlock.clear();
        originalTabName.clear();
    }

    private static Long max(Long a, Long b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a >= b ? a : b;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
