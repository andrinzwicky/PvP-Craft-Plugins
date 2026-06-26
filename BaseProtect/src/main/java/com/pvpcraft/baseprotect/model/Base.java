package com.pvpcraft.baseprotect.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player base. Geometry (the cuboid) lives in WorldGuard as region
 * {@code base_<id>}; this object owns the <em>role hierarchy</em> only:
 * who is owner / leader / member, and since when.
 *
 * <p>There is at most one {@link Role#OWNER}. Every entry in {@link #members}
 * (owner, leaders and plain members alike) is mirrored as a WorldGuard region
 * member by the sync code.
 */
public class Base {

    private final String id;
    private final String world;
    // Insertion order kept for stable display; "oldest" is computed via joinedAt.
    private final Map<UUID, Membership> members = new LinkedHashMap<>();

    public Base(String id, String world) {
        this.id = id;
        this.world = world;
    }

    public String getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public Map<UUID, Membership> getMembers() {
        return members;
    }

    // --- role queries ----------------------------------------------------

    public boolean contains(UUID uuid) {
        return members.containsKey(uuid);
    }

    public Role roleOf(UUID uuid) {
        Membership m = members.get(uuid);
        return m == null ? null : m.getRole();
    }

    public UUID getOwner() {
        for (Map.Entry<UUID, Membership> e : members.entrySet()) {
            if (e.getValue().getRole() == Role.OWNER) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean isOwner(UUID uuid) {
        return uuid != null && uuid.equals(getOwner());
    }

    public boolean isLeader(UUID uuid) {
        return roleOf(uuid) == Role.LEADER;
    }

    /** Leaders, oldest first. */
    public List<UUID> getLeaders() {
        return uuidsWithRole(Role.LEADER);
    }

    /** Plain members (role MEMBER only), oldest first. */
    public List<UUID> getPlainMembers() {
        return uuidsWithRole(Role.MEMBER);
    }

    private List<UUID> uuidsWithRole(Role role) {
        List<Map.Entry<UUID, Membership>> matching = new ArrayList<>();
        for (Map.Entry<UUID, Membership> e : members.entrySet()) {
            if (e.getValue().getRole() == role) {
                matching.add(e);
            }
        }
        matching.sort(Comparator.comparingLong(e -> e.getValue().getJoinedAt()));
        List<UUID> result = new ArrayList<>(matching.size());
        for (Map.Entry<UUID, Membership> e : matching) {
            result.add(e.getKey());
        }
        return result;
    }

    // --- mutations -------------------------------------------------------

    public void put(UUID uuid, Role role, long joinedAt) {
        members.put(uuid, new Membership(role, joinedAt));
    }

    public void setRole(UUID uuid, Role role) {
        Membership m = members.get(uuid);
        if (m != null) {
            m.setRole(role);
        }
    }

    public void remove(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
