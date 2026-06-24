package me.Erotoro.sleepskip.placeholders;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.listeners.SleepListener;
import me.Erotoro.sleepskip.util.SleepTimingRules;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI bridge backed by cached listener status rather than bespoke calculations.
 */
public class SleepSkipPlaceholderExpansion extends PlaceholderExpansion {

    private final SleepSkip plugin;

    public SleepSkipPlaceholderExpansion(SleepSkip plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sleepskip";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        SleepListener listener = plugin.getSleepListener();
        if (listener == null) {
            return "";
        }

        String normalizedParams = params.toLowerCase();
        World world = resolveWorld(offlinePlayer);
        if (world == null) {
            return getUnavailableValue(normalizedParams);
        }

        SleepListener.SleepStatus status = listener.getSleepStatus(world);
        return switch (normalizedParams) {
            case "sleeping" -> Integer.toString(status.sleepingPlayers());
            case "needed" -> Integer.toString(status.requiredPlayers());
            case "active_players" -> Integer.toString(status.activePlayers());
            case "remaining" -> Integer.toString(Math.max(0, status.requiredPlayers() - status.sleepingPlayers()));
            case "percent" -> Integer.toString(computeProgressPercent(status));
            case "state" -> listener.getBehaviorStateName(world);
            case "speed" -> formatSpeed(listener.getCurrentSpeedMultiplier(world));
            case "is_night" -> Boolean.toString(SleepTimingRules.isNight(world.getTime()));
            case "day_count" -> Integer.toString(plugin.getDayCounterService().getDayCount(world));
            case "world" -> world.getName();
            default -> null;
        };
    }

    private int computeProgressPercent(SleepListener.SleepStatus status) {
        if (status.requiredPlayers() <= 0) {
            return 0;
        }
        int percent = (int) Math.round(status.sleepingPlayers() * 100.0D / status.requiredPlayers());
        return Math.max(0, Math.min(100, percent));
    }

    private String formatSpeed(double speedMultiplier) {
        double normalized = Math.max(1.0D, speedMultiplier);
        return String.format(java.util.Locale.ROOT, "%.1f", normalized);
    }

    private World resolveWorld(OfflinePlayer offlinePlayer) {
        if (offlinePlayer instanceof Player player) {
            return player.getWorld();
        }

        String offlineMode = plugin.getConfig().getString("placeholders.offline-mode", "none");
        String normalizedMode = offlineMode == null ? "none" : offlineMode.trim().toLowerCase();

        if ("fallback-world".equals(normalizedMode)) {
            return resolveConfiguredFallbackWorld();
        }
        if ("global".equals(normalizedMode)) {
            return resolveGlobalWorld();
        }
        if ("none".equals(normalizedMode)) {
            return null;
        }

        // Unknown mode is treated as conservative "none".
        return null;
    }

    private World resolveConfiguredFallbackWorld() {
        String fallbackWorldName = plugin.getConfig().getString("placeholders.fallback-world", "");
        if (fallbackWorldName == null || fallbackWorldName.isBlank()) {
            return null;
        }
        return plugin.getServer().getWorld(fallbackWorldName);
    }

    private World resolveGlobalWorld() {
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0));
    }

    private String getUnavailableValue(String params) {
        return switch (params) {
            case "world", "sleeping", "needed", "active_players", "remaining",
                 "percent", "state", "speed", "is_night", "day_count" -> "N/A";
            default -> "";
        };
    }
}
