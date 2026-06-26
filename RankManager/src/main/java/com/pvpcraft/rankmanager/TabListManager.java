package com.pvpcraft.rankmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies the gradient "[Rank] Playername" tab list name, the over-head nametag
 * ("[Rank] Playername [ClanTag]") via a scoreboard team prefix/suffix, controls
 * tab sort order, and sets the configurable header/footer.
 *
 * <h2>Why one team per player and not one team per rank</h2>
 * The nametag shown above a player's skin is rendered from that player's single
 * scoreboard team (prefix + name + suffix). A player can only ever belong to one
 * team, so the same team has to carry both the rank prefix <em>and</em> the clan
 * suffix. Because the clan tag is per-player, a shared per-rank team cannot hold
 * it. We therefore give every player their own team, but name it
 * {@code rm_<priority>_<id>} so teams still sort by rank priority exactly like the
 * previous per-rank scheme — the tab list grouping is unchanged, we just gain a
 * per-player suffix slot for the clan tag.
 */
public final class TabListManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RankManager plugin;

    // Stable short id per player for this session, so the team name (and thus the
    // sort key) only changes when the player's rank priority changes.
    private final Map<UUID, String> teamIds = new ConcurrentHashMap<>();
    private final AtomicInteger teamCounter = new AtomicInteger();

    public TabListManager(RankManager plugin) {
        this.plugin = plugin;
    }

    /** Applies tab name, nametag team (prefix + clan suffix) and header/footer. */
    public void apply(Player player, Rank rank) {
        player.playerListName(rank.formatTabName(player.getName()));
        applyNametagTeam(player, rank);
        applyHeaderFooter(player);
    }

    /** Reapplies everything for every online player (used on reload). */
    public void applyAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player, plugin.getEffectiveRank(player));
        }
    }

    /**
     * Puts the player in their own scoreboard team whose name encodes the rank
     * priority (for sort order) and sets the team prefix to the rank gradient and
     * the suffix to the clan tag (empty when ClanSystem is absent). Together with
     * the player's name this renders "[Rank] Playername [ClanTag]" above the skin.
     */
    private void applyNametagTeam(Player player, Rank rank) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player, rank);

        // Remove from any previous RankManager team (the rank/priority, and thus the
        // team name, may have changed) and clean up the now-empty old team.
        Team current = board.getEntryTeam(player.getName());
        if (current != null && current.getName().startsWith("rm_") && !current.getName().equals(teamName)) {
            current.removeEntry(player.getName());
            if (current.getEntries().isEmpty()) {
                current.unregister();
            }
        }

        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.prefix(rank.nametagPrefix());
        team.suffix(ClanHook.nametagSuffix(player));
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    /** Removes the player from (and unregisters) their nametag team on quit. */
    public void removeFromSortTeam(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = board.getEntryTeam(player.getName());
        if (current != null && current.getName().startsWith("rm_")) {
            current.removeEntry(player.getName());
            if (current.getEntries().isEmpty()) {
                current.unregister();
            }
        }
        teamIds.remove(player.getUniqueId());
    }

    /**
     * Per-player team name: {@code rm_<priority>_<id>}, padded so alphabetical team
     * ordering matches numeric priority. Truncated to the 16-char team-name limit.
     */
    private String teamName(Player player, Rank rank) {
        String id = teamIds.computeIfAbsent(player.getUniqueId(),
                uuid -> Integer.toString(teamCounter.getAndIncrement(), 36));
        String name = String.format("rm_%03d_%s", rank.priority(), id);
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private void applyHeaderFooter(Player player) {
        Component header = joinLines(plugin.getConfig().getStringList("tablist.header"));
        Component footer = joinLines(plugin.getConfig().getStringList("tablist.footer"));
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private Component joinLines(List<String> lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(MM.deserialize(lines.get(i)));
        }
        return result;
    }
}
