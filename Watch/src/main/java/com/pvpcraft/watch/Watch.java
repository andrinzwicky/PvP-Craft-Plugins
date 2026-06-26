package com.pvpcraft.watch;

import com.pvpcraft.watch.command.UnwatchCommand;
import com.pvpcraft.watch.command.WatchCommand;
import com.pvpcraft.watch.listener.WatchListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Watch — lets staff observe a player in spectator mode while leashed to a
 * 100-block radius around that player. The watched player is never notified.
 */
public final class Watch extends JavaPlugin {

    private WatchStorage storage;
    private WatchManager manager;
    private GSitHook gsit;

    @Override
    public void onEnable() {
        this.storage = new WatchStorage(this);
        storage.init();

        this.gsit = new GSitHook(this);
        this.manager = new WatchManager(this, storage);

        WatchCommand watchCommand = new WatchCommand(manager, gsit);
        registerCommand("watch", watchCommand, watchCommand);
        registerCommand("unwatch", new UnwatchCommand(manager), null);

        getServer().getPluginManager().registerEvents(new WatchListener(this, manager), this);

        // Boundary / follow enforcement a few times per second.
        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 5L);

        // Recover anyone left mid-watch across a /reload (online players only;
        // offline watchers are restored on their next join).
        for (Player online : getServer().getOnlinePlayers()) {
            manager.handleWatcherJoin(online);
        }

        getLogger().info("Watch aktiviert." + (gsit.isPresent() ? " GSit-Integration aktiv." : ""));
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdownRestore();
        }
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                 org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Befehl /" + name + " ist nicht in der plugin.yml registriert.");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }
}
