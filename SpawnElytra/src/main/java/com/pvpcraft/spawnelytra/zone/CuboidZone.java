package com.pvpcraft.spawnelytra.zone;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * A box-shaped launch zone defined by two free corner points, the same idea as a
 * WorldEdit selection: pick two positions with the selection wand and everything
 * between them (inclusive of both blocks) becomes the zone.
 *
 * <p>Coordinates are stored normalised so {@code min*} is always the lower corner
 * and {@code max*} the upper one, regardless of which order the points were set.
 */
public record CuboidZone(String name, String world,
                         double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) implements Zone {

    /**
     * Builds a cuboid from two arbitrary block corners. The order of the points
     * does not matter; the upper corner is expanded by one block so the whole
     * volume of the clicked blocks is included.
     */
    public static CuboidZone of(String name, String world, Location a, Location b) {
        double minX = Math.min(a.getBlockX(), b.getBlockX());
        double minY = Math.min(a.getBlockY(), b.getBlockY());
        double minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        double maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1.0;
        double maxY = Math.max(a.getBlockY(), b.getBlockY()) + 1.0;
        double maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1.0;
        return new CuboidZone(name, world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    @Override
    public String describe() {
        return String.format(Locale.ROOT,
                "<gray>Quader in <white>%s <gray>von <white>%.0f, %.0f, %.0f <gray>bis <white>%.0f, %.0f, %.0f",
                world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public void write(ConfigurationSection sec) {
        sec.set("type", "cuboid");
        sec.set("world", world);
        sec.set("min-x", minX);
        sec.set("min-y", minY);
        sec.set("min-z", minZ);
        sec.set("max-x", maxX);
        sec.set("max-y", maxY);
        sec.set("max-z", maxZ);
    }
}
