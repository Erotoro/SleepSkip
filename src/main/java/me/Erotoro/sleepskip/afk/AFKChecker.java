package me.Erotoro.sleepskip.afk;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AFKChecker implements Listener {

    private final SleepSkip plugin;
    private final boolean ignoreAfk;
    private final Map<UUID, Long> lastActivity = new HashMap<>();

    public AFKChecker(@NotNull SleepSkip plugin) {
        this.plugin = plugin;
        this.ignoreAfk = plugin.getConfig().getBoolean("settings.ignore-afk", true);

        // Регистрация слушателя событий
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Инициализация времени активности для текущих игроков
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Обновляем время активности при движении игрока
        updatePlayerActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Удаляем игрока из списка при выходе
        lastActivity.remove(event.getPlayer().getUniqueId());
    }

    public boolean isPlayerAFK(Player player) {
        if (!ignoreAfk) return false; // Если игнорирование AFK отключено, считаем игрока активным

        UUID uuid = player.getUniqueId();
        if (!lastActivity.containsKey(uuid)) {
            // Если игрок только зашел, инициализируем его время
            lastActivity.put(uuid, System.currentTimeMillis());
            return false;
        }

        long afkTimeout = plugin.getConfig().getLong("settings.afk-timeout", 300) * 1000; // По умолчанию 300 секунд
        return System.currentTimeMillis() - lastActivity.get(uuid) > afkTimeout;
    }

    // Метод для обновления активности игрока (вызывается при движении или других действиях)
    public void updatePlayerActivity(Player player) {
        if (player.hasMetadata("NPC")) return; // Игнорируем NPC
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }
}