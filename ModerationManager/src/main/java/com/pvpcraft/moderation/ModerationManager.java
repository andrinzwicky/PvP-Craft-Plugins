package com.pvpcraft.moderation;

import com.pvpcraft.moderation.command.MuteCommand;
import com.pvpcraft.moderation.command.UnmuteCommand;
import com.pvpcraft.moderation.command.UnwarnCommand;
import com.pvpcraft.moderation.command.WarnCommand;
import com.pvpcraft.moderation.command.WarnsCommand;
import com.pvpcraft.moderation.history.HistoryManager;
import com.pvpcraft.moderation.listener.ChatListener;
import com.pvpcraft.moderation.listener.MessageListener;
import com.pvpcraft.moderation.listener.PunishmentLogListener;
import com.pvpcraft.moderation.log.MessageLog;
import com.pvpcraft.moderation.mute.MuteManager;
import com.pvpcraft.moderation.util.TeamUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight, extensible moderation plugin. Ships with /warn and /warns and a
 * shared per-player history of warns, kicks and bans. New moderation commands
 * can reuse {@link HistoryManager}, {@link #notifyTeam} and {@link #msg}.
 */
public final class ModerationManager extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private HistoryManager history;
    private MuteManager mutes;
    private MessageLog messageLog;
    private DateTimeFormatter dateFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        history = new HistoryManager(this);
        history.load();

        mutes = new MuteManager(this);
        mutes.load();

        messageLog = new MessageLog(this);

        String pattern = getConfig().getString("date-format", "dd.MM.yyyy HH:mm");
        this.dateFormat = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());

        register("warn", new WarnCommand(this));
        register("warns", new WarnsCommand(this));
        register("unwarn", new UnwarnCommand(this));
        register("mute", new MuteCommand(this));
        register("unmute", new UnmuteCommand(this));

        getServer().getPluginManager().registerEvents(new PunishmentLogListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new MessageListener(this), this);

        getLogger().info("ModerationManager enabled"
                + (messageLog.enabled() ? " (PN-Logging aktiv)." : "."));
    }

    @Override
    public void onDisable() {
        if (history != null) {
            history.save();
        }
        if (mutes != null) {
            mutes.save();
        }
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter completer) {
                command.setTabCompleter(completer);
            }
        } else {
            getLogger().severe("Command '" + name + "' missing from plugin.yml!");
        }
    }

    // --- accessors -------------------------------------------------------

    public HistoryManager history() {
        return history;
    }

    public MuteManager mutes() {
        return mutes;
    }

    public MessageLog messageLog() {
        return messageLog;
    }

    public String formatTime(long epochMillis) {
        return dateFormat.format(Instant.ofEpochMilli(epochMillis));
    }

    // --- messaging -------------------------------------------------------

    /** Sends a prefixed message from the config's messages section. */
    public void msg(CommandSender to, String key, TagResolver... resolvers) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw.isEmpty()) {
            return;
        }
        String prefix = getConfig().getString("messages.prefix", "");
        to.sendMessage(MM.deserialize(prefix + raw, resolvers));
    }

    /** Deserializes a raw MiniMessage string (no prefix) into a Component. */
    public Component render(String mini, TagResolver... resolvers) {
        return MM.deserialize(mini, resolvers);
    }

    public void send(CommandSender to, Component component) {
        to.sendMessage(component);
    }

    /**
     * Sends a prefixed message from config to every online team member
     * (Supporter and above). Used so warnings are visible to the whole staff
     * team but not to normal players.
     */
    public void notifyTeam(String key, TagResolver... resolvers) {
        notifyTeamExcept(null, key, resolvers);
    }

    /**
     * Like {@link #notifyTeam} but skips the player with {@code exclude} (e.g.
     * the warned player, who already received a personal message).
     */
    public void notifyTeamExcept(java.util.UUID exclude, String key, TagResolver... resolvers) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw.isEmpty()) {
            return;
        }
        String prefix = getConfig().getString("messages.prefix", "");
        Component message = MM.deserialize(prefix + raw, resolvers);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (exclude != null && online.getUniqueId().equals(exclude)) {
                continue;
            }
            if (TeamUtil.isTeam(online)) {
                online.sendMessage(message);
            }
        }
    }

    public static TagResolver ph(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    /**
     * Sends the "you are muted" notice to a player, including the remaining time
     * (or "dauerhaft" for a permanent mute). Safe to call from async chat.
     */
    public void sendMutedNotice(Player player) {
        long remaining = mutes.remainingMillis(player.getUniqueId());
        String durationText = remaining < 0
                ? "dauerhaft"
                : com.pvpcraft.moderation.util.Durations.format(remaining);
        msg(player, "muted-notice", ph("duration", durationText));
    }
}
