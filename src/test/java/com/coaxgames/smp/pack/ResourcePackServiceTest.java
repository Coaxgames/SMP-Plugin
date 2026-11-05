package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResourcePackServiceTest {
    private static final String RELEASE = """
            {"assets":[
              {"name":"galaxy-smp-plugin-2.22.17.jar","browser_download_url":"https://github.com/o/r/releases/download/2.22.17/galaxy-smp-plugin-2.22.17.jar"},
              {"name":"galaxy-smp-resource-pack-2.22.17.zip","browser_download_url":"https://github.com/o/r/releases/download/2.22.17/galaxy-smp-resource-pack-2.22.17.zip"},
              {"name":"galaxy-smp-resource-pack-2.22.17.zip.sha1","browser_download_url": "https://github.com/o/r/releases/download/2.22.17/galaxy-smp-resource-pack-2.22.17.zip.sha1"}
            ]}""";

    @Test
    void picksZipAndSidecarSeparately() {
        assertEquals(Optional.of("https://github.com/o/r/releases/download/2.22.17/galaxy-smp-resource-pack-2.22.17.zip"),
                ResourcePackService.findAssetUrl(RELEASE, ".zip"));
        assertEquals(Optional.of("https://github.com/o/r/releases/download/2.22.17/galaxy-smp-resource-pack-2.22.17.zip.sha1"),
                ResourcePackService.findAssetUrl(RELEASE, ".zip.sha1"));
    }

    @Test
    void ignoresReleasesWithoutAPack() {
        assertTrue(ResourcePackService.findAssetUrl("{\"assets\":[]}", ".zip").isEmpty());
    }

    @Test
    void readsDigestFromSidecarWithTrailingWhitespace() {
        assertEquals(Optional.of("037ee6bad9db952b7db4ac906477a3082dbfd2ce"),
                ResourcePackService.parseSha1("037EE6BAD9DB952B7DB4AC906477A3082DBFD2CE\n\n"));
        assertEquals(Optional.of("037ee6bad9db952b7db4ac906477a3082dbfd2ce"),
                ResourcePackService.parseSha1("037ee6bad9db952b7db4ac906477a3082dbfd2ce  pack.zip\n"));
    }

    @Test
    void rejectsMalformedDigest() {
        assertTrue(ResourcePackService.parseSha1("not a hash").isEmpty());
        assertTrue(ResourcePackService.parseSha1("037ee6ba").isEmpty());
    }
}
