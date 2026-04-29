package com.barry.modsync.discord;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.concurrent.CompletableFuture;

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

        // 1. Zbieramy wszystkie dane z gry NA GŁÓWNYM WĄTKU (bezpieczne dla serwera)
        String playerName = player.getName().getString();
        String playerUuid = player.getUUID().toString();
        int playerCount = server.getPlayerList().getPlayerCount();
        int maxPlayers = server.getPlayerList().getMaxPlayers();

        // 2. Wysyłamy do Discorda W TLE (nie blokuje serwera!)
        CompletableFuture.runAsync(() -> {
            discord.sendPlayerJoin(playerName, playerUuid, playerCount, maxPlayers);
            discord.updateActivity();
        });
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (discord == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // W momencie opuszczania gry, gracz jest nadal wliczony do playerCount,
        // więc odejmujemy 1, żeby Discord pokazał poprawną wartość po jego wyjściu.
        String playerName = player.getName().getString();
        String playerUuid = player.getUUID().toString();
        int playerCount = server.getPlayerList().getPlayerCount() - 1;
        int maxPlayers = server.getPlayerList().getMaxPlayers();

        CompletableFuture.runAsync(() -> {
            discord.sendPlayerLeave(playerName, playerUuid, playerCount, maxPlayers);
            discord.updateActivity();
        });
    }
}