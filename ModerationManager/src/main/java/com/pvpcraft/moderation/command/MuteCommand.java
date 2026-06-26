package com.pvpcraft.moderation.command;

import com.pvpcraft.moderation.ModerationManager;
import com.pvpcraft.moderation.history.ActionType;
import com.pvpcraft.moderation.history.ModEntry;
import com.pvpcraft.moderation.util.Durations;
import com.pvpcraft.moderation.util.PlayerLookup;
import com.pvpcraft.moderation.util.TeamUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /mute &lt;player&gt; &lt;duration&gt; [reason] — silences a player in chat and
 * in private-message commands for the given time (e.g. 30m, 2h, 1d, perm).
 */
public final class MuteCommand implements TabExecutor {

    private final ModerationManager plugin;

    public MuteCommand(ModerationManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!TeamUtil.canModerate(sender)) {
            plugin.msg(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.msg(sender, "mute-usage");
            return true;
        }

        OfflinePlayer target = PlayerLookup.resolve(args[0]);
        if (target == null) {
            plugin.msg(sender, "player-not-found", ModerationManager.ph("player", args[0]));
            return true;
        }

        long duration = Durations.parse(args[1]);
        if (duration == Durations.INVALID) {
            plugin.msg(sender, "mute-bad-duration", ModerationManager.ph("input", args[1]));
            return true;
        }
        boolean permanent = duration == Durations.PERMANENT;
        long until = permanent ? Durations.PERMANENT : System.currentTimeMillis() + duration;
        String durationText = permanent ? "dauerhaft" : Durations.format(duration);

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
                : "";
        String moderator = sender instanceof Player p ? p.getName() : "Konsole";
        String targetName = target.getName() == null ? args[0] : target.getName();

        plugin.mutes().mute(target.getUniqueId(), until, reason, moderator);

        String historyReason = durationText + (reason.isEmpty() ? "" : " | " + reason);
        plugin.history().record(target.getUniqueId(), targetName,
                new ModEntry(ActionType.MUTE, moderator, historyReason, System.currentTimeMillis()));

        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
        if (onlineTarget != null) {
            plugin.msg(onlineTarget, "mute-target",
                    ModerationManager.ph("duration", durationText),
                    ModerationManager.ph("reason", reason),
                    ModerationManager.ph("moderator", moderator));
        }

        plugin.notifyTeamExcept(onlineTarget == null ? null : onlineTarget.getUniqueId(), "mute-team",
                ModerationManager.ph("player", targetName),
                ModerationManager.ph("moderator", moderator),
                ModerationManager.ph("duration", durationText),
                ModerationManager.ph("reason", reason));

        if (!(sender instanceof Player)) {
            plugin.msg(sender, "mute-confirm",
                    ModerationManager.ph("player", targetName),
                    ModerationManager.ph("duration", durationText));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!TeamUtil.canModerate(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (String s : List.of("10m", "30m", "1h", "6h", "1d", "7d", "perm")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    suggestions.add(s);
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
