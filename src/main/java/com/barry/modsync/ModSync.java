package com.barry.modsync;

import com.barry.modsync.client.StartupModChecker;
import com.barry.modsync.config.ModSyncClientConfig;
import com.barry.modsync.config.ModSyncConfig;
import com.barry.modsync.discord.DiscordBridgeConfig;
import com.barry.modsync.discord.DiscordBridgeManager;
import com.barry.modsync.discord.ServerEventHandler;
import com.barry.modsync.server.ModFileHttpServer;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod("modsync")
public class ModSync {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static DiscordBridgeManager discordManager;

    public ModSync(IEventBus modEventBus, ModContainer container) {
        // HTTP server + excluded mods
        container.registerConfig(ModConfig.Type.SERVER, ModSyncConfig.SPEC);
        // Client: server IP, HTTP IP, join button
        container.registerConfig(ModConfig.Type.CLIENT, ModSyncClientConfig.SPEC);
        // Discord bridge — separate file so server ops can find it easily
        container.registerConfig(ModConfig.Type.SERVER, DiscordBridgeConfig.SPEC,
                "modsync-discord-server.toml");

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(this::onClientSetup);
        }

        if (FMLEnvironment.dist.isDedicatedServer()) {
            NeoForge.EVENT_BUS.addListener(this::onServerStarting);
            NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        }

        LOGGER.info("[ModSync] Loaded.");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[ModSync] Running startup mod check…");
        StartupModChecker.runAsync();
    }

    private void onServerStarting(ServerStartingEvent event) {
        // HTTP mod-file server
        int port = ModSyncConfig.HTTP_PORT.get();
        try {
            ModFileHttpServer.start(port);
        } catch (Exception e) {
            LOGGER.error("[ModSync] HTTP server failed on port {}: {}", port, e.getMessage());
        }

        // Discord bridge
        discordManager = new DiscordBridgeManager();
        ServerEventHandler.setManager(discordManager);
        discordManager.start(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ModFileHttpServer.stop();
        if (discordManager != null) {
            discordManager.stop();
            discordManager = null;
        }
    }
}