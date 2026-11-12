package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.logging.Logger;
import org.bukkit.Server;
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
    @SuppressWarnings("unchecked")
    void stagesOnlyWhenPluginContentsChange(@TempDir Path directory) throws Exception {
        Path installed = directory.resolve("installed.jar");
        Files.write(installed, PluginJarComparisonTest.jar("2.22.37", "same code"));
        Path update = directory.resolve("update");
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getUpdateFolderFile()).thenReturn(update.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        HttpClient http = mock(HttpClient.class);
        HttpResponse<Path> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String installedName = Path.of(plugin.getClass().getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getFileName().toString();
        Path download = update.resolve(installedName + ".download");
        when(http.<Path>send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            Files.write(download, PluginJarComparisonTest.jar("2.22.38", "same code"));
            return response;
        }).thenAnswer(call -> {
            Files.write(download, PluginJarComparisonTest.jar("2.22.38", "new code"));
            return response;
        });
        var service = new PluginUpdateService(plugin, http);
        try (var comparison = mockStatic(PluginJarComparison.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            comparison.when(PluginJarComparison::installedJar).thenReturn(installed);
            assertFalse(service.stage("https://example.test/galaxy-smp-plugin-2.22.38.jar", "2.22.38"));
            assertFalse(Files.exists(update.resolve(installedName)));
            assertTrue(service.stage("https://example.test/galaxy-smp-plugin-2.22.38.jar", "2.22.38"));
            assertTrue(Files.exists(update.resolve(installedName)));
        }
    }

    @Test
    void describesCountdownTimes() {
        assertEquals("10 minutes", PluginUpdateService.describe(600));
        assertEquals("1 minute", PluginUpdateService.describe(60));
        assertEquals("30 seconds", PluginUpdateService.describe(30));
    }
}
