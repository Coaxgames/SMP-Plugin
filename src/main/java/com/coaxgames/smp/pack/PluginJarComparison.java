package com.coaxgames.smp.pack;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.bukkit.configuration.file.YamlConfiguration;

final class PluginJarComparison {
    private static final Pattern VERSION = Pattern.compile(
            "(?m)^version:[ \\t]*['\"]?\\d+\\.\\d+\\.\\d+['\"]?[ \\t]*(?=\\r?$)");

    private PluginJarComparison() {
    }

    static Path installedJar() throws URISyntaxException {
        return Path.of(GalaxySmpPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    static boolean sameContents(Path installed, byte[] candidate, String candidateVersion) throws IOException {
        Map<String, byte[]> current;
        try (InputStream input = Files.newInputStream(installed)) {
            current = readEntries(input);
        }
        Map<String, byte[]> next = readEntries(new ByteArrayInputStream(candidate));
        normalizeMetadata(current, null);
        normalizeMetadata(next, candidateVersion);
        if (!current.keySet().equals(next.keySet())) {
            return false;
        }
        for (String name : current.keySet()) {
            if (!Arrays.equals(current.get(name), next.get(name))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, byte[]> readEntries(InputStream input) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && entries.putIfAbsent(entry.getName(), zip.readAllBytes()) != null) {
                    throw new IOException("Plugin jar contains duplicate entry: " + entry.getName());
                }
            }
        }
        return entries;
    }

    private static void normalizeMetadata(Map<String, byte[]> entries, String expectedVersion) throws IOException {
        byte[] bytes = entries.get("plugin.yml");
        if (bytes == null) {
            throw new IOException("Plugin jar has no plugin.yml.");
        }
        String metadata = new String(bytes, StandardCharsets.UTF_8);
        var matcher = VERSION.matcher(metadata);
        if (!matcher.find() || matcher.find()) {
            throw new IOException("Plugin jar must contain exactly one numeric version.");
        }
        var yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        if (!"GalaxySMP".equals(yaml.getString("name"))
                || expectedVersion != null && !expectedVersion.equals(yaml.getString("version"))) {
            throw new IOException("Plugin jar metadata does not match the expected GalaxySMP version.");
        }
        entries.put("plugin.yml", VERSION.matcher(metadata).replaceFirst("version: '<version>'")
                .getBytes(StandardCharsets.UTF_8));
    }
}
