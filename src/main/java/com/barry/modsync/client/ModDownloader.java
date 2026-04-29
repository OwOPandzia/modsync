package com.barry.modsync.client;

import com.mojang.logging.LogUtils;
import com.barry.modsync.server.ModListPayload;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public class ModDownloader {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * A single download job.
     * For missing mods: localFileToDelete is null.
     * For updates:      localFileToDelete is the current local jar to remove after download.
     */
    public record DownloadTask(ModListPayload.ModEntry serverMod, String localFileToDelete) {
        public static DownloadTask missing(ModListPayload.ModEntry mod) {
            return new DownloadTask(mod, null);
        }
        public static DownloadTask update(ModListPayload.ModEntry mod, String oldFileName) {
            return new DownloadTask(mod, oldFileName);
        }
    }

    /**
     * Downloads all tasks, tracks them in PendingModsTracker, then notifies
     * the progress screen it's done. Does NOT restart the game — the user
     * decides when to restart via the main menu.
     */
    public static void downloadMods(
            List<DownloadTask> tasks,
            String baseUrl,
            ModSyncProgressScreen screen
    ) {
        File modsDir = new File(Minecraft.getInstance().gameDirectory, "mods");

        for (DownloadTask task : tasks) {
            ModListPayload.ModEntry mod = task.serverMod();
            String fileUrl = baseUrl.replaceAll("/$", "") + "/mods/" + mod.fileName();
            File   dest    = new File(modsDir, mod.fileName());

            LOGGER.info("[ModSync] Downloading {} from {}", mod.fileName(), fileUrl);

            try {
                downloadWithProgress(fileUrl, dest, mod.fileName(), screen);

                // Verify hash
                String hash = sha256(dest);
                if (!hash.equals(mod.sha256())) {
                    LOGGER.error("[ModSync] Hash mismatch for {}! Deleting corrupt file.", mod.fileName());
                    dest.delete();
                    screen.setFailed(mod.modId());
                    return;
                }

                // For updates: delete the old local jar now that the new one is verified
                if (task.localFileToDelete() != null && !task.localFileToDelete().isEmpty()) {
                    File oldFile = new File(modsDir, task.localFileToDelete());
                    if (oldFile.exists() && oldFile.delete()) {
                        LOGGER.info("[ModSync] Deleted old version: {}", task.localFileToDelete());
                    }
                }

                PendingModsTracker.add(mod.fileName());
                screen.fileComplete();
                LOGGER.info("[ModSync] ✓ {} downloaded and verified.", mod.fileName());

            } catch (Exception e) {
                LOGGER.error("[ModSync] Failed to download {}: {}", mod.fileName(), e.getMessage());
                screen.setFailed(mod.modId());
                return;
            }
        }

        LOGGER.info("[ModSync] All {} download(s) complete.", tasks.size());
        screen.setComplete(); // tells the screen to show "Done — restart manually"
    }

    private static void downloadWithProgress(
            String urlStr, File dest,
            String displayName,
            ModSyncProgressScreen screen
    ) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "ModSync/1.0");

        long total      = conn.getContentLengthLong();
        long downloaded = 0;

        try (InputStream    in  = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                downloaded += read;
                if (total > 0)
                    screen.updateFile(displayName, (float) downloaded / total);
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) digest.update(buf, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}