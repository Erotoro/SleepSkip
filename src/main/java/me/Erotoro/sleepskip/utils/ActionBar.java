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

/**
 * Minimal action bar sender with keyed task replacement.
 */
public final class ActionBar {
    private static final long RESEND_PERIOD_TICKS = 20L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ActionBarTaskRegistry TASK_REGISTRY = new ActionBarTaskRegistry();

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

        Component component = MINI_MESSAGE.deserialize(message);
        Set<UUID> playerIds = Set.copyOf(recipients);
        int repeatCount = Math.max(1, durationInSeconds);
        final int[] remainingRepeats = {repeatCount};
        Object taskIdentity = new Object();

        PlatformScheduler.TaskHandle handle = PlatformScheduler.runGlobalAtFixedRate(plugin, () -> {
            sendToPlayers(plugin, playerIds, component);
            remainingRepeats[0]--;
            if (remainingRepeats[0] <= 0) {
                TASK_REGISTRY.completeIfCurrent(key, taskIdentity);
            }
        }, 1L, RESEND_PERIOD_TICKS);

        TASK_REGISTRY.replaceTask(key, taskIdentity, handle);
    }

    public static void cancelKey(String key) {
        TASK_REGISTRY.cancelKey(key);
    }

    public static void cancelCurrentTask() {
        TASK_REGISTRY.cancelAll();
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
