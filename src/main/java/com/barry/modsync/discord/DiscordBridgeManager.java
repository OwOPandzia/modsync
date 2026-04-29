package com.barry.modsync.discord;

import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.File;
import java.time.Instant;
import java.util.List;

public class DiscordBridgeManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Embed colour palette
    private static final int COLOR_CHAT   = 0x57F287; // green
    private static final int COLOR_JOIN   = 0x3BA55D; // dark green
    private static final int COLOR_LEAVE  = 0xED4245; // red
    private static final int COLOR_SERVER = 0x5865F2; // Discord blurple

    private JDA             jda;
    private MinecraftServer server;
    private String          channelId;
    private String          serverName;
    private List<String>    adminUserIds;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void start(MinecraftServer server) {
        this.server      = server;
        this.channelId   = DiscordBridgeConfig.CHANNEL_ID.get();
        this.serverName  = DiscordBridgeConfig.SERVER_NAME.get();
        this.adminUserIds = DiscordBridgeConfig.ADMIN_USER_IDS.get()
                .stream().map(Object::toString).toList();

        String token = DiscordBridgeConfig.BOT_TOKEN.get();
        if (token == null || token.isBlank()) {
            LOGGER.info("[ModSync/Discord] No bot token set — bridge disabled.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordJDAListener(this))
                    .build()
                    .awaitReady();

            LOGGER.info("[ModSync/Discord] Connected as {}", jda.getSelfUser().getAsTag());

            // Set bot avatar to server-icon.png if it exists
            File icon = new File("server-icon.png");
            if (icon.exists()) {
                jda.getSelfUser().getManager()
                        .setAvatar(Icon.from(icon))
                        .queue(
                                ok  -> LOGGER.info("[ModSync/Discord] Bot avatar set from server-icon.png"),
                                err -> LOGGER.warn("[ModSync/Discord] Avatar update failed: {}", err.getMessage())
                        );
            }

            updateActivity();
            sendSystemEmbed("🟢 Server Online",
                    "**" + serverName + "** has started.", COLOR_SERVER);

        } catch (Exception e) {
            LOGGER.error("[ModSync/Discord] Failed to start bot: {}", e.getMessage());
            jda = null;
        }
    }

    public void stop() {
        if (jda == null) return;
        sendSystemEmbed("🔴 Server Offline",
                "**" + serverName + "** has shut down.", COLOR_LEAVE);
        // Brief pause so the shutdown embed can be sent before JDA closes
        try { Thread.sleep(2_500); } catch (InterruptedException ignored) {}
        jda.shutdown();
        jda = null;
        LOGGER.info("[ModSync/Discord] Bot disconnected.");
    }

    // ── Activity ─────────────────────────────────────────────────────────────

    public void updateActivity() {
        if (jda == null || server == null) return;
        int online = server.getPlayerList().getPlayerCount();
        int max    = server.getPlayerList().getMaxPlayers();
        jda.getPresence().setActivity(
                Activity.playing(serverName + " | " + online + "/" + max + " players"));
    }

    // ── Minecraft → Discord ──────────────────────────────────────────────────

    /** Sends a player's chat message as an embed to the bridge channel. */
    public void sendChatMessage(String playerName, String uuid, String message) {
        sendEmbed(new EmbedBuilder()
                .setAuthor(playerName, null, crafatarUrl(uuid))
                .setDescription(message)
                .setColor(COLOR_CHAT)
                .setTimestamp(Instant.now())
                .setFooter(serverName)
                .build());
    }

    /** Sends a join notification embed. */
    public void sendPlayerJoin(String playerName, String uuid, int online, int max) {
        sendEmbed(new EmbedBuilder()
                .setAuthor("➕ " + playerName + " joined", null, crafatarUrl(uuid))
                .setDescription("**" + online + " / " + max + "** players online")
                .setColor(COLOR_JOIN)
                .setTimestamp(Instant.now())
                .setFooter(serverName)
                .build());
    }

    /** Sends a leave notification embed. */
    public void sendPlayerLeave(String playerName, String uuid, int online, int max) {
        sendEmbed(new EmbedBuilder()
                .setAuthor("➖ " + playerName + " left", null, crafatarUrl(uuid))
                .setDescription("**" + online + " / " + max + "** players online")
                .setColor(COLOR_LEAVE)
                .setTimestamp(Instant.now())
                .setFooter(serverName)
                .build());
    }

    private void sendSystemEmbed(String title, String description, int color) {
        sendEmbed(new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setFooter(serverName)
                .build());
    }

    private void sendEmbed(MessageEmbed embed) {
        if (jda == null) return;
        TextChannel ch = channel();
        if (ch != null) ch.sendMessageEmbeds(embed).queue();
    }

    // ── Discord → Minecraft ──────────────────────────────────────────────────

    /**
     * Called by DiscordJDAListener on a normal message.
     * Must be thread-safe — schedules work on the server thread.
     */
    public void onDiscordMessage(String displayName, String content) {
        if (server == null) return;
        server.execute(() -> {
            // [Discord] Name: message — Discord blurple for the prefix
            Component msg = Component.literal("[Discord] ")
                    .withStyle(s -> s.withColor(0x5865F2))
                    .append(Component.literal(displayName + ": ")
                            .withStyle(s -> s.withColor(0x5865F2)))
                    .append(Component.literal(content)
                            .withStyle(ChatFormatting.WHITE));
            server.getPlayerList().broadcastSystemMessage(msg, false);
        });
    }

    /**
     * Called by DiscordJDAListener for !commands from admin users.
     * Runs the command at permission level 4 (operator).
     */
    public void onAdminCommand(String command, String authorId) {
        if (server == null || !adminUserIds.contains(authorId)) return;
        // Strip leading slash if user typed !/kick instead of !kick
        if (command.startsWith("/")) command = command.substring(1);
        final String finalCommand = command;
        server.execute(() -> {
            try {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack()
                                .withPermission(4)
                                .withMaximumPermission(4),
                        finalCommand);
                LOGGER.info("[ModSync/Discord] Admin '{}' ran: {}", authorId, finalCommand);
            } catch (Exception e) {
                LOGGER.warn("[ModSync/Discord] Command '{}' failed: {}", finalCommand, e.getMessage());
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public String getChannelId() { return channelId; }

    private TextChannel channel() {
        if (jda == null || channelId == null || channelId.isBlank()) return null;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch == null) LOGGER.warn("[ModSync/Discord] Channel '{}' not found.", channelId);
        return ch;
    }

    private static String crafatarUrl(String uuid) {
        return "https://crafatar.com/avatars/" + uuid + "?size=32&overlay=true";
    }
}