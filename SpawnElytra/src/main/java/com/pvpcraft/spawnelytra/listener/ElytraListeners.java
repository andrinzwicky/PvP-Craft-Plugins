package com.pvpcraft.spawnelytra.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.pvpcraft.spawnelytra.LaunchManager;
import com.pvpcraft.spawnelytra.SpawnElytra;
import com.pvpcraft.spawnelytra.selection.SelectionManager;
import com.pvpcraft.spawnelytra.zone.ZoneManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wires SpawnElytra to Paper events: detecting zone entry, glide/landing,
 * locking the chestplate slot and the Elytra itself, and restoring/persisting
 * flights across reconnects.
 */
public final class ElytraListeners implements Listener {

    private static final String ADMIN_PERMISSION = "spawnelytra.admin";

    private final SpawnElytra plugin;
    private final ZoneManager zones;
    private final LaunchManager launches;
    private final SelectionManager selection;

    /** Players currently standing inside a launch zone (for entry-edge detection). */
    private final Set<UUID> inZone = new HashSet<>();

    public ElytraListeners(SpawnElytra plugin, ZoneManager zones,
                           LaunchManager launches, SelectionManager selection) {
        this.plugin = plugin;
        this.zones = zones;
        this.launches = launches;
        this.selection = selection;
    }

    // --- zone entry ------------------------------------------------------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Block from = event.getFrom().getBlock();
        Block to = event.getTo().getBlock();
        if (from.equals(to)) {
            return; // only react when the player actually changes block
        }

        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        boolean nowInside = zones.zoneAt(event.getTo()) != null;
        boolean wasInside = inZone.contains(pid);

        if (nowInside && !wasInside) {
            inZone.add(pid);
            if (!launches.hasActiveLaunch(pid)) {
                launches.launch(player);
            } else {
                // Already flying: make sure they still have their Elytra.
                launches.ensureElytraEquipped(player);
            }
        } else if (!nowInside && wasInside) {
            inZone.remove(pid);
        }
    }

    // --- selection wand --------------------------------------------------

    /**
     * Left-click sets corner point 1, right-click sets corner point 2 when the
     * admin is holding the tagged selection wand (golden shovel).
     */
    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!selection.isWand(event.getItem()) || !player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Location loc = block.getLocation();
        UUID pid = player.getUniqueId();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.setPos1(pid, loc);
            sendPoint(player, 1, loc);
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.setPos2(pid, loc);
            sendPoint(player, 2, loc);
            event.setCancelled(true);
        }
    }

    private void sendPoint(Player player, int number, Location loc) {
        plugin.send(player, "<green>Punkt <white>" + number + "</white> gesetzt: <white>"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    // --- glide / landing -------------------------------------------------

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!launches.hasActiveLaunch(player.getUniqueId())) {
            return;
        }
        if (event.isGliding()) {
            launches.markGliding(player);
        } else {
            launches.onGlideStop(player);
        }
    }

    // --- chestplate / Elytra lock ----------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !launches.hasActiveLaunch(player.getUniqueId())) {
            return;
        }
        // Any interaction involving the tagged Elytra is forbidden.
        if (launches.isSpawnElytra(event.getCurrentItem())
                || launches.isSpawnElytra(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        // Number-key swap that would pull/push the Elytra via the hotbar.
        if (event.getHotbarButton() >= 0
                && launches.isSpawnElytra(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
            return;
        }
        // Lock the chestplate slot itself (direct click / hotbar swap onto it).
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                && event.getSlot() == LaunchManager.CHESTPLATE_SLOT) {
            event.setCancelled(true);
            return;
        }
        // Shift-clicking a chestplate/Elytra would auto-equip it to the chest slot.
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && launches.isChestArmor(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    /** Right-click-to-equip a chestplate/Elytra held in hand is blocked. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!launches.hasActiveLaunch(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (launches.isChestArmor(event.getItem())) {
            // Deny only the item use (the equip); block interaction still works.
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /**
     * Last line of defence: if any chestplate other than the tagged Elytra ends
     * up equipped while a launch is active, undo it. {@link PlayerArmorChangeEvent}
     * is not cancellable, so the manager reverts the change next tick.
     */
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getSlotType() != PlayerArmorChangeEvent.SlotType.CHEST) {
            return;
        }
        Player player = event.getPlayer();
        if (!launches.hasActiveLaunch(player.getUniqueId())) {
            return;
        }
        // The plugin's own equip of the tagged Elytra is the only allowed change.
        if (launches.isElytraFor(event.getNewItem(), player.getUniqueId())) {
            return;
        }
        launches.revertChestplate(player, event.getNewItem());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !launches.hasActiveLaunch(player.getUniqueId())) {
            return;
        }
        if (launches.isSpawnElytra(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (launches.hasActiveLaunch(event.getPlayer().getUniqueId())
                && launches.isSpawnElytra(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // --- death -----------------------------------------------------------

    /**
     * On death during an active flight, delete the tagged Elytra and drop (or, with
     * keepInventory, re-equip) the real saved chestplate, then wipe the flight state
     * so nothing is restored afterwards. Runs at HIGHEST so the keepInventory gamerule
     * and any other plugin toggling it are already reflected in
     * {@link PlayerDeathEvent#getKeepInventory()} when we read it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID pid = player.getUniqueId();
        if (!launches.hasActiveLaunch(pid)) {
            return;
        }
        launches.handleDeath(player, event.getDrops(), event.getKeepInventory());
        inZone.remove(pid);
    }

    // --- container tracking ----------------------------------------------

    @EventHandler
    public void onContainerClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            launches.rememberContainer(player, event.getInventory());
        }
    }

    // --- join / quit -----------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        launches.restoreOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        launches.handleQuit(player);
        inZone.remove(player.getUniqueId());
        selection.clear(player.getUniqueId());
    }
}
