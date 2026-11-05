package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

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
        String release = "{\"assets\":[{\"browser_download_url\":\"https://x/galaxy-smp-resource-pack-1.zip\"},"
                + "{\"browser_download_url\":\"https://x/galaxy-smp-plugin-1.jar\"}]}";
        assertEquals(Optional.of("https://x/galaxy-smp-plugin-1.jar"), PluginUpdateService.findJarUrl(release));
    }

    @Test
    void describesCountdownTimes() {
        assertEquals("10 minutes", PluginUpdateService.describe(600));
        assertEquals("1 minute", PluginUpdateService.describe(60));
        assertEquals("30 seconds", PluginUpdateService.describe(30));
    }
}
