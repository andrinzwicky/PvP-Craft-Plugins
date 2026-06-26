package com.pvpcraft.spawnelytra.zone;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A launch zone. Two shapes exist: a {@link SphereZone} (centre + radius) and a
 * {@link CuboidZone} (two free corner points, like a WorldEdit selection). A
 * player whose location is {@link #contains(Location) inside} the zone is
 * launched.
 */
public sealed interface Zone permits SphereZone, CuboidZone {

    /** The zone's unique name. */
    String name();

    /** Name of the world this zone lives in. */
    String world();

    /** Whether {@code loc} lies inside this zone. */
    boolean contains(Location loc);

    /** A one-line MiniMessage summary for {@code /spawnelytra listzone}. */
    String describe();

    /** Writes this zone's data (including its {@code type}) into {@code sec}. */
    void write(ConfigurationSection sec);
}
