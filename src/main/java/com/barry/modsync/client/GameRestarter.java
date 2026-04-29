package com.barry.modsync.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class GameRestarter {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void restart() {
        try {
            String java = ProcessHandle.current()
                    .info().command()
                    .orElse(System.getProperty("java.home") + "/bin/java");

            List<String> cmd = new ArrayList<>();
            cmd.add(java);
            cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));

            String mainCmd = System.getProperty("sun.java.command");
            if (mainCmd != null) {
                cmd.addAll(List.of(mainCmd.split(" ")));
            }

            LOGGER.info("[ModSync] Restarting game...");
            new ProcessBuilder(cmd).inheritIO().start();
        } catch (Exception e) {
            LOGGER.error("[ModSync] Auto-restart failed, exiting for launcher restart: {}", e.getMessage());
        }

        // Always exit — launcher will restart
        System.exit(0);
    }
}