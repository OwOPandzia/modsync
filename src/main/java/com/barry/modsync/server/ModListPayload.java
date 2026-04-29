package com.barry.modsync.server;

public class ModListPayload {
    public record ModEntry(
            String modId,
            String version,
            String fileName,
            String sha256
    ) {}
}