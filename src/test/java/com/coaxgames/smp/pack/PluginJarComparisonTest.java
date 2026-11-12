package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginJarComparisonTest {
    @Test
    void ignoresOnlyVersionAndDetectsPluginChanges(@TempDir Path directory) throws Exception {
        Path installed = directory.resolve("installed.jar");
        Files.write(installed, jar("2.22.37", "old code"));
        assertTrue(PluginJarComparison.sameContents(installed, jar("2.22.38", "old code"), "2.22.38"));
        assertFalse(PluginJarComparison.sameContents(installed, jar("2.22.39", "new code"), "2.22.39"));
        assertThrows(IOException.class,
                () -> PluginJarComparison.sameContents(installed, jar("2.22.38", "old code"), "2.22.39"));
        assertThrows(IOException.class,
                () -> PluginJarComparison.sameContents(installed, new byte[] {1, 2, 3}, "2.22.38"));
    }

    static byte[] jar(String version, String code) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: GalaxySMP\nversion: '" + version + "'\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Galaxy.class"));
            zip.write(code.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
