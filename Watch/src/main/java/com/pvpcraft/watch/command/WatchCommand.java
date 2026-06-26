package com.pvpcraft.watch.command;

import com.pvpcraft.watch.GSitHook;
import com.pvpcraft.watch.Messages;
import com.pvpcraft.watch.WatchManager;
import com.pvpcraft.watch.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /watch <player>} — start (or switch) watching a player in spectator
 * mode, leashed to a 100-block radius. The watched player is never notified.
 */
public final class WatchCommand implements CommandExecutor, TabCompleter {

    private final WatchManager manager;
    private final GSitHook gsit;

    public WatchCommand(WatchManager manager, GSitHook gsit) {
        this.manager = manager;
        this.gsit = gsit;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player watcher)) {
            Messages.send(sender, Messages.PLAYER_ONLY);
            return true;
        }
        if (!RankUtil.canWatch(watcher)) {
            Messages.send(watcher, Messages.NO_PERMISSION);
            return true;
        }
        if (args.length != 1) {
            Messages.send(watcher, Messages.USAGE);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            Messages.send(watcher, Messages.NOT_FOUND);
            return true;
        }
        if (target.getUniqueId().equals(watcher.getUniqueId())) {
            Messages.send(watcher, Messages.CANNOT_WATCH_SELF);
            return true;
        }
        if (manager.isWatching(target.getUniqueId())) {
            Messages.send(watcher, Messages.TARGET_IS_WATCHING);
            return true;
        }
        // Only the watcher's own GSit pose blocks the command; the target's pose
        // is irrelevant. Skipped silently when GSit is absent.
        if (gsit.isInPose(watcher)) {
            Messages.send(watcher, Messages.CANNOT_WATCH_WHILE_SITTING);
            return true;
        }

        boolean started = manager.start(watcher, target);
        Messages.send(watcher,
                started ? Messages.WATCH_STARTED : Messages.WATCH_SWITCHED,
                Messages.player(target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !(sender instanceof Player watcher) || !RankUtil.canWatch(watcher)) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase();
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(watcher.getUniqueId())) {
                continue;
            }
            if (online.getName().toLowerCase().startsWith(prefix)) {
                names.add(online.getName());
            }
        }
        Collections.sort(names);
        return names;
    }
}
