package com.pvpcraft.dimensioncontrol.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Decides who may use /dimension. Only Admin, Developer and Owner ranks (plus
 * the console and holders of dimensioncontrol.admin / op) may change or view the
 * dimension status.
 *
 * Integration with RankManager is purely through Bukkit permission nodes
 * ({@code rankmanager.rank.*}), so it degrades gracefully when RankManager is
 * absent: the nodes are simply never granted and only op / dimensioncontrol.admin
 * applies.
 */
public final class RankUtil {

    /** Ranks allowed to change the dimension status. */
    private static final String[] STATUS_NODES = {
            "rankmanager.rank.admin",
            "rankmanager.rank.developer",
            "rankmanager.rank.owner"
    };

    private RankUtil() {
    }

    /**
     * True for the console, anyone with the explicit {@code dimensioncontrol.admin}
     * permission (default: op), or an Admin/Developer/Owner rank holder.
     */
    public static boolean canManage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true; // console / command blocks have full authority
        }
        if (player.hasPermission("dimensioncontrol.admin")) {
            return true;
        }
        for (String node : STATUS_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
