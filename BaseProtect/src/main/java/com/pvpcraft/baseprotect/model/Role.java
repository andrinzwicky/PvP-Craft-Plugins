package com.pvpcraft.baseprotect.model;

/**
 * BaseProtect's own role hierarchy, layered on top of WorldGuard.
 *
 * <p>WorldGuard only knows a flat "member" list. BaseProtect tracks who is
 * {@link #OWNER}, {@link #LEADER} or {@link #MEMBER} itself (see {@link Base}),
 * but mirrors <em>all three</em> roles into the WorldGuard region's member
 * domain so the protection flags never apply to any of them.
 */
public enum Role {
    OWNER,
    LEADER,
    MEMBER;

    public String display() {
        return switch (this) {
            case OWNER -> "Besitzer";
            case LEADER -> "Leader";
            case MEMBER -> "Mitglied";
        };
    }
}
