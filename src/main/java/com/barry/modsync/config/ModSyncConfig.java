package com.barry.modsync.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class ModSyncConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> DOWNLOAD_BASE_URL =
            BUILDER
                    .comment(
                            "The URL clients will use to download mods.",
                            "For playit.gg: set this to your tunnel address e.g. http://abc123.at.playit.gg:50000",
                            "For local testing: http://localhost:50000"
                    )
                    .define("downloadBaseUrl", "http://localhost:50000");

    public static final ModConfigSpec.IntValue HTTP_PORT =
            BUILDER
                    .comment("The local port the mod file HTTP server listens on (server only).")
                    .defineInRange("httpPort", 50000, 1024, 65535);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_MODS =
            BUILDER
                    .comment("Mod IDs to exclude from sync. Add client-only mods here.")
                    .defineListAllowEmpty(
                            "excludedMods",
                            List.of("sodium", "iris", "replaymod", "optifine", "chunky", "bluemap"),
                            () -> "placeholder",
                            e -> e instanceof String
                    );

    public static final ModConfigSpec SPEC = BUILDER.build();
}