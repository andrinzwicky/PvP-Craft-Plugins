package com.pvpcraft.afkmode;

import com.pvpcraft.afkmode.command.AFKCommand;
import com.pvpcraft.afkmode.listener.AFKListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * AFKMode entry point.
 *
 * <p>{@code /afk} pins a player in place and shields them from PvP/explosion
 * damage and all external knockback, while still letting them take environmental
 * and mob damage, hit blocks/mobs and fish. AFK may only be entered out of combat
 * and at least three minutes after the last combat ended (read from AntiCombatLog
 * via {@link CombatHook}). Own WASD/jump input ends it; mouse-look and sneak don't.
 */
public final class AFKMode extends JavaPlugin {

    private AFKManager afkManager;
    private BukkitTask pinTask;
    private BukkitTask combatPollTask;

    @Override
    public void onEnable() {
        CombatHook combatHook = new CombatHook(this);
        this.afkManager = new AFKManager(this, combatHook);

        getServer().getPluginManager().registerEvents(new AFKListener(afkManager), this);

        AFKCommand command = new AFKCommand(afkManager);
        getCommand("afk").setExecutor(command);
        getCommand("afkstopp").setExecutor(command);

        // Hard position reset every tick.
        this.pinTask = getServer().getScheduler().runTaskTimer(
                this, afkManager::pinTick, 1L, 1L);
        // Cache combat-end timestamps once per second for the 3-minute rule.
        this.combatPollTask = getServer().getScheduler().runTaskTimer(
                this, afkManager::combatPollTick, 20L, 20L);

        getLogger().info("AFKMode enabled.");
    }

    @Override
    public void onDisable() {
        if (pinTask != null) {
            pinTask.cancel();
            pinTask = null;
        }
        if (combatPollTask != null) {
            combatPollTask.cancel();
            combatPollTask = null;
        }
        if (afkManager != null) {
            afkManager.shutdownAll();
        }
    }
}
