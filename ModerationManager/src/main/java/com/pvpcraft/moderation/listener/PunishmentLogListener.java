package com.pvpcraft.moderation.listener;

import com.pvpcraft.moderation.ModerationManager;
import com.pvpcraft.moderation.history.ActionType;
import com.pvpcraft.moderation.history.ModEntry;
import com.pvpcraft.moderation.util.PlayerLookup;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Records vanilla /kick and /ban usage into the shared moderation history so it
 * shows up alongside warns in {@code /warns}. We log at MONITOR priority and
 * only when the command wasn't cancelled and the executor actually holds the
 * matching permission, so unauthorized attempts and typos aren't recorded.
 */
public final class PunishmentLogListener implements Listener {

    private final ModerationManager plugin;

    public PunishmentLogListener(ModerationManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player executor = event.getPlayer();
        handle(executor, executor.getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        handle(event.getSender(), "Konsole", "/" + event.getCommand());
    }

    // ------------------------------------------------------------------

    private void handle(CommandSender sender, String moderator, String rawMessage) {
        String[] parts = split(rawMessage);
        if (parts.length < 2) {
            return; // need at least a command and a target
        }
        String cmd = normalize(parts[0]);

        ActionType type;
        String permission;
        if (cmd.equals("kick")) {
            type = ActionType.KICK;
            permission = "minecraft.command.kick";
        } else if (cmd.equals("ban") || cmd.equals("ban-ip")) {
            type = ActionType.BAN;
            permission = "minecraft.command.ban";
        } else {
            return;
        }

        // Skip unauthorized attempts so we don't log commands vanilla will reject.
        if (sender instanceof Player p && !p.hasPermission(permission)) {
            return;
        }

        OfflinePlayer target = PlayerLookup.resolve(parts[1]);
        if (target == null) {
            return; // unknown name / raw IP — nothing meaningful to attribute
        }

        String reason = parts.length > 2
                ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)).trim()
                : "";
        String targetName = target.getName() == null ? parts[1] : target.getName();

        plugin.history().record(target.getUniqueId(), targetName,
                new ModEntry(type, moderator, reason, System.currentTimeMillis()));
    }

    private String[] split(String message) {
        String body = message.startsWith("/") ? message.substring(1) : message;
        body = body.trim();
        if (body.isEmpty()) {
            return new String[0];
        }
        return body.split("\\s+");
    }

    /** Lowercases and strips any namespace prefix like "minecraft:". */
    private String normalize(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }
}
