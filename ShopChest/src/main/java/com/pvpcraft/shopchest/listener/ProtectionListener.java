package com.pvpcraft.shopchest.listener;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.model.Plot;
import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.Msg;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Enforces plot build permissions (break/place) and protects shop chests from
 * explosions. Inside a plot only the owner, invited members and staff may build
 * or break; outside plots vanilla behaviour applies. Breaking a shop chest (when
 * allowed) cleans up its shop record.
 */
public class ProtectionListener implements Listener {

    private static final String NO_BUILD =
            "<red>✖ Das ist nicht dein Plot – du kannst hier nicht bauen oder abbauen.";

    private final ShopChestPlugin plugin;

    public ProtectionListener(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Plot plot = plugin.plots().getPlotAt(block.getLocation());

        // Inside a plot, only owner/members/staff may break blocks.
        if (plot != null && !plugin.plots().canModify(player, plot)) {
            event.setCancelled(true);
            player.sendMessage(Msg.prefixed(NO_BUILD));
            return;
        }

        // Break is allowed; if this block was a shop chest, drop its record.
        Shop shop = plugin.shops().getShopByBlock(block);
        if (shop != null) {
            plugin.shops().removeShop(shop.getLocation());
            player.sendMessage(Msg.prefixed("<gray>Shop entfernt."));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Plot plot = plugin.plots().getPlotAt(event.getBlock().getLocation());
        if (plot != null && !plugin.plots().canModify(player, plot)) {
            event.setCancelled(true);
            player.sendMessage(Msg.prefixed(NO_BUILD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtected(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtected(event.blockList());
    }

    private void removeProtected(List<Block> blocks) {
        blocks.removeIf(block -> plugin.shops().getShopByBlock(block) != null);
    }
}
