package com.pvpcraft.clansystem.command;

import com.pvpcraft.clansystem.ClanManager;
import com.pvpcraft.clansystem.ClanSystem;
import com.pvpcraft.clansystem.ColorUtil;
import com.pvpcraft.clansystem.InviteManager;
import com.pvpcraft.clansystem.Messages;
import com.pvpcraft.clansystem.display.DisplayManager;
import com.pvpcraft.clansystem.gui.InviteGui;
import com.pvpcraft.clansystem.model.Clan;
import com.pvpcraft.clansystem.model.ClanRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{2,16}");
    private static final int MAX_TAG_LENGTH = 10;

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "invite", "accept", "kick", "leave",
            "setadmin", "removeadmin", "info", "setcolor", "settag");

    private final ClanSystem plugin;

    public ClanCommand(ClanSystem plugin) {
        this.plugin = plugin;
    }

    private ClanManager clans() {
        return plugin.getClanManager();
    }

    private InviteManager invites() {
        return plugin.getInviteManager();
    }

    private DisplayManager display() {
        return plugin.getDisplayManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, Messages.PLAYER_ONLY);
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create"      -> handleCreate(player, args);
            case "delete"      -> handleDelete(player);
            case "invite"      -> handleInvite(player, args);
            case "accept"      -> handleAccept(player);
            case "kick"        -> handleKick(player, args);
            case "leave"       -> handleLeave(player);
            case "setadmin"    -> handleSetAdmin(player, args);
            case "removeadmin" -> handleRemoveAdmin(player, args);
            case "info"        -> handleInfo(player);
            case "setcolor"    -> handleSetColor(player, args);
            case "settag"      -> handleSetTag(player, args);
            default            -> Messages.send(player, Messages.UNKNOWN_SUB);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan create <name></yellow>");
            return;
        }
        if (clans().getClan(player.getUniqueId()) != null) {
            Messages.send(player, Messages.ALREADY_IN);
            return;
        }
        String name = args[1];
        if (!NAME_PATTERN.matcher(name).matches()) {
            Messages.send(player, Messages.NAME_INVALID);
            return;
        }
        if (clans().nameExists(name)) {
            Messages.send(player, Messages.NAME_TAKEN);
            return;
        }

        clans().createClan(name, player.getUniqueId());
        display().update(player);
        Messages.send(player, Messages.CLAN_CREATED, Messages.p("name", name));
    }

    private void handleDelete(Player player) {
        Clan clan = clans().getClan(player.getUniqueId());
        if (clan == null) {
            Messages.send(player, Messages.NOT_IN);
            return;
        }
        if (clan.roleOf(player.getUniqueId()) != ClanRole.OWNER) {
            Messages.send(player, Messages.NO_PERMISSION);
            return;
        }

        List<UUID> members = new ArrayList<>(clan.allMembers());
        String name = clan.name();
        invites().purgeClan(clan.key());
        clans().disband(clan);

        // Notify and reset display for every online member.
        for (UUID uuid : members) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Messages.send(online, Messages.DISBANDED, Messages.p("name", name));
                display().update(online);
            }
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan invite <player></yellow>");
            return;
        }
        Clan clan = clans().getClan(player.getUniqueId());
        if (clan == null) {
            Messages.send(player, Messages.NOT_IN);
            return;
        }
        if (!clan.roleOf(player.getUniqueId()).canManageMembers()) {
            Messages.send(player, Messages.NO_PERMISSION);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Messages.send(player, Messages.PLAYER_NOT_FOUND);
            return;
        }
        if (target.equals(player)) {
            Messages.send(player, Messages.CANNOT_TARGET_SELF);
            return;
        }
        if (clans().getClan(target.getUniqueId()) != null) {
            Messages.send(player, Messages.TARGET_IN_CLAN, Messages.p("player", target.getName()));
            return;
        }

        invites().invite(target.getUniqueId(), clan.key());
        Messages.send(player, Messages.INVITED, Messages.p("player", target.getName()));
        Messages.send(target, Messages.INVITE_RECEIVED, Messages.p("name", clan.name()));
    }

    private void handleAccept(Player player) {
        if (clans().getClan(player.getUniqueId()) != null) {
            Messages.send(player, Messages.ALREADY_IN);
            return;
        }

        List<Clan> pending = new ArrayList<>();
        for (String key : invites().validInvites(player.getUniqueId())) {
            Clan clan = clans().getClanByKey(key);
            if (clan != null) {
                pending.add(clan);
            }
        }
        if (pending.isEmpty()) {
            Messages.send(player, Messages.NO_INVITES);
            return;
        }

        InviteGui.open(plugin, player, pending);
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan kick <player></yellow>");
            return;
        }
        Clan clan = clans().getClan(player.getUniqueId());
        if (clan == null) {
            Messages.send(player, Messages.NOT_IN);
            return;
        }
        ClanRole actorRole = clan.roleOf(player.getUniqueId());
        if (!actorRole.canManageMembers()) {
            Messages.send(player, Messages.NO_PERMISSION);
            return;
        }

        UUID target = findClanMemberByName(clan, args[1]);
        if (target == null) {
            Messages.send(player, Messages.TARGET_NOT_IN_CLAN, Messages.p("player", args[1]));
            return;
        }
        if (target.equals(player.getUniqueId())) {
            Messages.send(player, Messages.CANNOT_TARGET_SELF);
            return;
        }
        ClanRole targetRole = clan.roleOf(target);
        // Owner can kick Leaders and Members; a Leader can only kick plain Members.
        if (targetRole == ClanRole.OWNER
                || (actorRole == ClanRole.LEADER && targetRole != ClanRole.MEMBER)) {
            Messages.send(player, Messages.NO_PERMISSION);
            return;
        }

        String targetName = nameOf(target);
        clans().removeMember(clan, target);
        invites().purgeClan(clan.key()); // (no-op for them; keeps things tidy)

        Player onlineTarget = Bukkit.getPlayer(target);
        if (onlineTarget != null) {
            display().update(onlineTarget);
            Messages.send(onlineTarget, "<red>✗ Du wurdest aus dem Clan <gold><name></gold> entfernt.",
                    Messages.p("name", clan.name()));
        }
        Messages.send(player, Messages.KICKED, Messages.p("player", targetName));
    }

    private void handleLeave(Player player) {
        Clan clan = clans().getClan(player.getUniqueId());
        if (clan == null) {
            Messages.send(player, Messages.NOT_IN);
            return;
        }
        if (clan.roleOf(player.getUniqueId()) == ClanRole.OWNER) {
            Messages.send(player, Messages.OWNER_CANT_LEAVE);
            return;
        }

        String name = clan.name();
        clans().removeMember(clan, player.getUniqueId());
        display().update(player);
        Messages.send(player, Messages.LEFT, Messages.p("name", name));
        broadcast(clan, "<gray><player> hat den Clan verlassen.", player.getUniqueId(),
                Messages.p("player", player.getName()));
    }

    private void handleSetAdmin(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan setadmin <player></yellow>");
            return;
        }
        Clan clan = requireOwner(player);
        if (clan == null) {
            return;
        }
        UUID target = findClanMemberByName(clan, args[1]);
        if (target == null) {
            Messages.send(player, Messages.TARGET_NOT_IN_CLAN, Messages.p("player", args[1]));
            return;
        }
        if (!clan.promote(target)) {
            Messages.send(player, Messages.NOT_A_MEMBER_ROLE, Messages.p("player", nameOf(target)));
            return;
        }
        clans().update(clan);
        Messages.send(player, Messages.PROMOTED, Messages.p("player", nameOf(target)));
        notify(target, "<green>✔ Du wurdest im Clan <gold><name></gold> zum Leader befördert.",
                Messages.p("name", clan.name()));
    }

    private void handleRemoveAdmin(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan removeadmin <player></yellow>");
            return;
        }
        Clan clan = requireOwner(player);
        if (clan == null) {
            return;
        }
        UUID target = findClanMemberByName(clan, args[1]);
        if (target == null) {
            Messages.send(player, Messages.TARGET_NOT_IN_CLAN, Messages.p("player", args[1]));
            return;
        }
        if (!clan.demote(target)) {
            Messages.send(player, Messages.NOT_A_MEMBER_ROLE, Messages.p("player", nameOf(target)));
            return;
        }
        clans().update(clan);
        Messages.send(player, Messages.DEMOTED, Messages.p("player", nameOf(target)));
        notify(target, "<yellow>Du wurdest im Clan <gold><name></gold> zum Member degradiert.",
                Messages.p("name", clan.name()));
    }

    private void handleInfo(Player player) {
        Clan clan = clans().getClan(player.getUniqueId());
        if (clan == null) {
            Messages.send(player, Messages.NOT_IN);
            return;
        }

        Messages.send(player, "<gold>— Clan <name> —", Messages.p("name", clan.name()));
        player.sendMessage(Messages.mm("<gray>Tag: ").append(display().clanTag(clan)));
        Messages.send(player, "<gray>Farbe: <color:" + clan.colorHex() + ">" + clan.colorHex() + "</color>");
        Messages.send(player, "<gray>Mitglieder: <white><count></white>",
                Messages.p("count", String.valueOf(clan.size())));

        Messages.send(player, "<gold>Owner: <white><name>", Messages.p("name", nameOf(clan.owner())));
        if (!clan.leaders().isEmpty()) {
            Messages.send(player, "<yellow>Leader: <white><names>",
                    Messages.p("names", joinNames(clan.leaders())));
        }
        if (!clan.members().isEmpty()) {
            Messages.send(player, "<gray>Member: <white><names>",
                    Messages.p("names", joinNames(clan.members())));
        }
    }

    private void handleSetColor(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan setcolor <color></yellow>");
            return;
        }
        Clan clan = requireOwner(player);
        if (clan == null) {
            return;
        }
        String hex = ColorUtil.toHex(args[1]);
        if (hex == null) {
            Messages.send(player, Messages.COLOR_INVALID);
            return;
        }
        clan.setColorHex(hex);
        clans().update(clan);
        refreshClan(clan);
        Messages.send(player, "<green>✔ Clan-Farbe gesetzt: <color:" + hex + ">■ " + hex + "</color>");
    }

    private void handleSetTag(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>✗ Nutzung: <yellow>/clan settag <tag></yellow>");
            return;
        }
        Clan clan = requireOwner(player);
        if (clan == null) {
            return;
        }
        String tag = args[1];
        if (tag.isBlank()) {
            Messages.send(player, Messages.TAG_EMPTY);
            return;
        }
        if (tag.length() > MAX_TAG_LENGTH) {
            Messages.send(player, Messages.TAG_TOO_LONG);
            return;
        }
        clan.setTag(tag);
        clans().update(clan);
        refreshClan(clan);
        player.sendMessage(Messages.mm("<green>✔ Clan-Tag gesetzt: ").append(display().clanTag(clan)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Returns the player's clan iff they are its Owner, else messages and null. */
    private Clan requireOwner(Player player) {
        Clan clan = clans().getClan(player.getUniqueId());
        if (clan == null) {
            Messages.send(player, Messages.NOT_IN);
            return null;
        }
        if (clan.roleOf(player.getUniqueId()) != ClanRole.OWNER) {
            Messages.send(player, Messages.NO_PERMISSION);
            return null;
        }
        return clan;
    }

    /** Finds a clan member whose (cached) name matches, online or offline. */
    private UUID findClanMemberByName(Clan clan, String name) {
        for (UUID uuid : clan.allMembers()) {
            String memberName = nameOf(uuid);
            if (memberName != null && memberName.equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        return null;
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = off.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private String joinNames(Iterable<UUID> uuids) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : uuids) {
            names.add(nameOf(uuid));
        }
        return String.join(", ", names);
    }

    /** Reapplies tab name + metadata for every online member of the clan. */
    private void refreshClan(Clan clan) {
        for (UUID uuid : clan.allMembers()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                display().update(online);
            }
        }
    }

    /** Sends a message to a clan member if they are online. */
    private void notify(UUID uuid, String raw, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Messages.send(online, raw, r);
        }
    }

    /** Broadcasts to all online clan members except {@code except}. */
    private void broadcast(Clan clan, String raw, UUID except,
                           net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        for (UUID uuid : clan.allMembers()) {
            if (uuid.equals(except)) {
                continue;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Messages.send(online, raw, r);
            }
        }
    }

    private void sendUsage(Player player) {
        Messages.send(player, Messages.USAGE_HEADER);
        Messages.send(player, "<yellow>/clan create <name> <gray>- Clan erstellen");
        Messages.send(player, "<yellow>/clan invite <player> <gray>- Spieler einladen");
        Messages.send(player, "<yellow>/clan accept <gray>- Einladung annehmen");
        Messages.send(player, "<yellow>/clan kick <player> <gray>- Mitglied entfernen");
        Messages.send(player, "<yellow>/clan leave <gray>- Clan verlassen");
        Messages.send(player, "<yellow>/clan setadmin/removeadmin <player> <gray>- Rolle ändern");
        Messages.send(player, "<yellow>/clan settag <tag> <gray>- Tag setzen");
        Messages.send(player, "<yellow>/clan setcolor <color> <gray>- Farbe setzen");
        Messages.send(player, "<yellow>/clan info <gray>- Clan-Infos");
        Messages.send(player, "<yellow>/clan delete <gray>- Clan auflösen");
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "invite" -> {
                    return filter(onlinePlayerNames(), args[1]);
                }
                case "kick", "setadmin", "removeadmin" -> {
                    Clan clan = clans().getClan(player.getUniqueId());
                    if (clan != null) {
                        List<String> names = new ArrayList<>();
                        for (UUID uuid : clan.allMembers()) {
                            if (!uuid.equals(player.getUniqueId())) {
                                names.add(nameOf(uuid));
                            }
                        }
                        return filter(names, args[1]);
                    }
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
