package com.pvpcraft.baseprotect.integration;

import com.pvpcraft.baseprotect.model.Base;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * All BaseProtect ↔ WorldGuard / WorldEdit interaction lives here.
 *
 * <h2>Namespace isolation</h2>
 * Every region this class creates, reads, edits or deletes is named
 * {@code base_<id>} (see {@link #PREFIX} / {@link #regionName}). When scanning
 * existing regions (overlap check, "which base am I standing in") it filters by
 * that prefix and ignores everything else. ShopChest's {@code plot_} regions are
 * therefore never touched, and the two plugins never share a region.
 *
 * <h2>Soft dependency</h2>
 * WorldGuard and WorldEdit are both optional at runtime. {@link #initialize()}
 * detects them once; if either is missing {@link #enabled} stays {@code false}
 * and every API method becomes a safe no-op / failure result. The WG/WE classes
 * are only ever reached while {@code enabled} is {@code true}.
 */
public class WorldGuardService {

    /** The one and only region prefix this plugin will ever touch. */
    public static final String PREFIX = "base_";

    private final Plugin plugin;
    private boolean enabled;

    public WorldGuardService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Detect WorldGuard + WorldEdit once, on startup. Safe when they are absent. */
    public void initialize() {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wg == null || !wg.isEnabled()) {
            enabled = false;
            plugin.getLogger().warning("WorldGuard not found - base region management disabled.");
            return;
        }
        if (we == null || !we.isEnabled()) {
            enabled = false;
            plugin.getLogger().warning("WorldEdit not found - /base define needs a //wand selection, disabled.");
            return;
        }
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            Class.forName("com.sk89q.worldedit.bukkit.WorldEditPlugin");
            enabled = true;
            plugin.getLogger().info("WorldGuard + WorldEdit detected - base region management enabled.");
        } catch (Throwable t) {
            enabled = false;
            plugin.getLogger().warning("WorldGuard/WorldEdit present but their API could not be loaded - region management disabled.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The region name a base id maps to: {@code base_<id>} (lower-cased). */
    public static String regionName(String baseId) {
        return PREFIX + baseId.toLowerCase(Locale.ROOT);
    }

    /** True if the given region id belongs to BaseProtect's namespace. */
    public static boolean isBaseRegion(String regionId) {
        return regionId != null && regionId.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    // --- /base define ----------------------------------------------------

    /** Outcome of {@link #defineFromSelection}. {@code ok==false} → {@code error} explains why. */
    public static final class DefineResult {
        public final boolean ok;
        public final String error;
        public final String world;

        private DefineResult(boolean ok, String error, String world) {
            this.ok = ok;
            this.error = error;
            this.world = world;
        }

        static DefineResult fail(String error) {
            return new DefineResult(false, error, null);
        }

        static DefineResult success(String world) {
            return new DefineResult(true, null, world);
        }
    }

    /**
     * Read {@code player}'s current WorldEdit selection, verify it does not
     * intersect any existing {@code base_} region, then create the WorldGuard
     * region {@code base_<id>} with the full protection flag set (no members yet
     * - the owner is assigned later via /base setowner).
     *
     * <p>Does not create anything if the selection is missing/incomplete or if
     * an overlap is detected. Returns a {@link DefineResult} describing the
     * outcome; never throws.
     */
    public DefineResult defineFromSelection(Player player, String baseId) {
        if (!enabled) {
            return DefineResult.fail("WorldGuard/WorldEdit ist nicht verfügbar.");
        }
        try {
            WorldEditPlugin we = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
            if (we == null) {
                return DefineResult.fail("WorldEdit ist nicht verfügbar.");
            }

            com.sk89q.worldedit.regions.Region selection;
            World bukkitWorld;
            try {
                com.sk89q.worldedit.world.World weWorld = we.getSession(player).getSelectionWorld();
                if (weWorld == null) {
                    return DefineResult.fail("Du hast keine WorldEdit-Auswahl. Nutze //wand und markiere Pos1/Pos2.");
                }
                selection = we.getSession(player).getSelection(weWorld);
                bukkitWorld = BukkitAdapter.adapt(weWorld);
            } catch (IncompleteRegionException ex) {
                return DefineResult.fail("Deine WorldEdit-Auswahl ist unvollständig. Setze Pos1 UND Pos2 (//wand).");
            }

            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();

            RegionManager regions = regionManager(bukkitWorld);
            if (regions == null) {
                return DefineResult.fail("Für die Welt '" + bukkitWorld.getName() + "' gibt es keinen WorldGuard-RegionManager.");
            }

            String name = regionName(baseId);
            if (regions.hasRegion(name)) {
                return DefineResult.fail("Eine WorldGuard-Region '" + name + "' existiert bereits.");
            }

            // --- overlap check: ONLY against base_ regions ------------------
            ProtectedCuboidRegion candidate = new ProtectedCuboidRegion(name, min, max);
            List<ProtectedRegion> baseRegions = new ArrayList<>();
            for (ProtectedRegion existing : regions.getRegions().values()) {
                if (isBaseRegion(existing.getId())) {
                    baseRegions.add(existing);
                }
            }
            List<ProtectedRegion> intersecting = candidate.getIntersectingRegions(baseRegions);
            if (!intersecting.isEmpty()) {
                String hit = intersecting.get(0).getId();
                return DefineResult.fail("Die Auswahl überschneidet eine bestehende Basis ('" + hit + "'). Basen dürfen sich nie überlappen.");
            }

            applyProtectionFlags(candidate);
            regions.addRegion(candidate);
            return DefineResult.success(bukkitWorld.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("defineFromSelection failed for base '" + baseId + "': " + t.getMessage());
            return DefineResult.fail("Interner Fehler beim Anlegen der Region: " + t.getMessage());
        }
    }

    /**
     * Apply the "everything denied except entry" protection set to a region.
     *
     * <p>Each deny flag is scoped to {@link RegionGroup#NON_MEMBERS} via the
     * flag's group flag, so the deny only ever applies to <em>non-members</em>.
     * Region members (= base owner/leaders/members) are exempt and may do
     * everything. {@code entry} is set to ALLOW for everyone so anyone may walk
     * in. This is the correct way to express "Member dürfen alles, Fremde nur
     * betreten" - a plain {@code build deny} would otherwise lock out members
     * too.
     */
    private void applyProtectionFlags(ProtectedRegion region) {
        // Everyone may enter.
        region.setFlag(Flags.ENTRY, StateFlag.State.ALLOW);
        // Everything else: denied for non-members only.
        denyForNonMembers(region, Flags.BUILD);          // bauen/abbauen
        denyForNonMembers(region, Flags.INTERACT);       // Türen, Knöpfe, Hebel ...
        denyForNonMembers(region, Flags.USE);            // generische Nutzung
        denyForNonMembers(region, Flags.CHEST_ACCESS);   // Truhen/Container
        denyForNonMembers(region, Flags.DAMAGE_ANIMALS); // Tiere verletzen
        denyForNonMembers(region, Flags.PVP);            // Spieler-Schaden
        denyForNonMembers(region, Flags.ITEM_PICKUP);    // Items aufheben
        denyForNonMembers(region, Flags.RIDE);           // Reiten/Boote/Minecarts
    }

    private void denyForNonMembers(ProtectedRegion region, StateFlag flag) {
        region.setFlag(flag, StateFlag.State.DENY);
        region.setFlag(flag.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);
    }

    // --- membership sync -------------------------------------------------

    /**
     * Rewrite the region's member domain so it exactly mirrors the base's role
     * map (owner + leaders + members are all WorldGuard members). Idempotent;
     * called after every membership change. No-op if WG is absent or the region
     * is gone.
     */
    public void syncMembers(Base base) {
        if (!enabled || base == null) {
            return;
        }
        try {
            ProtectedRegion region = regionOf(base);
            if (region == null) {
                return;
            }
            DefaultDomain domain = new DefaultDomain();
            for (UUID uuid : base.getMembers().keySet()) {
                domain.addPlayer(uuid);
            }
            region.setMembers(domain);
        } catch (Throwable t) {
            plugin.getLogger().warning("syncMembers failed for base '" + base.getId() + "': " + t.getMessage());
        }
    }

    // --- /base delete ----------------------------------------------------

    /** Remove the base's WorldGuard region. Returns true if a region was removed. */
    public boolean deleteRegion(Base base) {
        if (!enabled || base == null) {
            return false;
        }
        try {
            World world = Bukkit.getWorld(base.getWorld());
            if (world == null) {
                plugin.getLogger().warning("Cannot delete region for base '" + base.getId()
                        + "': world '" + base.getWorld() + "' not loaded.");
                return false;
            }
            RegionManager regions = regionManager(world);
            if (regions == null) {
                return false;
            }
            String name = regionName(base.getId());
            if (!regions.hasRegion(name)) {
                return false;
            }
            regions.removeRegion(name);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("deleteRegion failed for base '" + base.getId() + "': " + t.getMessage());
            return false;
        }
    }

    // --- "which base am I standing in" -----------------------------------

    /**
     * The base id of the {@code base_} region the location sits in, or
     * {@code null} if the player is not standing in any base region. If several
     * base regions overlap (they shouldn't - overlap is blocked at define) the
     * first one found is returned.
     */
    public String baseIdAt(Location loc) {
        if (!enabled || loc == null || loc.getWorld() == null) {
            return null;
        }
        try {
            RegionManager regions = regionManager(loc.getWorld());
            if (regions == null) {
                return null;
            }
            BlockVector3 point = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            ApplicableRegionSet set = regions.getApplicableRegions(point);
            for (ProtectedRegion region : set) {
                if (isBaseRegion(region.getId())) {
                    return region.getId().substring(PREFIX.length());
                }
            }
            return null;
        } catch (Throwable t) {
            plugin.getLogger().warning("baseIdAt failed: " + t.getMessage());
            return null;
        }
    }

    /** True if a {@code base_<id>} region currently exists in its world. */
    public boolean regionExists(Base base) {
        if (!enabled || base == null) {
            return false;
        }
        World world = Bukkit.getWorld(base.getWorld());
        if (world == null) {
            return false;
        }
        RegionManager regions = regionManager(world);
        return regions != null && regions.hasRegion(regionName(base.getId()));
    }

    // --- internals -------------------------------------------------------

    private ProtectedRegion regionOf(Base base) {
        World world = Bukkit.getWorld(base.getWorld());
        if (world == null) {
            return null;
        }
        RegionManager regions = regionManager(world);
        return regions == null ? null : regions.getRegion(regionName(base.getId()));
    }

    private RegionManager regionManager(World world) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(world));
    }
}
