package me.Erotoro.sleepskip.afk;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight activity tracker used when no external AFK plugin marks the player as inactive.
 */
public class AFKChecker implements Listener {

    private static final long CLEANUP_INTERVAL_MS = 600_000L;

    private final SleepSkip plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    public AFKChecker(@NotNull SleepSkip plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            lastActivity.put(player.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        // Ignore rotation-only movement.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        updatePlayerActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
    }

    public boolean isPlayerAFK(Player player) {
        if (player == null) {
            return true;
        }

        if (!plugin.getConfig().getBoolean("settings.ignore-afk", true)) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastSeen = lastActivity.putIfAbsent(uuid, now);
        if (lastSeen == null) {
            return false;
        }

        cleanupOldEntries(now);

        long afkTimeoutMs = plugin.getConfig().getLong("settings.afk-timeout", 300L) * 1000L;
        return now - lastSeen > afkTimeoutMs;
    }

    public void updatePlayerActivity(Player player) {
        if (player == null || player.hasMetadata("NPC")) {
            return;
        }

        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void cleanupOldEntries(long now) {
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }

        long staleAfterMs = plugin.getConfig().getLong("settings.afk-timeout", 300L) * 2000L;
        lastActivity.entrySet().removeIf(entry -> now - entry.getValue() > staleAfterMs);
        lastCleanup = now;
    }
}
