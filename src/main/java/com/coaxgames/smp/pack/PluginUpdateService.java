package com.coaxgames.smp.pack;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.scheduler.BukkitTask;

/**
 * Checks GitHub for a newer plugin release. When one exists it downloads the jar into the server's update
 * folder (Paper swaps it in on the next start) and restarts the server after a public countdown.
 */
public final class PluginUpdateService {
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET_URL = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Set<Integer> ANNOUNCE_SECONDS = Set.of(600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1);

    private final GalaxySmpPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private boolean restartScheduled;
    private BukkitTask countdown;

    public PluginUpdateService(GalaxySmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var config = plugin.getConfig();
        if (!config.getBoolean("update.enabled", true)) {
            return;
        }
        String repository = config.getString("resource-pack.repository", "Coaxgames/SMP-Plugin");
        long intervalTicks = Math.max(1L, config.getLong("update.check-interval-minutes", 30L)) * 60L * 20L;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> check(repository), 20L * 60L, intervalTicks);
    }

    public void shutdown() {
        if (countdown != null) {
            countdown.cancel();
        }
    }

    private void check(String repository) {
        if (restartScheduled) {
            return;
        }
        try {
            String release = get("https://api.github.com/repos/" + repository + "/releases/latest");
            Matcher tag = TAG.matcher(release);
            if (!tag.find()) {
                return;
            }
            String latest = tag.group(1);
            String installed = plugin.getPluginMeta().getVersion();
            if (compareVersions(latest, installed) <= 0) {
                return;
            }
            String jarUrl = findJarUrl(release).orElseThrow(() -> new IllegalStateException("Release " + latest + " has no plugin jar."));
            stage(jarUrl);
            plugin.getLogger().info("Plugin " + latest + " is staged in the update folder (installed: " + installed + ").");
            plugin.getServer().getScheduler().runTask(plugin, () -> scheduleRestart(latest));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not check for a plugin update.", exception);
        }
    }

    private void stage(String jarUrl) throws Exception {
        Path updateFolder = plugin.getServer().getUpdateFolderFile().toPath();
        Files.createDirectories(updateFolder);
        // Paper only replaces a plugin whose update-folder jar has the same file name as the installed jar.
        String jarName = installedJarName();
        Path target = updateFolder.resolve(jarName);
        Path temp = updateFolder.resolve(jarName + ".download");
        HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "GalaxySMP-Updater")
                .build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() != 200 || !looksLikeJar(temp)) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("Plugin jar download failed (HTTP " + response.statusCode() + ").");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String installedJarName() throws Exception {
        return Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString();
    }

    private void scheduleRestart(String version) {
        if (restartScheduled) {
            return;
        }
        restartScheduled = true;
        int[] remaining = {Math.max(1, plugin.getConfig().getInt("update.restart-delay-minutes", 10)) * 60};
        countdown = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int seconds = remaining[0]--;
            if (seconds <= 0) {
                countdown.cancel();
                plugin.getServer().broadcast(Component.text("Restarting now to install Galaxy SMP " + version + "."));
                String command = plugin.getConfig().getString("update.restart-command", "restart").replaceFirst("^/", "");
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            } else if (ANNOUNCE_SECONDS.contains(seconds)) {
                plugin.getServer().broadcast(Component.text("Galaxy SMP " + version + " is ready. The server restarts in " + describe(seconds) + "."));
            }
        }, 0L, 20L);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "GalaxySMP-Updater")
                .header("Accept", "application/vnd.github+json")
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    private static boolean looksLikeJar(Path file) throws java.io.IOException {
        try (var input = Files.newInputStream(file)) {
            return input.read() == 'P' && input.read() == 'K';
        }
    }

    static String describe(int seconds) {
        return seconds >= 60 && seconds % 60 == 0
                ? seconds / 60 + (seconds == 60 ? " minute" : " minutes")
                : seconds + (seconds == 1 ? " second" : " seconds");
    }

    static Optional<String> findJarUrl(String releaseJson) {
        Matcher matcher = ASSET_URL.matcher(releaseJson);
        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.endsWith(".jar") && url.contains("plugin")) {
                return Optional.of(url);
            }
        }
        return Optional.empty();
    }

    /** Compares dotted numeric versions; non-numeric parts count as 0. */
    static int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("\\.");
        String[] b = right.replaceFirst("^[vV]", "").split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int difference = Integer.compare(part(a, index), part(b, index));
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String digits = parts[index].replaceAll("[^0-9].*$", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
