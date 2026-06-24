package me.Erotoro.sleepskip.update;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, Folia-safe update checker.
 *
 * <p>Queries the GitHub Releases API off-thread, compares the latest published version against the
 * running one, and (optionally) notifies admins when they join. It never touches game state from
 * the network thread; the only main/region-thread work is sending the join notification.
 */
public final class UpdateChecker implements Listener {

    private static final String DEFAULT_API_URL = "https://api.github.com/repos/Erotoro/SleepSkip/releases/latest";
    private static final String DEFAULT_DOWNLOAD_URL = "https://github.com/Erotoro/SleepSkip/releases";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final long MIN_INTERVAL_HOURS = 1L;
    private static final long MAX_INTERVAL_HOURS = 24L * 7L;

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private final SleepSkip plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String currentVersion;

    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    private HttpClient httpClient;
    private String apiUrl;
    private String downloadUrl;
    private boolean registered;
    private PlatformScheduler.TaskHandle recheckHandle;

    public UpdateChecker(SleepSkip plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /** Reads config, performs the first check, and schedules periodic re-checks. Call from onEnable. */
    public void start() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        this.apiUrl = configuredUrl("update-checker.api-url", DEFAULT_API_URL);
        this.downloadUrl = configuredUrl("update-checker.download-url", DEFAULT_DOWNLOAD_URL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registered = true;

        PlatformScheduler.runAsync(plugin, this::performCheck);

        long intervalHours = resolveIntervalHours();
        if (intervalHours > 0L) {
            long periodTicks = intervalHours * 3600L * 20L;
            recheckHandle = PlatformScheduler.runAsyncAtFixedRate(plugin, this::performCheck, periodTicks, periodTicks);
        }
    }

    /** Cancels the re-check task and unregisters the join listener. Call from onDisable. */
    public void stop() {
        if (recheckHandle != null) {
            recheckHandle.cancel();
            recheckHandle = null;
        }
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
        httpClient = null;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    /** Runs on a network thread. Must not touch game state. */
    private void performCheck() {
        HttpClient client = httpClient;
        if (client == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SleepSkipUltra/" + currentVersion)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logCheckFailed("HTTP " + response.statusCode());
                return;
            }

            String tag = extractTagName(response.body());
            String latest = normalizeVersion(tag);
            if (latest.isEmpty()) {
                logCheckFailed("could not parse a version from the latest release");
                return;
            }

            handleResult(latest);
        } catch (Exception exception) {
            logCheckFailed(exception.getMessage() == null ? exception.toString() : exception.getMessage());
        }
    }

    private void handleResult(String latest) {
        if (isNewer(latest, currentVersion)) {
            latestVersion = latest;
            updateAvailable = true;
            plugin.getLogger().warning("A new SleepSkipUltra version is available: "
                    + currentVersion + " -> " + latest + " (" + downloadUrl + ")");
        } else {
            latestVersion = latest;
            updateAvailable = false;
            plugin.getLogger().info("SleepSkipUltra is up to date (running " + currentVersion + ").");
        }
    }

    private void logCheckFailed(String detail) {
        plugin.getLogger().log(Level.FINE, "SleepSkipUltra update check failed: " + detail);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable) {
            return;
        }
        if (!plugin.getConfig().getBoolean("update-checker.notify-admins-on-join", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("sleepskip.admin")) {
            return;
        }

        String latest = latestVersion;
        if (latest == null) {
            return;
        }

        String message = plugin.tr(
                        "messages.update-available",
                        "<#F6AD72>A new SleepSkipUltra version is available: <gray>{current}</gray> "
                                + "<#F6AD72>→ <green>{latest}</green>. "
                                + "<click:open_url:'{url}'><underlined>Download here</underlined></click>"
                )
                .replace("{current}", currentVersion)
                .replace("{latest}", latest)
                .replace("{url}", downloadUrl);

        // PlayerJoinEvent already runs on the player's region/main thread, so sending is safe here.
        player.sendMessage(miniMessage.deserialize(message));
    }

    private String configuredUrl(String path, String fallback) {
        String configured = plugin.getConfig().getString(path, "");
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        return configured.trim();
    }

    private long resolveIntervalHours() {
        long configured = plugin.getConfig().getLong("update-checker.interval-hours", 6L);
        if (configured <= 0L) {
            return 0L;
        }
        return Math.max(MIN_INTERVAL_HOURS, Math.min(MAX_INTERVAL_HOURS, configured));
    }

    // ------------------------------------------------------------------
    // Pure helpers (package-private for unit testing)
    // ------------------------------------------------------------------

    static String extractTagName(String json) {
        if (json == null) {
            return null;
        }
        Matcher matcher = TAG_NAME_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Extracts the first dotted-number sequence from a raw tag like {@code v1.9.0} or {@code release-1.9.0}. */
    static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        Matcher matcher = VERSION_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group() : "";
    }

    /** True when {@code candidate} is a strictly newer semantic version than {@code current}. */
    static boolean isNewer(String candidate, String current) {
        int[] candidateParts = parseVersion(candidate);
        int[] currentParts = parseVersion(current);
        int length = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < length; index++) {
            int candidateValue = index < candidateParts.length ? candidateParts[index] : 0;
            int currentValue = index < currentParts.length ? currentParts[index] : 0;
            if (candidateValue != currentValue) {
                return candidateValue > currentValue;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String normalized = normalizeVersion(version);
        if (normalized.isEmpty()) {
            return new int[0];
        }
        String[] parts = normalized.split("\\.");
        int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            try {
                result[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                result[index] = 0;
            }
        }
        return result;
    }
}
