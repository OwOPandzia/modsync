package com.barry.modsync.server;

import com.barry.modsync.config.ModSyncConfig;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class ModListProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Bug 2 fix: cache the result — no reason to re-hash every mod on every client request
    private static List<ModListPayload.ModEntry> cachedModList = null;

    public static List<ModListPayload.ModEntry> getServerMods() {
        if (cachedModList != null) return cachedModList;

        List<String> excluded = ModSyncConfig.EXCLUDED_MODS.get()
                .stream().map(Object::toString).toList();

        cachedModList = ModList.get().getMods().stream()
                .filter(mod -> !isBuiltIn(mod.getModId()))
                .filter(mod -> !excluded.contains(mod.getModId()))
                .map(mod -> {
                    File jar = getJarFile(mod);
                    // Bug 2 fix: some mods resolve to a directory (exploded/virtual mods),
                    // not a jar. isFile() guards against trying to hash a directory,
                    // which was causing the "Could not hash" warning on every client request.
                    if (jar == null || !jar.isFile()) return null;

                    String hash = "";
                    try {
                        hash = sha256(jar);
                    } catch (Exception e) {
                        LOGGER.warn("[ModSync] Could not hash {} — skipping.", jar.getName());
                    }
                    return new ModListPayload.ModEntry(
                            mod.getModId(),
                            mod.getVersion().toString(),
                            jar.getName(),
                            hash
                    );
                })
                .filter(e -> e != null && !e.fileName().isEmpty() && !e.sha256().isEmpty())
                .toList();

        LOGGER.info("[ModSync] Mod list cached: {} mod(s) ready to serve.", cachedModList.size());
        return cachedModList;
    }

    private static boolean isBuiltIn(String modId) {
        return modId.equals("minecraft")
                || modId.equals("neoforge")
                || modId.equals("modsync");
    }

    private static File getJarFile(IModInfo mod) {
        try {
            return mod.getOwningFile().getFile().getFilePath().toFile();
        } catch (Exception e) {
            return null;
        }
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) digest.update(buf, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}