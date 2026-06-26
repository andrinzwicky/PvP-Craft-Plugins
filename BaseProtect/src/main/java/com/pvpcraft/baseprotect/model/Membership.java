package com.pvpcraft.baseprotect.model;

/**
 * A single player's membership in a base: their {@link Role} plus the time they
 * joined (epoch millis). The join time drives the "ältester" (longest-serving)
 * tie-break used when an owner is removed and someone has to move up.
 */
public final class Membership {

    private Role role;
    private final long joinedAt;

    public Membership(Role role, long joinedAt) {
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /** Epoch millis when this player first joined the base. */
    public long getJoinedAt() {
        return joinedAt;
    }
}
