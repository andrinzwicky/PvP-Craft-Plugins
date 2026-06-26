package com.pvpcraft.rankmanager.command;

import com.pvpcraft.rankmanager.Rank;
import com.pvpcraft.rankmanager.RankAbilities;
import com.pvpcraft.rankmanager.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RankCommand implements TabExecutor {

    private final RankManager plugin;

    public RankCommand(RankManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "get" -> handleGet(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messageNoPrefix(sender, "<gray>Usage: /rank set <player> <rank></gray>");
            return;
        }

        Rank target = plugin.getRank(args[2]);
        if (target == null) {
            plugin.message(sender, "rank-not-found", RankManager.placeholder("rank", args[2]));
            return;
        }

        String executorId = executorRankId(sender);
        if (!RankAbilities.canAssign(executorId, target.id())) {
            plugin.message(sender, "cannot-assign", RankManager.placeholder("rank", target.display()));
            return;
        }

        OfflinePlayer offline = resolvePlayer(args[1]);
        if (offline == null) {
            plugin.message(sender, "player-not-found", RankManager.placeholder("player", args[1]));
            return;
        }

        plugin.setRank(offline.getUniqueId(), target.id());
        plugin.message(sender, "rank-set",
                RankManager.placeholder("player", offline.getName() == null ? args[1] : offline.getName()),
                RankManager.placeholder("rank", target.display()));
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (!canRead(sender)) {
            plugin.message(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messageNoPrefix(sender, "<gray>Usage: /rank get <player></gray>");
            return;
        }

        Player online = Bukkit.getPlayerExact(args[1]);
        Rank rank;
        String name;
        if (online != null) {
            rank = plugin.getEffectiveRank(online);
            name = online.getName();
        } else {
            OfflinePlayer offline = resolvePlayer(args[1]);
            if (offline == null) {
                plugin.message(sender, "player-not-found", RankManager.placeholder("player", args[1]));
                return;
            }
            rank = plugin.getStoredRank(offline.getUniqueId());
            name = offline.getName() == null ? args[1] : offline.getName();
        }

        plugin.message(sender, "rank-get",
                RankManager.placeholder("player", name),
                RankManager.placeholder("rank", rank.display()));
    }

    private void handleList(CommandSender sender) {
        if (!canRead(sender)) {
            plugin.message(sender, "no-permission");
            return;
        }
        plugin.messageNoPrefix(sender, "<gray>Ranks (by priority):</gray>");
        for (Rank rank : plugin.getRanksByPriority()) {
            Component line = Component.text("  #" + rank.priority() + " ", NamedTextColor.DARK_GRAY)
                    .append(rank.coloredDisplay())
                    .append(Component.text("  " + rank.permission(), NamedTextColor.GRAY));
            if (rank.isDefault()) {
                line = line.append(Component.text(" (default)", NamedTextColor.DARK_GRAY));
            }
            sender.sendMessage(line);
        }
    }

    private void handleReload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            plugin.message(sender, "no-permission");
            return;
        }
        plugin.reload();
        plugin.message(sender, "reloaded");
    }

    // ------------------------------------------------------------------

    private void sendUsage(CommandSender sender) {
        plugin.messageNoPrefix(sender, "<gray>/rank set <player> <rank></gray>");
        plugin.messageNoPrefix(sender, "<gray>/rank get <player></gray>");
        plugin.messageNoPrefix(sender, "<gray>/rank list</gray>");
        plugin.messageNoPrefix(sender, "<gray>/rank reload</gray>");
    }

    /** "owner" for console; otherwise the player's effective rank id. */
    private String executorRankId(CommandSender sender) {
        if (sender instanceof Player player) {
            return plugin.getEffectiveRank(player).id();
        }
        return "owner"; // console / command blocks have full authority
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission("rankmanager.admin");
    }

    /** Read-only commands: admins or any staff rank. */
    private boolean canRead(CommandSender sender) {
        return hasAdmin(sender) || RankAbilities.isStaff(executorRankId(sender));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline;
        }
        return null;
    }

    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (canRead(sender) || hasAdmin(sender)) {
                subs.add("set");
                subs.add("get");
                subs.add("list");
            }
            if (hasAdmin(sender)) {
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("set") || sub.equals("get"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && sub.equals("set")) {
            String executorId = executorRankId(sender);
            List<String> assignable = new ArrayList<>();
            for (String id : plugin.getRankIds()) {
                if (RankAbilities.canAssign(executorId, id)) {
                    assignable.add(id);
                }
            }
            return filter(assignable, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
