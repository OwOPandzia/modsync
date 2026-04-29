package com.barry.modsync.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModSyncClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> SERVER_IP =
            BUILDER
                    .comment("The Minecraft server IP clients will connect to via the Join button.",
                            "Example: play.myserver.net  or  abc123.at.playit.gg:25565")
                    .define("serverIp", "");

    public static final ModConfigSpec.ConfigValue<String> HTTP_IP =
            BUILDER
                    .comment("The HTTP address of the mod file server.",
                            "For playit.gg: your TCP tunnel address e.g. http://abc123.at.playit.gg:50000",
                            "For local testing: http://localhost:50000")
                    .define("httpIp", "http://localhost:50000");

    public static final ModConfigSpec.ConfigValue<String> SERVER_NAME =
            BUILDER
                    .comment("Display name shown on the Join button: 'Join {serverName}'")
                    .define("serverName", "My Server");

    public static final ModConfigSpec.BooleanValue SHOW_JOIN_BUTTON =
            BUILDER
                    .comment("Show the Join Server button on the main menu title screen.")
                    .define("showJoinButton", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}