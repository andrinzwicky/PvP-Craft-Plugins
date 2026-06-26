package com.pvpcraft.clansystem.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Optional, reflection-based bridge to the RankManager plugin. ClanSystem never
 * compiles against RankManager (no external dependency), so we look its API up
 * reflectively and degrade gracefully when it is missing or has a different
 * shape.
 *
 * <p>We rely on two public methods of RankManager 1.x:
 * <pre>
 *   Rank RankManager#getEffectiveRank(Player)
 *   Component Rank#formatTabName(String)   // builds "[Rank] Playername"
 * </pre>
 * If either cannot be resolved the hook reports itself unavailable and callers
 * fall back to showing just the player name.
 *
 * <p>Additionally we try to resolve {@code RankManager#refreshDisplay(Player)},
 * which re-applies the over-head nametag (scoreboard team prefix + clan suffix)
 * from the player's current metadata. It is optional: if it is missing the hook
 * stays usable for tab naming and {@link DisplayManager} drives the nametag
 * suffix itself.
 */
public final class RankHook {

    private final Logger logger;

    private boolean available;
    private Plugin rankPlugin;
    private Method getEffectiveRank;
    private Method formatTabName;
    private Method refreshDisplay;

    public RankHook(Logger logger) {
        this.logger = logger;
        resolve();
    }

    private void resolve() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("RankManager");
        if (plugin == null || !plugin.isEnabled()) {
            logger.info("RankManager not found - clan tags will show without a rank prefix.");
            return;
        }
        try {
            this.getEffectiveRank = plugin.getClass().getMethod("getEffectiveRank", Player.class);
            Class<?> rankClass = getEffectiveRank.getReturnType();
            this.formatTabName = rankClass.getMethod("formatTabName", String.class);
            this.rankPlugin = plugin;
            this.available = true;
            logger.info("Hooked into RankManager for rank prefixes.");
        } catch (ReflectiveOperationException ex) {
            logger.warning("RankManager is present but its API did not match; "
                    + "clan tags will show without a rank prefix. (" + ex.getMessage() + ")");
            return;
        }
        // Optional: lets RankManager re-render the over-head nametag from our
        // metadata. Absence is non-fatal - we drive the suffix ourselves instead.
        try {
            this.refreshDisplay = plugin.getClass().getMethod("refreshDisplay", Player.class);
        } catch (ReflectiveOperationException ex) {
            logger.info("RankManager#refreshDisplay not found; ClanSystem will set the "
                    + "nametag suffix directly.");
        }
    }

    /** Re-resolves the hook, e.g. if RankManager is enabled after us. */
    public void refresh() {
        if (!available) {
            resolve();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the "[Rank] Playername" component for the player as rendered by
     * RankManager, or {@code null} if RankManager is unavailable or the call
     * fails. The first failure disables the hook to avoid log spam.
     */
    public Component rankedName(Player player) {
        if (!available) {
            return null;
        }
        try {
            Object rank = getEffectiveRank.invoke(rankPlugin, player);
            if (rank == null) {
                return null;
            }
            Object component = formatTabName.invoke(rank, player.getName());
            return component instanceof Component c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            available = false;
            logger.warning("RankManager call failed; disabling rank prefix integration. ("
                    + ex.getMessage() + ")");
            return null;
        }
    }

    /**
     * Asks RankManager to re-render the player's over-head nametag (it re-reads our
     * {@code clan_tag}/{@code clan_color} metadata, so that must be set first).
     * Returns {@code true} if RankManager handled it, or {@code false} when
     * RankManager (or its {@code refreshDisplay} method) is unavailable, signalling
     * the caller to drive the nametag suffix itself.
     */
    public boolean refreshDisplay(Player player) {
        if (!available || refreshDisplay == null) {
            return false;
        }
        try {
            refreshDisplay.invoke(rankPlugin, player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            available = false;
            logger.warning("RankManager#refreshDisplay failed; disabling rank integration "
                    + "and setting the nametag suffix directly. (" + ex.getMessage() + ")");
            return false;
        }
    }
}
