package me.Erotoro.sleepskip.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Small facade over Bukkit/Paper/Folia schedulers used by the plugin.
 */
public final class PlatformScheduler {

    private PlatformScheduler() {
    }

    public static void runGlobal(SleepSkip plugin, Runnable runnable) {
        if (plugin.isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public static void runGlobalDelayed(SleepSkip plugin, Runnable runnable, long delayTicks) {
        if (plugin.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    public static TaskHandle runGlobalAtFixedRate(SleepSkip plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (plugin.isFolia()) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(), delayTicks, periodTicks);
            return task::cancel;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    public static void runForPlayer(SleepSkip plugin, Player player, Runnable runnable) {
        if (player == null) {
            return;
        }

        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> runnable.run(), null);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public static void runForPlayerDelayed(SleepSkip plugin, Player player, Runnable runnable, long delayTicks) {
        if (player == null) {
            return;
        }

        if (plugin.isFolia()) {
            player.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delayTicks);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }
}
