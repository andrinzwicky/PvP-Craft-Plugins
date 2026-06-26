package com.pvpcraft.rankmanager;

import com.pvpcraft.rankmanager.command.RankCommand;
import com.pvpcraft.rankmanager.listener.ChatListener;
import com.pvpcraft.rankmanager.listener.CommandListener;
import com.pvpcraft.rankmanager.listener.PlayerListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RankManager extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private PlayerDataManager dataManager;
    private PermissionManager permissionManager;
    private TabListManager tabListManager;

    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private Rank defaultRank;
    private Rank ownerRank;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataManager = new PlayerDataManager(this);
        dataManager.load();

        permissionManager = new PermissionManager(this);
        tabListManager = new TabListManager(this);

        loadRanks();

        getCommand("rank").setExecutor(new RankCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        startOpWatcher();

        // Reapply for anyone already online (e.g. /reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            reapply(player);
        }

        getLogger().info("RankManager enabled with " + ranks.size() + " ranks.");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.save();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permissionManager != null) {
                permissionManager.clear(player);
            }
        }
    }

    // ------------------------------------------------------------------
    // Rank loading
    // ------------------------------------------------------------------

    public void loadRanks() {
        ranks.clear();
        defaultRank = null;
        ownerRank = null;

        ConfigurationSection section = getConfig().getConfigurationSection("ranks");
        if (section == null) {
            getLogger().severe("No 'ranks' section in config.yml!");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection rs = section.getConfigurationSection(id);
            if (rs == null) {
                continue;
            }
            String key = id.toLowerCase();
            String display = rs.getString("display", id);
            List<String> colors = rs.getStringList("colors");
            if (colors.isEmpty() && rs.isString("colors")) {
                colors = List.of(rs.getString("colors"));
            }
            int priority = rs.getInt("priority", 100);
            String permission = rs.getString("permission", "rankmanager.rank." + key);
            boolean isDefault = rs.getBoolean("default", false);
            List<String> extraPermissions = rs.getStringList("extra-permissions");

            Rank rank = new Rank(key, display, colors, priority, permission, isDefault, extraPermissions);
            ranks.put(key, rank);

            if (isDefault && defaultRank == null) {
                defaultRank = rank;
            }
            if (key.equals("owner")) {
                ownerRank = rank;
            }
        }

        if (defaultRank == null && !ranks.isEmpty()) {
            // Fall back to the highest-priority (lowest number) rank.
            defaultRank = getRanksByPriority().get(getRanksByPriority().size() - 1);
            getLogger().warning("No default rank flagged; using '" + defaultRank.id() + "'.");
        }
        if (ownerRank == null) {
            ownerRank = defaultRank;
            getLogger().warning("No 'owner' rank defined; op players will get the default rank.");
        }
    }

    public void reload() {
        reloadConfig();
        loadRanks();
        tabListManager.applyAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            permissionManager.apply(player, getEffectiveRank(player));
        }
    }

    // ------------------------------------------------------------------
    // Rank resolution
    // ------------------------------------------------------------------

    /** Effective rank = Owner if op, otherwise the stored (or default) rank. */
    public Rank getEffectiveRank(Player player) {
        if (player.isOp() && ownerRank != null) {
            return ownerRank;
        }
        return getStoredRank(player.getUniqueId());
    }

    public Rank getStoredRank(UUID uuid) {
        String id = dataManager.getRankId(uuid);
        if (id != null) {
            Rank rank = ranks.get(id.toLowerCase());
            if (rank != null) {
                return rank;
            }
        }
        return defaultRank;
    }

    public Rank getRank(String id) {
        return id == null ? null : ranks.get(id.toLowerCase());
    }

    public List<Rank> getRanksByPriority() {
        List<Rank> list = new ArrayList<>(ranks.values());
        list.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        return list;
    }

    public List<String> getRankIds() {
        return new ArrayList<>(ranks.keySet());
    }

    public Rank getDefaultRank() {
        return defaultRank;
    }

    public Rank getOwnerRank() {
        return ownerRank;
    }

    // ------------------------------------------------------------------
    // Applying ranks
    // ------------------------------------------------------------------

    /** Stores a new rank for a player and reapplies if online. */
    public void setRank(UUID uuid, String rankId) {
        dataManager.setRankId(uuid, rankId.toLowerCase());
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            reapply(online);
        }
    }

    /**
     * Refreshes only the visual display (tab name + over-head nametag, including
     * the clan tag) for a player from their effective rank, without touching
     * permissions or stored data. Intended for the sibling ClanSystem plugin to
     * call after a player's clan tag changes so the nametag updates immediately.
     */
    public void refreshDisplay(Player player) {
        tabListManager.apply(player, getEffectiveRank(player));
    }

    /** Reapplies permissions and tab list for a player from their effective rank. */
    public void reapply(Player player) {
        // New players with no stored rank get the default rank persisted.
        if (!dataManager.hasRank(player.getUniqueId())) {
            dataManager.setRankId(player.getUniqueId(), defaultRank.id());
        }
        Rank rank = getEffectiveRank(player);
        permissionManager.apply(player, rank);
        tabListManager.apply(player, rank);
    }

    // ------------------------------------------------------------------
    // Op watcher: gaining/losing /op grants/removes the Owner rank.
    // ------------------------------------------------------------------

    private final Map<UUID, Boolean> lastOpState = new LinkedHashMap<>();

    private void startOpWatcher() {
        long interval = Math.max(1, getConfig().getLong("op-check-interval-ticks", 20));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean op = player.isOp();
                Boolean previous = lastOpState.get(player.getUniqueId());
                if (previous == null || previous != op) {
                    lastOpState.put(player.getUniqueId(), op);
                    if (previous != null) {
                        // Status actually changed while online -> reapply.
                        reapply(player);
                    }
                }
            }
        }, interval, interval);
    }

    /** Called when a player joins/leaves to seed the op-state cache. */
    public void trackOpState(Player player) {
        lastOpState.put(player.getUniqueId(), player.isOp());
    }

    public void untrackOpState(UUID uuid) {
        lastOpState.remove(uuid);
    }

    /** Forces an immediate op recheck (used right after /op or /deop runs). */
    public void recheckOp(Player player) {
        boolean op = player.isOp();
        Boolean previous = lastOpState.put(player.getUniqueId(), op);
        if (previous != null && previous != op) {
            reapply(player);
        }
    }

    // ------------------------------------------------------------------
    // Gamemode lock overrides
    // ------------------------------------------------------------------

    // Players whose next gamemode change has been authorized by a Moderator+ even
    // though their rank is normally locked to Spectator (see CommandListener).
    private final Set<UUID> authorizedGamemodeChanges = new HashSet<>();

    /** Allows the given player's next gamemode change to bypass the rank lock once. */
    public void authorizeGamemodeChange(UUID uuid) {
        authorizedGamemodeChanges.add(uuid);
        // Drop the authorization at the end of this tick if it goes unused, so a
        // stale token can't later let the player bypass the lock themselves. The
        // authorized change runs synchronously during this tick's command dispatch,
        // before this next-tick cleanup task fires.
        Bukkit.getScheduler().runTask(this, () -> authorizedGamemodeChanges.remove(uuid));
    }

    /** Consumes a one-shot gamemode-change authorization; true if one was present. */
    public boolean consumeGamemodeChange(UUID uuid) {
        return authorizedGamemodeChanges.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Managers + messaging
    // ------------------------------------------------------------------

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public TabListManager getTabListManager() {
        return tabListManager;
    }

    public void message(CommandSender to, String configKey, TagResolver... resolvers) {
        String raw = getConfig().getString("messages." + configKey, "");
        if (raw.isEmpty()) {
            return;
        }
        String prefix = getConfig().getString("messages.prefix", "");
        to.sendMessage(MM.deserialize(prefix + raw, resolvers));
    }

    public void messageNoPrefix(CommandSender to, String mini, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(mini, resolvers));
    }

    public static TagResolver placeholder(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public List<String> sortedRankIds() {
        List<String> ids = new ArrayList<>();
        for (Rank rank : getRanksByPriority()) {
            ids.add(rank.id());
        }
        return Collections.unmodifiableList(ids);
    }
}
