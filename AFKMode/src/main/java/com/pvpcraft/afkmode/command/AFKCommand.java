package com.pvpcraft.afkmode.command;

import com.pvpcraft.afkmode.AFKManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Handles both {@code /afk} (activate) and {@code /afkstopp} (deactivate),
 * dispatched by command label.
 */
public final class AFKCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AFKManager afk;

    public AFKCommand(AFKManager afk) {
        this.afk = afk;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Nur Spieler koennen diesen Befehl nutzen."));
            return true;
        }

        if (command.getName().toLowerCase(Locale.ROOT).equals("afkstopp")) {
            afk.deactivate(player, true);
            return true;
        }

        // /afk
        Component error = afk.tryActivate(player);
        if (error != null) {
            player.sendMessage(error);
        }
        return true;
    }
}
