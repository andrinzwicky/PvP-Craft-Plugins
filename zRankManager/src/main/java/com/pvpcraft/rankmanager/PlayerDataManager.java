package com.pvpcraft.rankmanager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists the UUID -> rank id mapping in players.yml.
 */
public final class PlayerDataManager {

    private final RankManager plugin;
    private final File file;
    private FileConfiguration data;

    public PlayerDataManager(RankManager plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                if (plugin.getDataFolder().mkdirs() || plugin.getDataFolder().exists()) {
                    file.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create players.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", e);
        }
    }

    /** Returns the stored rank id for a player, or null if none is stored. */
    public String getRankId(UUID uuid) {
        return data.getString("players." + uuid);
    }

    public boolean hasRank(UUID uuid) {
        return data.contains("players." + uuid);
    }

    /** Stores the rank id and immediately persists to disk. */
    public void setRankId(UUID uuid, String rankId) {
        data.set("players." + uuid, rankId);
        save();
    }
}
