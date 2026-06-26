package com.pvpcraft.spawn.util;

import org.bukkit.entity.Player;

/**
 * Cooldown-bypass checks. Integrates with the RankManager plugin (if present)
 * purely through Bukkit permission nodes, so it degrades gracefully when
 * RankManager is absent: the nodes are simply never granted.
 */
public final class RankUtil {

    /** Ranks (and the explicit node) that skip the /spawn cooldown. */
    private static final String[] BYPASS_NODES = {
            "rankmanager.rank.supporter",
            "rankmanager.rank.moderator",
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    private RankUtil() {
    }

    /**
     * True if the player may skip the cooldown after a teleport: holders of the
     * explicit {@code spawn.bypasscooldown} permission, or of a Supporter/
     * Moderator/Developer/Admin/Owner rank node. The warmup countdown still
     * applies to everyone.
     */
    public static boolean bypassesCooldown(Player player) {
        if (player.hasPermission("spawn.bypasscooldown")) {
            return true;
        }
        for (String node : BYPASS_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
