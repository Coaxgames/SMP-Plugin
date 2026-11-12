package com.coaxgames.smp.pack;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Finds the newest GitHub release's resource-pack ZIP and its SHA-1 sidecar, then sends that pack to
 * players on join and to everyone online whenever a newer release changes the SHA-1.
 */
public final class ResourcePackService implements Listener {
    // A fixed id makes each new pack replace the previous one on the client.
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("galaxy-smp-resource-pack".getBytes(StandardCharsets.UTF_8));
    private static final Pattern ASSET_URL = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHA1 = Pattern.compile("^\\s*([0-9a-fA-F]{40})(?![0-9a-fA-F])");

    public record Pack(String url, String sha1) {
    }

    private final GalaxySmpPlugin plugin;
    private final HttpClient http;
    private volatile Pack current;
    private boolean required;
    private Component prompt;

    public ResourcePackService(GalaxySmpPlugin plugin) {
        this(plugin, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    ResourcePackService(GalaxySmpPlugin plugin, HttpClient http) {
        this.plugin = plugin;
        this.http = http;
    }

    public void start() {
        var config = plugin.getConfig();
        if (!config.getBoolean("resource-pack.enabled", true)) {
            return;
        }
        required = config.getBoolean("resource-pack.required", true);
        prompt = Component.text(config.getString("resource-pack.prompt", "Galaxy SMP needs its resource pack to show custom items."));
        String repository = config.getString("resource-pack.repository", "Coaxgames/SMP-Plugin");
        long intervalTicks = Math.max(1L, config.getLong("resource-pack.check-interval-minutes", 5L)) * 60L * 20L;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> refresh(repository), 0L, intervalTicks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Pack pack = current;
            if (pack != null && player.isOnline()) {
                send(player, pack);
            }
        }, 10L);
    }

    private void refresh(String repository) {
        try {
            Pack latest = fetchLatest(repository);
            Pack previous = current;
            current = latest;
            if (previous != null && previous.sha1().equals(latest.sha1())) {
                plugin.getLogger().info("[ResourcePack] Pack unchanged (SHA-1 " + latest.sha1() + ").");
                return;
            }
            plugin.getLogger().info("Resource pack set to " + latest.url() + " (SHA-1 " + latest.sha1() + ").");
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    send(player, latest);
                }
            });
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not check the latest resource pack release; keeping "
                    + (current == null ? "no pack" : "the previous pack") + ".", exception);
        }
    }

    Pack fetchLatest(String repository) throws Exception {
        String release = get("https://api.github.com/repos/" + repository + "/releases/latest");
        String installed = plugin.getPluginMeta().getVersion();
        String required = requiredPluginVersion(release);
        if (PluginUpdateService.compareVersions(required, installed) != 0
                && !latestPluginMatchesInstalled(release, required)) {
            if (!installed.matches("\\d+\\.\\d+\\.\\d+")) {
                throw new IllegalStateException("Installed plugin version is not a numeric release version: " + installed);
            }
            plugin.getLogger().info("[ResourcePack] Latest pack requires plugin " + required
                    + "; looking for the pack matching installed plugin " + installed + ".");
            release = get("https://api.github.com/repos/" + repository + "/releases/tags/" + installed);
            if (PluginUpdateService.compareVersions(requiredPluginVersion(release), installed) != 0) {
                throw new IllegalStateException("The fallback release does not match installed plugin " + installed + ".");
            }
        }
        String zip = findAssetUrl(release, ".zip").orElseThrow(() -> new IllegalStateException("The latest release has no resource-pack ZIP."));
        String sidecar = findAssetUrl(release, ".zip.sha1").orElseThrow(() -> new IllegalStateException("The latest release has no resource-pack SHA-1 file."));
        String sha1 = parseSha1(get(sidecar)).orElseThrow(() -> new IllegalStateException("The SHA-1 file does not start with a 40-character hex digest."));
        return new Pack(zip, sha1);
    }

    private boolean latestPluginMatchesInstalled(String release, String version) throws Exception {
        String jarUrl = PluginUpdateService.findJarUrl(release)
                .orElseThrow(() -> new IllegalStateException("The release has no versioned plugin jar."));
        HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "GalaxySMP-ResourcePack")
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Plugin jar download failed (HTTP " + response.statusCode() + ").");
        }
        return PluginJarComparison.sameContents(PluginJarComparison.installedJar(), response.body(), version);
    }

    static String requiredPluginVersion(String release) {
        String jarUrl = PluginUpdateService.findJarUrl(release)
                .orElseThrow(() -> new IllegalStateException("The release has no versioned plugin jar."));
        return PluginUpdateService.pluginVersion(jarUrl)
                .orElseThrow(() -> new IllegalStateException("The release plugin jar version is invalid."));
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "GalaxySMP-ResourcePack")
                .header("Accept", "application/vnd.github+json, text/plain, */*")
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            plugin.getLogger().warning("[ResourcePack] GET " + url + " failed: " + exception);
            throw exception;
        }
        if (response.statusCode() != 200) {
            plugin.getLogger().warning("[ResourcePack] GET " + url + " returned HTTP " + response.statusCode() + ".");
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        plugin.getLogger().info("[ResourcePack] GET " + url + " returned HTTP 200.");
        return response.body();
    }

    private void send(Player player, Pack pack) {
        player.setResourcePack(PACK_ID, pack.url(), HexFormat.of().parseHex(pack.sha1()), prompt, required);
    }

    static Optional<String> findAssetUrl(String releaseJson, String suffix) {
        Matcher matcher = ASSET_URL.matcher(releaseJson);
        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.contains("resource-pack") && url.endsWith(suffix)) {
                return Optional.of(url);
            }
        }
        return Optional.empty();
    }

    static Optional<String> parseSha1(String text) {
        Matcher matcher = SHA1.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).toLowerCase()) : Optional.empty();
    }
}
