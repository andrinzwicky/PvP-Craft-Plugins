package com.pvpcraft.moderation.command;

import com.pvpcraft.moderation.ModerationManager;
import com.pvpcraft.moderation.history.ActionType;
import com.pvpcraft.moderation.history.ModEntry;
import com.pvpcraft.moderation.util.PlayerLookup;
import com.pvpcraft.moderation.util.TeamUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /warn &lt;player&gt; &lt;reason&gt; — records a warning and shows it to the
 * warned player and the whole team (Supporter+). Normal players never see it.
 */
public final class WarnCommand implements TabExecutor {

    private final ModerationManager plugin;

    public WarnCommand(ModerationManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!TeamUtil.canModerate(sender)) {
            plugin.msg(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.msg(sender, "warn-usage");
            return true;
        }

        OfflinePlayer target = PlayerLookup.resolve(args[0]);
        if (target == null) {
            plugin.msg(sender, "player-not-found", ModerationManager.ph("player", args[0]));
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        String moderator = sender instanceof Player p ? p.getName() : "Konsole";
        String targetName = target.getName() == null ? args[0] : target.getName();

        plugin.history().record(target.getUniqueId(), targetName,
                new ModEntry(ActionType.WARN, moderator, reason, System.currentTimeMillis()));

        long warnCount = plugin.history().countOfType(target.getUniqueId(), ActionType.WARN);

        // Tell the warned player directly (if online).
        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
        if (onlineTarget != null) {
            plugin.msg(onlineTarget, "warn-target",
                    ModerationManager.ph("reason", reason),
                    ModerationManager.ph("moderator", moderator),
                    ModerationManager.ph("count", String.valueOf(warnCount)));
        }

        // Broadcast to the whole team, skipping the warned player (already told).
        plugin.notifyTeamExcept(onlineTarget == null ? null : onlineTarget.getUniqueId(), "warn-team",
                ModerationManager.ph("player", targetName),
                ModerationManager.ph("moderator", moderator),
                ModerationManager.ph("reason", reason),
                ModerationManager.ph("count", String.valueOf(warnCount)));

        // Make sure the issuer always gets confirmation even if not a team member
        // currently online in range (e.g. console).
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "warn-confirm",
                    ModerationManager.ph("player", targetName),
                    ModerationManager.ph("reason", reason));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!TeamUtil.canModerate(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
