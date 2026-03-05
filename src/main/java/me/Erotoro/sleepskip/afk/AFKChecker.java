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
import java.util.Iterator;

public class AFKChecker implements Listener {

    private final SleepSkip plugin;
    private final boolean ignoreAfk;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private static final long CLEANUP_INTERVAL = 600000L; // 10 минут в миллисекундах
    private long lastCleanup = System.currentTimeMillis();

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
        if (player == null) return true; // Защита от null
        
        if (!ignoreAfk) return false; // Если игнорирование AFK отключено, считаем игрока активным

        UUID uuid = player.getUniqueId();
        if (!lastActivity.containsKey(uuid)) {
            // Если игрок только зашел, инициализируем его время
            lastActivity.put(uuid, System.currentTimeMillis());
            return false;
        }

        long afkTimeout = plugin.getConfig().getLong("settings.afk-timeout", 300) * 1000L; // По умолчанию 300 секунд
        boolean isAfk = System.currentTimeMillis() - lastActivity.get(uuid) > afkTimeout;
        
        // Периодическая очистка устаревших записей
        cleanupOldEntries();
        
        return isAfk;
    }

    // Метод для обновления активности игрока (вызывается при движении или других действиях)
    public void updatePlayerActivity(Player player) {
        if (player == null) return; // Защита от null
        if (player.hasMetadata("NPC")) return; // Игнорируем NPC
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    // Очистка устаревших записей (для предотвращения утечки памяти)
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup < CLEANUP_INTERVAL) {
            return;
        }
        
        long afkTimeout = plugin.getConfig().getLong("settings.afk-timeout", 300) * 1000L * 2; // Удаляем записи старше 2x timeout
        Iterator<Map.Entry<UUID, Long>> iterator = lastActivity.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > afkTimeout) {
                iterator.remove();
            }
        }
        
        lastCleanup = currentTime;
    }
}