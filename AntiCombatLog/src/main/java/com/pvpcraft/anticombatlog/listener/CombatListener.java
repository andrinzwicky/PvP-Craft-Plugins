package com.pvpcraft.anticombatlog.listener;

import com.pvpcraft.anticombatlog.CombatManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;

/**
 * Wires the combat rules to Paper events: PvP detection, the logout death,
 * spawn-teleport blocking and the rank-based game-mode restriction.
 */
public final class CombatListener implements Listener {

    /** Ranks that may NOT switch game mode while in combat. */
    private static final String[] RESTRICTED_RANKS = {
            "rankmanager.rank.supporter",
            "rankmanager.rank.moderator"
    };

    /** Ranks that bypass the game-mode restriction entirely. */
    private static final String[] BYPASS_RANKS = {
            "rankmanager.rank.admin",
            "rankmanager.rank.developer",
            "rankmanager.rank.owner"
    };

    private final CombatManager combat;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /** Lower-cased command roots that teleport to spawn, blocked while in combat. */
    private final List<String> blockedSpawnCommands;

    public CombatListener(CombatManager combat, List<String> blockedSpawnCommands) {
        this.combat = combat;
        this.blockedSpawnCommands = blockedSpawnCommands.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
    }

    // --- PvP detection ---------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return; // PvE, environment, or self-damage: no combat tag.
        }
        combat.tag(attacker, victim);
    }

    /** Resolves the responsible player for a hit: direct melee or a player's projectile. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player shooterPlayer) {
                return shooterPlayer;
            }
        }
        return null;
    }

    // --- logout death ----------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combat.isInCombat(player.getUniqueId())) {
            combat.handleLogout(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        combat.handleJoin(event.getPlayer());
    }

    // --- spawn teleport block --------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombat(player.getUniqueId())) {
            return;
        }
        if (isBlockedSpawnCommand(event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize(
                    "<red>✗ Du kannst dich im Kampf nicht teleportieren."));
        }
    }

    private boolean isBlockedSpawnCommand(String message) {
        // Strip the leading slash and isolate the command root.
        String root = message.startsWith("/") ? message.substring(1) : message;
        int space = root.indexOf(' ');
        if (space >= 0) {
            root = root.substring(0, space);
        }
        return blockedSpawnCommands.contains(root.toLowerCase(Locale.ROOT));
    }

    // --- game-mode restriction -------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombat(player.getUniqueId())) {
            return;
        }
        if (hasAny(player, BYPASS_RANKS)) {
            return; // Admin / Developer / Owner bypass.
        }
        if (hasAny(player, RESTRICTED_RANKS)) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize(
                    "<red>✗ Gamemode-Wechsel im Kampf nicht erlaubt."));
        }
    }

    private static boolean hasAny(Player player, String[] nodes) {
        for (String node : nodes) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
