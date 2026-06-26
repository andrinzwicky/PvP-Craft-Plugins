package com.pvpcraft.moderation.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Resolves a name to an online or previously-seen offline player. */
public final class PlayerLookup {

    private PlayerLookup() {
    }

    /**
     * Returns the matching player by exact name, preferring an online player,
     * otherwise an offline player who has joined before. Returns null if the
     * name was never seen on this server (filters out typos).
     */
    @SuppressWarnings("deprecation")
    public static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline;
        }
        return null;
    }
}
