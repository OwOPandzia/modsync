package com.barry.modsync.server;

import com.barry.modsync.config.ModSyncConfig;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ModFileHttpServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static HttpServer server;
    // Bug 3 fix: keep a reference so we can shut it down properly
    private static ExecutorService executor;

    public static void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/mods", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            File modFile = findModFile(fileName);

            if (modFile == null || !modFile.exists()) {
                byte[] msg = "404 Not Found".getBytes();
                exchange.sendResponseHeaders(404, msg.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(msg); }
            } else {
                exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(modFile.length()));
                exchange.sendResponseHeaders(200, modFile.length());
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fis = new FileInputStream(modFile)) {
                    fis.transferTo(os);
                }
            }
            exchange.close();
        });

        server.createContext("/modlist", exchange -> {
            try {
                List<ModListPayload.ModEntry> mods = ModListProvider.getServerMods();
                String baseUrl = ModSyncConfig.DOWNLOAD_BASE_URL.get();

                StringBuilder json = new StringBuilder();
                json.append("{\"baseUrl\":\"").append(baseUrl).append("\",\"mods\":[");
                for (int i = 0; i < mods.size(); i++) {
                    ModListPayload.ModEntry m = mods.get(i);
                    json.append("{")
                            .append("\"modId\":\"").append(m.modId()).append("\",")
                            .append("\"version\":\"").append(m.version()).append("\",")
                            .append("\"fileName\":\"").append(m.fileName()).append("\",")
                            .append("\"sha256\":\"").append(m.sha256()).append("\"")
                            .append("}");
                    if (i < mods.size() - 1) json.append(",");
                }
                json.append("]}");

                byte[] response = json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                byte[] err = "error".getBytes();
                exchange.sendResponseHeaders(500, err.length);
                exchange.getResponseBody().write(err);
            }
            exchange.close();
        });

        // Bug 3 fix: store the executor so stop() can fully shut it down.
        // newCachedThreadPool() uses non-daemon threads — without shutdownNow(),
        // they keep the JVM alive even after server.stop(), which is why AMP
        // thought the process was still running.
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        LOGGER.info("[ModSync] HTTP server started on port {}", port);
    }

    public static void stop() {
        if (server != null) {
            server.stop(1);
            LOGGER.info("[ModSync] HTTP server stopped.");
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOGGER.info("[ModSync] HTTP executor shut down.");
        }
    }

    private static File findModFile(String fileName) {
        File modsDir = new File("mods");
        if (!modsDir.exists()) return null;
        File[] files = modsDir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.getName().equals(fileName)) return f;
        }
        return null;
    }
}