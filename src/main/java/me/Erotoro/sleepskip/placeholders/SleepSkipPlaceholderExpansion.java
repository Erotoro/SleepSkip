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

        World world = resolveWorld(offlinePlayer);
        if (world == null) {
            return "";
        }

        SleepListener.SleepStatus status = listener.getSleepStatus(world);
        return switch (params.toLowerCase()) {
            case "sleeping" -> Integer.toString(status.sleepingPlayers());
            case "needed" -> Integer.toString(status.requiredPlayers());
            case "active_players" -> Integer.toString(status.activePlayers());
            case "world" -> world.getName();
            default -> null;
        };
    }

    private World resolveWorld(OfflinePlayer offlinePlayer) {
        if (offlinePlayer instanceof Player player) {
            return player.getWorld();
        }

        // Offline lookups may not have player context.
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().getFirst());
    }
}
