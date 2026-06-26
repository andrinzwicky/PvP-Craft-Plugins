package com.pvpcraft.shopchest.command;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.gui.ShopGui;
import com.pvpcraft.shopchest.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /shop — opens the personal shop management overview. */
public class ShopCommand implements CommandExecutor {

    private final ShopChestPlugin plugin;

    public ShopCommand(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.prefixed("<red>Nur Spieler können das Shop-Menü öffnen."));
            return true;
        }
        ShopGui.openManagement(plugin, player);
        return true;
    }
}
