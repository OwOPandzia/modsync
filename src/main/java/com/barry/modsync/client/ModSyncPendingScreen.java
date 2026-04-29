package com.barry.modsync.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists mod files that ModSync downloaded but the game hasn't loaded yet.
 * Each row has a Delete button to cancel the pending download.
 */
public class ModSyncPendingScreen extends Screen {

    private final Screen parent;

    public ModSyncPendingScreen(Screen parent) {
        super(Component.literal("ModSync — Pending Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop    = 50;
        int listBottom = this.height - 50;

        PendingList list = new PendingList(this.minecraft, this.width,
                listBottom - listTop, listTop, 24, this);
        this.addRenderableWidget(list);

        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                btn -> this.minecraft.setScreen(parent)
        ).bounds(this.width / 2 - 75, this.height - 38, 150, 20).build());
    }

    /** Called by a row after it deletes its file — rebuilds the widget list. */
    void onRowDeleted() {
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        g.drawCenteredString(this.font,
                Component.literal("Downloaded — Not Yet Loaded")
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE),
                this.width / 2, 12, 0xFFFFFF);

        int count = PendingModsTracker.getAll().size();
        String sub = count == 0
                ? "Nothing pending — all downloads are loaded!"
                : count + " mod(s) waiting for a game restart";
        g.drawCenteredString(this.font,
                Component.literal(sub).withStyle(ChatFormatting.GRAY),
                this.width / 2, 26, 0xAAAAAA);

        if (count == 0) {
            g.drawCenteredString(this.font,
                    Component.literal("Restart the game to load your downloaded mods.")
                            .withStyle(ChatFormatting.YELLOW),
                    this.width / 2, this.height / 2, 0xFFFF55);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    // ── List ────────────────────────────────────────────────────────────────

    static class PendingList extends ObjectSelectionList<PendingList.PendingRow> {

        PendingList(Minecraft mc, int width, int height, int y, int itemH,
                    ModSyncPendingScreen parent) {
            super(mc, width, height, y, itemH);
            for (String fn : new ArrayList<>(PendingModsTracker.getAll())) {
                addEntry(new PendingRow(fn, parent));
            }
        }

        @Override protected boolean isSelectedItem(int index) { return false; }
        @Override public int getRowWidth() { return this.width - 24; }
        @Override protected int getScrollbarPosition() { return this.width - 10; }

        static class PendingRow extends Entry<PendingRow> {
            private final String fileName;
            private final ModSyncPendingScreen parent;
            // Populated during render so mouseClicked can use it
            private int delX, delY, delW = 76, delH = 12;

            PendingRow(String fileName, ModSyncPendingScreen parent) {
                this.fileName = fileName;
                this.parent   = parent;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left,
                               int width, int height, int mx, int my,
                               boolean hovered, float partial) {

                int bg = hovered ? 0x33FFFFFF : (index % 2 == 0 ? 0x22FFFFFF : 0x11FFFFFF);
                g.fill(left, top, left + width, top + height, bg);

                // Orange "pending" dot
                int midY = top + height / 2;
                g.fill(left + 5, midY - 4, left + 13, midY + 4, 0xFFFF9900);

                // Filename
                g.drawString(Minecraft.getInstance().font,
                        Component.literal(fileName).withStyle(ChatFormatting.WHITE),
                        left + 20, top + (height - 8) / 2, 0xFFFFFF);

                // Delete button — right side
                delW = Minecraft.getInstance().font.width("✖ Delete") + 10;
                delH = height - 6;
                delX = left + width - delW - 4;
                delY = top + 3;

                boolean over = mx >= delX && mx <= delX + delW
                        && my >= delY && my <= delY + delH;
                g.fill(delX, delY, delX + delW, delY + delH,
                        over ? 0xFFCC0000 : 0xFF881111);
                g.drawCenteredString(Minecraft.getInstance().font,
                        Component.literal("✖ Delete").withStyle(ChatFormatting.WHITE),
                        delX + delW / 2, delY + (delH - 8) / 2, 0xFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (mx >= delX && mx <= delX + delW && my >= delY && my <= delY + delH) {
                    PendingModsTracker.deleteAndRemove(fileName);
                    parent.onRowDeleted();
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal("Pending: " + fileName);
            }
        }
    }
}