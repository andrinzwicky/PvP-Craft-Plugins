package com.pvpcraft.clansystem.model;

/**
 * A member's role inside a clan.
 *
 * <ul>
 *   <li>{@link #OWNER} - full control: promote/demote, invite, kick, disband,
 *       settag, setcolor. Exactly one per clan.</li>
 *   <li>{@link #LEADER} - can invite and kick members, but cannot disband or
 *       manage other leaders.</li>
 *   <li>{@link #MEMBER} - no management permissions.</li>
 * </ul>
 */
public enum ClanRole {
    OWNER,
    LEADER,
    MEMBER;

    /** True if this role may invite and kick members (Owner and Leader). */
    public boolean canManageMembers() {
        return this == OWNER || this == LEADER;
    }
}
