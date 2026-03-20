package me.Erotoro.sleepskip.utils;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal action bar sender with keyed task replacement.
 */
public final class ActionBar {
    private static final long RESEND_PERIOD_TICKS = 20L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ConcurrentHashMap<String, PlatformScheduler.TaskHandle> ACTIVE_TASKS = new ConcurrentHashMap<>();

    private ActionBar() {
    }

    public static void sendToAll(SleepSkip plugin, String key, String message, int durationInSeconds) {
        Set<UUID> recipients = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            recipients.add(player.getUniqueId());
        }
        send(plugin, key, recipients, message, durationInSeconds);
    }

    public static void send(SleepSkip plugin, String key, Collection<UUID> recipients, String message, int durationInSeconds) {
        if (recipients.isEmpty()) {
            cancelKey(key);
            return;
        }

        PlatformScheduler.TaskHandle previous = ACTIVE_TASKS.remove(key);
        if (previous != null) {
            previous.cancel();
        }

        Component component = MINI_MESSAGE.deserialize(message);
        Set<UUID> playerIds = Set.copyOf(recipients);
        int repeatCount = Math.max(1, durationInSeconds);
        final int[] remainingRepeats = {repeatCount};

        PlatformScheduler.TaskHandle handle = PlatformScheduler.runGlobalAtFixedRate(plugin, () -> {
            sendToPlayers(plugin, playerIds, component);
            remainingRepeats[0]--;
            if (remainingRepeats[0] <= 0) {
                PlatformScheduler.TaskHandle current = ACTIVE_TASKS.remove(key);
                if (current != null) {
                    current.cancel();
                }
            }
        }, 1L, RESEND_PERIOD_TICKS);

        ACTIVE_TASKS.put(key, handle);
    }

    public static void cancelKey(String key) {
        PlatformScheduler.TaskHandle handle = ACTIVE_TASKS.remove(key);
        if (handle != null) {
            handle.cancel();
        }
    }

    public static void cancelCurrentTask(SleepSkip plugin) {
        for (String key : Set.copyOf(ACTIVE_TASKS.keySet())) {
            cancelKey(key);
        }
    }

    private static void sendToPlayers(SleepSkip plugin, Collection<UUID> recipients, Component component) {
        for (UUID recipient : recipients) {
            Player player = Bukkit.getPlayer(recipient);
            if (player != null && player.isOnline()) {
                PlatformScheduler.runForPlayer(plugin, player, () -> player.sendActionBar(component));
            }
        }
    }
}
