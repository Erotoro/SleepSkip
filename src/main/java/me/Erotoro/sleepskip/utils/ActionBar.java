package me.Erotoro.sleepskip.utils;

import me.Erotoro.sleepskip.SleepSkip;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class ActionBar {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final boolean isFolia = isFoliaServer();
    private static BukkitTask currentTask = null;
    private static Object currentFoliaTask = null;

    private static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Отправляет сообщение в ActionBar всем игрокам на указанную длительность.
     * Если уже есть активная задача, она отменяется и запускается новая.
     */
    public static void sendToAll(SleepSkip plugin, String message, int durationInSeconds) {
        // Отменяем предыдущую задачу, если есть
        cancelCurrentTask(plugin);

        Component component = miniMessage.deserialize(message);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            plugin.getLogger().info("ActionBar: Нет игроков для отправки сообщения: " + message);
            return;
        }

        int totalTicks = durationInSeconds * 20;

        if (isFolia) {
            // Folia: используем региональный планировщик
            final int[] ticksPassed = {0};
            currentFoliaTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                ticksPassed[0]++;
                
                // Отправляем ActionBar
                for (Player player : players) {
                    player.sendActionBar(component);
                }

                // Очищаем ActionBar после завершения
                if (ticksPassed[0] >= totalTicks) {
                    for (Player player : players) {
                        player.sendActionBar(Component.empty());
                    }
                    task.cancel();
                    currentFoliaTask = null;
                }
            }, 1L, 1L);
        } else {
            // Paper/Spigot: используем стандартный BukkitRunnable
            currentTask = new BukkitRunnable() {
                int ticksPassed = 0;

                @Override
                public void run() {
                    ticksPassed++;

                    // Отправляем ActionBar
                    for (Player player : players) {
                        player.sendActionBar(component);
                    }

                    // Очищаем ActionBar после завершения
                    if (ticksPassed >= totalTicks) {
                        for (Player player : players) {
                            player.sendActionBar(Component.empty());
                        }
                        cancel();
                        currentTask = null;
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    /**
     * Отменяет текущую задачу ActionBar и очищает ActionBar у всех игроков.
     */
    public static void cancelCurrentTask(SleepSkip plugin) {
        if (isFolia) {
            if (currentFoliaTask != null) {
                try {
                    // Отменяем задачу Folia через reflection
                    Class<?> taskClass = currentFoliaTask.getClass();
                    java.lang.reflect.Method cancelMethod = taskClass.getMethod("cancel");
                    cancelMethod.invoke(currentFoliaTask);
                } catch (Exception e) {
                    plugin.getLogger().warning("Не удалось отменить задачу Folia: " + e.getMessage());
                }
                currentFoliaTask = null;
            }
        } else {
            if (currentTask != null && !currentTask.isCancelled()) {
                currentTask.cancel();
                currentTask = null;
            }
        }

        // Очищаем ActionBar у всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.empty());
        }
    }
}