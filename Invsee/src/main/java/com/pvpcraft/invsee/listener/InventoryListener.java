package com.pvpcraft.invsee.listener;

import com.pvpcraft.invsee.Invsee;
import com.pvpcraft.invsee.InventoryMirror;
import com.pvpcraft.invsee.Messages;
import com.pvpcraft.invsee.MirrorHolder;
import com.pvpcraft.invsee.ViewSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Enforces read-only views, syncs editable inventory mirrors back to the target,
 * cleans up sessions on close, and closes a viewer's GUI the moment the player
 * they are viewing logs out.
 */
public final class InventoryListener implements Listener {

    private final Invsee plugin;

    public InventoryListener(Invsee plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        ViewSession session = plugin.sessions().get(viewer.getUniqueId());
        if (session == null) {
            return;
        }

        // Read-only: cancel every click, in both the top (viewed) and bottom
        // (viewer's own) inventory, so nothing leaves the viewed container.
        if (!session.editable()) {
            event.setCancelled(true);
            return;
        }

        // Editable: in a mirror, block clicks on the filler slots; otherwise let
        // the interaction through and sync the result back to the target.
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MirrorHolder mirror) {
            int raw = event.getRawSlot();
            if (raw < event.getView().getTopInventory().getSize() && InventoryMirror.isFiller(raw)) {
                event.setCancelled(true);
                return;
            }
            scheduleWriteBack(mirror);
        }
        // Editable enderchest views are the live container - no write-back needed.
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        ViewSession session = plugin.sessions().get(viewer.getUniqueId());
        if (session == null) {
            return;
        }

        if (!session.editable()) {
            event.setCancelled(true);
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MirrorHolder mirror) {
            for (int raw : event.getRawSlots()) {
                if (raw < event.getView().getTopInventory().getSize() && InventoryMirror.isFiller(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            scheduleWriteBack(mirror);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Final synchronous write-back so the last change is never lost.
        if (event.getInventory().getHolder() instanceof MirrorHolder mirror && mirror.editable()) {
            Player target = Bukkit.getPlayer(mirror.targetId());
            if (target != null && target.isOnline() && mirror.getInventory() != null) {
                InventoryMirror.writeBack(mirror.getInventory(), target);
            }
        }
        plugin.sessions().close(event.getPlayer().getUniqueId());
    }

    /**
     * When a viewed player leaves, close every viewer's GUI immediately and tell
     * them why. Also drops the quitter's own session if they had one open.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID quitterId = event.getPlayer().getUniqueId();
        String quitterName = event.getPlayer().getName();

        for (Map.Entry<UUID, ViewSession> entry : plugin.sessions().snapshot().entrySet()) {
            if (!entry.getValue().targetId().equals(quitterId)) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) {
                viewer.closeInventory(); // fires onClose -> removes the session
                Messages.send(viewer, Messages.TARGET_LEFT, Messages.player(quitterName));
            } else {
                plugin.sessions().close(entry.getKey());
            }
        }

        // The quitter's own open view (if any). The client's InventoryCloseEvent
        // on disconnect usually handles this, but clear it explicitly to be safe.
        plugin.sessions().close(quitterId);
    }

    /**
     * Applies the mirror's mapped slots to the target one tick later, after the
     * click has resolved (cursor moves, shift-clicks, hotbar swaps).
     */
    private void scheduleWriteBack(MirrorHolder mirror) {
        Inventory gui = mirror.getInventory();
        if (gui == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(mirror.targetId());
            if (target != null && target.isOnline()) {
                InventoryMirror.writeBack(gui, target);
            }
        });
    }
}
