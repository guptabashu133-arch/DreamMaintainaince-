package com.dreammaintenance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Saved state (plugins/dreammaintenance/data.json) - survives proxy restarts. */
final class MaintenanceData {

    boolean enabled;
    String preset;
    String customReason;
    /** epoch millis when maintenance will turn ON (0 = no start timer). */
    long startAt;
    /** epoch millis when maintenance will turn OFF (0 = no end timer). */
    long endAt;
    /** lower-case name -> display name */
    Map<String, String> whitelist = new LinkedHashMap<>();
    /** lower-case server names under maintenance */
    Set<String> servers = new LinkedHashSet<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static MaintenanceData load(Path file) throws IOException {
        if (Files.notExists(file)) {
            return new MaintenanceData();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MaintenanceData data = GSON.fromJson(reader, MaintenanceData.class);
            if (data == null) {
                data = new MaintenanceData();
            }
            if (data.whitelist == null) {
                data.whitelist = new LinkedHashMap<>();
            }
            if (data.servers == null) {
                data.servers = new LinkedHashSet<>();
            }
            return data;
        }
    }

    synchronized void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
