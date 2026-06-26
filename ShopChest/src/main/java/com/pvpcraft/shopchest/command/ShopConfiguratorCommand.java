package com.pvpcraft.shopchest.command;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.util.ItemUtil;
import com.pvpcraft.shopchest.util.Msg;
import com.pvpcraft.shopchest.util.RankUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /shopconfigurator give &lt;player&gt; — hand out the Shop Konfigurator.
 * Restricted to shopchest.admin / Owner / Admin / Developer (not Moderator).
 */
public class ShopConfiguratorCommand implements CommandExecutor, TabCompleter {

    private final ShopChestPlugin plugin;

    public ShopConfiguratorCommand(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender sender) {
        if (sender instanceof Player player) {
            return RankUtil.canManageConfigurator(player);
        }
        // Console / command blocks: gate on the permission node.
        return sender.hasPermission("shopchest.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage(Msg.prefixed("<red>Dazu hast du keine Berechtigung. <gray>(nur Owner/Admin/Developer)"));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Msg.prefixed("<gold>/shopconfigurator give <player>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Msg.prefixed("<red>Spieler nicht online: <white>" + args[1]));
            return true;
        }
        target.getInventory().addItem(ItemUtil.configurator(plugin.keys(),
                plugin.configuratorMaterial(), plugin.configuratorModelData()));
        sender.sendMessage(Msg.prefixed("<green><player> hat den Shop Konfigurator erhalten.",
                Placeholder.unparsed("player", target.getName())));
        target.sendMessage(Msg.prefixed("<green>Du hast den <gold>Shop Konfigurator</gold> erhalten. <gray>Rechtsklick auf eine Truhe auf deinem Plot."));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!allowed(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return "give".startsWith(args[0].toLowerCase()) ? List.of("give") : List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String lower = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower)).collect(Collectors.toList());
        }
        return List.of();
    }
}
