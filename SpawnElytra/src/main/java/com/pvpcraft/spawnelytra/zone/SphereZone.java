package com.pvpcraft.spawnelytra.zone;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * A spherical launch zone: anyone whose location is within {@code radius} blocks
 * of the centre (in the matching world) is considered inside the zone.
 */
public record SphereZone(String name, String world, double x, double y, double z, double radius)
        implements Zone {

    @Override
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        double dx = loc.getX() - x;
        double dy = loc.getY() - y;
        double dz = loc.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    @Override
    public String describe() {
        return String.format(Locale.ROOT,
                "<gray>Kugel in <white>%s <gray>@ <white>%.1f, %.1f, %.1f <gray>(r=<white>%.1f<gray>)",
                world, x, y, z, radius);
    }

    @Override
    public void write(ConfigurationSection sec) {
        sec.set("type", "sphere");
        sec.set("world", world);
        sec.set("x", x);
        sec.set("y", y);
        sec.set("z", z);
        sec.set("radius", radius);
    }
}
