package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.World;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-troll gates that decide whether a natural skip is currently allowed: a minimum online
 * player count and a per-world cooldown between skips.
 *
 * <p>These gate <em>natural</em> skips only. Admin {@code /sleep forceskip} deliberately bypasses
 * them. All values are read live from config so {@code /sleep reload} takes effect immediately;
 * both gates are no-ops at their default values, so behaviour is unchanged unless an owner opts in.
 */
final class SleepGuards {

    private final SleepSkip plugin;
    private final ConcurrentHashMap<UUID, Long> lastSkipAtMs = new ConcurrentHashMap<>();

    SleepGuards(SleepSkip plugin) {
        this.plugin = plugin;
    }

    /** Whether a natural skip is currently blocked for this world. */
    boolean isSkipBlocked(World world, int activePlayers) {
        return isBelowMinimumPlayers(activePlayers) || isOnCooldown(world);
    }

    private boolean isBelowMinimumPlayers(int activePlayers) {
        int minimum = Math.max(0, plugin.getConfig().getInt("limits.min-players-online", 0));
        return minimum > 0 && activePlayers < minimum;
    }

    boolean isOnCooldown(World world) {
        long cooldownMs = cooldownMs();
        if (cooldownMs <= 0L || world == null) {
            return false;
        }
        Long last = lastSkipAtMs.get(world.getUID());
        return last != null && (System.currentTimeMillis() - last) < cooldownMs;
    }

    /** Records that a skip just completed in this world, starting the cooldown window. */
    void markSkipped(World world) {
        if (world == null) {
            return;
        }
        if (cooldownMs() > 0L) {
            lastSkipAtMs.put(world.getUID(), System.currentTimeMillis());
        } else {
            lastSkipAtMs.remove(world.getUID());
        }
    }

    void clear() {
        lastSkipAtMs.clear();
    }

    private long cooldownMs() {
        return Math.max(0L, plugin.getConfig().getLong("limits.skip-cooldown-seconds", 0L)) * 1000L;
    }
}
