package com.pvpcraft.afkmode.listener;

import com.pvpcraft.afkmode.AFKManager;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Iterator;

/**
 * Wires the AFK rules to Paper events. See {@code README}/the class-level event
 * list for the why of each handler.
 */
public final class AFKListener implements Listener {

    private final AFKManager afk;

    public AFKListener(AFKManager afk) {
        this.afk = afk;
    }

    // --- auto-end on the player's OWN movement input ----------------------

    /**
     * The only way AFK ends automatically: the player presses a movement key
     * (WASD or jump). Look-only (mouse/head turn) is not part of {@link Input}
     * at all, and sneaking/sprinting are deliberately ignored.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (!afk.isAfk(player)) {
            return;
        }
        Input input = event.getInput();
        if (input.isForward() || input.isBackward() || input.isLeft()
                || input.isRight() || input.isJump()) {
            afk.deactivate(player, true);
        }
    }

    // --- position pinning -------------------------------------------------

    /**
     * Pins the position within the tick: any positional change from the
     * environment (water/lava flow, pistons, falling, boats, sand) is snapped
     * back to the pin while the current look direction is preserved. Teleports
     * (a {@link PlayerTeleportEvent} subclass) are intentionally skipped here and
     * handled by {@link #onTeleport}.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Player player = event.getPlayer();
        Location pin = afk.getPinned(player.getUniqueId());
        if (pin == null) {
            return;
        }
        Location to = event.getTo();
        if (!pin.getWorld().equals(to.getWorld())
                || pin.getX() != to.getX() || pin.getY() != to.getY() || pin.getZ() != to.getZ()) {
            event.setTo(new Location(pin.getWorld(), pin.getX(), pin.getY(), pin.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    /**
     * An external teleport (/tp, plugin) drags the pin along so the AFK player
     * stays AFK and pinned at the destination. The player's own movement already
     * ends AFK before any teleport, so this only ever moves admin/plugin teleports.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (afk.isAfk(player)) {
            afk.repin(player.getUniqueId(), event.getTo());
        }
    }

    /** Cancels any external knockback/velocity (players, mobs, explosions). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (afk.isAfk(event.getPlayer())) {
            event.setVelocity(new Vector(0, 0, 0));
            event.setCancelled(true);
        }
    }

    // --- damage rules -----------------------------------------------------

    /**
     * Damage handling while AFK:
     * <ul>
     *   <li>victim AFK + explosion (TNT/bed/crystal) &rarr; cancelled (counts as player-caused)</li>
     *   <li>victim AFK + damage from a player (melee or projectile) &rarr; cancelled (no PvP)</li>
     *   <li>victim AFK + mob/environment &rarr; normal damage</li>
     *   <li>attacker AFK hitting a player &rarr; cancelled (AFK can't attack players)</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // Victim side.
        if (event.getEntity() instanceof Player victim && afk.isAfk(victim)) {
            switch (event.getCause()) {
                case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> {
                    event.setCancelled(true);
                    return;
                }
                default -> {
                    if (event instanceof EntityDamageByEntityEvent byEntity
                            && resolveAttacker(byEntity) instanceof Player) {
                        event.setCancelled(true); // PvP damage blocked
                        return;
                    }
                    // mob / environment damage passes through unchanged
                }
            }
        }

        // Attacker side: an AFK player may not damage other players.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getEntity() instanceof Player
                && resolveAttacker(byEntity) instanceof Player attacker
                && afk.isAfk(attacker)) {
            event.setCancelled(true);
        }
    }

    /** Resolves the responsible player of a hit: direct melee or a player's projectile. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player direct) {
            return direct;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player shooterPlayer) {
                return shooterPlayer;
            }
        }
        return null;
    }

    // --- protect the floor block -----------------------------------------

    /** The block directly under an AFK player can't be mined out from under them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (afk.isStandBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Keep the floor block out of any entity explosion (TNT, creeper, crystal). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtectedBlocks(event.blockList().iterator());
    }

    /** Keep the floor block out of any block explosion (bed/anchor in dimensions). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtectedBlocks(event.blockList().iterator());
    }

    private void removeProtectedBlocks(Iterator<Block> it) {
        while (it.hasNext()) {
            if (afk.isStandBlock(it.next())) {
                it.remove();
            }
        }
    }

    // --- lifecycle / cleanup ---------------------------------------------

    /** Dying (by environment/mob) while AFK ends the AFK status cleanly. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        afk.clear(event.getEntity().getUniqueId());
    }

    /** Logging out clears AFK so the player is never still AFK on next login. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        afk.clear(event.getPlayer().getUniqueId());
        afk.forgetCombat(event.getPlayer().getUniqueId());
    }

    /** Defensive: a freshly joined player is never AFK (in-memory state is gone). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        afk.clear(event.getPlayer().getUniqueId());
    }
}
