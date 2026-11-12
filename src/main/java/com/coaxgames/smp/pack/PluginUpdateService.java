package com.coaxgames.smp.pack;

import com.coaxgames.smp.GalaxySmpPlugin;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

/**
 * Checks GitHub for a newer plugin release. When one exists it downloads the jar into the server's update
 * folder (Paper swaps it in on the next start) and restarts the server after a public countdown.
 */
public final class PluginUpdateService implements CommandExecutor {
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET_URL = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JAR_VERSION = Pattern.compile("/galaxy-smp-plugin-(\\d+\\.\\d+\\.\\d+)\\.jar$");
    private static final Set<Integer> ANNOUNCE_SECONDS = Set.of(600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1);

    private final GalaxySmpPlugin plugin;
    private final HttpClient http;
    private boolean restartScheduled;
    private BukkitTask countdown;

    public PluginUpdateService(GalaxySmpPlugin plugin) {
        this(plugin, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    PluginUpdateService(GalaxySmpPlugin plugin, HttpClient http) {
        this.plugin = plugin;
        this.http = http;
    }

    public void start() {
        var config = plugin.getConfig();
        if (!config.getBoolean("update.enabled", true)) {
            plugin.getLogger().info("[Update] Auto-update is disabled (update.enabled: false).");
            return;
        }
        String repository = config.getString("resource-pack.repository", "Coaxgames/SMP-Plugin");
        long intervalTicks = Math.max(1L, config.getLong("update.check-interval-minutes", 5L)) * 60L * 20L;
        plugin.getLogger().info("[Update] Installed version " + plugin.getPluginMeta().getVersion() + "; checking "
                + repository + " in 1 minute, then every " + intervalTicks / 1200L + " minutes.");
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> check(repository), 20L * 60L, intervalTicks);
    }

    public void shutdown() {
        if (countdown != null) {
            countdown.cancel();
        }
    }

    private void check(String repository) {
        if (restartScheduled) {
            plugin.getLogger().info("[Update] Skipping check; a restart countdown is already running.");
            return;
        }
        check(repository, null);
    }

    private void check(String repository, CommandSender requester) {
        plugin.getLogger().info("[Update] Checking for a new release (installed " + plugin.getPluginMeta().getVersion() + ").");
        try {
            String release = get("https://api.github.com/repos/" + repository + "/releases/latest");
            Matcher tag = TAG.matcher(release);
            if (!tag.find()) {
                plugin.getLogger().warning("[Update] The release response had no tag_name.");
                reply(requester, "Could not read the latest release version.");
                return;
            }
            String releaseVersion = tag.group(1);
            String jarUrl = findJarUrl(release)
                    .orElseThrow(() -> new IllegalStateException("Release " + releaseVersion + " has no plugin jar."));
            String latest = pluginVersion(jarUrl)
                    .orElseThrow(() -> new IllegalStateException("Release " + releaseVersion + " has an invalid plugin jar version."));
            String installed = plugin.getPluginMeta().getVersion();
            if (compareVersions(latest, installed) <= 0) {
                plugin.getLogger().info("[Update] Up to date: release " + releaseVersion
                        + ", latest plugin " + latest + ", installed " + installed + ".");
                reply(requester, "Galaxy SMP is up to date (" + installed + ").");
                return;
            }
            plugin.getLogger().info("[Update] Newer release found: " + latest + " (installed " + installed + ").");
            if (!stage(jarUrl, latest)) {
                plugin.getLogger().info("[Update] Release " + releaseVersion
                        + " changes only the plugin version; no restart is needed.");
                reply(requester, "Plugin contents are up to date; no restart is needed.");
                return;
            }
            plugin.getLogger().info("Plugin " + latest + " is staged in the update folder (installed: " + installed + ").");
            reply(requester, "Found " + latest + " (installed " + installed + "). Restart countdown started.");
            plugin.getServer().getScheduler().runTask(plugin, () -> scheduleRestart(latest));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not check for a plugin update.", exception);
            reply(requester, "The update check failed. See the server log.");
        }
    }

    private void reply(CommandSender requester, String message) {
        if (requester != null) {
            requester.sendMessage(Component.text(message));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        restartScheduled = false;
        sender.sendMessage(Component.text("Checking for a Galaxy SMP update..."));
        String repository = plugin.getConfig().getString("resource-pack.repository", "Coaxgames/SMP-Plugin");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> check(repository, sender));
        return true;
    }

    boolean stage(String jarUrl, String expectedVersion) throws Exception {
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
        try {
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Plugin jar download failed (HTTP " + response.statusCode() + ").");
            }
            if (!hasPluginVersion(temp, expectedVersion)) {
                throw new IllegalStateException("Downloaded jar does not identify GalaxySMP " + expectedVersion + ".");
            }
            if (PluginJarComparison.sameContents(PluginJarComparison.installedJar(),
                    Files.readAllBytes(temp), expectedVersion)) {
                return false;
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        plugin.getLogger().info("[Update] Downloaded " + jarUrl + " to " + target + ".");
        return true;
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
                Component message = Component.text("Galaxy SMP " + version + " is ready. The server restarts in " + describe(seconds) + ".");
                plugin.getServer().broadcast(message);
                if (seconds == 120 || seconds == 30) {
                    plugin.getServer().showTitle(net.kyori.adventure.title.Title.title(
                            Component.text("Server restarting", net.kyori.adventure.text.format.NamedTextColor.RED),
                            Component.text("in " + describe(seconds) + " to install Galaxy SMP " + version)));
                }
            }
        }, 0L, 20L);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "GalaxySMP-Updater")
                .header("Accept", "application/vnd.github+json")
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            plugin.getLogger().warning("[Update] GET " + url + " failed: " + exception);
            throw exception;
        }
        if (response.statusCode() != 200) {
            plugin.getLogger().warning("[Update] GET " + url + " returned HTTP " + response.statusCode()
                    + (response.statusCode() == 403 || response.statusCode() == 429 ? " (likely GitHub rate limiting)" : "")
                    + ": " + abbreviate(response.body()));
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        plugin.getLogger().info("[Update] GET " + url + " returned HTTP 200 (" + response.body().length() + " bytes).");
        return response.body();
    }

    private static String abbreviate(String body) {
        String flat = body == null ? "" : body.replaceAll("\\s+", " ");
        return flat.length() > 200 ? flat.substring(0, 200) + "..." : flat;
    }

    static boolean hasPluginVersion(Path file, String expectedVersion) throws java.io.IOException {
        try (var jar = new ZipFile(file.toFile())) {
            var entry = jar.getEntry("plugin.yml");
            if (entry == null) {
                return false;
            }
            try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                var metadata = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                return expectedVersion.equals(metadata.getString("version"))
                        && "GalaxySMP".equals(metadata.getString("name"));
            }
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
            if (pluginVersion(url).isPresent()) {
                return Optional.of(url);
            }
        }
        return Optional.empty();
    }

    static Optional<String> pluginVersion(String jarUrl) {
        Matcher matcher = JAR_VERSION.matcher(jarUrl);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
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
