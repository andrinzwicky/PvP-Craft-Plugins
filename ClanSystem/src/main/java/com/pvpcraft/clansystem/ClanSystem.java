package com.pvpcraft.clansystem;

import com.pvpcraft.clansystem.command.ClanCommand;
import com.pvpcraft.clansystem.display.DisplayManager;
import com.pvpcraft.clansystem.display.RankHook;
import com.pvpcraft.clansystem.listener.ChatListener;
import com.pvpcraft.clansystem.listener.InviteGuiListener;
import com.pvpcraft.clansystem.listener.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ClanSystem entry point. Players create and join clans; the clan tag is shown
 * after the player's name in chat and the tab list. Integrates with RankManager
 * (optional, via reflection) for the rank prefix, and publishes each online
 * player's clan tag and color as metadata for RankManager to read.
 */
public final class ClanSystem extends JavaPlugin {

    /** How often (in ticks) we re-assert the tab name so it survives RankManager
     *  reapplying its own "[Rank] Player" name (e.g. on an op or rank change). */
    private static final long REASSERT_INTERVAL_TICKS = 100L; // 5 seconds

    private ClanManager clanManager;
    private InviteManager inviteManager;
    private DisplayManager displayManager;

    @Override
    public void onEnable() {
        // Writes config.yml on first run; the "debug" flag toggles nametag logging.
        saveDefaultConfig();

        clanManager = new ClanManager(this);
        clanManager.load();

        inviteManager = new InviteManager();

        RankHook rankHook = new RankHook(getLogger());
        displayManager = new DisplayManager(this, rankHook);

        ClanCommand clanCommand = new ClanCommand(this);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new InviteGuiListener(this), this);

        // Periodically re-assert tab name + metadata so they recover after
        // RankManager reapplies its own tab name.
        Bukkit.getScheduler().runTaskTimer(this, () -> displayManager.updateAll(),
                REASSERT_INTERVAL_TICKS, REASSERT_INTERVAL_TICKS);

        // Apply to anyone already online (e.g. after a /reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            displayManager.handleJoin(player);
        }

        getLogger().info("ClanSystem enabled.");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) {
            clanManager.save();
        }
        if (displayManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                displayManager.clearMetadata(player);
            }
        }
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }

    public DisplayManager getDisplayManager() {
        return displayManager;
    }
}
