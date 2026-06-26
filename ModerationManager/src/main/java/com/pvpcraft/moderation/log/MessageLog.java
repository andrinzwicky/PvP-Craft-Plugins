package com.pvpcraft.moderation.log;

import com.pvpcraft.moderation.ModerationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Appends delivered private messages (/msg, /tell, ...) to a plain-text log
 * file for moderation review. Logging can be disabled in config.yml.
 *
 * NOTE: logging private messages is personal data — make sure your server rules
 * / privacy notice disclose it (see config.yml).
 */
public final class MessageLog {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModerationManager plugin;
    private final Path file;
    private final boolean enabled;

    public MessageLog(ModerationManager plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("message-logging.enabled", true);
        String name = plugin.getConfig().getString("message-logging.file", "private-messages.log");
        this.file = plugin.getDataFolder().toPath().resolve(name);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Appends one private message line; never throws into the caller. */
    public void log(String sender, String target, String message) {
        if (!enabled) {
            return;
        }
        String line = "[" + STAMP.format(LocalDateTime.now()) + "] "
                + sender + " -> " + target + ": " + message
                + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write to message log", e);
        }
    }
}
