package com.pvpcraft.shopchest.command;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.model.Plot;
import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.ItemUtil;
import com.pvpcraft.shopchest.util.Msg;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** /shopplot — admin management of shop plots. */
public class ShopPlotCommand implements CommandExecutor, TabCompleter {

    private final ShopChestPlugin plugin;

    public ShopPlotCommand(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shopchest.admin")) {
            sender.sendMessage(Msg.prefixed("<red>Dazu hast du keine Berechtigung."));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "wand" -> giveWand(sender);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "assign" -> assign(sender, args);
            case "addmember" -> changeMember(sender, args, true);
            case "removemember" -> changeMember(sender, args, false);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void giveWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.prefixed("<red>Nur Spieler können den Selektor erhalten."));
            return;
        }
        player.getInventory().addItem(ItemUtil.selector(plugin.keys(), plugin.selectorMaterial()));
        player.sendMessage(Msg.prefixed("<green>Du hast den Plot-Selektor erhalten. <gray>Linksklick = Pos 1, Rechtsklick = Pos 2."));
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.prefixed("<red>Nur Spieler können Plots erstellen (Auswahl nötig)."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/shopplot create <id>"));
            return;
        }
        String id = args[1];
        if (plugin.plots().exists(id)) {
            sender.sendMessage(Msg.prefixed("<red>Ein Plot mit dieser ID existiert bereits."));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!plugin.plots().hasSelection(uuid)) {
            sender.sendMessage(Msg.prefixed("<red>Du hast keine vollständige Auswahl. Nutze <yellow>/shopplot wand</yellow>."));
            return;
        }
        Location p1 = plugin.plots().getPos1(uuid);
        Location p2 = plugin.plots().getPos2(uuid);
        if (p1.getWorld() == null || p2.getWorld() == null
                || !p1.getWorld().equals(p2.getWorld())) {
            sender.sendMessage(Msg.prefixed("<red>Beide Positionen müssen in derselben Welt liegen."));
            return;
        }
        Plot plot = new Plot(id, p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ(), null);
        plugin.plots().addPlot(plot);
        plugin.plots().clearSelection(uuid);
        sender.sendMessage(Msg.prefixed("<green>Plot <gold><id></gold> erstellt. <gray>(<vol> Blöcke) Nutze /shopplot assign zum Zuweisen.",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("vol", String.valueOf(plot.getVolume()))));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/shopplot delete <id>"));
            return;
        }
        String id = args[1];
        if (!plugin.plots().exists(id)) {
            sender.sendMessage(Msg.prefixed("<red>Kein Plot mit dieser ID."));
            return;
        }
        plugin.plots().removePlot(id);
        plugin.shops().removeShopsOnPlot(id);
        sender.sendMessage(Msg.prefixed("<green>Plot <gold><id></gold> und zugehörige Shops gelöscht.",
                Placeholder.unparsed("id", id)));
    }

    private void assign(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.prefixed("<red>/shopplot assign <id> <player>"));
            return;
        }
        String id = args[1];
        Plot plot = plugin.plots().getPlot(id);
        if (plot == null) {
            sender.sendMessage(Msg.prefixed("<red>Kein Plot mit dieser ID."));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Msg.prefixed("<red>Spieler nicht gefunden: <white>" + args[2]));
            return;
        }
        UUID previousOwner = plot.getOwner();
        plot.setOwner(target.getUniqueId());
        plot.getMembers().remove(target.getUniqueId());
        plugin.plots().save();
        // Mirror the ownership change into the WorldGuard region "plot_<id>":
        // drop the old owner from members, add the new one.
        plugin.worldGuard().syncOwnerChange(plot, previousOwner, target.getUniqueId());
        sender.sendMessage(Msg.prefixed("<green>Plot <gold><id></gold> gehört jetzt <gold><player></gold>.",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("player", target.getName() == null ? args[2] : target.getName())));
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(Msg.prefixed("<green>Dir wurde das Plot <gold><id></gold> zugewiesen. <gray>Platziere Truhen und nutze den Konfigurator.",
                    Placeholder.unparsed("id", id)));
        }
    }

    /** /shopplot addmember|removemember <id> <player> — manage plot members and sync the region. */
    private void changeMember(CommandSender sender, String[] args, boolean add) {
        String verb = add ? "addmember" : "removemember";
        if (args.length < 3) {
            sender.sendMessage(Msg.prefixed("<red>/shopplot " + verb + " <id> <player>"));
            return;
        }
        String id = args[1];
        Plot plot = plugin.plots().getPlot(id);
        if (plot == null) {
            sender.sendMessage(Msg.prefixed("<red>Kein Plot mit dieser ID."));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Msg.prefixed("<red>Spieler nicht gefunden: <white>" + args[2]));
            return;
        }
        UUID targetId = target.getUniqueId();
        String name = target.getName() == null ? args[2] : target.getName();
        if (add) {
            if (plot.isOwner(targetId)) {
                sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist bereits Besitzer des Plots."));
                return;
            }
            if (!plot.getMembers().add(targetId)) {
                sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist bereits Mitglied."));
                return;
            }
            plugin.plots().save();
            plugin.worldGuard().addMember(plot, targetId);
            sender.sendMessage(Msg.prefixed("<green><player> ist jetzt Mitglied von Plot <gold><id></gold>.",
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("id", id)));
        } else {
            if (!plot.getMembers().remove(targetId)) {
                sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist kein Mitglied des Plots."));
                return;
            }
            plugin.plots().save();
            plugin.worldGuard().removeMember(plot, targetId);
            sender.sendMessage(Msg.prefixed("<green><player> wurde aus Plot <gold><id></gold> entfernt.",
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("id", id)));
        }
    }

    private void list(CommandSender sender) {
        var plots = plugin.plots().getPlots();
        if (plots.isEmpty()) {
            sender.sendMessage(Msg.prefixed("<gray>Es gibt noch keine Plots."));
            return;
        }
        sender.sendMessage(Msg.prefixed("<gold>Plots (<count>):", Placeholder.unparsed("count", String.valueOf(plots.size()))));
        for (Plot plot : plots) {
            String owner = ownerName(plot.getOwner());
            sender.sendMessage(Msg.mm("<dark_gray>• <yellow><id></yellow> <gray>in <white><world></white> — Besitzer: <aqua><owner></aqua> <dark_gray>(<shops> Shops)",
                    Placeholder.unparsed("id", plot.getId()),
                    Placeholder.unparsed("world", plot.getWorld()),
                    Placeholder.unparsed("owner", owner),
                    Placeholder.unparsed("shops", String.valueOf(plugin.shops().getShopsOnPlot(plot.getId()).size()))));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/shopplot info <id>"));
            return;
        }
        Plot plot = plugin.plots().getPlot(args[1]);
        if (plot == null) {
            sender.sendMessage(Msg.prefixed("<red>Kein Plot mit dieser ID."));
            return;
        }
        sender.sendMessage(Msg.prefixed("<gold>Plot <yellow><id></yellow>", Placeholder.unparsed("id", plot.getId())));
        sender.sendMessage(Msg.mm("<gray>Welt: <white><world>", Placeholder.unparsed("world", plot.getWorld())));
        sender.sendMessage(Msg.mm("<gray>Von: <white><a></white> bis <white><b>",
                Placeholder.unparsed("a", plot.getMinX() + ", " + plot.getMinY() + ", " + plot.getMinZ()),
                Placeholder.unparsed("b", plot.getMaxX() + ", " + plot.getMaxY() + ", " + plot.getMaxZ())));
        sender.sendMessage(Msg.mm("<gray>Besitzer: <aqua><owner>", Placeholder.unparsed("owner", ownerName(plot.getOwner()))));
        List<String> memberNames = new ArrayList<>();
        for (UUID m : plot.getMembers()) {
            memberNames.add(ownerName(m));
        }
        sender.sendMessage(Msg.mm("<gray>Mitglieder: <white><members>",
                Placeholder.unparsed("members", memberNames.isEmpty() ? "—" : String.join(", ", memberNames))));
        List<Shop> shops = plugin.shops().getShopsOnPlot(plot.getId());
        sender.sendMessage(Msg.mm("<gray>Shops: <white><count>", Placeholder.unparsed("count", String.valueOf(shops.size()))));
    }

    private String ownerName(UUID uuid) {
        if (uuid == null) {
            return "—";
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Msg.prefixed("<gold>/shopplot</gold> <gray>Befehle:"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot wand <gray>- Selektor erhalten"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot create <id> <gray>- Plot aus Auswahl erstellen"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot delete <id>"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot assign <id> <player>"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot addmember <id> <player>"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot removemember <id> <player>"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot list"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/shopplot info <id>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("shopchest.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(Arrays.asList("wand", "create", "delete", "assign", "addmember", "removemember", "list", "info"), args[0]);
        }
        if (args.length == 2 && Arrays.asList("delete", "assign", "addmember", "removemember", "info").contains(args[0].toLowerCase())) {
            return filter(plugin.plots().getPlots().stream().map(Plot::getId).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && Arrays.asList("assign", "addmember", "removemember").contains(args[0].toLowerCase())) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
