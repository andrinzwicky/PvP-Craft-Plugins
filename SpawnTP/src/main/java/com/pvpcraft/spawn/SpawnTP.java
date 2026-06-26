package com.pvpcraft.spawn;

import com.pvpcraft.spawn.command.SpawnCommand;
import com.pvpcraft.spawn.listener.CancelListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Adds /spawn: after a short countdown the player is teleported to the main
 * world's spawn. The countdown aborts if the player moves or takes damage, and
 * a cooldown prevents spamming once a teleport succeeds.
 */
public final class SpawnTP extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private WarmupManager warmups;
    private int warmupSeconds;
    private int cooldownSeconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        this.warmups = new WarmupManager(this);

        PluginCommand command = getCommand("spawn");
        if (command != null) {
            command.setExecutor(new SpawnCommand(this));
        } else {
            getLogger().severe("Command 'spawn' missing from plugin.yml!");
        }
        getServer().getPluginManager().registerEvents(new CancelListener(this), this);

        getLogger().info("SpawnTP enabled (warmup " + warmupSeconds + "s, cooldown " + cooldownSeconds + "s).");
    }

    private void loadSettings() {
        reloadConfig();
        this.warmupSeconds = Math.max(0, getConfig().getInt("warmup-seconds", 5));
        this.cooldownSeconds = Math.max(0, getConfig().getInt("cooldown-seconds", 60));
    }

    // --- accessors -------------------------------------------------------

    public WarmupManager warmups() {
        return warmups;
    }

    public int warmupSeconds() {
        return warmupSeconds;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Spawn of the main (first) world, or null if no world is loaded. */
    public Location mainSpawn() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        World main = Bukkit.getWorlds().get(0);
        return main.getSpawnLocation();
    }

    // --- messaging -------------------------------------------------------

    /** Sends a prefixed message from the config's messages section. */
    public void msg(CommandSender to, String key, TagResolver... resolvers) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw.isEmpty()) {
            return;
        }
        String prefix = getConfig().getString("messages.prefix", "");
        to.sendMessage(MM.deserialize(prefix + raw, resolvers));
    }

    /** Shows the per-second countdown in the player's action bar. */
    public void actionBarCountdown(Player player, int secondsLeft) {
        String raw = getConfig().getString("messages.countdown", "<yellow><seconds></yellow>");
        player.sendActionBar(MM.deserialize(raw,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                        "seconds", String.valueOf(secondsLeft))));
    }
}
