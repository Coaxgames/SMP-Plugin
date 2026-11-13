package com.coaxgames.smp.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coaxgames.smp.GalaxySmpPlugin;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void incompatibleLatestPackFallsBackToInstalledPluginRelease(@TempDir Path directory) throws Exception {
        List<String> requests = new ArrayList<>();
        ResourcePackService service = service("2.22.30",
                release("2.22.35", "2.22.35"), release("2.22.30", "2.22.30"), requests);
        Path installed = directory.resolve("installed.jar");
        Files.write(installed, PluginJarComparisonTest.jar("2.22.30", "old code"));
        try (var comparison = mockStatic(PluginJarComparison.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            comparison.when(PluginJarComparison::installedJar).thenReturn(installed);
            ResourcePackService.Pack pack = service.fetchLatest("o/r");
            assertTrue(pack.url().endsWith("galaxy-smp-resource-pack-2.22.30.zip"));
            assertTrue(requests.stream().anyMatch(url -> url.endsWith("/releases/tags/2.22.30")));
        }
    }

    @Test
    void matchingPluginContentsAllowNewVersionPackWithoutRestart(@TempDir Path directory) throws Exception {
        List<String> requests = new ArrayList<>();
        ResourcePackService service = service("2.22.30", release("2.22.35", "2.22.35"), null, requests);
        Path installed = directory.resolve("installed.jar");
        Files.write(installed, PluginJarComparisonTest.jar("2.22.30", "new code"));
        try (var comparison = mockStatic(PluginJarComparison.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            comparison.when(PluginJarComparison::installedJar).thenReturn(installed);
            ResourcePackService.Pack pack = service.fetchLatest("o/r");
            assertTrue(pack.url().endsWith("galaxy-smp-resource-pack-2.22.35.zip"));
            assertTrue(requests.stream().noneMatch(url -> url.contains("/releases/tags/")));
        }
    }

    @Test
    void refusesFallbackWhenItsPluginVersionDoesNotMatch(@TempDir Path directory) throws Exception {
        ResourcePackService service = service("2.22.30",
                release("2.22.35", "2.22.35"), release("2.22.30", "2.22.29"), new ArrayList<>());
        Path installed = directory.resolve("installed.jar");
        Files.write(installed, PluginJarComparisonTest.jar("2.22.30", "old code"));
        try (var comparison = mockStatic(PluginJarComparison.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            comparison.when(PluginJarComparison::installedJar).thenReturn(installed);
            assertThrows(IllegalStateException.class, () -> service.fetchLatest("o/r"));
        }
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
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            String url = call.getArgument(0, HttpRequest.class).uri().toString();
            requests.add(url);
            if (url.endsWith(".jar")) {
                HttpResponse<byte[]> response = mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(200);
                when(response.body()).thenReturn(PluginJarComparisonTest.jar("2.22.35", "new code"));
                return response;
            }
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

    @Test
    void restoresOnlyOnceForEachAcceptedOtherPackRequest() throws Exception {
        PackFixture fixture = packFixture(true);
        UUID otherPack = UUID.randomUUID();
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.ACCEPTED));
        verify(fixture.player, never()).sendResourcePacks(any(ResourcePackRequest.class));

        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.SUCCESSFULLY_LOADED));
        ArgumentCaptor<ResourcePackRequest> request = ArgumentCaptor.forClass(ResourcePackRequest.class);
        verify(fixture.player).sendResourcePacks(request.capture());
        assertFalse(request.getValue().replace());
        assertTrue(request.getValue().required());
        assertEquals(Component.text("Galaxy prompt"), request.getValue().prompt());
        assertEquals(ResourcePackService.PACK_ID, request.getValue().packs().getFirst().id());
        assertEquals("037ee6bad9db952b7db4ac906477a3082dbfd2ce", request.getValue().packs().getFirst().hash());

        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, ResourcePackService.PACK_ID, Status.SUCCESSFULLY_LOADED));
        for (int i = 0; i < 3; i++) {
            fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.SUCCESSFULLY_LOADED));
        }
        verify(fixture.player, times(1)).sendResourcePacks(any(ResourcePackRequest.class));

        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.FAILED_DOWNLOAD));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.FAILED_DOWNLOAD));
        verify(fixture.player, times(2)).sendResourcePacks(any(ResourcePackRequest.class));
    }

    @Test
    void respectsDeclinedGalaxyPackUntilPlayerLeaves() throws Exception {
        PackFixture fixture = packFixture(false);
        UUID otherPack = UUID.randomUUID();
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, ResourcePackService.PACK_ID, Status.DECLINED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.SUCCESSFULLY_LOADED));
        verify(fixture.player, never()).sendResourcePacks(any(ResourcePackRequest.class));

        fixture.service.onQuit(new PlayerQuitEvent(fixture.player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.SUCCESSFULLY_LOADED));
        ArgumentCaptor<ResourcePackRequest> request = ArgumentCaptor.forClass(ResourcePackRequest.class);
        verify(fixture.player).sendResourcePacks(request.capture());
        assertFalse(request.getValue().required());
    }

    @Test
    void tracksOtherRequestsSeparatelyAndClearsPendingOnQuit() throws Exception {
        PackFixture fixture = packFixture(true);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, first, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, second, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, first, Status.SUCCESSFULLY_LOADED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, second, Status.SUCCESSFULLY_LOADED));
        verify(fixture.player, times(2)).sendResourcePacks(any(ResourcePackRequest.class));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, first, Status.ACCEPTED));
        fixture.service.onQuit(new PlayerQuitEvent(fixture.player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, first, Status.SUCCESSFULLY_LOADED));
        verify(fixture.player, times(2)).sendResourcePacks(any(ResourcePackRequest.class));
    }

    @Test
    void tracksTheSameOtherPackIndependentlyForEachPlayer() throws Exception {
        PackFixture fixture = packFixture(true);
        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.isOnline()).thenReturn(true);
        UUID otherPack = UUID.randomUUID();
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(second, otherPack, Status.ACCEPTED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(fixture.player, otherPack, Status.SUCCESSFULLY_LOADED));
        fixture.service.onPackStatus(new PlayerResourcePackStatusEvent(second, otherPack, Status.SUCCESSFULLY_LOADED));
        verify(fixture.player).sendResourcePacks(any(ResourcePackRequest.class));
        verify(second).sendResourcePacks(any(ResourcePackRequest.class));
    }

    @Test
    void joinSendsStackablePackOnlyWhilePlayerIsOnline() throws Exception {
        PackFixture fixture = packFixture(true);
        fixture.service.onJoin(new PlayerJoinEvent(fixture.player, (Component) null));
        ArgumentCaptor<Runnable> join = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).runTaskLater(eq(fixture.plugin), join.capture(), eq(10L));
        when(fixture.player.isOnline()).thenReturn(false);
        join.getValue().run();
        verify(fixture.player, never()).sendResourcePacks(any(ResourcePackRequest.class));
        when(fixture.player.isOnline()).thenReturn(true);
        join.getValue().run();
        ArgumentCaptor<ResourcePackRequest> request = ArgumentCaptor.forClass(ResourcePackRequest.class);
        verify(fixture.player).sendResourcePacks(request.capture());
        assertFalse(request.getValue().replace());
        assertEquals(ResourcePackService.PACK_ID, request.getValue().packs().getFirst().id());
    }

    @SuppressWarnings("unchecked")
    private static PackFixture packFixture(boolean required) throws Exception {
        GalaxySmpPlugin plugin = mock(GalaxySmpPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginMeta metadata = mock(PluginMeta.class);
        HttpClient http = mock(HttpClient.class);
        Player player = mock(Player.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getBoolean("resource-pack.enabled", true)).thenReturn(true);
        when(config.getBoolean("resource-pack.required", true)).thenReturn(required);
        when(config.getString(eq("resource-pack.prompt"), any())).thenReturn("Galaxy prompt");
        when(config.getString(eq("resource-pack.repository"), any())).thenReturn("o/r");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(plugin.getPluginMeta()).thenReturn(metadata);
        when(metadata.getVersion()).thenReturn("2.22.17");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            String url = call.getArgument(0, HttpRequest.class).uri().toString();
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(url.endsWith("/releases/latest")
                    ? RELEASE : "037ee6bad9db952b7db4ac906477a3082dbfd2ce");
            return response;
        });
        ResourcePackService service = new ResourcePackService(plugin, http);
        service.start();
        ArgumentCaptor<Runnable> refresh = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), refresh.capture(), anyLong(), anyLong());
        refresh.getValue().run();
        return new PackFixture(service, player, plugin, scheduler);
    }

    private record PackFixture(ResourcePackService service, Player player, GalaxySmpPlugin plugin,
                               BukkitScheduler scheduler) { }
}
