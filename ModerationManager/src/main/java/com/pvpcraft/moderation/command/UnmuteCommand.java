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

/** /unmute &lt;player&gt; — lifts an active mute. */
public final class UnmuteCommand implements TabExecutor {

    private final ModerationManager plugin;

    public UnmuteCommand(ModerationManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!TeamUtil.canModerate(sender)) {
            plugin.msg(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.msg(sender, "unmute-usage");
            return true;
        }

        OfflinePlayer target = PlayerLookup.resolve(args[0]);
        if (target == null) {
            plugin.msg(sender, "player-not-found", ModerationManager.ph("player", args[0]));
            return true;
        }

        String targetName = target.getName() == null ? args[0] : target.getName();
        if (!plugin.mutes().unmute(target.getUniqueId())) {
            plugin.msg(sender, "unmute-notmuted", ModerationManager.ph("player", targetName));
            return true;
        }

        String moderator = sender instanceof Player p ? p.getName() : "Konsole";
        plugin.history().record(target.getUniqueId(), targetName,
                new ModEntry(ActionType.UNMUTE, moderator, "", System.currentTimeMillis()));

        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
        if (onlineTarget != null) {
            plugin.msg(onlineTarget, "unmute-target");
        }
        plugin.notifyTeamExcept(onlineTarget == null ? null : onlineTarget.getUniqueId(), "unmute-team",
                ModerationManager.ph("player", targetName),
                ModerationManager.ph("moderator", moderator));

        if (!(sender instanceof Player)) {
            plugin.msg(sender, "unmute-confirm", ModerationManager.ph("player", targetName));
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
