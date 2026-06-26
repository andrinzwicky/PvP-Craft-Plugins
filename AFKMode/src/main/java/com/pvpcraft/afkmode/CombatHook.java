package com.pvpcraft.afkmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Soft dependency on the <strong>AntiCombatLog</strong> plugin, accessed purely
 * via reflection so AFKMode compiles and runs without it on the classpath.
 *
 * <p>AntiCombatLog's main object holds a private {@code CombatManager combatManager}
 * field. That manager exposes a public {@code boolean isInCombat(UUID)} method and
 * keeps a private {@code Map<UUID, Long> combatUntil} whose value is the epoch-millis
 * timestamp at which a player's combat tag expires &mdash; i.e. the moment combat
 * ends. We read both: {@link #isInCombat(Player)} for the live status and
 * {@link #combatEndMillis(Player)} for that end timestamp.
 *
 * <p>Every access is wrapped defensively. If the plugin is missing, disabled, not
 * yet loaded, or its internals have changed, all methods fall back to "not in
 * combat" / {@code null} and AFKMode simply treats the player as combat-free.
 */
public final class CombatHook {

    private final Plugin afkPlugin;

    // Lazily resolved & cached AntiCombatLog reflection handles.
    private Object combatManager;
    private Method isInCombatMethod;
    private Field combatUntilField;
    private boolean resolved;
    private boolean warned;

    public CombatHook(Plugin afkPlugin) {
        this.afkPlugin = afkPlugin;
    }

    /** True if the combat hook is currently usable. */
    public boolean isAvailable() {
        return resolve();
    }

    /**
     * @return whether the player is currently combat-tagged in AntiCombatLog;
     *         {@code false} if the plugin is absent or anything goes wrong.
     */
    public boolean isInCombat(Player player) {
        if (!resolve()) {
            return false;
        }
        try {
            Object result = isInCombatMethod.invoke(combatManager, player.getUniqueId());
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return the epoch-millis timestamp at which the player's combat tag expires
     *         (its end). While in combat this lies in the future; the entry is
     *         removed by AntiCombatLog shortly after it elapses. Returns
     *         {@code null} when unknown or unavailable.
     */
    public Long combatEndMillis(Player player) {
        if (!resolve()) {
            return null;
        }
        try {
            Object raw = combatUntilField.get(combatManager);
            if (raw instanceof Map<?, ?> map) {
                Object value = map.get(player.getUniqueId());
                if (value instanceof Long l) {
                    return l;
                }
            }
        } catch (Throwable ignored) {
            // fall through to null
        }
        return null;
    }

    /**
     * Resolves and caches the reflection handles on first successful use. Returns
     * false (silently, warning at most once) if AntiCombatLog is not usable.
     */
    private boolean resolve() {
        if (resolved && combatManager != null) {
            return true;
        }
        try {
            Plugin combat = Bukkit.getPluginManager().getPlugin("AntiCombatLog");
            if (combat == null || !combat.isEnabled()) {
                return false;
            }
            Field cmField = combat.getClass().getDeclaredField("combatManager");
            cmField.setAccessible(true);
            Object cm = cmField.get(combat);
            if (cm == null) {
                return false;
            }
            Method isInCombat = cm.getClass().getMethod("isInCombat", UUID.class);
            Field untilField = cm.getClass().getDeclaredField("combatUntil");
            untilField.setAccessible(true);

            this.combatManager = cm;
            this.isInCombatMethod = isInCombat;
            this.combatUntilField = untilField;
            this.resolved = true;
            afkPlugin.getLogger().info("AntiCombatLog gefunden - Combat-Status wird ausgelesen.");
            return true;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                afkPlugin.getLogger().log(Level.WARNING,
                        "AntiCombatLog nicht lesbar - Spieler werden als 'nicht im Combat' behandelt: "
                                + t.getMessage());
            }
            return false;
        }
    }
}
