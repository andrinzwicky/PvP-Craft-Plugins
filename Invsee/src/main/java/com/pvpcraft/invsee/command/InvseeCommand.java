package com.pvpcraft.invsee.command;

import com.pvpcraft.invsee.Invsee;
import com.pvpcraft.invsee.InventoryMirror;
import com.pvpcraft.invsee.Messages;
import com.pvpcraft.invsee.ViewSession;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles all four commands: {@code /invsee}, {@code /invsee+}, {@code /echest}
 * and {@code /echest+}. The command name decides the container (inventory vs
 * enderchest) and the mode (read-only vs editable).
 */
public final class InvseeCommand implements TabExecutor {

    private final Invsee plugin;

    public InvseeCommand(Invsee plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            Messages.send(sender, Messages.PREFIX + "<red>Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        boolean enderchest = name.startsWith("echest");
        boolean editable = name.endsWith("+");

        boolean allowed = editable ? plugin.canEdit(viewer) : plugin.canView(viewer);
        if (!allowed) {
            Messages.send(viewer, Messages.NO_PERMISSION);
            return true;
        }

        if (args.length < 1) {
            Messages.send(viewer, Messages.USAGE, Placeholder.unparsed("command", label));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
            if (cached != null && cached.hasPlayedBefore()) {
                Messages.send(viewer, Messages.OFFLINE);
            } else {
                Messages.send(viewer, Messages.NOT_FOUND);
            }
            return true;
        }

        ViewSession session = new ViewSession(target.getUniqueId(), editable);
        if (enderchest) {
            // Enderchests have no armor/offhand, so the live container is opened
            // directly: read-only clicks are cancelled, editable changes apply
            // straight to the real enderchest.
            viewer.openInventory(target.getEnderChest());
        } else {
            // Inventories use a 45-slot mirror so armor and offhand are visible.
            // Read-only mirrors are kept live by a repeating refresh; editable
            // mirrors are written back to the target on each change.
            Inventory mirror = InventoryMirror.build(target, editable);
            viewer.openInventory(mirror);
            if (!editable) {
                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    Player live = Bukkit.getPlayer(session.targetId());
                    if (live != null && live.isOnline()) {
                        InventoryMirror.refresh(mirror, live);
                    }
                }, 10L, 10L);
                session.setRefreshTask(task);
            }
        }
        plugin.sessions().open(viewer.getUniqueId(), session);

        String opened = enderchest ? Messages.OPENED_ENDERCHEST : Messages.OPENED_INVENTORY;
        Messages.send(viewer, opened, Messages.player(target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(online.getName());
            }
        }
        return names;
    }
}
