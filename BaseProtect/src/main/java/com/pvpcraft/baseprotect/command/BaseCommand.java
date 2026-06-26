package com.pvpcraft.baseprotect.command;

import com.pvpcraft.baseprotect.BaseProtectPlugin;
import com.pvpcraft.baseprotect.integration.WorldGuardService;
import com.pvpcraft.baseprotect.model.Base;
import com.pvpcraft.baseprotect.model.Role;
import com.pvpcraft.baseprotect.util.Msg;
import com.pvpcraft.baseprotect.util.RankUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
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

/** /base — define, manage and inspect player bases. */
public class BaseCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = List.of("define", "setowner", "delete");
    private static final List<String> ALL_SUBS = List.of(
            "define", "setowner", "delete", "setleader", "removeleader",
            "invite", "kick", "leave", "info");

    private final BaseProtectPlugin plugin;

    public BaseCommand(BaseProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "define" -> define(sender, args);
            case "setowner" -> setOwner(sender, args);
            case "delete" -> delete(sender, args);
            case "setleader" -> setLeader(sender, args);
            case "removeleader" -> removeLeader(sender, args);
            case "invite" -> invite(sender, args);
            case "kick" -> kick(sender, args);
            case "leave" -> leave(sender);
            case "info" -> info(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    // --- admin commands --------------------------------------------------

    /** /base define <id> — admin. Build the base_<id> region from the WorldEdit selection. */
    private void define(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.prefixed("<red>Nur Spieler können Basen definieren (WorldEdit-Auswahl nötig)."));
            return;
        }
        if (!RankUtil.isServerAdmin(player)) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base define <id>"));
            return;
        }
        String id = args[1];
        if (!isValidId(id)) {
            sender.sendMessage(Msg.prefixed("<red>Ungültige ID. Nur Buchstaben, Zahlen, - und _ erlaubt."));
            return;
        }
        if (plugin.bases().exists(id)) {
            sender.sendMessage(Msg.prefixed("<red>Eine Basis mit der ID <gold><id></gold> existiert bereits.",
                    Placeholder.unparsed("id", id)));
            return;
        }
        WorldGuardService.DefineResult result = plugin.worldGuard().defineFromSelection(player, id);
        if (!result.ok) {
            sender.sendMessage(Msg.prefixed("<red><err>", Placeholder.unparsed("err", result.error)));
            return;
        }
        // Region created (no members yet). Record the (still owner-less) base.
        Base base = new Base(id, result.world);
        plugin.bases().addBase(base);
        sender.sendMessage(Msg.prefixed("<green>Basis <gold><id></gold> in Welt <white><world></white> erstellt. "
                + "<gray>Setze einen Besitzer mit <yellow>/base setowner <id> <spieler></yellow>.",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("world", result.world)));
    }

    /** /base setowner <id> <player> — admin. Assign/replace the owner. */
    private void setOwner(CommandSender sender, String[] args) {
        if (!isServerAdmin(sender)) {
            noPermission(sender);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Msg.prefixed("<red>/base setowner <id> <spieler>"));
            return;
        }
        Base base = plugin.bases().getBase(args[1]);
        if (base == null) {
            unknownBase(sender, args[1]);
            return;
        }
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            playerNotFound(sender, args[2]);
            return;
        }
        UUID targetId = target.getUniqueId();
        UUID previousOwner = base.getOwner();
        if (targetId.equals(previousOwner)) {
            sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist bereits Besitzer der Basis."));
            return;
        }
        // Demote the previous owner to a plain member (kept in the base).
        if (previousOwner != null) {
            base.setRole(previousOwner, Role.MEMBER);
        }
        // Promote target: keep their join time if already a member, else join now.
        if (base.contains(targetId)) {
            base.setRole(targetId, Role.OWNER);
        } else {
            base.put(targetId, Role.OWNER, System.currentTimeMillis());
        }
        plugin.bases().save();
        plugin.worldGuard().syncMembers(base);

        sender.sendMessage(Msg.prefixed("<green>Besitzer von Basis <gold><id></gold> ist jetzt <gold><player></gold>.",
                Placeholder.unparsed("id", base.getId()),
                Placeholder.unparsed("player", nameOf(target, args[2]))));
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(Msg.prefixed("<green>Dir wurde die Basis <gold><id></gold> als Besitzer zugewiesen.",
                    Placeholder.unparsed("id", base.getId())));
        }
    }

    /** /base delete <id> — admin. Remove region AND data. */
    private void delete(CommandSender sender, String[] args) {
        if (!isServerAdmin(sender)) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base delete <id>"));
            return;
        }
        Base base = plugin.bases().getBase(args[1]);
        if (base == null) {
            unknownBase(sender, args[1]);
            return;
        }
        boolean regionRemoved = plugin.worldGuard().deleteRegion(base);
        plugin.bases().removeBase(base.getId());
        sender.sendMessage(Msg.prefixed("<green>Basis <gold><id></gold> gelöscht. <gray>(Region <region>, Daten entfernt)",
                Placeholder.unparsed("id", base.getId()),
                Placeholder.unparsed("region", regionRemoved ? "entfernt" : "nicht gefunden")));
    }

    // --- owner commands --------------------------------------------------

    /** /base setleader <player> — owner of the base the sender stands in. */
    private void setLeader(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base setleader <spieler>"));
            return;
        }
        Base base = baseAt(player);
        if (base == null) {
            notInBase(sender);
            return;
        }
        if (!base.isOwner(player.getUniqueId())) {
            sender.sendMessage(Msg.prefixed("<red>Nur der Besitzer der Basis kann Leader ernennen."));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            playerNotFound(sender, args[1]);
            return;
        }
        UUID targetId = target.getUniqueId();
        if (base.isOwner(targetId)) {
            sender.sendMessage(Msg.prefixed("<red>Der Besitzer ist bereits die höchste Rolle."));
            return;
        }
        if (!base.contains(targetId)) {
            sender.sendMessage(Msg.prefixed("<red><player> ist kein Mitglied dieser Basis. Lade ihn zuerst mit <yellow>/base invite</yellow> ein.",
                    Placeholder.unparsed("player", nameOf(target, args[1]))));
            return;
        }
        if (base.isLeader(targetId)) {
            sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist bereits Leader."));
            return;
        }
        base.setRole(targetId, Role.LEADER);
        plugin.bases().save();
        plugin.worldGuard().syncMembers(base); // role change, member set unchanged - kept for consistency
        sender.sendMessage(Msg.prefixed("<green><player> ist jetzt Leader der Basis <gold><id></gold>.",
                Placeholder.unparsed("player", nameOf(target, args[1])),
                Placeholder.unparsed("id", base.getId())));
        notify(target, "<green>Du bist jetzt Leader der Basis <gold>" + base.getId() + "</gold>.");
    }

    /** /base removeleader <player> — owner of the base the sender stands in. */
    private void removeLeader(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base removeleader <spieler>"));
            return;
        }
        Base base = baseAt(player);
        if (base == null) {
            notInBase(sender);
            return;
        }
        if (!base.isOwner(player.getUniqueId())) {
            sender.sendMessage(Msg.prefixed("<red>Nur der Besitzer der Basis kann Leader entfernen."));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            playerNotFound(sender, args[1]);
            return;
        }
        UUID targetId = target.getUniqueId();
        if (!base.isLeader(targetId)) {
            sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist kein Leader dieser Basis."));
            return;
        }
        base.setRole(targetId, Role.MEMBER); // demoted, stays a member
        plugin.bases().save();
        plugin.worldGuard().syncMembers(base);
        sender.sendMessage(Msg.prefixed("<green><player> ist kein Leader mehr (jetzt Mitglied).",
                Placeholder.unparsed("player", nameOf(target, args[1]))));
        notify(target, "<yellow>Du bist nicht mehr Leader der Basis <gold>" + base.getId() + "</gold> (jetzt Mitglied).");
    }

    // --- owner + leader commands -----------------------------------------

    /** /base invite <player> — owner/leader of the base the sender stands in. */
    private void invite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base invite <spieler>"));
            return;
        }
        Base base = baseAt(player);
        if (base == null) {
            notInBase(sender);
            return;
        }
        if (!isOwnerOrLeader(base, player.getUniqueId())) {
            sender.sendMessage(Msg.prefixed("<red>Nur Besitzer und Leader dürfen einladen."));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            playerNotFound(sender, args[1]);
            return;
        }
        UUID targetId = target.getUniqueId();
        if (base.contains(targetId)) {
            Role role = base.roleOf(targetId);
            sender.sendMessage(Msg.prefixed("<yellow><player> ist bereits <role> dieser Basis.",
                    Placeholder.unparsed("player", nameOf(target, args[1])),
                    Placeholder.unparsed("role", role.display())));
            return;
        }
        base.put(targetId, Role.MEMBER, System.currentTimeMillis());
        plugin.bases().save();
        plugin.worldGuard().syncMembers(base);
        sender.sendMessage(Msg.prefixed("<green><player> ist jetzt Mitglied der Basis <gold><id></gold>.",
                Placeholder.unparsed("player", nameOf(target, args[1])),
                Placeholder.unparsed("id", base.getId())));
        notify(target, "<green>Du wurdest in die Basis <gold>" + base.getId() + "</gold> eingeladen.");
    }

    /** /base kick <player> — owner/leader of the base the sender stands in. */
    private void kick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base kick <spieler>"));
            return;
        }
        Base base = baseAt(player);
        if (base == null) {
            notInBase(sender);
            return;
        }
        UUID actorId = player.getUniqueId();
        if (!isOwnerOrLeader(base, actorId)) {
            sender.sendMessage(Msg.prefixed("<red>Nur Besitzer und Leader dürfen kicken."));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            playerNotFound(sender, args[1]);
            return;
        }
        UUID targetId = target.getUniqueId();
        if (!base.contains(targetId)) {
            sender.sendMessage(Msg.prefixed("<red>Dieser Spieler ist kein Mitglied der Basis."));
            return;
        }
        if (base.isOwner(targetId)) {
            sender.sendMessage(Msg.prefixed("<red>Der Besitzer kann nicht gekickt werden."));
            return;
        }
        // A leader may not kick another leader - only the owner can remove leaders.
        if (base.isLeader(targetId) && !base.isOwner(actorId)) {
            sender.sendMessage(Msg.prefixed("<red>Nur der Besitzer kann Leader entfernen."));
            return;
        }
        base.remove(targetId);
        plugin.bases().save();
        plugin.worldGuard().syncMembers(base);
        sender.sendMessage(Msg.prefixed("<green><player> wurde aus der Basis <gold><id></gold> entfernt.",
                Placeholder.unparsed("player", nameOf(target, args[1])),
                Placeholder.unparsed("id", base.getId())));
        notify(target, "<red>Du wurdest aus der Basis <gold>" + base.getId() + "</gold> entfernt.");
    }

    /** /base leave — any member of the base the sender stands in (owner may not leave). */
    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        Base base = baseAt(player);
        if (base == null) {
            notInBase(sender);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!base.contains(uuid)) {
            sender.sendMessage(Msg.prefixed("<red>Du bist kein Mitglied dieser Basis."));
            return;
        }
        if (base.isOwner(uuid)) {
            sender.sendMessage(Msg.prefixed("<red>Als Besitzer kannst du die Basis nicht verlassen. Ein Admin muss dich via <yellow>/base setowner</yellow> oder <yellow>/base delete</yellow> entfernen."));
            return;
        }
        base.remove(uuid);
        plugin.bases().save();
        plugin.worldGuard().syncMembers(base);
        sender.sendMessage(Msg.prefixed("<green>Du hast die Basis <gold><id></gold> verlassen.",
                Placeholder.unparsed("id", base.getId())));
    }

    // --- info ------------------------------------------------------------

    /** /base info <id> — anyone. Show owner/leaders/members. */
    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.prefixed("<red>/base info <id>"));
            return;
        }
        Base base = plugin.bases().getBase(args[1]);
        if (base == null) {
            unknownBase(sender, args[1]);
            return;
        }
        sender.sendMessage(Msg.prefixed("<gold>Basis <yellow><id></yellow>", Placeholder.unparsed("id", base.getId())));
        sender.sendMessage(Msg.mm("<gray>Welt: <white><world>", Placeholder.unparsed("world", base.getWorld())));
        sender.sendMessage(Msg.mm("<gray>Besitzer: <aqua><owner>",
                Placeholder.unparsed("owner", base.getOwner() == null ? "— (ownerlos)" : nameOf(base.getOwner()))));
        sender.sendMessage(Msg.mm("<gray>Leader: <white><leaders>",
                Placeholder.unparsed("leaders", joinNames(base.getLeaders()))));
        sender.sendMessage(Msg.mm("<gray>Mitglieder: <white><members>",
                Placeholder.unparsed("members", joinNames(base.getPlainMembers()))));
    }

    // --- helpers ---------------------------------------------------------

    private Base baseAt(Player player) {
        String id = plugin.worldGuard().baseIdAt(player.getLocation());
        return id == null ? null : plugin.bases().getBase(id);
    }

    private boolean isOwnerOrLeader(Base base, UUID uuid) {
        return base.isOwner(uuid) || base.isLeader(uuid);
    }

    private boolean isServerAdmin(CommandSender sender) {
        // Console always counts as a server admin; players go through RankUtil.
        return !(sender instanceof Player player) || RankUtil.isServerAdmin(player);
    }

    private boolean isValidId(String id) {
        return id.matches("[A-Za-z0-9_-]+");
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String nameOf(OfflinePlayer player, String fallback) {
        return player.getName() == null ? fallback : player.getName();
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "—";
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
    }

    private String joinNames(List<UUID> uuids) {
        if (uuids.isEmpty()) {
            return "—";
        }
        List<String> names = new ArrayList<>(uuids.size());
        for (UUID u : uuids) {
            names.add(nameOf(u));
        }
        return String.join(", ", names);
    }

    private void notify(OfflinePlayer target, String message) {
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(Msg.prefixed(message));
        }
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Msg.prefixed("<red>Dazu hast du keine Berechtigung (Server-Admin nötig)."));
    }

    private void playerOnly(CommandSender sender) {
        sender.sendMessage(Msg.prefixed("<red>Diesen Befehl können nur Spieler im Spiel nutzen."));
    }

    private void notInBase(CommandSender sender) {
        sender.sendMessage(Msg.prefixed("<red>Du stehst in keiner Basis. Stelle dich in deine Basis und versuche es erneut."));
    }

    private void unknownBase(CommandSender sender, String id) {
        sender.sendMessage(Msg.prefixed("<red>Keine Basis mit der ID <white><id></white>.",
                Placeholder.unparsed("id", id)));
    }

    private void playerNotFound(CommandSender sender, String name) {
        sender.sendMessage(Msg.prefixed("<red>Spieler nicht gefunden: <white><name></white>.",
                Placeholder.unparsed("name", name)));
    }

    private void sendUsage(CommandSender sender) {
        boolean admin = isServerAdmin(sender);
        sender.sendMessage(Msg.prefixed("<gold>/base</gold> <gray>Befehle:"));
        if (admin) {
            sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base define <id> <gray>- Basis aus //wand-Auswahl erstellen (Admin)"));
            sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base setowner <id> <spieler> <gray>- Besitzer setzen (Admin)"));
            sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base delete <id> <gray>- Basis löschen (Admin)"));
        }
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base setleader <spieler> <gray>- Leader ernennen (Besitzer)"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base removeleader <spieler> <gray>- Leader entfernen (Besitzer)"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base invite <spieler> <gray>- einladen (Besitzer/Leader)"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base kick <spieler> <gray>- entfernen (Besitzer/Leader)"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base leave <gray>- eigene Basis verlassen (Mitglied)"));
        sender.sendMessage(Msg.mm("<dark_gray>• <yellow>/base info <id> <gray>- Basis-Info (alle)"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        boolean admin = isServerAdmin(sender);
        if (args.length == 1) {
            List<String> subs = admin ? ALL_SUBS
                    : ALL_SUBS.stream().filter(s -> !ADMIN_SUBS.contains(s)).collect(Collectors.toList());
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (Arrays.asList("setowner", "delete", "info").contains(sub)) {
                return filter(plugin.bases().getBases().stream().map(Base::getId).collect(Collectors.toList()), args[1]);
            }
            if (Arrays.asList("setleader", "removeleader", "invite", "kick").contains(sub)) {
                return filter(onlineNames(), args[1]);
            }
        }
        if (args.length == 3 && sub.equals("setowner")) {
            return filter(onlineNames(), args[2]);
        }
        return List.of();
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
