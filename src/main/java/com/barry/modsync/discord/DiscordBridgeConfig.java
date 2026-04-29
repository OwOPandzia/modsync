package com.barry.modsync.discord;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class DiscordBridgeConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> BOT_TOKEN =
            BUILDER
                    .comment("Discord bot token — https://discord.com/developers/applications")
                    .define("botToken", "");

    public static final ModConfigSpec.ConfigValue<String> GUILD_ID =
            BUILDER
                    .comment("Discord server (guild) ID. Enable Developer Mode → right-click server → Copy ID.")
                    .define("guildId", "");

    public static final ModConfigSpec.ConfigValue<String> CHANNEL_ID =
            BUILDER
                    .comment("Text channel ID used for the Minecraft ↔ Discord bridge.")
                    .define("channelId", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADMIN_USER_IDS =
            BUILDER
                    .comment("Discord user IDs allowed to run admin commands (!kick, !ban, etc.).",
                            "Right-click a user → Copy ID with Developer Mode enabled.")
                    .defineListAllowEmpty("adminUserIds", List.of(), () -> "", e -> e instanceof String);

    public static final ModConfigSpec.ConfigValue<String> SERVER_NAME =
            BUILDER
                    .comment("Server name shown in the bot's Discord activity status.")
                    .define("serverName", "My Minecraft Server");

    public static final ModConfigSpec SPEC = BUILDER.build();
}