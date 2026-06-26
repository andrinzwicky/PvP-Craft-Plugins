package com.pvpcraft.baseprotect.util;

import org.bukkit.entity.Player;

/**
 * Server-admin rank checks for BaseProtect's admin commands
 * (/base define, setowner, delete).
 *
 * <p>Integrates with the RankManager plugin (if present) purely through Bukkit
 * permission nodes, so it degrades gracefully when RankManager is absent: the
 * nodes are simply never granted and the check falls back to the
 * {@code baseprotect.admin} permission (default: op).
 *
 * <p>The requirement is "Admin+": only the Admin and Owner ranks qualify -
 * Moderator deliberately does <em>not</em> (unlike a generic staff bypass).
 */
public final class RankUtil {

    /** RankManager nodes that count as "Server-Admin (Admin+)". */
    private static final String[] ADMIN_NODES = {
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    private RankUtil() {
    }

    /** True if the player is a server admin (RankManager Admin/Owner) or holds baseprotect.admin. */
    public static boolean isServerAdmin(Player player) {
        if (player.hasPermission("baseprotect.admin")) {
            return true;
        }
        for (String node : ADMIN_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
