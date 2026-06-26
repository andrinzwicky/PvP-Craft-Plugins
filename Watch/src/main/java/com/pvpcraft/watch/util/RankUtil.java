package com.pvpcraft.watch.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Decides who may use /watch. Allowed are the Supporter rank and everything
 * above it (Supporter, Moderator, Developer, Admin, Owner).
 *
 * Integration with RankManager is purely through Bukkit permission nodes
 * ({@code rankmanager.rank.*}), so it degrades gracefully when RankManager is
 * absent: the nodes are simply never granted and only op / {@code watch.use}
 * applies.
 */
public final class RankUtil {

    /** Ranks allowed to watch (Supporter and higher). */
    private static final String[] WATCH_NODES = {
            "rankmanager.rank.supporter",
            "rankmanager.rank.moderator",
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    private RankUtil() {
    }

    /**
     * True for anyone holding the explicit {@code watch.use} permission
     * (default: op) or a Supporter+/Moderator/Developer/Admin/Owner rank.
     */
    public static boolean canWatch(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false; // /watch needs an in-game watcher
        }
        if (player.hasPermission("watch.use")) {
            return true;
        }
        for (String node : WATCH_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
