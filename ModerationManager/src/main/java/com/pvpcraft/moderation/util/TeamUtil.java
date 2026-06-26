package com.pvpcraft.moderation.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Defines who counts as "team" for this plugin and who may run its commands.
 *
 * Integration with RankManager happens purely through Bukkit permission nodes
 * ({@code rankmanager.rank.*}), so it degrades gracefully when RankManager is
 * absent: the nodes are simply never granted and only the explicit
 * {@code moderation.use} permission (default: op) applies.
 *
 * Team = Supporter and above. Builder, Marketing and Spieler are deliberately
 * NOT team and never see warnings or run moderation commands.
 */
public final class TeamUtil {

    /** Rank nodes considered "team" (Supporter and above). */
    private static final String[] TEAM_NODES = {
            "rankmanager.rank.supporter",
            "rankmanager.rank.moderator",
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    private TeamUtil() {
    }

    /** True if this player holds a Supporter-or-higher rank node. */
    public static boolean isTeam(Player player) {
        for (String node : TEAM_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the sender may use the moderation commands: the console, anyone
     * with the explicit {@code moderation.use} permission, or any team member.
     */
    public static boolean canModerate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true; // console / command blocks have full authority
        }
        return player.hasPermission("moderation.use") || isTeam(player);
    }
}
