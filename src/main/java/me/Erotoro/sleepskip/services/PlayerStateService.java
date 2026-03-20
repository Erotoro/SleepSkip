package me.Erotoro.sleepskip.services;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.afk.AFKChecker;
import me.Erotoro.sleepskip.hooks.ExternalPluginHooks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a thread-safe cache of player state for sleep calculations and Folia-safe aggregation.
 */
public class PlayerStateService implements Listener {

    private static final long REFRESH_PERIOD_TICKS = 20L;

    private final SleepSkip plugin;
    private final AFKChecker afkChecker;
    private final ExternalPluginHooks externalPluginHooks;
    private final ConcurrentHashMap<UUID, PlayerStateSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledTask> foliaTasks = new ConcurrentHashMap<>();
    private BukkitTask bukkitRefreshTask;

    public PlayerStateService(SleepSkip plugin, AFKChecker afkChecker, ExternalPluginHooks externalPluginHooks) {
        this.plugin = plugin;
        this.afkChecker = afkChecker;
        this.externalPluginHooks = externalPluginHooks;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (plugin.isFolia()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduleFoliaRefresh(player);
            }
            return;
        }

        bukkitRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllBukkitPlayers, 1L, REFRESH_PERIOD_TICKS);
        refreshAllBukkitPlayers();
    }

    public void stop() {
        if (bukkitRefreshTask != null) {
            bukkitRefreshTask.cancel();
            bukkitRefreshTask = null;
        }

        for (ScheduledTask task : List.copyOf(foliaTasks.values())) {
            task.cancel();
        }
        foliaTasks.clear();
        snapshots.clear();
    }

    public void refreshNow(Player player) {
        if (player == null) {
            return;
        }

        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> refreshSnapshot(player), () -> removePlayer(player.getUniqueId()));
            return;
        }

        refreshSnapshot(player);
    }

    public PlayerStateSnapshot getSnapshot(UUID playerId) {
        return snapshots.get(playerId);
    }

    public Collection<PlayerStateSnapshot> getSnapshots() {
        return List.copyOf(snapshots.values());
    }

    public Set<UUID> getKnownPlayerIds() {
        return Set.copyOf(snapshots.keySet());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.isFolia()) {
            scheduleFoliaRefresh(player);
            return;
        }

        refreshSnapshot(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        refreshNow(event.getPlayer());
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        refreshNow(event.getPlayer());
    }

    private void refreshAllBukkitPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshSnapshot(player);
        }
        snapshots.keySet().retainAll(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet()));
    }

    private void scheduleFoliaRefresh(Player player) {
        UUID playerId = player.getUniqueId();
        ScheduledTask existing = foliaTasks.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> refreshSnapshot(player),
                () -> removePlayer(playerId),
                1L,
                REFRESH_PERIOD_TICKS
        );
        foliaTasks.put(playerId, task);
        refreshNow(player);
    }

    private void refreshSnapshot(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        World world = player.getWorld();
        snapshots.put(player.getUniqueId(), new PlayerStateSnapshot(
                player.getUniqueId(),
                world.getUID(),
                world.getEnvironment() == World.Environment.NORMAL,
                player.hasPermission("sleepskip.bypass"),
                player.getGameMode() == GameMode.SPECTATOR,
                player.hasMetadata("NPC"),
                externalPluginHooks.isVanished(player),
                afkChecker.isPlayerAFK(player) || externalPluginHooks.isAfk(player),
                player.isSleeping()
        ));
    }

    private void removePlayer(UUID playerId) {
        snapshots.remove(playerId);
        ScheduledTask task = foliaTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
