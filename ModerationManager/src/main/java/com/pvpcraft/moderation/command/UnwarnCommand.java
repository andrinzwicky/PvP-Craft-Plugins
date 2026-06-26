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
 * /unwarn &lt;player&gt; [number] — removes a warning. Without a number the most
 * recent warning is removed; with a number the entry at that position in
 * {@code /warns} is removed (only WARN entries — kicks/bans are facts and stay).
 */
public final class UnwarnCommand implements TabExecutor {

    private final ModerationManager plugin;

    public UnwarnCommand(ModerationManager plugin) {
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
            plugin.msg(sender, "unwarn-usage");
            return true;
        }

        OfflinePlayer target = PlayerLookup.resolve(args[0]);
        if (target == null) {
            plugin.msg(sender, "player-not-found", ModerationManager.ph("player", args[0]));
            return true;
        }

        String targetName = target.getName() == null ? args[0] : target.getName();
        List<ModEntry> entries = plugin.history().getEntries(target.getUniqueId());
        if (entries.isEmpty()) {
            plugin.msg(sender, "unwarn-none", ModerationManager.ph("player", targetName));
            return true;
        }

        int index;
        if (args.length >= 2) {
            Integer parsed = parsePositiveInt(args[1]);
            if (parsed == null || parsed < 1 || parsed > entries.size()) {
                plugin.msg(sender, "unwarn-bad-number",
                        ModerationManager.ph("input", args[1]),
                        ModerationManager.ph("total", String.valueOf(entries.size())));
                return true;
            }
            index = parsed - 1;
            if (entries.get(index).type() != ActionType.WARN) {
                plugin.msg(sender, "unwarn-not-a-warn",
                        ModerationManager.ph("number", args[1]),
                        ModerationManager.ph("type", entries.get(index).type().display()));
                return true;
            }
        } else {
            index = lastWarnIndex(entries);
            if (index < 0) {
                plugin.msg(sender, "unwarn-no-warns", ModerationManager.ph("player", targetName));
                return true;
            }
        }

        ModEntry removed = plugin.history().removeEntry(target.getUniqueId(), index);
        if (removed == null) {
            plugin.msg(sender, "unwarn-none", ModerationManager.ph("player", targetName));
            return true;
        }

        long remaining = plugin.history().countOfType(target.getUniqueId(), ActionType.WARN);
        String moderator = sender instanceof Player p ? p.getName() : "Konsole";

        plugin.msg(sender, "unwarn-done",
                ModerationManager.ph("player", targetName),
                ModerationManager.ph("reason", removed.reason().isEmpty() ? "-" : removed.reason()),
                ModerationManager.ph("count", String.valueOf(remaining)));

        plugin.notifyTeamExcept(null, "unwarn-team",
                ModerationManager.ph("player", targetName),
                ModerationManager.ph("moderator", moderator),
                ModerationManager.ph("count", String.valueOf(remaining)));
        return true;
    }

    /** Index of the most recent WARN entry, or -1 if there is none. */
    private int lastWarnIndex(List<ModEntry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).type() == ActionType.WARN) {
                return i;
            }
        }
        return -1;
    }

    private Integer parsePositiveInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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
