package com.barry.modsync.discord;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "modsync")
public class ServerEventHandler {

    private static DiscordBridgeManager discord;

    public static void setManager(DiscordBridgeManager manager) {
        discord = manager;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (discord == null) return;
        ServerPlayer player = event.getPlayer();
        discord.sendChatMessage(
                player.getName().getString(),
                player.getUUID().toString(),
                event.getMessage().getString()
        );
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (discord == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Schedule for next tick so the player list count is already updated
        server.execute(() -> discord.sendPlayerJoin(
                player.getName().getString(),
                player.getUUID().toString(),
                server.getPlayerList().getPlayerCount(),
                server.getPlayerList().getMaxPlayers()
        ));
        server.execute(discord::updateActivity);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (discord == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        server.execute(() -> discord.sendPlayerLeave(
                player.getName().getString(),
                player.getUUID().toString(),
                server.getPlayerList().getPlayerCount(),
                server.getPlayerList().getMaxPlayers()
        ));
        server.execute(discord::updateActivity);
    }
}