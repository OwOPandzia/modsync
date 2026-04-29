package com.barry.modsync.client;

import com.barry.modsync.server.ModListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModSyncConfirmScreen extends Screen {

    /**
     * A unified display entry for the list — covers both missing and
     * outdated mods so they can be sorted and rendered together.
     */
    record DisplayEntry(
            ModListPayload.ModEntry serverMod,
            boolean isUpdate,          // false = missing, true = wrong version
            String  localFileToDelete  // non-null only for updates
    ) {}

    private List<DisplayEntry>            displayEntries;
    private String                        downloadBaseUrl;
    private boolean                       isRefreshing = false;

    private ModList  modList;
    private Button   downloadBtn;
    private Button   refreshBtn;
    private Button   pendingBtn;

    public ModSyncConfirmScreen(
            List<ModListPayload.ModEntry>          missing,
            List<StartupModChecker.OutdatedEntry>  outdated,
            String                                 baseUrl
    ) {
        super(Component.literal("ModSync — Mod Updates"));
        this.downloadBaseUrl = baseUrl;
        this.displayEntries  = buildEntries(missing, outdated);
    }

    private static List<DisplayEntry> buildEntries(
            List<ModListPayload.ModEntry>         missing,
            List<StartupModChecker.OutdatedEntry> outdated
    ) {
        List<DisplayEntry> entries = new ArrayList<>();
        for (ModListPayload.ModEntry m : missing)
            entries.add(new DisplayEntry(m, false, null));
        for (StartupModChecker.OutdatedEntry o : outdated)
            entries.add(new DisplayEntry(o.serverMod(), true, o.localFileName()));
        entries.sort(Comparator.comparing(e -> e.serverMod().modId()));
        return entries;
    }

    @Override
    protected void init() {
        int cx         = this.width / 2;
        int listTop    = 55;
        int listBottom = this.height - 65;

        modList = new ModList(this.minecraft, this.width,
                listBottom - listTop, listTop, 26);
        for (DisplayEntry entry : displayEntries)
            modList.addModEntry(new ModList.ModRow(entry, this));
        this.addRenderableWidget(modList);

        // ── Bottom button bar ─────────────────────────────────────────────
        // [ ⟳ Refresh ]  [ Pending (N) ]  [ ⬇ Download (N) ]  [ ✖ Cancel ]

        refreshBtn = Button.builder(
                Component.literal("⟳  Refresh"),
                btn -> doRefresh()
        ).bounds(cx - 237, this.height - 52, 72, 20).build();
        refreshBtn.active = !isRefreshing;
        this.addRenderableWidget(refreshBtn);

        int pendingCount = PendingModsTracker.getAll().size();
        pendingBtn = Button.builder(
                Component.literal("Pending (" + pendingCount + ")"),
                btn -> this.minecraft.setScreen(new ModSyncPendingScreen(this))
        ).bounds(cx - 158, this.height - 52, 105, 20).build();
        this.addRenderableWidget(pendingBtn);

        downloadBtn = Button.builder(
                buildDownloadLabel(),
                btn -> startDownload()
        ).bounds(cx - 46, this.height - 52, 150, 20).build();
        downloadBtn.active = !isRefreshing
                && modList.children().stream().anyMatch(r -> r.checked);
        this.addRenderableWidget(downloadBtn);

        this.addRenderableWidget(Button.builder(
                Component.literal("✖  Cancel"),
                btn -> this.minecraft.setScreen(null)
        ).bounds(cx + 111, this.height - 52, 72, 20).build());
    }

    @Override
    public void tick() {
        if (isRefreshing && StartupModChecker.checkComplete) {
            isRefreshing = false;
            List<ModListPayload.ModEntry>         missing  = StartupModChecker.missingMods  != null ? StartupModChecker.missingMods  : List.of();
            List<StartupModChecker.OutdatedEntry> outdated = StartupModChecker.outdatedMods != null ? StartupModChecker.outdatedMods : List.of();
            if (StartupModChecker.downloadBaseUrl != null)
                downloadBaseUrl = StartupModChecker.downloadBaseUrl;
            displayEntries = buildEntries(missing, outdated);
            rebuildWidgets();
        }
    }

    /** Called by ModRow whenever a checkbox is toggled. */
    void refreshDownloadButton() {
        if (downloadBtn == null) return;
        long checked = modList.children().stream().filter(r -> r.checked).count();
        downloadBtn.setMessage(buildDownloadLabel());
        downloadBtn.active = checked > 0;
    }

    private Component buildDownloadLabel() {
        long count = modList == null ? displayEntries.size()
                : modList.children().stream().filter(r -> r.checked).count();
        return Component.literal("⬇  Download (" + count + ")");
    }

    // ── Blur fix: plain black fill instead of the GPU blur shader ───────────
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        g.drawCenteredString(this.font,
                Component.literal("Mod Updates").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE),
                this.width / 2, 12, 0xFFFFFF);

        String subtitle = isRefreshing ? "Checking server…"
                : displayEntries.size() + " mod(s) need attention  •  "
                + displayEntries.stream().filter(e -> !e.isUpdate()).count() + " missing  •  "
                + displayEntries.stream().filter(DisplayEntry::isUpdate).count() + " updates";
        g.drawCenteredString(this.font,
                Component.literal(subtitle).withStyle(ChatFormatting.GRAY),
                this.width / 2, 26, 0xAAAAAA);

        g.drawCenteredString(this.font,
                Component.literal("Click rows to deselect. No auto-restart — you choose when.")
                        .withStyle(ChatFormatting.YELLOW),
                this.width / 2, this.height - 22, 0xFFFF55);

        super.render(g, mouseX, mouseY, partial);
    }

    private void doRefresh() {
        isRefreshing   = true;
        displayEntries = List.of();
        rebuildWidgets();
        StartupModChecker.refresh();
    }

    private void startDownload() {
        List<ModDownloader.DownloadTask> tasks = modList.children().stream()
                .filter(r -> r.checked)
                .map(r -> r.entry.isUpdate()
                        ? ModDownloader.DownloadTask.update(r.entry.serverMod(), r.entry.localFileToDelete())
                        : ModDownloader.DownloadTask.missing(r.entry.serverMod()))
                .toList();

        if (tasks.isEmpty()) return;

        ModSyncProgressScreen progress = new ModSyncProgressScreen(tasks.size());
        this.minecraft.setScreen(progress);

        Thread t = new Thread(() ->
                ModDownloader.downloadMods(tasks, downloadBaseUrl, progress));
        t.setName("modsync-downloader");
        t.setDaemon(false);
        t.start();
    }

    // ── Mod list ─────────────────────────────────────────────────────────────

    static class ModList extends ObjectSelectionList<ModList.ModRow> {

        public void addModEntry(ModRow row) {
            this.addEntry(row);
        }

        ModList(Minecraft mc, int width, int height, int y, int itemH) {
            super(mc, width, height, y, itemH);
        }

        @Override protected boolean isSelectedItem(int index) { return false; }
        @Override public    int     getRowWidth()              { return this.width - 24; }
        @Override protected int     getScrollbarPosition()     { return this.width - 10; }

        static class ModRow extends Entry<ModRow> {
            final DisplayEntry        entry;
            boolean                   checked = true;
            private final ModSyncConfirmScreen parent;

            ModRow(DisplayEntry entry, ModSyncConfirmScreen parent) {
                this.entry  = entry;
                this.parent = parent;
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                checked = !checked;
                parent.refreshDownloadButton();
                return true;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left,
                               int width, int height, int mx, int my,
                               boolean hovered, float partial) {

                int bg = hovered ? 0x33FFFFFF : (index % 2 == 0 ? 0x22FFFFFF : 0x11FFFFFF);
                g.fill(left, top, left + width, top + height, bg);

                // Checkbox
                int cbX = left + 5, cbY = top + (height - 10) / 2;
                g.fill(cbX,     cbY,     cbX + 10, cbY + 10, 0xFF666666);
                g.fill(cbX + 1, cbY + 1, cbX + 9,  cbY + 9,  0xFF222222);
                if (checked)
                    g.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF55FF55);

                // Mod ID — white for missing, yellow for updates
                ChatFormatting nameColor = !checked       ? ChatFormatting.DARK_GRAY
                        : entry.isUpdate() ? ChatFormatting.YELLOW
                        : ChatFormatting.WHITE;
                g.drawString(Minecraft.getInstance().font,
                        Component.literal(entry.serverMod().modId()).withStyle(nameColor),
                        left + 20, top + 4, 0xFFFFFF);

                // Version
                g.drawString(Minecraft.getInstance().font,
                        Component.literal("v" + entry.serverMod().version())
                                .withStyle(ChatFormatting.GRAY),
                        left + 20, top + 14, 0x888888);

                // Badge: "UPDATE" in gold, "NEW" in green
                String badge     = entry.isUpdate() ? "UPDATE" : "NEW";
                int    badgeCol  = entry.isUpdate() ? 0xFFAA6600 : 0xFF006622;
                int    badgeTCol = entry.isUpdate() ? 0xFFFFAA00 : 0xFF55FF55;
                int    badgeW    = Minecraft.getInstance().font.width(badge) + 6;
                int    badgeX    = left + width - badgeW - 4;
                int    badgeY    = top + (height - 10) / 2;
                g.fill(badgeX,     badgeY,      badgeX + badgeW, badgeY + 10, badgeCol);
                g.drawString(Minecraft.getInstance().font,
                        Component.literal(badge),
                        badgeX + 3, badgeY + 1, badgeTCol);

                // Filename — left of badge
                String fn    = entry.serverMod().fileName();
                int    fnW   = Minecraft.getInstance().font.width(fn);
                int    fnMax = badgeX - left - 28;
                if (fnW <= fnMax) {
                    g.drawString(Minecraft.getInstance().font,
                            Component.literal(fn).withStyle(ChatFormatting.DARK_GRAY),
                            badgeX - fnW - 6, top + (height - 8) / 2, 0x555555);
                }
            }

            @Override
            public Component getNarration() {
                return Component.literal(
                        (checked ? "Selected: " : "Deselected: ")
                                + entry.serverMod().modId() + " v" + entry.serverMod().version()
                                + (entry.isUpdate() ? " [UPDATE]" : " [NEW]"));
            }
        }
    }
}