package com.pvpcraft.moderation.history;

import com.pvpcraft.moderation.ModerationManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists every moderation action (warn, kick, ban, ...) per player in
 * history.yml so it can be looked up later via {@code /warns}. Keyed by UUID,
 * with the player's last known name stored alongside for offline display.
 */
public final class HistoryManager {

    private final ModerationManager plugin;
    private final File file;
    private FileConfiguration data;

    public HistoryManager(ModerationManager plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "history.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                if (plugin.getDataFolder().mkdirs() || plugin.getDataFolder().exists()) {
                    file.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create history.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (data == null) {
            return;
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save history.yml", e);
        }
    }

    /** Appends an entry to the player's history and persists immediately. */
    public void record(UUID uuid, String name, ModEntry entry) {
        String base = "history." + uuid;
        if (name != null) {
            data.set(base + ".name", name);
        }
        List<Map<?, ?>> stored = new ArrayList<>(data.getMapList(base + ".entries"));
        stored.add(entry.toMap());
        data.set(base + ".entries", stored);
        save();
    }

    /**
     * Removes the entry at the given 0-based index from the player's history and
     * persists. Returns the removed entry, or null if the index was out of range.
     */
    public ModEntry removeEntry(UUID uuid, int index) {
        String base = "history." + uuid;
        List<Map<?, ?>> stored = new ArrayList<>(data.getMapList(base + ".entries"));
        if (index < 0 || index >= stored.size()) {
            return null;
        }
        ModEntry removed = ModEntry.fromMap(stored.remove(index));
        data.set(base + ".entries", stored);
        save();
        return removed;
    }

    /** All recorded entries for a player, oldest first (empty if none). */
    public List<ModEntry> getEntries(UUID uuid) {
        List<ModEntry> result = new ArrayList<>();
        for (Map<?, ?> raw : data.getMapList("history." + uuid + ".entries")) {
            result.add(ModEntry.fromMap(raw));
        }
        return result;
    }

    /** How many entries of a given type the player has (e.g. WARN count). */
    public long countOfType(UUID uuid, ActionType type) {
        long count = 0;
        for (ModEntry entry : getEntries(uuid)) {
            if (entry.type() == type) {
                count++;
            }
        }
        return count;
    }

    /** Last known name for a player, or null if we never stored one. */
    public String getName(UUID uuid) {
        return data.getString("history." + uuid + ".name");
    }
}
