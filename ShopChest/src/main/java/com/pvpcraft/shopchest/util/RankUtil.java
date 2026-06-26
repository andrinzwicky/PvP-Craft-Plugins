package com.pvpcraft.shopchest.util;

import org.bukkit.entity.Player;

/**
 * Rank bypass checks. Integrates with the RankManager plugin (if present) purely
 * through Bukkit permission nodes, so it degrades gracefully when RankManager is
 * absent: the nodes are simply never granted and the methods return {@code false}.
 */
public final class RankUtil {

    /** Permission nodes whose holders may always manage plots and shops. */
    private static final String[] BYPASS_NODES = {
            "rankmanager.rank.moderator",
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    /**
     * Higher-tier nodes that may hand out the configurator. Deliberately excludes
     * Moderator: only Owner/Admin/Developer (or the shopchest.admin permission).
     */
    private static final String[] CONFIGURATOR_NODES = {
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    private RankUtil() {
    }

    /** True if the player has a staff rank (Moderator/Developer/Admin/Owner) or the admin permission. */
    public static boolean hasBypass(Player player) {
        if (player.hasPermission("shopchest.admin")) {
            return true;
        }
        for (String node : BYPASS_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the player may give out the Shop Konfigurator: holders of
     * shopchest.admin, or the Owner/Admin/Developer RankManager ranks. If
     * RankManager is absent those nodes are simply never granted, so the check
     * falls back to shopchest.admin only.
     */
    public static boolean canManageConfigurator(Player player) {
        if (player.hasPermission("shopchest.admin")) {
            return true;
        }
        for (String node : CONFIGURATOR_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
