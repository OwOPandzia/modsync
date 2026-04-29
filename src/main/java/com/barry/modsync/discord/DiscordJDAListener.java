package com.barry.modsync.discord;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class DiscordJDAListener extends ListenerAdapter {

    private final DiscordBridgeManager manager;

    public DiscordJDAListener(DiscordBridgeManager manager) {
        this.manager = manager;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Ignore bots (including the bridge bot itself)
        if (event.getAuthor().isBot()) return;
        // Only handle messages in the configured bridge channel
        if (!event.getChannel().getId().equals(manager.getChannelId())) return;

        String content = event.getMessage().getContentDisplay().trim();
        if (content.isEmpty()) return;

        String displayName = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getName();

        // Admin commands start with !
        if (content.startsWith("!")) {
            manager.onAdminCommand(content.substring(1).trim(), event.getAuthor().getId());
            return;
        }

        manager.onDiscordMessage(displayName, content);
    }
}