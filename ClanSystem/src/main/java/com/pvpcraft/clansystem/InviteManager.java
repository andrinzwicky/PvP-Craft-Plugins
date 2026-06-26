package com.pvpcraft.clansystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks pending clan invitations in memory (they intentionally do not survive a
 * restart). Each invite expires after {@link #EXPIRY_MILLIS}.
 */
public final class InviteManager {

    public static final long EXPIRY_MILLIS = 5 * 60 * 1000L; // 5 minutes

    /** invited player -> (clan key -> expiry timestamp in epoch millis). */
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();

    public void invite(UUID player, String clanKey) {
        invites.computeIfAbsent(player, k -> new HashMap<>())
                .put(clanKey, System.currentTimeMillis() + EXPIRY_MILLIS);
    }

    /**
     * Checks whether {@code player} currently has a valid invite to {@code clanKey}.
     * Expired entries are pruned as a side effect.
     */
    public boolean hasValidInvite(UUID player, String clanKey) {
        Map<String, Long> byClan = invites.get(player);
        if (byClan == null) {
            return false;
        }
        Long expiry = byClan.get(clanKey);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            byClan.remove(clanKey);
            if (byClan.isEmpty()) {
                invites.remove(player);
            }
            return false;
        }
        return true;
    }

    /**
     * Returns the clan keys this player currently has a <em>valid</em> invite to.
     * Expired entries are pruned as a side effect.
     */
    public List<String> validInvites(UUID player) {
        List<String> keys = new ArrayList<>();
        Map<String, Long> byClan = invites.get(player);
        if (byClan == null) {
            return keys;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Long>> it = byClan.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() < now) {
                it.remove();
            } else {
                keys.add(entry.getKey());
            }
        }
        if (byClan.isEmpty()) {
            invites.remove(player);
        }
        return keys;
    }

    /** True if the player has (or had) any invite to this clan, valid or expired. */
    public boolean hasAnyInvite(UUID player, String clanKey) {
        Map<String, Long> byClan = invites.get(player);
        return byClan != null && byClan.containsKey(clanKey);
    }

    public void consume(UUID player, String clanKey) {
        Map<String, Long> byClan = invites.get(player);
        if (byClan != null) {
            byClan.remove(clanKey);
            if (byClan.isEmpty()) {
                invites.remove(player);
            }
        }
    }

    /** Drops all invites referencing a clan that no longer exists. */
    public void purgeClan(String clanKey) {
        for (Iterator<Map.Entry<UUID, Map<String, Long>>> it = invites.entrySet().iterator(); it.hasNext(); ) {
            Map<String, Long> byClan = it.next().getValue();
            byClan.remove(clanKey);
            if (byClan.isEmpty()) {
                it.remove();
            }
        }
    }
}
