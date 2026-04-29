package com.barry.modsync.client;

import com.barry.modsync.server.ModListPayload;
import com.barry.modsync.config.ModSyncClientConfig;
import com.barry.modsync.server.ModListProvider;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class StartupModChecker {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Mods on the server that are not installed locally at all. */
    public static volatile List<ModListPayload.ModEntry> missingMods = null;

    /**
     * Mods installed locally but with a different hash than the server version.
     * Each entry carries the server's mod info plus the local filename to delete on update.
     */
    public static volatile List<OutdatedEntry> outdatedMods = null;

    public static volatile String downloadBaseUrl = null;
    public static volatile boolean checkComplete = false;
    public static volatile String checkError = null;

    /** A mod installed locally at a different version than what the server has. */
    public record OutdatedEntry(ModListPayload.ModEntry serverMod, String localFileName) {}

    // ── Private record for building the local map ────────────────────────────
    private record LocalInfo(String sha256, String fileName) {}

    // ── Run ─────────────────────────────────────────────────────────────────

    public static void runAsync() {
        Thread thread = new Thread(() -> {
            try {
                // Clean up stale pending entries before checking
                PendingModsTracker.pruneStale();

                String httpIp = ModSyncClientConfig.HTTP_IP.get();
                if (httpIp == null || httpIp.isBlank()) {
                    LOGGER.info("[ModSync] No HTTP IP configured — skipping startup check.");
                    checkComplete = true;
                    return;
                }

                String url = httpIp.replaceAll("/$", "") + "/modlist";
                LOGGER.info("[ModSync] Querying mod list from {}", url);

                QueryResult result = queryModList(url);
                downloadBaseUrl = result.baseUrl();

                // Build local mod map: modId → LocalInfo(hash, fileName)
                Map<String, LocalInfo> localInfo = new HashMap<>();
                for (IModInfo mod : ModList.get().getMods()) {
                    try {
                        File jar = mod.getOwningFile().getFile().getFilePath().toFile();
                        if (!jar.isFile()) continue;
                        String hash = ModListProvider.sha256(jar);
                        localInfo.put(mod.getModId(), new LocalInfo(hash, jar.getName()));
                    } catch (Exception e) {
                        localInfo.put(mod.getModId(), new LocalInfo("", ""));
                    }
                }

                List<ModListPayload.ModEntry> missing  = new ArrayList<>();
                List<OutdatedEntry>           outdated = new ArrayList<>();

                for (ModListPayload.ModEntry entry : result.mods()) {
                    // Skip mods already downloaded and waiting for a restart
                    if (PendingModsTracker.contains(entry.fileName())) continue;

                    LocalInfo local = localInfo.get(entry.modId());
                    if (local == null || local.sha256().isEmpty()) {
                        // Not installed at all
                        missing.add(entry);
                    } else if (!local.sha256().equals(entry.sha256())) {
                        // Installed but wrong version
                        outdated.add(new OutdatedEntry(entry, local.fileName()));
                    }
                }

                missingMods  = missing;
                outdatedMods = outdated;

                LOGGER.info("[ModSync] Check complete — {} missing, {} outdated.",
                        missing.size(), outdated.size());

            } catch (Exception e) {
                LOGGER.warn("[ModSync] Startup check failed: {}", e.getMessage());
                checkError   = e.getMessage();
                missingMods  = List.of();
                outdatedMods = List.of();
            } finally {
                checkComplete = true;
            }
        });
        thread.setName("modsync-startup-check");
        thread.setDaemon(true);
        thread.start();
    }

    /** Resets all state and re-runs the check from scratch. */
    public static void refresh() {
        checkComplete   = false;
        checkError      = null;
        missingMods     = null;
        outdatedMods    = null;
        downloadBaseUrl = null;
        runAsync();
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private static QueryResult queryModList(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(8_000);
        conn.setRequestProperty("User-Agent", "ModSync/1.0");

        if (conn.getResponseCode() != 200)
            throw new IOException("HTTP " + conn.getResponseCode());

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return parseJson(sb.toString());
    }

    private static QueryResult parseJson(String json) {
        String baseUrl = extractString(json, "baseUrl");
        List<ModListPayload.ModEntry> mods = new ArrayList<>();

        int start = json.indexOf("\"mods\":[");
        if (start == -1) return new QueryResult(baseUrl, mods);

        String arr = json.substring(start + 8);
        int depth = 0, objStart = -1;

        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    String obj    = arr.substring(objStart, i + 1);
                    String modId  = extractString(obj, "modId");
                    String ver    = extractString(obj, "version");
                    String fn     = extractString(obj, "fileName");
                    String sha    = extractString(obj, "sha256");
                    if (modId != null && !modId.isEmpty())
                        mods.add(new ModListPayload.ModEntry(modId, ver, fn, sha));
                    objStart = -1;
                }
            } else if (c == ']' && depth == 0) break;
        }
        return new QueryResult(baseUrl, mods);
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int s = json.indexOf(search);
        if (s == -1) return "";
        s += search.length();
        int e = json.indexOf("\"", s);
        return e == -1 ? "" : json.substring(s, e);
    }

    private record QueryResult(String baseUrl, List<ModListPayload.ModEntry> mods) {}
}