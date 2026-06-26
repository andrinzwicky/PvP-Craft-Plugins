package com.pvpcraft.spawn.command;

import com.pvpcraft.spawn.SpawnTP;
import com.pvpcraft.spawn.util.RankUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** /spawn — begins a warmup teleport to the main world spawn. */
public final class SpawnCommand implements CommandExecutor {

    private final SpawnTP plugin;

    public SpawnCommand(SpawnTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg(sender, "only-players");
            return true;
        }
        if (!player.hasPermission("spawn.use")) {
            plugin.msg(player, "no-permission");
            return true;
        }

        UUID id = player.getUniqueId();
        if (plugin.warmups().isPending(id)) {
            plugin.msg(player, "already");
            return true;
        }

        if (!RankUtil.bypassesCooldown(player)) {
            long remainingMillis = plugin.warmups().cooldownRemainingMillis(id);
            if (remainingMillis > 0) {
                long seconds = (remainingMillis + 999L) / 1000L; // round up
                plugin.msg(player, "cooldown",
                        Placeholder.unparsed("seconds", String.valueOf(seconds)));
                return true;
            }
        }

        plugin.warmups().begin(player);
        plugin.msg(player, "start",
                Placeholder.unparsed("seconds", String.valueOf(plugin.warmupSeconds())));
        return true;
    }
}
