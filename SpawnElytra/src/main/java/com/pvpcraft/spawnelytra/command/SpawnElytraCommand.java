package com.pvpcraft.spawnelytra.command;

import com.pvpcraft.spawnelytra.LaunchManager;
import com.pvpcraft.spawnelytra.SpawnElytra;
import com.pvpcraft.spawnelytra.selection.SelectionManager;
import com.pvpcraft.spawnelytra.zone.Zone;
import com.pvpcraft.spawnelytra.zone.ZoneManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /spawnelytra} with the sub-commands wand, setzone, removezone,
 * reload and listzone. All require the {@code spawnelytra.admin} permission.
 */
public final class SpawnElytraCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "spawnelytra.admin";
    private static final List<String> SUBCOMMANDS =
            List.of("wand", "setzone", "removezone", "reload", "listzone");

    private final SpawnElytra plugin;
    private final ZoneManager zones;
    private final LaunchManager launches;
    private final SelectionManager selection;

    public SpawnElytraCommand(SpawnElytra plugin, ZoneManager zones,
                              LaunchManager launches, SelectionManager selection) {
        this.plugin = plugin;
        this.zones = zones;
        this.launches = launches;
        this.selection = selection;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.send(sender, "<red>Dazu hast du keine Berechtigung.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> handleWand(sender);
            case "setzone" -> handleSetZone(sender, args);
            case "removezone" -> handleRemoveZone(sender, args);
            case "reload" -> handleReload(sender);
            case "listzone" -> handleListZone(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "<red>Nur Spieler können das Auswahl-Tool erhalten.");
            return;
        }
        player.getInventory().addItem(selection.createWand());
        plugin.send(player, "<green>Auswahl-Tool erhalten. <gray>Links-Klick = Punkt 1, "
                + "Rechts-Klick = Punkt 2, dann <white>/spawnelytra setzone <name></white>.");
    }

    private void handleSetZone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "<red>Nur Spieler können Zonen setzen.");
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, "<red>Verwendung: <white>/spawnelytra setzone <name> [radius]");
            return;
        }
        String name = args[1];

        // Two args -> cuboid zone from the wand selection.
        if (args.length == 2) {
            setCuboidZone(player, name);
            return;
        }

        // Three args -> spherical zone at the player's location (legacy behaviour).
        double radius;
        try {
            radius = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            plugin.send(sender, "<red>Radius muss eine Zahl sein.");
            return;
        }
        if (radius <= 0) {
            plugin.send(sender, "<red>Radius muss größer als 0 sein.");
            return;
        }
        zones.setSphereZone(name, player.getLocation(), radius);
        plugin.send(sender, "<green>Kugel-Zone <white>" + name
                + "</white> gesetzt (Radius <white>" + radius + "</white> Blöcke).");
    }

    private void setCuboidZone(Player player, String name) {
        if (!selection.hasCompleteSelection(player.getUniqueId())) {
            plugin.send(player, "<red>Keine vollständige Auswahl. <gray>Hol dir das Tool mit "
                    + "<white>/spawnelytra wand</white> und setze beide Punkte.");
            return;
        }
        Location a = selection.pos1(player.getUniqueId());
        Location b = selection.pos2(player.getUniqueId());
        zones.setCuboidZone(name, a, b);
        plugin.send(player, "<green>Quader-Zone <white>" + name + "</white> gesetzt.");
    }

    private void handleRemoveZone(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.send(sender, "<red>Verwendung: <white>/spawnelytra removezone <name>");
            return;
        }
        if (zones.removeZone(args[1])) {
            plugin.send(sender, "<green>Zone <white>" + args[1] + "</white> entfernt.");
        } else {
            plugin.send(sender, "<red>Zone <white>" + args[1] + "</white> existiert nicht.");
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        zones.load();
        plugin.send(sender, "<green>Konfiguration neu geladen (<white>"
                + zones.all().size() + "</white> Zonen).");
    }

    private void handleListZone(CommandSender sender) {
        if (zones.all().isEmpty()) {
            plugin.send(sender, "<gray>Es sind keine Launch-Zonen definiert.");
            return;
        }
        plugin.send(sender, "<gray>Launch-Zonen (<white>" + zones.all().size() + "</white>):");
        for (Zone zone : zones.all()) {
            plugin.sendRaw(sender, "<dark_gray>- <aqua>" + zone.name() + " " + zone.describe());
        }
    }

    private void sendUsage(CommandSender sender) {
        plugin.send(sender, "<gray>Befehle:");
        plugin.sendRaw(sender, "<dark_gray>- <white>/spawnelytra wand <gray>(Auswahl-Tool für 2 Punkte)");
        plugin.sendRaw(sender, "<dark_gray>- <white>/spawnelytra setzone <name> <gray>(Quader aus Auswahl)");
        plugin.sendRaw(sender, "<dark_gray>- <white>/spawnelytra setzone <name> <radius> <gray>(Kugel)");
        plugin.sendRaw(sender, "<dark_gray>- <white>/spawnelytra removezone <name>");
        plugin.sendRaw(sender, "<dark_gray>- <white>/spawnelytra listzone");
        plugin.sendRaw(sender, "<dark_gray>- <white>/spawnelytra reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("removezone")) {
            List<String> names = new ArrayList<>();
            zones.all().forEach(z -> names.add(z.name()));
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
