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
 * /warns &lt;player&gt; — shows a player's full moderation history (warns,
 * kicks and bans), newest last. Team only.
 */
public final class WarnsCommand implements TabExecutor {

    private final ModerationManager plugin;

    public WarnsCommand(ModerationManager plugin) {
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
            plugin.msg(sender, "warns-usage");
            return true;
        }

        OfflinePlayer target = PlayerLookup.resolve(args[0]);
        if (target == null) {
            plugin.msg(sender, "player-not-found", ModerationManager.ph("player", args[0]));
            return true;
        }

        String name = target.getName() == null ? args[0] : target.getName();
        List<ModEntry> entries = plugin.history().getEntries(target.getUniqueId());

        if (entries.isEmpty()) {
            plugin.msg(sender, "warns-empty", ModerationManager.ph("player", name));
            return true;
        }

        long warns = plugin.history().countOfType(target.getUniqueId(), ActionType.WARN);
        long kicks = plugin.history().countOfType(target.getUniqueId(), ActionType.KICK);
        long bans = plugin.history().countOfType(target.getUniqueId(), ActionType.BAN);

        plugin.msg(sender, "warns-header",
                ModerationManager.ph("player", name),
                ModerationManager.ph("total", String.valueOf(entries.size())),
                ModerationManager.ph("warns", String.valueOf(warns)),
                ModerationManager.ph("kicks", String.valueOf(kicks)),
                ModerationManager.ph("bans", String.valueOf(bans)));

        int index = 1;
        for (ModEntry entry : entries) {
            ActionType type = entry.type();
            String line = "<gray>#<index></gray> "
                    + "<" + type.color() + "><bold>[<type>]</bold></" + type.color() + "> "
                    + "<gray>von</gray> <white><moderator></white> "
                    + "<dark_gray>(<time>)</dark_gray>"
                    + "<newline>    <gray>Grund:</gray> <white><reason></white>";
            plugin.send(sender, plugin.render(line,
                    ModerationManager.ph("index", String.valueOf(index)),
                    ModerationManager.ph("type", type.display()),
                    ModerationManager.ph("moderator", entry.moderator()),
                    ModerationManager.ph("time", plugin.formatTime(entry.time())),
                    ModerationManager.ph("reason", entry.reason().isEmpty() ? "-" : entry.reason())));
            index++;
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
