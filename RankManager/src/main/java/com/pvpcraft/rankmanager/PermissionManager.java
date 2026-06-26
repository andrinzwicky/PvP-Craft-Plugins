package com.pvpcraft.rankmanager;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grants each player the permission nodes that belong to their effective rank,
 * using a Bukkit {@link PermissionAttachment}. No external permission plugin is
 * required.
 */
public final class PermissionManager {

    private final RankManager plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public PermissionManager(RankManager plugin) {
        this.plugin = plugin;
    }

    /** Replaces the player's attachment with one matching the given rank. */
    public void apply(Player player, Rank rank) {
        clear(player);

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);

        // The rank's own marker permission.
        attachment.setPermission(rank.permission(), true);

        Set<String> nodes = RankAbilities.permissionNodes(rank.id(), plugin.getRankIds());
        for (String node : nodes) {
            attachment.setPermission(node, true);
        }

        // Extra permission nodes declared per-rank in config.yml (e.g. external
        // plugin nodes like coreprotect.inspect). Applied via the same attachment.
        for (String node : rank.extraPermissions()) {
            if (node != null && !node.isBlank()) {
                attachment.setPermission(node, true);
            }
        }
        player.recalculatePermissions();

        // Push the refreshed permission set to the client's command tree. Without
        // this the granted vanilla command nodes (minecraft.command.*) are not
        // re-evaluated after a join/restart, so the rank's permissions appear lost
        // even though the tab list prefix shows correctly.
        player.updateCommands();
    }

    /** Removes the player's attachment (e.g. on quit). */
    public void clear(Player player) {
        PermissionAttachment existing = attachments.remove(player.getUniqueId());
        if (existing != null) {
            try {
                player.removeAttachment(existing);
            } catch (IllegalArgumentException ignored) {
                // Attachment already gone (player relogged); safe to ignore.
            }
        }
    }
}
