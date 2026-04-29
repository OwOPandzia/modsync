package com.barry.modsync.client;

import com.barry.modsync.config.ModSyncClientConfig;
import com.barry.modsync.server.ModListPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

import java.util.List;

@EventBusSubscriber(modid = "modsync", value = Dist.CLIENT)
public class MainMenuHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BTN_SIZE   = 20;
    private static final int BTN_MARGIN = 6;

    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;

        // ── M button — bottom-right corner ───────────────────────────────────
        int btnX = screen.width  - BTN_SIZE - BTN_MARGIN;
        int btnY = screen.height - BTN_SIZE - BTN_MARGIN;

        event.addListener(Button.builder(
                Component.literal("M"),
                btn -> openModScreen()
        ).bounds(btnX, btnY, BTN_SIZE, BTN_SIZE).build());

        // ── Optional Join button ──────────────────────────────────────────────
        if (!ModSyncClientConfig.SHOW_JOIN_BUTTON.get()) return;
        String ip   = ModSyncClientConfig.SERVER_IP.get();
        String name = ModSyncClientConfig.SERVER_NAME.get();
        if (ip == null || ip.isBlank()) return;

        event.addListener(Button.builder(
                Component.literal("Join " + name),
                btn -> joinServer(ip, name)
        ).bounds(screen.width / 2 - 100, screen.height / 4 + 8, 200, 20).build());
    }

    /** Draws the red notification badge on the M button every frame. */
    @SubscribeEvent
    public static void onTitleScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;

        int total = notificationCount();
        if (total <= 0) return;

        String label = total > 9 ? "9+" : String.valueOf(total);

        GuiGraphics g    = event.getGuiGraphics();
        var         font = Minecraft.getInstance().font;

        int btnX   = screen.width  - BTN_SIZE - BTN_MARGIN;
        int btnY   = screen.height - BTN_SIZE - BTN_MARGIN;
        int badgeW = Math.max(10, font.width(label) + 4);
        int badgeH = 10;
        int badgeX = btnX + BTN_SIZE - badgeW / 2;
        int badgeY = btnY - badgeH / 2;

        g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFFCC0000);
        g.drawCenteredString(font, label, badgeX + badgeW / 2, badgeY + 1, 0xFFFFFFFF);
    }

    private static int notificationCount() {
        int missing  = StartupModChecker.missingMods  != null ? StartupModChecker.missingMods.size()  : 0;
        int outdated = StartupModChecker.outdatedMods != null ? StartupModChecker.outdatedMods.size() : 0;
        return missing + outdated;
    }

    private static void openModScreen() {
        List<ModListPayload.ModEntry>         missing  = StartupModChecker.missingMods  != null ? StartupModChecker.missingMods  : List.of();
        List<StartupModChecker.OutdatedEntry> outdated = StartupModChecker.outdatedMods != null ? StartupModChecker.outdatedMods : List.of();
        String baseUrl = StartupModChecker.downloadBaseUrl != null ? StartupModChecker.downloadBaseUrl : "";
        Minecraft.getInstance().setScreen(new ModSyncConfirmScreen(missing, outdated, baseUrl));
    }

    private static void joinServer(String ip, String name) {
        Minecraft mc  = Minecraft.getInstance();
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                mc.screen, mc,
                ServerAddress.parseString(ip),
                new ServerData(name, ip, ServerData.Type.OTHER),
                false, null
        );
    }
}