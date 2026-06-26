package com.pvpcraft.dimensioncontrol.command;

import com.pvpcraft.dimensioncontrol.DimensionControl;
import com.pvpcraft.dimensioncontrol.util.RankUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /dimension &lt;enable|disable&gt; &lt;nether|end&gt;, /dimension status and
 * /dimension reload. Access is gated by the dimensioncontrol.admin permission
 * declared on the command in plugin.yml.
 */
public final class DimensionCommand implements TabExecutor {

    private final DimensionControl plugin;

    public DimensionCommand(DimensionControl plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Only Admin / Developer / Owner (plus console / op) may use /dimension.
        if (!RankUtil.canManage(sender)) {
            plugin.send(sender, "&cOnly Admins, Developers and the Owner may change the dimension status.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable" -> handleToggle(sender, args, true);
            case "disable" -> handleToggle(sender, args, false);
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }
        String dimension = args[1].toLowerCase(Locale.ROOT);
        switch (dimension) {
            case "nether" -> {
                plugin.setNetherEnabled(enable);
                plugin.send(sender, "&eThe Nether is now " + (enable ? "&aenabled" : "&cdisabled") + "&e.");
            }
            case "end" -> {
                plugin.setEndEnabled(enable);
                plugin.send(sender, "&eThe End is now " + (enable ? "&aenabled" : "&cdisabled") + "&e.");
            }
            default -> plugin.send(sender, "&cUnknown dimension: &f" + args[1] + "&c. Use &fnether &cor &fend&c.");
        }
    }

    private void handleStatus(CommandSender sender) {
        plugin.send(sender, "&6DimensionControl status:");
        plugin.send(sender, "  &7Nether: " + (plugin.isNetherEnabled() ? "&aenabled" : "&cdisabled"));
        plugin.send(sender, "  &7End: " + (plugin.isEndEnabled() ? "&aenabled" : "&cdisabled"));
    }

    private void handleReload(CommandSender sender) {
        plugin.loadState();
        plugin.send(sender, "&aConfiguration reloaded. &7(Nether: "
                + (plugin.isNetherEnabled() ? "enabled" : "disabled")
                + ", End: " + (plugin.isEndEnabled() ? "enabled" : "disabled") + ")");
    }

    private void sendUsage(CommandSender sender) {
        plugin.send(sender, "&6/dimension &7commands:");
        plugin.send(sender, "  &f/dimension disable <nether|end>");
        plugin.send(sender, "  &f/dimension enable <nether|end>");
        plugin.send(sender, "  &f/dimension status");
        plugin.send(sender, "  &f/dimension reload");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!RankUtil.canManage(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("enable", "disable", "status", "reload"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("enable") || sub.equals("disable")) {
                return filter(List.of("nether", "end"), args[1]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
