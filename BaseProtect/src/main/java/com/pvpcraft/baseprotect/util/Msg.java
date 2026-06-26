package com.pvpcraft.baseprotect.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Central MiniMessage helper. All player-facing text goes through here. */
public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#3a86ff:#06d6a0><bold>BaseProtect</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    /** Deserialize a MiniMessage string with optional placeholders. */
    public static Component mm(String input, TagResolver... resolvers) {
        return MM.deserialize(input, resolvers);
    }

    /** Deserialize a MiniMessage string with the BaseProtect prefix prepended. */
    public static Component prefixed(String input, TagResolver... resolvers) {
        return MM.deserialize(PREFIX + input, resolvers);
    }
}
