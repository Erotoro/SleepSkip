package me.Erotoro.sleepskip.placeholders;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.listeners.SleepListener;
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
            case "day_count" -> Integer.toString(plugin.getDayCounterService().getDayCount(world));
            case "world" -> world.getName();
            default -> null;
        };
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
                .orElse(plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().getFirst());
    }

    private String getUnavailableValue(String params) {
        return switch (params) {
            case "world" -> "N/A";
            case "sleeping", "needed", "active_players", "day_count" -> "N/A";
            default -> "";
        };
    }
}
