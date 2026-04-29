package com.barry.modsync.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModSyncProgressScreen extends Screen {

    private volatile String  currentFileName = "Preparing…";
    private volatile float   fileProgress    = 0f;
    private volatile float   overallProgress = 0f;
    private volatile int     completedCount  = 0;
    private final   int      totalCount;

    private volatile boolean hasFailed      = false;
    private volatile String  failedName     = "";
    private volatile boolean downloadComplete = false;

    // Tracks whether we've already added the Close button after completion
    private boolean closeButtonAdded = false;

    private int tick = 0;
    private static final String[] SPINNER = {"⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏"};

    public ModSyncProgressScreen(int total) {
        super(Component.literal("ModSync — Downloading"));
        this.totalCount = total;
    }

    // ── Called from download thread ──────────────────────────────────────────

    public void updateFile(String fileName, float progress) {
        this.currentFileName = fileName;
        this.fileProgress    = progress;
        this.overallProgress = (completedCount + progress) / totalCount;
    }

    public void fileComplete() {
        completedCount++;
        fileProgress    = 1f;
        overallProgress = (float) completedCount / totalCount;
    }

    public void setFailed(String name) {
        hasFailed  = true;
        failedName = name;
    }

    public void setComplete() {
        downloadComplete = true;
    }

    // ── Screen lifecycle ─────────────────────────────────────────────────────

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        tick++;
        // Add Close button as soon as download finishes or fails
        if ((downloadComplete || hasFailed) && !closeButtonAdded) {
            closeButtonAdded = true;
            this.addRenderableWidget(Button.builder(
                    Component.literal("Close"),
                    btn -> this.minecraft.setScreen(null)
            ).bounds(this.width / 2 - 50, this.height / 2 + 55, 100, 20).build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g, mx, my, partial);
        int cx = this.width  / 2;
        int cy = this.height / 2;

        // ── Failure state ────────────────────────────────────────────────────
        if (hasFailed) {
            g.drawCenteredString(this.font,
                    Component.literal("✖  Download Failed")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    cx, cy - 40, 0xFF5555);
            g.drawCenteredString(this.font,
                    Component.literal("Failed on: " + failedName).withStyle(ChatFormatting.GRAY),
                    cx, cy - 20, 0xAAAAAA);
            g.drawCenteredString(this.font,
                    Component.literal("Check your connection and try again.")
                            .withStyle(ChatFormatting.YELLOW),
                    cx, cy + 5, 0xFFFF55);
            super.render(g, mx, my, partial);
            return;
        }

        // ── Done state ───────────────────────────────────────────────────────
        if (downloadComplete) {
            g.drawCenteredString(this.font,
                    Component.literal("✔  All Downloads Complete!")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    cx, cy - 40, 0x55FF55);
            g.drawCenteredString(this.font,
                    Component.literal(completedCount + " mod(s) downloaded successfully")
                            .withStyle(ChatFormatting.GRAY),
                    cx, cy - 20, 0xAAAAAA);
            g.drawCenteredString(this.font,
                    Component.literal("Restart the game to load your new mods.")
                            .withStyle(ChatFormatting.YELLOW),
                    cx, cy + 5, 0xFFFF55);
            super.render(g, mx, my, partial);
            return;
        }

        // ── In-progress state ────────────────────────────────────────────────
        String spin = SPINNER[(tick / 2) % SPINNER.length];
        g.drawCenteredString(this.font,
                Component.literal(spin + "  Downloading Mods  " + spin)
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                cx, cy - 75, 0xFFFFFF);

        g.drawCenteredString(this.font,
                Component.literal("File: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(currentFileName).withStyle(ChatFormatting.WHITE)),
                cx, cy - 52, 0xFFFFFF);

        drawBar(g, cx - 120, cy - 38, 240, 12, fileProgress,    0xFF55FFFF, "File");
        drawBar(g, cx - 120, cy - 14, 240, 16, overallProgress, 0xFF22AA22, "Overall");

        g.drawCenteredString(this.font,
                Component.literal(completedCount + " / " + totalCount + " mods complete")
                        .withStyle(ChatFormatting.GRAY),
                cx, cy + 14, 0xAAAAAA);

        super.render(g, mx, my, partial);
    }

    private void drawBar(GuiGraphics g, int x, int y, int w, int h,
                         float progress, int color, String label) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF444444); // border
        g.fill(x,     y,     x + w,     y + h,     0xFF111111); // bg
        int fill = (int)(w * Math.min(Math.max(progress, 0f), 1f));
        if (fill > 0) {
            g.fill(x, y,         x + fill, y + h,     color);
            g.fill(x, y,         x + fill, y + 2,     0x44FFFFFF); // shine
        }
        g.drawCenteredString(this.font,
                Component.literal(label + "  " + (int)(progress * 100) + "%")
                        .withStyle(ChatFormatting.WHITE),
                x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
    }
}