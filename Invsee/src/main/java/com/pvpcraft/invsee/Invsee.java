package com.pvpcraft.invsee;

import com.pvpcraft.invsee.command.InvseeCommand;
import com.pvpcraft.invsee.listener.InventoryListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lets staff inspect (and, with the {@code +} variants, edit) other players'
 * inventories and enderchests.
 *
 * <p>Access is granted purely through RankManager's permission nodes
 * ({@code rankmanager.rank.*}), so the plugin works without RankManager present
 * - the nodes are then simply never granted and only operators keep access.
 *
 * <p>Read-only views open the target's live inventory and cancel every click;
 * editable views open the same live inventory and allow free interaction, so
 * taken/given items apply directly with no syncing and no risk of duping.
 */
public final class Invsee extends JavaPlugin {

    // Ranks allowed to read (view) inventories/enderchests.
    private static final String[] VIEW_NODES = {
            "rankmanager.rank.moderator",
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    // Ranks allowed to edit inventories/enderchests.
    private static final String[] EDIT_NODES = {
            "rankmanager.rank.developer",
            "rankmanager.rank.admin",
            "rankmanager.rank.owner"
    };

    private final SessionManager sessions = new SessionManager();

    @Override
    public void onEnable() {
        InvseeCommand executor = new InvseeCommand(this);
        register("invsee", executor);
        register("invsee+", executor);
        register("echest", executor);
        register("echest+", executor);

        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        getLogger().info("Invsee enabled.");
    }

    private void register(String name, InvseeCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command '" + name + "' missing from plugin.yml!");
        }
    }

    public SessionManager sessions() {
        return sessions;
    }

    /** True if the player may open a read-only view (Moderator and above). */
    public boolean canView(Player player) {
        return hasAny(player, VIEW_NODES);
    }

    /** True if the player may open an editable view (Developer and above). */
    public boolean canEdit(Player player) {
        return hasAny(player, EDIT_NODES);
    }

    private static boolean hasAny(Player player, String[] nodes) {
        if (player.isOp()) {
            return true;
        }
        for (String node : nodes) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
