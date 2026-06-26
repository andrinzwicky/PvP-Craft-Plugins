package com.pvpcraft.clansystem.display;

import com.pvpcraft.clansystem.ClanSystem;
import com.pvpcraft.clansystem.Messages;
import com.pvpcraft.clansystem.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns everything visible about a player's clan: the tab-list name, the chat
 * prefix, and the player metadata that RankManager reads.
 *
 * <p><b>Metadata contract</b> (consumed by RankManager): for any player in a
 * clan we keep two non-null metadata values set —
 * {@code "clan_tag"} (the tag text) and {@code "clan_color"} (a hex string such
 * as {@code #ff0000}). They are written on join and on every clan change, and
 * removed when the player leaves their clan.
 *
 * <p>Tab-list naming coexists with RankManager: RankManager sets
 * "[Rank] Playername"; we read that same component back via {@link RankHook} and
 * append " [Tag]". A light repeating re-assert (see {@link ClanSystem}) recovers
 * the suffix after RankManager reapplies its own name (e.g. on an op change).
 *
 * <p><b>Over-head nametag</b>: the tag above the player's skin is a scoreboard
 * team suffix. When RankManager is present we set our metadata first and then ask
 * it to re-render the nametag ({@code refreshDisplay}); when it is absent we own a
 * per-player {@code cs_*} team and set the suffix ourselves.
 */
public final class DisplayManager {

    public static final String META_TAG = "clan_tag";
    public static final String META_COLOR = "clan_color";

    /** Prefix for the per-player nametag teams we manage when RankManager is absent. */
    private static final String FALLBACK_TEAM_PREFIX = "cs_";

    private final ClanSystem plugin;
    private final RankHook rankHook;

    // Stable short id per player so our fallback team name is stable for the
    // session; only used on the no-RankManager path.
    private final Map<UUID, String> fallbackTeamIds = new ConcurrentHashMap<>();
    private final AtomicInteger fallbackTeamCounter = new AtomicInteger();

    public DisplayManager(ClanSystem plugin, RankHook rankHook) {
        this.plugin = plugin;
        this.rankHook = rankHook;
    }

    public RankHook rankHook() {
        return rankHook;
    }

    // ------------------------------------------------------------------
    // Component building
    // ------------------------------------------------------------------

    /** The clan tag as "[Tag]" in the clan's color. */
    public Component clanTag(Clan clan) {
        // The hex is validated on input, so it is safe to inline into the tag;
        // the tag text itself is inserted unparsed so it cannot inject markup.
        return Messages.mm("<color:" + clan.colorHex() + ">[<tag>]</color>",
                Placeholder.unparsed("tag", clan.tag()));
    }

    /**
     * The over-head nametag suffix " [Tag]" (note the leading space) in the clan's
     * color. Mirrors RankManager's ClanHook so the nametag looks identical on the
     * fallback (no-RankManager) path.
     */
    public Component nametagSuffix(Clan clan) {
        return Messages.mm("<color:" + clan.colorHex() + "> [<tag>]</color>",
                Placeholder.unparsed("tag", clan.tag()));
    }

    /** "[Rank] Playername" from RankManager, or just the player name if absent. */
    public Component baseName(Player player) {
        Component ranked = rankHook.rankedName(player);
        return ranked != null ? ranked : Component.text(player.getName());
    }

    /** Full tab/chat prefix: base name plus the clan tag when in a clan. */
    public Component displayName(Player player) {
        Component name = baseName(player);
        Clan clan = plugin.getClanManager().getClan(player.getUniqueId());
        if (clan != null) {
            name = name.append(Component.space()).append(clanTag(clan));
        }
        return name;
    }

    // ------------------------------------------------------------------
    // Tab list
    // ------------------------------------------------------------------

    public void updateTab(Player player) {
        player.playerListName(displayName(player));
    }

    /**
     * Join handling: refresh the RankManager hook (it may have enabled after us),
     * set metadata right away, and reapply the nametag + tab name one tick later so
     * they land on top of RankManager's MONITOR-priority name.
     */
    public void handleJoin(Player player) {
        rankHook.refresh();
        applyMetadata(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                applyNametag(player);
                updateTab(player);
            }
        });
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /** Refresh the metadata, the over-head nametag and the tab name for one player. */
    public void update(Player player) {
        // Metadata first: RankManager#refreshDisplay re-reads clan_tag/clan_color.
        applyMetadata(player);
        applyNametag(player);
        updateTab(player);
    }

    // ------------------------------------------------------------------
    // Over-head nametag (scoreboard team suffix)
    // ------------------------------------------------------------------

    /**
     * Updates the clan tag shown above the player's head. Prefers RankManager
     * (which owns the player's nametag team); falls back to driving a per-player
     * scoreboard team ourselves when RankManager is absent. Assumes metadata has
     * already been applied for this player.
     */
    public void applyNametag(Player player) {
        boolean handled = rankHook.refreshDisplay(player);
        logNametag(player, handled);
        if (handled) {
            return; // RankManager re-rendered the nametag from our metadata.
        }
        applyNametagFallback(player);
    }

    /**
     * Optional diagnostic line (enabled by {@code debug: true} in config.yml) that
     * shows the metadata RankManager will read and whether RankManager handled the
     * nametag ({@code refreshDisplay} returned true) or we took the fallback path.
     * Helps pin down "clan tag not showing above the head" reports.
     */
    private void logNametag(Player player, boolean refreshDisplayHandled) {
        if (!plugin.getConfig().getBoolean("debug", false)) {
            return;
        }
        plugin.getLogger().info("[debug] nametag " + player.getName()
                + " clan_tag=" + readMeta(player, META_TAG)
                + " clan_color=" + readMeta(player, META_COLOR)
                + " refreshDisplay=" + (refreshDisplayHandled ? "called (RankManager)" : "skipped (fallback)"));
    }

    /** Current value of one of our metadata keys, or {@code "<none>"} if unset. */
    private String readMeta(Player player, String key) {
        for (var value : player.getMetadata(key)) {
            if (value.getOwningPlugin() == plugin) {
                return value.asString();
            }
        }
        return "<none>";
    }

    /**
     * No-RankManager path: put the player in their own {@code cs_*} team (unless
     * they already belong to a team) and set its suffix to the clan tag, or clear
     * it when they have no clan.
     */
    private void applyNametagFallback(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getEntryTeam(player.getName());
        if (team == null) {
            String teamName = fallbackTeamName(player);
            team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            team.addEntry(player.getName());
        }
        Clan clan = plugin.getClanManager().getClan(player.getUniqueId());
        team.suffix(clan != null ? nametagSuffix(clan) : Component.empty());
    }

    /** Removes the player from (and unregisters) the fallback nametag team on quit. */
    public void removeNametag(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = board.getEntryTeam(player.getName());
        if (current != null && current.getName().startsWith(FALLBACK_TEAM_PREFIX)) {
            current.removeEntry(player.getName());
            if (current.getEntries().isEmpty()) {
                current.unregister();
            }
        }
        fallbackTeamIds.remove(player.getUniqueId());
    }

    /** Stable per-player team name {@code cs_<id>}, capped at the 16-char limit. */
    private String fallbackTeamName(Player player) {
        String id = fallbackTeamIds.computeIfAbsent(player.getUniqueId(),
                uuid -> Integer.toString(fallbackTeamCounter.getAndIncrement(), 36));
        String name = FALLBACK_TEAM_PREFIX + id;
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    // ------------------------------------------------------------------
    // Metadata (read by RankManager)
    // ------------------------------------------------------------------

    public void applyMetadata(Player player) {
        Clan clan = plugin.getClanManager().getClan(player.getUniqueId());
        if (clan != null) {
            // Both values are guaranteed non-null: tag() and colorHex() never
            // return null, and colorHex() is always canonical hex.
            player.setMetadata(META_TAG, new FixedMetadataValue(plugin, clan.tag()));
            player.setMetadata(META_COLOR, new FixedMetadataValue(plugin, clan.colorHex()));
        } else {
            clearMetadata(player);
        }
    }

    public void clearMetadata(Player player) {
        player.removeMetadata(META_TAG, plugin);
        player.removeMetadata(META_COLOR, plugin);
    }
}
