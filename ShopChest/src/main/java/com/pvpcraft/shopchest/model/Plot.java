package com.pvpcraft.shopchest.model;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A rectangular cuboid region that a player owns and may host shop chests. */
public class Plot {

    private final String id;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private UUID owner;
    private final Set<UUID> members = new HashSet<>();

    public Plot(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2, UUID owner) {
        this.id = id;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    /**
     * True if the location is within this plot's X/Z footprint, at any height.
     * Plots are treated as full-height columns: the Y axis is intentionally not
     * constrained so a chest placed on top of a ground-level selection still counts
     * as inside the plot. The stored minY/maxY are kept only for info/persistence.
     */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int getVolume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
