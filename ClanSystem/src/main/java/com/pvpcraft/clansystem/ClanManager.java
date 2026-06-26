package com.pvpcraft.clansystem;

import com.pvpcraft.clansystem.model.Clan;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads, stores and indexes all clans from {@code clans.yml}. Everything is
 * UUID-based. A reverse index ({@code playerToClanKey}) makes "which clan is this
 * player in?" an O(1) lookup, which the chat/tab display path hits constantly.
 */
public final class ClanManager {

    private final ClanSystem plugin;
    private final File file;

    // Concurrent because the async chat renderer reads these off the main thread
    // while command handlers mutate them on the main thread.
    private final Map<String, Clan> clansByKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToClanKey = new ConcurrentHashMap<>();

    public ClanManager(ClanSystem plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void load() {
        clansByKey.clear();
        playerToClanKey.clear();

        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("clans");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(key);
            if (cs == null) {
                continue;
            }
            String ownerRaw = cs.getString("owner");
            if (ownerRaw == null) {
                plugin.getLogger().warning("Clan '" + key + "' has no owner; skipping.");
                continue;
            }
            UUID owner;
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Clan '" + key + "' has an invalid owner UUID; skipping.");
                continue;
            }

            String name = cs.getString("name", key);
            String tag = cs.getString("tag", name);
            String color = cs.getString("color", "#ffffff");
            if (ColorUtil.parse(color) == null) {
                color = "#ffffff";
            }

            Clan clan = new Clan(key, name, tag, color, owner);
            for (String s : cs.getStringList("leaders")) {
                parseUuid(s).ifPresent(clan.leaders()::add);
            }
            for (String s : cs.getStringList("members")) {
                parseUuid(s).ifPresent(clan.members()::add);
            }
            // Guard against an owner/leader also appearing in lower buckets.
            clan.leaders().remove(owner);
            clan.members().remove(owner);
            clan.members().removeAll(clan.leaders());

            clansByKey.put(key, clan);
            for (UUID member : clan.allMembers()) {
                playerToClanKey.put(member, key);
            }
        }
        plugin.getLogger().info("Loaded " + clansByKey.size() + " clan(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Clan clan : clansByKey.values()) {
            String base = "clans." + clan.key();
            yaml.set(base + ".name", clan.name());
            yaml.set(base + ".tag", clan.tag());
            yaml.set(base + ".color", clan.colorHex());
            yaml.set(base + ".owner", clan.owner().toString());
            yaml.set(base + ".leaders", toStrings(clan.leaders()));
            yaml.set(base + ".members", toStrings(clan.members()));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save clans.yml", ex);
        }
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public Clan getClan(UUID player) {
        String key = playerToClanKey.get(player);
        return key == null ? null : clansByKey.get(key);
    }

    public Clan getClanByKey(String key) {
        return clansByKey.get(key);
    }

    public Clan getClanByName(String name) {
        return clansByKey.get(toKey(name));
    }

    public boolean nameExists(String name) {
        return clansByKey.containsKey(toKey(name));
    }

    public List<Clan> allClans() {
        return new ArrayList<>(clansByKey.values());
    }

    // ------------------------------------------------------------------
    // Mutations (each persists immediately and keeps the index in sync)
    // ------------------------------------------------------------------

    /** Creates a clan owned by {@code owner}. Caller must check the name first. */
    public Clan createClan(String name, UUID owner) {
        String key = toKey(name);
        String tag = name.length() > 10 ? name.substring(0, 10) : name;
        Clan clan = new Clan(key, name, tag, "#ffffff", owner);
        clansByKey.put(key, clan);
        playerToClanKey.put(owner, key);
        save();
        return clan;
    }

    public void disband(Clan clan) {
        for (UUID member : clan.allMembers()) {
            playerToClanKey.remove(member);
        }
        clansByKey.remove(clan.key());
        save();
    }

    public void addMember(Clan clan, UUID player) {
        clan.addMember(player);
        playerToClanKey.put(player, clan.key());
        save();
    }

    public void removeMember(Clan clan, UUID player) {
        clan.removeMember(player);
        playerToClanKey.remove(player);
        save();
    }

    /** Persists after an in-place edit (tag, color, promote, demote). */
    public void update(Clan clan) {
        save();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Lowercased name used as the storage/index key. */
    public static String toKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<String> toStrings(Iterable<UUID> uuids) {
        List<String> out = new ArrayList<>();
        for (UUID u : uuids) {
            out.add(u.toString());
        }
        return out;
    }

    private java.util.Optional<UUID> parseUuid(String s) {
        try {
            return java.util.Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Ignoring invalid UUID in clans.yml: " + s);
            return java.util.Optional.empty();
        }
    }
}
