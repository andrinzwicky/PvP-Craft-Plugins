package com.pvpcraft.anticombatlog;

import com.pvpcraft.anticombatlog.listener.CombatListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * AntiCombatLog entry point.
 *
 * <p>Players who land a PvP hit (and their victims) are tagged for a
 * configurable number of seconds. While tagged they see an action-bar
 * countdown, cannot teleport to spawn, and certain ranks cannot switch
 * game mode. Logging out while tagged kills the player and drops their items.
 */
public final class AntiCombatLog extends JavaPlugin {

    private CombatManager combatManager;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int duration = Math.max(1, getConfig().getInt("combat-duration-seconds", 30));
        List<String> blockedCommands = getConfig().getStringList("blocked-spawn-commands");

        this.combatManager = new CombatManager(this, duration);

        getServer().getPluginManager().registerEvents(
                new CombatListener(combatManager, blockedCommands), this);

        // Drive the countdown / expiry once per second.
        this.tickTask = getServer().getScheduler().runTaskTimer(
                this, combatManager::tick, 20L, 20L);

        getLogger().info("AntiCombatLog enabled (combat duration: " + duration + "s).");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (combatManager != null) {
            combatManager.shutdown();
        }
    }
}
