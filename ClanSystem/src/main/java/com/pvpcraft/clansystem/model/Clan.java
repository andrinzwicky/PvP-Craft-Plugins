package com.pvpcraft.clansystem.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A clan and its membership. UUID-based throughout.
 *
 * <p>Membership is split into three buckets so role lookups are O(1):
 * a single {@code owner}, a set of {@code leaders}, and a set of plain
 * {@code members}. A given UUID appears in exactly one bucket. The clan tag is
 * stored as plain text; the color is always a canonical hex string (e.g.
 * {@code #ff0000}) so it can be handed to RankManager unchanged.
 */
public final class Clan {

    /** Stable lowercase key used as the clans.yml section name and index key. */
    private final String key;

    private String name;
    private String tag;
    private String colorHex;

    private UUID owner;
    private final Set<UUID> leaders = new LinkedHashSet<>();
    private final Set<UUID> members = new LinkedHashSet<>();

    public Clan(String key, String name, String tag, String colorHex, UUID owner) {
        this.key = key;
        this.name = name;
        this.tag = tag;
        this.colorHex = colorHex;
        this.owner = owner;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String tag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    /** Canonical hex color, e.g. {@code #ff0000}. Never null. */
    public String colorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public UUID owner() {
        return owner;
    }

    public Set<UUID> leaders() {
        return leaders;
    }

    public Set<UUID> members() {
        return members;
    }

    /** All members in the clan: owner + leaders + plain members. */
    public Set<UUID> allMembers() {
        Set<UUID> all = new LinkedHashSet<>();
        all.add(owner);
        all.addAll(leaders);
        all.addAll(members);
        return all;
    }

    public int size() {
        return 1 + leaders.size() + members.size();
    }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || leaders.contains(uuid) || members.contains(uuid);
    }

    public ClanRole roleOf(UUID uuid) {
        if (owner.equals(uuid)) {
            return ClanRole.OWNER;
        }
        if (leaders.contains(uuid)) {
            return ClanRole.LEADER;
        }
        if (members.contains(uuid)) {
            return ClanRole.MEMBER;
        }
        return null;
    }

    /** Adds a UUID as a plain member (no-op if already in the clan). */
    public void addMember(UUID uuid) {
        if (!isMember(uuid)) {
            members.add(uuid);
        }
    }

    /** Removes a UUID from every bucket except owner. */
    public void removeMember(UUID uuid) {
        leaders.remove(uuid);
        members.remove(uuid);
    }

    /** Moves a plain member up to leader. Returns false if not a plain member. */
    public boolean promote(UUID uuid) {
        if (members.remove(uuid)) {
            leaders.add(uuid);
            return true;
        }
        return false;
    }

    /** Moves a leader down to plain member. Returns false if not a leader. */
    public boolean demote(UUID uuid) {
        if (leaders.remove(uuid)) {
            members.add(uuid);
            return true;
        }
        return false;
    }
}
