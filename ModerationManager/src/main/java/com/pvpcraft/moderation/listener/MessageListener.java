package com.pvpcraft.moderation.listener;

import com.pvpcraft.moderation.ModerationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Two jobs around private messaging:
 *  1) blocks /msg, /tell, /r (and /me) for muted players, and
 *  2) logs every delivered private message to the message log file.
 *
 * Reply targets are resolved through an in-memory "last conversation partner"
 * map, so /r is both blocked while muted and logged with the right recipient.
 */
public final class MessageListener implements Listener {

    /** Commands that carry an explicit target: "/msg <player> <text>". */
    private static final Set<String> MESSAGE_COMMANDS = Set.of(
            "msg", "tell", "w", "whisper", "m", "pm", "dm", "message", "t", "emsg");
    /** Reply commands whose target is the last conversation partner. */
    private static final Set<String> REPLY_COMMANDS = Set.of("r", "reply");

    private final ModerationManager plugin;
    private final Map<UUID, UUID> lastPartner = new HashMap<>();

    public MessageListener(ModerationManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        String[] parts = split(event.getMessage());
        if (parts.length == 0) {
            return;
        }
        String cmd = normalize(parts[0]);

        boolean isMessage = MESSAGE_COMMANDS.contains(cmd);
        boolean isReply = REPLY_COMMANDS.contains(cmd);
        if (!isMessage && !isReply && !cmd.equals("me")) {
            return;
        }

        // Muted players may not use any of these.
        if (plugin.mutes().isMuted(sender.getUniqueId())) {
            event.setCancelled(true);
            plugin.sendMutedNotice(sender);
            return;
        }

        // /me is blocked above when muted but is not a private message: don't log.
        if (cmd.equals("me")) {
            return;
        }

        // Resolve recipient + text, then log if it will actually be delivered.
        Player recipient;
        String text;
        if (isMessage) {
            if (parts.length < 3) {
                return; // "/msg name" with no text — let vanilla handle it
            }
            recipient = Bukkit.getPlayerExact(parts[1]);
            text = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        } else { // reply
            if (parts.length < 2) {
                return;
            }
            UUID partner = lastPartner.get(sender.getUniqueId());
            recipient = partner == null ? null : Bukkit.getPlayer(partner);
            text = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        }

        if (recipient == null || recipient.equals(sender)) {
            return; // can't confirm delivery (offline / unknown) — don't log
        }

        plugin.messageLog().log(sender.getName(), recipient.getName(), text);

        // Remember the conversation both ways so /r resolves for either side.
        lastPartner.put(sender.getUniqueId(), recipient.getUniqueId());
        lastPartner.put(recipient.getUniqueId(), sender.getUniqueId());
    }

    private String[] split(String message) {
        String body = message.startsWith("/") ? message.substring(1) : message;
        body = body.trim();
        if (body.isEmpty()) {
            return new String[0];
        }
        return body.split("\\s+");
    }

    private String normalize(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }
}
