package com.barry.modsync.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Persists a list of mod filenames downloaded by ModSync that haven't been
 * loaded yet (game hasn't restarted). Stored as a JSON array at
 * .minecraft/modsync_pending.json
 */
public class PendingModsTracker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "modsync_pending.json";

    private static LinkedHashSet<String> pending = null;

    // ── Public API ──────────────────────────────────────────────────────────

    public static Set<String> getAll() {
        ensureLoaded();
        return Collections.unmodifiableSet(pending);
    }

    public static boolean contains(String fileName) {
        ensureLoaded();
        return pending.contains(fileName);
    }

    public static void add(String fileName) {
        ensureLoaded();
        pending.add(fileName);
        save();
        LOGGER.info("[ModSync] Tracked pending mod: {}", fileName);
    }

    /**
     * Deletes the mod file from disk and removes it from the pending list.
     * @return true if removed successfully (or file was already gone)
     */
    public static boolean deleteAndRemove(String fileName) {
        ensureLoaded();
        File modsDir = new File(Minecraft.getInstance().gameDirectory, "mods");
        File file    = new File(modsDir, fileName);
        boolean ok   = !file.exists() || file.delete();
        if (ok) {
            pending.remove(fileName);
            save();
            LOGGER.info("[ModSync] Deleted pending mod: {}", fileName);
        } else {
            LOGGER.warn("[ModSync] Could not delete: {}", file.getAbsolutePath());
        }
        return ok;
    }

    /**
     * Removes entries whose files no longer exist on disk.
     * Call once on startup so stale entries don't clutter the list.
     */
    public static void pruneStale() {
        ensureLoaded();
        File modsDir = new File(Minecraft.getInstance().gameDirectory, "mods");
        boolean changed = pending.removeIf(name -> !new File(modsDir, name).exists());
        if (changed) save();
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private static void ensureLoaded() {
        if (pending == null) load();
    }

    private static void load() {
        pending = new LinkedHashSet<>();
        File f = dataFile();
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line.trim());
            String json = sb.toString();
            if (json.startsWith("[") && json.endsWith("]")) {
                String inner = json.substring(1, json.length() - 1).trim();
                if (!inner.isBlank()) {
                    for (String tok : inner.split(",")) {
                        String name = tok.trim();
                        if (name.startsWith("\"")) name = name.substring(1);
                        if (name.endsWith("\""))   name = name.substring(0, name.length() - 1);
                        if (!name.isBlank()) pending.add(name);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ModSync] Could not load pending list: {}", e.getMessage());
        }
    }

    private static void save() {
        try (PrintWriter w = new PrintWriter(new FileWriter(dataFile()))) {
            StringBuilder sb = new StringBuilder("[");
            Iterator<String> it = pending.iterator();
            while (it.hasNext()) {
                sb.append('"').append(it.next()).append('"');
                if (it.hasNext()) sb.append(',');
            }
            sb.append(']');
            w.print(sb);
        } catch (Exception e) {
            LOGGER.warn("[ModSync] Could not save pending list: {}", e.getMessage());
        }
    }

    private static File dataFile() {
        return new File(Minecraft.getInstance().gameDirectory, FILE_NAME);
    }
}