package com.pvpcraft.watch.command;

import com.pvpcraft.watch.Messages;
import com.pvpcraft.watch.WatchManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /unwatch} — stop watching and restore the watcher's saved gamemode and
 * location. No target argument needed.
 */
public final class UnwatchCommand implements CommandExecutor {

    private final WatchManager manager;

    public UnwatchCommand(WatchManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player watcher)) {
            Messages.send(sender, Messages.PLAYER_ONLY);
            return true;
        }
        if (!manager.isWatching(watcher.getUniqueId())) {
            Messages.send(watcher, Messages.NOT_WATCHING);
            return true;
        }
        manager.stopAndRestore(watcher);
        Messages.send(watcher, Messages.WATCH_STOPPED);
        return true;
    }
}
