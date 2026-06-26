package com.pvpcraft.rankmanager;

import org.bukkit.GameMode;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Central definition of what each rank may do. Keyed by rank id (the config key)
 * so abilities stay fixed even though colors/priority are config-driven.
 *
 * Everything here is enforced internally by the plugin; no external permission
 * plugin is required.
 */
public final class RankAbilities {

    private RankAbilities() {
    }

    // Vanilla command permission nodes that Paper maps its built-in commands to.
    public static final String NODE_GAMEMODE = "minecraft.command.gamemode";
    public static final String NODE_TELEPORT = "minecraft.command.teleport";
    public static final String NODE_KICK = "minecraft.command.kick";
    private static final String[] NODES_BAN = {
            "minecraft.command.ban",
            "minecraft.command.ban-ip",
            "minecraft.command.banlist",
            "minecraft.command.pardon",
            "minecraft.command.pardon-ip"
    };
    private static final String[] NODES_OP = {
            "minecraft.command.op",
            "minecraft.command.deop"
    };

    /**
     * Builds the full set of permission nodes a rank grants. {@code allRankIds}
     * is used to expand "rankmanager.rank.set.*" style grants.
     */
    public static Set<String> permissionNodes(String rankId, Collection<String> allRankIds) {
        Set<String> nodes = new LinkedHashSet<>();
        switch (rankId) {
            case "owner" -> {
                // Everything, unrestricted.
                nodes.add("rankmanager.*");
                nodes.add("rankmanager.admin");
                addSetNodes(nodes, allRankIds, null); // every rank, incl. owner
                nodes.add(NODE_GAMEMODE);
                nodes.add(NODE_TELEPORT);
                nodes.add(NODE_KICK);
                addAll(nodes, NODES_BAN);
                addAll(nodes, NODES_OP);
            }
            case "admin" -> {
                nodes.add("rankmanager.admin");
                addSetNodes(nodes, allRankIds, "owner"); // all except owner
                nodes.add(NODE_GAMEMODE);
                nodes.add(NODE_TELEPORT);
                nodes.add(NODE_KICK);
                addAll(nodes, NODES_BAN);
            }
            case "developer" -> {
                nodes.add("rankmanager.admin");
                addSetNodes(nodes, allRankIds, "owner"); // all except owner, no /op
                nodes.add(NODE_GAMEMODE);
                nodes.add(NODE_TELEPORT);
                nodes.add(NODE_KICK);
                addAll(nodes, NODES_BAN);
            }
            case "moderator" -> {
                // May assign supporter, builder, marketing, spieler.
                nodes.add("rankmanager.rank.set.supporter");
                nodes.add("rankmanager.rank.set.builder");
                nodes.add("rankmanager.rank.set.marketing");
                nodes.add("rankmanager.rank.set.spieler");
                // Gamemode granted but creative is blocked via command interception.
                nodes.add(NODE_GAMEMODE);
                nodes.add(NODE_TELEPORT);
                nodes.add(NODE_KICK);
                addAll(nodes, NODES_BAN);
            }
            case "supporter" -> {
                // Survival/Spectator gamemode (may switch between the two); /tp only
                // while spectating. Enforced via command interception plus a hard
                // gamemode lock that keeps them out of Creative/Adventure by any means.
                nodes.add(NODE_GAMEMODE);
                nodes.add(NODE_TELEPORT);
            }
            default -> {
                // builder, marketing, spieler and any custom rank: no extra perms.
            }
        }
        return nodes;
    }

    /** Gamemodes a rank is permitted to switch to via /gamemode. */
    public static Set<GameMode> allowedGamemodes(String rankId) {
        return switch (rankId) {
            case "owner", "admin", "developer" -> EnumSet.allOf(GameMode.class);
            case "moderator" -> EnumSet.of(GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.SPECTATOR);
            case "supporter" -> EnumSet.of(GameMode.SPECTATOR, GameMode.SURVIVAL);
            default -> EnumSet.noneOf(GameMode.class);
        };
    }

    /**
     * Whether this rank's /gamemode usage should be policed by the plugin at all.
     * Owner/admin/developer can use any gamemode, lower ranks without the node are
     * blocked by vanilla, so we only intercept the partially-restricted ranks.
     */
    public static boolean restrictsGamemode(String rankId) {
        return rankId.equals("moderator") || rankId.equals("supporter");
    }

    /** Supporters may only teleport while spectating. */
    public static boolean teleportOnlyInSpectator(String rankId) {
        return rankId.equals("supporter");
    }

    /** Whether this rank may change OTHER players' gamemodes (Moderator and above). */
    public static boolean canChangeOthersGamemode(String rankId) {
        return isStaff(rankId); // owner, admin, developer, moderator
    }

    /**
     * Whether an executor holding {@code executorRankId} may assign
     * {@code targetRankId} via /rank set.
     */
    public static boolean canAssign(String executorRankId, String targetRankId) {
        return switch (executorRankId) {
            case "owner" -> true;
            case "admin", "developer" -> !targetRankId.equals("owner");
            case "moderator" -> targetRankId.equals("supporter")
                    || targetRankId.equals("builder")
                    || targetRankId.equals("marketing")
                    || targetRankId.equals("spieler");
            default -> false;
        };
    }

    /** Ranks considered staff for read-only commands like /rank get and /rank list. */
    public static boolean isStaff(String rankId) {
        return switch (rankId) {
            case "owner", "admin", "developer", "moderator" -> true;
            default -> false;
        };
    }

    private static void addSetNodes(Set<String> nodes, Collection<String> allRankIds, String exclude) {
        for (String id : allRankIds) {
            if (exclude != null && id.equals(exclude)) {
                continue;
            }
            nodes.add("rankmanager.rank.set." + id);
        }
    }

    private static void addAll(Set<String> nodes, String[] values) {
        for (String v : values) {
            nodes.add(v);
        }
    }
}
