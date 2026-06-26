package com.pvpcraft.moderation.mute;

import com.pvpcraft.moderation.ModerationManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks active mutes and persists them in mutes.yml so they survive restarts.
 * Expired mutes are cleaned up lazily on access.
 */
public final class MuteManager {

    /** An active mute: until == {@link com.pvpcraft.moderation.util.Durations#PERMANENT} means permanent. */
    public record MuteInfo(long until, String reason, String by) {
        public boolean permanent() {
            return until < 0;
        }
    }

    private final ModerationManager plugin;
    private final File file;
    private FileConfiguration data;

    public MuteManager(ModerationManager plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                if (plugin.getDataFolder().mkdirs() || plugin.getDataFolder().exists()) {
                    file.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create mutes.yml", e);
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
            plugin.getLogger().log(Level.SEVERE, "Could not save mutes.yml", e);
        }
    }

    /** Stores (or replaces) a mute. {@code until} < 0 means permanent. */
    public void mute(UUID uuid, long until, String reason, String by) {
        String base = "mutes." + uuid;
        data.set(base + ".until", until);
        data.set(base + ".reason", reason);
        data.set(base + ".by", by);
        save();
    }

    /** Removes a mute; returns true if one was present. */
    public boolean unmute(UUID uuid) {
        if (!data.contains("mutes." + uuid)) {
            return false;
        }
        data.set("mutes." + uuid, null);
        save();
        return true;
    }

    /** Whether the player is currently muted (clears the record if expired). */
    public boolean isMuted(UUID uuid) {
        return getMute(uuid) != null;
    }

    /**
     * Returns the active mute, or null if none / expired. An expired mute is
     * removed from storage as a side effect.
     */
    public MuteInfo getMute(UUID uuid) {
        String base = "mutes." + uuid;
        if (!data.contains(base)) {
            return null;
        }
        long until = data.getLong(base + ".until", 0L);
        String reason = data.getString(base + ".reason", "");
        String by = data.getString(base + ".by", "?");
        if (until >= 0 && System.currentTimeMillis() >= until) {
            // Expired -> clean up.
            data.set(base, null);
            save();
            return null;
        }
        return new MuteInfo(until, reason, by);
    }

    /** Remaining mute time in millis, or -1 if permanent, 0 if not muted. */
    public long remainingMillis(UUID uuid) {
        MuteInfo info = getMute(uuid);
        if (info == null) {
            return 0L;
        }
        if (info.permanent()) {
            return -1L;
        }
        return Math.max(0L, info.until() - System.currentTimeMillis());
    }
}
