package com.pvpcraft.anticombatlog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently in PvP combat and enforces the combat
 * rules: an action-bar countdown, expiry after the configured duration, and
 * the logout-while-in-combat death.
 *
 * <p>State lives purely in memory (UUID &rarr; combat-end timestamp). Combat
 * therefore does not survive a server restart, but a logout <em>during</em>
 * combat still drops the player's items into the world immediately, so the
 * death takes effect regardless of what happens afterwards.
 */
public final class CombatManager {

    private final AntiCombatLog plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /** Player UUID -> epoch millis at which their combat tag expires. */
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();

    /** Players who logged out in combat; defensively re-cleared on next join. */
    private final Set<UUID> combatLoggedOut = ConcurrentHashMap.newKeySet();

    private final long combatDurationMillis;

    public CombatManager(AntiCombatLog plugin, int combatDurationSeconds) {
        this.plugin = plugin;
        this.combatDurationMillis = combatDurationSeconds * 1000L;
    }

    /**
     * Puts both the attacker and the victim into combat (or refreshes their
     * timer back to the full duration) and immediately shows their timers.
     */
    public void tag(Player attacker, Player victim) {
        tagSingle(attacker);
        tagSingle(victim);
    }

    private void tagSingle(Player player) {
        long until = System.currentTimeMillis() + combatDurationMillis;
        combatUntil.put(player.getUniqueId(), until);
        player.sendActionBar(timerComponent(secondsLeft(until)));
    }

    public boolean isInCombat(UUID uuid) {
        Long until = combatUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Per-second tick: refreshes every online combatant's action bar and ends
     * combat for anyone whose timer has run out. Driven by a repeating task in
     * {@link AntiCombatLog}.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = combatUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (entry.getValue() <= now) {
                it.remove();
                if (player != null) {
                    player.sendActionBar(Component.empty());
                    player.sendMessage(mm.deserialize("<green>✔ Du bist nicht mehr im Kampf."));
                }
            } else if (player != null) {
                player.sendActionBar(timerComponent(secondsLeft(entry.getValue())));
            }
        }
    }

    /**
     * Handles a player leaving while in combat: drops their full inventory and
     * armor into the world, empties it so they respawn with nothing, kills them
     * and announces the combat log. Safe to call during {@code PlayerQuitEvent}
     * because the player entity and world are still valid at that point.
     */
    public void handleLogout(Player player) {
        UUID uuid = player.getUniqueId();
        combatUntil.remove(uuid);
        combatLoggedOut.add(uuid);

        World world = player.getWorld();
        Location loc = player.getLocation();
        PlayerInventory inv = player.getInventory();

        List<ItemStack> drops = new ArrayList<>();
        collect(drops, inv.getStorageContents());
        collect(drops, inv.getArmorContents());
        collect(drops, inv.getExtraContents());

        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);

        for (ItemStack drop : drops) {
            world.dropItemNaturally(loc, drop);
        }

        // Best-effort immediate death. The inventory is already empty, so the
        // resulting death drops nothing further.
        try {
            player.setHealth(0.0);
        } catch (Throwable ignored) {
            // Some server states reject setHealth on a quitting player; the
            // inventory has already been dropped and cleared, so the player
            // still rejoins empty.
        }

        Bukkit.broadcast(mm.deserialize(
                "<red>⚔ <player> hat sich im Kampf ausgeloggt und ist gestorben!",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                        "player", player.getName())));
    }

    /** Defensively re-clears a combat-logger's inventory the next time they join. */
    public void handleJoin(Player player) {
        if (combatLoggedOut.remove(player.getUniqueId())) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
    }

    public void shutdown() {
        for (UUID uuid : combatUntil.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendActionBar(Component.empty());
            }
        }
        combatUntil.clear();
    }

    private Component timerComponent(long seconds) {
        return mm.deserialize("<red>⚔ Combat: " + seconds + "s");
    }

    private long secondsLeft(long until) {
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            return 0;
        }
        return (remaining + 999) / 1000; // round up to whole seconds
    }

    private static void collect(List<ItemStack> out, ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                out.add(item);
            }
        }
    }
}
