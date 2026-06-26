package com.pvpcraft.watch;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Soft integration with GSit. GSit is accessed entirely through reflection on
 * {@code dev.geco.gsit.api.GSitAPI}, so the plugin compiles and runs with or
 * without GSit installed and degrades silently if the API ever changes.
 *
 * <p>{@link #isInPose(Player)} reports whether a player is currently sitting,
 * laying or crawling — used to block the watcher from starting a watch while in
 * a GSit pose. The target's pose is irrelevant and never checked.
 */
public final class GSitHook {

    private final boolean present;
    private final List<Method> poseChecks = new ArrayList<>();

    public GSitHook(Plugin plugin) {
        boolean found = Bukkit.getPluginManager().getPlugin("GSit") != null;
        if (found) {
            try {
                Class<?> api = Class.forName("dev.geco.gsit.api.GSitAPI");
                // Primary names as of GSit 3.4.x. Sitting is reported for any
                // LivingEntity; posing (lay/lounge) and crawling are player-specific.
                addCheck(api, "isEntitySitting", LivingEntity.class);
                addCheck(api, "isPlayerPosing", Player.class);
                addCheck(api, "isPlayerCrawling", Player.class);
                // Fallbacks for other GSit versions / naming.
                addCheck(api, "isSitting", Entity.class);
                addCheck(api, "isSitting", LivingEntity.class);
                addCheck(api, "isPlayerLaying", Player.class);
                addCheck(api, "isCrawling", Player.class);
                if (poseChecks.isEmpty()) {
                    found = false;
                    plugin.getLogger().warning("GSit gefunden, aber keine bekannte GSitAPI-Methode - Pose-Pruefung deaktiviert.");
                } else {
                    plugin.getLogger().info("GSit erkannt - Pose-Pruefung aktiv.");
                }
            } catch (Throwable t) {
                found = false;
            }
        }
        this.present = found;
    }

    private void addCheck(Class<?> api, String name, Class<?> argType) {
        try {
            Method m = api.getMethod(name, argType);
            if (boolean.class.equals(m.getReturnType()) || Boolean.class.equals(m.getReturnType())) {
                poseChecks.add(m);
            }
        } catch (NoSuchMethodException ignored) {
            // method not present in this GSit version
        }
    }

    public boolean isPresent() {
        return present;
    }

    /** True if GSit reports the player as sitting, crawling or laying. */
    public boolean isInPose(Player player) {
        if (!present) {
            return false;
        }
        for (Method check : poseChecks) {
            try {
                Object result = check.invoke(null, player);
                if (result instanceof Boolean b && b) {
                    return true;
                }
            } catch (Throwable ignored) {
                // ignore individual failures and keep checking
            }
        }
        return false;
    }
}
