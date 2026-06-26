package com.pvpcraft.rankmanager.listener;

import com.pvpcraft.rankmanager.Rank;
import com.pvpcraft.rankmanager.RankAbilities;
import com.pvpcraft.rankmanager.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Enforces the fine-grained, rank-specific command restrictions that vanilla
 * permission nodes cannot express (e.g. "creative is blocked for moderators",
 * "supporters are locked in spectator"), and triggers an immediate Owner-rank
 * recheck right after /op or /deop runs.
 */
public final class CommandListener implements Listener {

    private final RankManager plugin;

    public CommandListener(RankManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String[] parts = splitCommand(event.getMessage());
        if (parts.length == 0) {
            return;
        }
        String cmd = normalize(parts[0]);

        // Immediate op recheck for /op and /deop.
        if (cmd.equals("op") || cmd.equals("deop")) {
            scheduleOpRecheck(parts);
            return;
        }

        Rank rank = plugin.getEffectiveRank(player);
        String id = rank.id();

        if (isGamemodeCommand(cmd)) {
            handleGamemodeCommand(event, player, rank, cmd, parts);
            return;
        }

        if (isTeleportCommand(cmd)) {
            if (RankAbilities.teleportOnlyInSpectator(id)
                    && player.getGameMode() != GameMode.SPECTATOR) {
                event.setCancelled(true);
                plugin.message(player, "tp-denied");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        String[] parts = splitCommand("/" + event.getCommand());
        if (parts.length == 0) {
            return;
        }
        String cmd = normalize(parts[0]);
        if (cmd.equals("op") || cmd.equals("deop")) {
            scheduleOpRecheck(parts);
            return;
        }
        if (isGamemodeCommand(cmd)) {
            // Console has full authority; pre-authorize changing a gamemode-restricted
            // rank so the PlayerGameModeChangeEvent lock below lets the change through.
            String targetName = gamemodeTargetName(cmd, parts);
            if (targetName != null) {
                Player victim = Bukkit.getPlayerExact(targetName);
                if (victim != null
                        && RankAbilities.restrictsGamemode(plugin.getEffectiveRank(victim).id())) {
                    plugin.authorizeGamemodeChange(victim.getUniqueId());
                }
            }
        }
    }

    /**
     * Keeps gamemode-restricted ranks (Supporter, Moderator) inside the set of
     * gamemodes their rank allows, no matter how the change is triggered — command,
     * plugin, API, etc. A Moderator+ can still move them to another mode, because
     * their /gamemode command pre-authorizes the change for one tick.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        String id = plugin.getEffectiveRank(player).id();
        if (!RankAbilities.restrictsGamemode(id)) {
            return;
        }
        if (RankAbilities.allowedGamemodes(id).contains(event.getNewGameMode())) {
            return; // switching within the rank's own allowed set is always fine
        }
        if (plugin.consumeGamemodeChange(player.getUniqueId())) {
            return; // a Moderator+ authorized this single change
        }
        event.setCancelled(true);
        plugin.message(player, "gamemode-denied");
    }

    // ------------------------------------------------------------------

    /**
     * Polices /gamemode usage:
     *  1) the executor may only ever apply gamemodes their own rank allows
     *     (e.g. moderators can't use creative, supporters only survival/spectator);
     *  2) changing ANOTHER player's gamemode requires a Moderator+;
     *  3) if that other player is gamemode-restricted and the requested mode is
     *     outside their own allowed set, we pre-authorize the change so the
     *     PlayerGameModeChangeEvent hard lock lets this staff override through.
     */
    private void handleGamemodeCommand(PlayerCommandPreprocessEvent event, Player executor,
                                       Rank executorRank, String cmd, String[] parts) {
        GameMode target = parseGameMode(cmd, parts);
        String executorId = executorRank.id();

        // (1) Executor may only apply modes their own rank allows.
        if (RankAbilities.restrictsGamemode(executorId)
                && target != null
                && !RankAbilities.allowedGamemodes(executorId).contains(target)) {
            event.setCancelled(true);
            plugin.message(executor, "gamemode-denied");
            return;
        }

        String targetName = gamemodeTargetName(cmd, parts);
        if (targetName == null || targetName.equalsIgnoreCase(executor.getName())) {
            return; // changing own gamemode; the allowed-set check above is enough
        }

        // (2) Changing someone else's gamemode is a Moderator+ ability.
        if (!RankAbilities.canChangeOthersGamemode(executorId)) {
            event.setCancelled(true);
            plugin.message(executor, "gamemode-denied");
            return;
        }

        // (3) Authorize the hard lock if the target mode is outside the victim's set.
        Player victim = Bukkit.getPlayerExact(targetName);
        if (victim == null || target == null) {
            return;
        }
        String victimId = plugin.getEffectiveRank(victim).id();
        if (RankAbilities.restrictsGamemode(victimId)
                && !RankAbilities.allowedGamemodes(victimId).contains(target)) {
            plugin.authorizeGamemodeChange(victim.getUniqueId());
        }
    }

    /** The player-name argument of a gamemode command, or null if it targets self. */
    private String gamemodeTargetName(String cmd, String[] parts) {
        // "/gamemode <mode> [player]" carries the name at index 2; the shortcut
        // commands ("/gms [player]", etc.) carry it at index 1.
        int idx = cmd.equals("gamemode") ? 2 : 1;
        return parts.length > idx ? parts[idx] : null;
    }

    private void scheduleOpRecheck(String[] parts) {
        if (parts.length < 2) {
            return;
        }
        String targetName = parts[1];
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                plugin.recheckOp(target);
            }
        });
    }

    private String[] splitCommand(String message) {
        String body = message.startsWith("/") ? message.substring(1) : message;
        body = body.trim();
        if (body.isEmpty()) {
            return new String[0];
        }
        return body.split("\\s+");
    }

    /** Lowercases and strips any namespace prefix like "minecraft:". */
    private String normalize(String label) {
        String lower = label.toLowerCase();
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }

    private boolean isGamemodeCommand(String cmd) {
        return cmd.equals("gamemode")
                || cmd.equals("gms") || cmd.equals("gmc")
                || cmd.equals("gma") || cmd.equals("gmsp");
    }

    private boolean isTeleportCommand(String cmd) {
        return cmd.equals("teleport") || cmd.equals("tp");
    }

    private GameMode parseGameMode(String cmd, String[] parts) {
        // Paper gamemode shortcut commands carry the mode in the label itself.
        switch (cmd) {
            case "gms": return GameMode.SURVIVAL;
            case "gmc": return GameMode.CREATIVE;
            case "gma": return GameMode.ADVENTURE;
            case "gmsp": return GameMode.SPECTATOR;
            default: break;
        }
        if (parts.length < 2) {
            return null;
        }
        String arg = parts[1].toLowerCase();
        int colon = arg.indexOf(':');
        if (colon >= 0) {
            arg = arg.substring(colon + 1);
        }
        return switch (arg) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }
}
