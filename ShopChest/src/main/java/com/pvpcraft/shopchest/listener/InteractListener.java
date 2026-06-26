package com.pvpcraft.shopchest.listener;

import com.pvpcraft.shopchest.ShopChestPlugin;
import com.pvpcraft.shopchest.gui.ShopGui;
import com.pvpcraft.shopchest.model.Plot;
import com.pvpcraft.shopchest.model.Shop;
import com.pvpcraft.shopchest.util.ItemUtil;
import com.pvpcraft.shopchest.util.Msg;
import com.pvpcraft.shopchest.util.RankUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Handles the selection wand, configurator usage and shop purchases. */
public class InteractListener implements Listener {

    private static final String NO_PERMISSION =
            "<red>✖ Du hast keine Berechtigung hier einen Shop zu erstellen.";

    private final ShopChestPlugin plugin;

    public InteractListener(ShopChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();
        Block clicked = event.getClickedBlock();

        // --- Selection wand ---
        if (ItemUtil.isSelector(plugin.keys(), item)) {
            if (!player.hasPermission("shopchest.admin")) {
                return;
            }
            if (action == Action.LEFT_CLICK_BLOCK && clicked != null) {
                setPos1(player, clicked.getLocation());
                event.setCancelled(true);
            } else if (action == Action.RIGHT_CLICK_BLOCK && clicked != null) {
                setPos2(player, clicked.getLocation());
                event.setCancelled(true);
            }
            return;
        }

        // Everything below only concerns right-clicking a chest.
        if (action != Action.RIGHT_CLICK_BLOCK || clicked == null) {
            return;
        }
        if (!(clicked.getState() instanceof Chest)) {
            return;
        }

        // --- Configurator: open the Setup GUI ---
        if (ItemUtil.isConfigurator(plugin.keys(), item)) {
            event.setCancelled(true);
            handleConfigurator(player, clicked);
            return;
        }

        // --- Purchase / owner+member restock ---
        Shop shop = plugin.shops().getShopByBlock(clicked);
        if (shop == null) {
            return; // not a shop chest; let the chest open normally
        }
        Plot plot = plugin.plots().getPlot(shop.getPlotId());
        UUID uuid = player.getUniqueId();
        boolean manager = RankUtil.hasBypass(player)
                || uuid.equals(shop.getOwner())
                || (plot != null && (plot.isOwner(uuid) || plot.isMember(uuid)));
        if (manager) {
            return; // owner, member or staff: open the chest to restock
        }
        // A buyer interacts: prevent chest opening and open the purchase GUI.
        event.setCancelled(true);
        if (!shop.isActive() || !shop.isConfigured()) {
            player.sendMessage(Msg.mm("<gray>⚠ Dieser Shop ist momentan inaktiv."));
            return;
        }
        if (plugin.shopService().getStock(shop) <= 0) {
            player.sendMessage(Msg.mm("<red>✖ Dieser Shop hat keinen Vorrat mehr."));
            return;
        }
        ShopGui.openPurchase(plugin, player, shop);
    }

    /** Creative instant-break with the wand still sets position 1. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemUtil.isSelector(plugin.keys(), item) && player.hasPermission("shopchest.admin")) {
            setPos1(player, event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    private void setPos1(Player player, Location loc) {
        plugin.plots().setPos1(player.getUniqueId(), loc);
        player.sendMessage(Msg.prefixed("<gray>Position 1: <yellow><x>, <y>, <z></yellow>",
                Placeholder.unparsed("x", String.valueOf(loc.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(loc.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(loc.getBlockZ()))));
    }

    private void setPos2(Player player, Location loc) {
        plugin.plots().setPos2(player.getUniqueId(), loc);
        player.sendMessage(Msg.prefixed("<gray>Position 2: <yellow><x>, <y>, <z></yellow>",
                Placeholder.unparsed("x", String.valueOf(loc.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(loc.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(loc.getBlockZ()))));
    }

    private void handleConfigurator(Player player, Block chest) {
        Location loc = chest.getLocation();
        Plot plot = plugin.plots().getPlotAt(loc);
        debugConfigurator(player, loc, plot);

        if (plot == null) {
            player.sendMessage(Msg.mm(NO_PERMISSION));
            return;
        }
        // Owner, invited members and staff may configure shop chests on the plot.
        if (!plugin.plots().canModify(player, plot)) {
            player.sendMessage(Msg.mm(NO_PERMISSION));
            return;
        }

        Shop shop = plugin.shops().getShopByBlock(chest);
        if (shop == null) {
            UUID shopOwner = (plot.getOwner() != null) ? plot.getOwner() : player.getUniqueId();
            shop = new Shop(loc, shopOwner, plot.getId());
            plugin.shops().addShop(shop);
        }
        ShopGui.openSetup(plugin, player, shop);
    }

    /**
     * Diagnostic for "keine Berechtigung hier einen Shop zu erstellen". Enabled by
     * {@code debug: true} in config.yml. Logs the chest location, whether a plot was
     * found and — crucially — when none was found, each plot's cuboid with per-axis
     * containment so a too-tight Y range (a chest sitting just above a ground-level
     * selection) is obvious. When a plot was found it logs both owner and player
     * UUID plus the comparison result.
     */
    private void debugConfigurator(Player player, Location loc, Plot found) {
        if (!plugin.getConfig().getBoolean("debug", false)) {
            return;
        }
        var log = plugin.getLogger();
        String world = loc.getWorld() == null ? "null" : loc.getWorld().getName();
        log.info("[debug] Configurator: player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " chest=" + world + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        if (found == null) {
            log.info("[debug] getPlotAt -> NO matching plot. Checking "
                    + plugin.plots().getPlots().size() + " plot(s) per axis:");
            for (Plot p : plugin.plots().getPlots()) {
                boolean worldMatch = loc.getWorld() != null && loc.getWorld().getName().equals(p.getWorld());
                boolean xIn = loc.getBlockX() >= p.getMinX() && loc.getBlockX() <= p.getMaxX();
                boolean yIn = loc.getBlockY() >= p.getMinY() && loc.getBlockY() <= p.getMaxY();
                boolean zIn = loc.getBlockZ() >= p.getMinZ() && loc.getBlockZ() <= p.getMaxZ();
                log.info("[debug]   plot '" + p.getId() + "' owner=" + p.getOwner()
                        + " box=[" + p.getMinX() + "," + p.getMinY() + "," + p.getMinZ()
                        + " .. " + p.getMaxX() + "," + p.getMaxY() + "," + p.getMaxZ() + "]"
                        + " worldMatch=" + worldMatch + " xIn=" + xIn + " yIn=" + yIn + " zIn=" + zIn);
            }
        } else {
            log.info("[debug] getPlotAt -> plot '" + found.getId() + "'"
                    + " plotOwner=" + found.getOwner()
                    + " playerUuid=" + player.getUniqueId()
                    + " isOwner=" + found.isOwner(player.getUniqueId())
                    + " isMember=" + found.isMember(player.getUniqueId())
                    + " bypass=" + RankUtil.hasBypass(player)
                    + " canModify=" + plugin.plots().canModify(player, found));
        }
    }
}
