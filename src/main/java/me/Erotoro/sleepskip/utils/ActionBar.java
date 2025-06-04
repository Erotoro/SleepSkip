package me.Erotoro.sleepskip.utils;

import me.Erotoro.sleepskip.SleepSkip;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class ActionBar {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static void sendToAll(SleepSkip plugin, String message, int durationInSeconds) {
        Component component = miniMessage.deserialize(message);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            plugin.getLogger().info("ActionBar: Нет игроков для отправки сообщения: " + message);
            return;
        }

        if (Bukkit.getServer().getName().contains("Folia")) {
            final int[] iterations = {0};
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                iterations[0]++;
                for (Player player : players) {
                    player.sendActionBar(component);
                }
                if (iterations[0] >= durationInSeconds) {
                    task.cancel();
                }
            }, 1L, 20L);
        } else {
            new BukkitRunnable() {
                int secondsLeft = durationInSeconds;
                @Override
                public void run() {
                    if (secondsLeft <= 0) {
                        cancel();
                        return;
                    }
                    for (Player player : players) {
                        player.sendActionBar(component);
                    }
                    secondsLeft--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }
}