package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
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
    void derivesPackCompatibilityFromPluginJarNotReleaseTag() {
        String release = """
                {"tag_name":"2.22.35","assets":[
                  {"browser_download_url":"https://github.com/o/r/releases/download/2.22.35/galaxy-smp-plugin-2.22.30.jar"},
                  {"browser_download_url":"https://github.com/o/r/releases/download/2.22.35/galaxy-smp-resource-pack-2.22.35.zip"}
                ]}""";
        assertEquals("2.22.30", ResourcePackService.requiredPluginVersion(release));
    }

    @Test
    void packOnlyReleaseIsUsedWithoutRequiringNewPlugin() throws Exception {
        List<String> requests = new ArrayList<>();
        ResourcePackService service = service("2.22.30", release("2.22.35", "2.22.30"), null, requests);
        ResourcePackService.Pack pack = service.fetchLatest("o/r");
        assertTrue(pack.url().endsWith("galaxy-smp-resource-pack-2.22.35.zip"));
        assertEquals(2, requests.size());
    }

    @Test
    void incompatibleLatestPackFallsBackToInstalledPluginRelease() throws Exception {
        List<String> requests = new ArrayList<>();
        ResourcePackService service = service("2.22.30",
                release("2.22.35", "2.22.35"), release("2.22.30", "2.22.30"), requests);
        ResourcePackService.Pack pack = service.fetchLatest("o/r");
        assertTrue(pack.url().endsWith("galaxy-smp-resource-pack-2.22.30.zip"));
        assertTrue(requests.stream().anyMatch(url -> url.endsWith("/releases/tags/2.22.30")));
    }

    @Test
    void refusesFallbackWhenItsPluginVersionDoesNotMatch() throws Exception {
        ResourcePackService service = service("2.22.30",
                release("2.22.35", "2.22.35"), release("2.22.30", "2.22.29"), new ArrayList<>());
        assertThrows(IllegalStateException.class, () -> service.fetchLatest("o/r"));
    }

    @SuppressWarnings("unchecked")
    private static ResourcePackService service(String installed, String latest, String fallback,
                                               List<String> requests) throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        PluginMeta metadata = mock(PluginMeta.class);
        when(plugin.getPluginMeta()).thenReturn(metadata);
        when(metadata.getVersion()).thenReturn(installed);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        HttpClient http = mock(HttpClient.class);
        when(http.<String>send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            String url = call.getArgument(0, HttpRequest.class).uri().toString();
            requests.add(url);
            String body = url.endsWith("/releases/latest") ? latest
                    : url.endsWith("/releases/tags/" + installed) ? fallback
                    : "037ee6bad9db952b7db4ac906477a3082dbfd2ce";
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(body);
            return response;
        });
        return new ResourcePackService(plugin, http);
    }

    private static String release(String revision, String pluginVersion) {
        return """
                {"assets":[
                  {"browser_download_url":"https://github.com/o/r/releases/download/%s/galaxy-smp-plugin-%s.jar"},
                  {"browser_download_url":"https://github.com/o/r/releases/download/%s/galaxy-smp-resource-pack-%s.zip"},
                  {"browser_download_url":"https://github.com/o/r/releases/download/%s/galaxy-smp-resource-pack-%s.zip.sha1"}
                ]}""".formatted(revision, pluginVersion, revision, revision, revision, revision);
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
