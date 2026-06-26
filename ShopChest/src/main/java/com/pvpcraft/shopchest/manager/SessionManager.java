package com.pvpcraft.shopchest.manager;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks per-player transient UI state: open setup target and pending chat input. */
public class SessionManager {

    /** What a pending chat message should be interpreted as. */
    public enum ChatRequestType {
        PRICE,
        INVITE_MEMBER
    }

    public record ChatRequest(ChatRequestType type, Location shopChest) {
    }

    /** Chest location of the shop whose Setup GUI a player currently has open. */
    private final Map<UUID, Location> openSetup = new HashMap<>();
    /** Players whose next inventory click should pick the sale item. */
    private final Set<UUID> awaitingSaleItem = new HashSet<>();
    /** Pending chat-input requests. */
    private final Map<UUID, ChatRequest> pendingChat = new HashMap<>();

    public void setOpenSetup(UUID player, Location chest) {
        openSetup.put(player, chest);
    }

    public Location getOpenSetup(UUID player) {
        return openSetup.get(player);
    }

    public void clearOpenSetup(UUID player) {
        openSetup.remove(player);
        awaitingSaleItem.remove(player);
    }

    public void setAwaitingSaleItem(UUID player, boolean awaiting) {
        if (awaiting) {
            awaitingSaleItem.add(player);
        } else {
            awaitingSaleItem.remove(player);
        }
    }

    public boolean isAwaitingSaleItem(UUID player) {
        return awaitingSaleItem.contains(player);
    }

    public void setPendingChat(UUID player, ChatRequest request) {
        pendingChat.put(player, request);
    }

    public ChatRequest getPendingChat(UUID player) {
        return pendingChat.get(player);
    }

    public ChatRequest consumePendingChat(UUID player) {
        return pendingChat.remove(player);
    }

    public boolean hasPendingChat(UUID player) {
        return pendingChat.containsKey(player);
    }

    public void clearAll(UUID player) {
        openSetup.remove(player);
        awaitingSaleItem.remove(player);
        pendingChat.remove(player);
    }
}
