package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginUpdateServiceTest {
    @Test
    void comparesVersionsNumerically() {
        assertTrue(PluginUpdateService.compareVersions("2.22.17", "2.22.8") > 0);
        assertTrue(PluginUpdateService.compareVersions("2.22.8", "2.22.17") < 0);
        assertEquals(0, PluginUpdateService.compareVersions("v2.22.17", "2.22.17"));
        assertTrue(PluginUpdateService.compareVersions("2.23", "2.22.99") > 0);
    }

    @Test
    void findsThePluginJarNotThePack() {
        String release = "{\"assets\":[{\"browser_download_url\":\"https://x/galaxy-smp-resource-pack-2.22.35.zip\"},"
                + "{\"browser_download_url\":\"https://x/galaxy-smp-plugin-2.22.30.jar\"}]}";
        String jarUrl = PluginUpdateService.findJarUrl(release).orElseThrow();
        assertEquals("2.22.30", PluginUpdateService.pluginVersion(jarUrl).orElseThrow());
    }

    @Test
    void verifiesDownloadedJarMetadata(@TempDir Path directory) throws Exception {
        Path jar = directory.resolve("plugin.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write("name: GalaxySMP\nversion: '2.22.30'\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(PluginUpdateService.hasPluginVersion(jar, "2.22.30"));
        assertFalse(PluginUpdateService.hasPluginVersion(jar, "2.22.35"));
    }

    @Test
    void describesCountdownTimes() {
        assertEquals("10 minutes", PluginUpdateService.describe(600));
        assertEquals("1 minute", PluginUpdateService.describe(60));
        assertEquals("30 seconds", PluginUpdateService.describe(30));
    }
}
