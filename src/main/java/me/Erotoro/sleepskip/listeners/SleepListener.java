package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.services.PlayerEligibilityService;
import me.Erotoro.sleepskip.services.PlayerStateService;
import me.Erotoro.sleepskip.services.PlayerStateSnapshot;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import me.Erotoro.sleepskip.util.SleepRequirementCalculator;
import me.Erotoro.sleepskip.util.SleepTimingRules;
import me.Erotoro.sleepskip.utils.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core gameplay coordinator for sleep counting, transitions and weather-specific sleep rules.
 */
public class SleepListener implements Listener {

    private static final long DEFAULT_DAY_TRANSITION_TICKS = 60L;
    private static final int DISABLED_SLEEP_PERCENTAGE = 101;
    private static final long WEATHER_SLEEP_CHECK_DELAY_TICKS = 20L;
    private static final long STATUS_CACHE_TTL_MS = 1000L;

    private final SleepSkip plugin;
    private final PlayerStateService playerStateService;
    private final PlayerEligibilityService eligibilityService = new PlayerEligibilityService();
    private final Set<UUID> sleepingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeTransitionWorlds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Integer> previousSleepingPercentages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CachedSleepStatus> cachedStatuses = new ConcurrentHashMap<>();

    public SleepListener(SleepSkip plugin, PlayerStateService playerStateService) {
        this.plugin = plugin;
        this.playerStateService = playerStateService;
    }

    @EventHandler
    public void onPlayerSleep(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        SleepTimingRules.SleepTarget sleepTarget = getSleepTarget(world);
        if (!isOverworld(world) || sleepTarget == SleepTimingRules.SleepTarget.NONE) {
            return;
        }

        playerStateService.refreshNow(player);
        sleepingPlayers.add(player.getUniqueId());
        invalidateSleepStatus(world);

        if (sleepTarget == SleepTimingRules.SleepTarget.WEATHER) {
            scheduleDelayedWeatherSleepUpdate(player);
            return;
        }

        scheduleSleepStateUpdate(world);
    }

    @EventHandler
    public void onPlayerWakeUp(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        sleepingPlayers.remove(player.getUniqueId());
        playerStateService.refreshNow(player);
        invalidateSleepStatus(player.getWorld());
        scheduleSleepStateUpdate(player.getWorld());
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        sleepingPlayers.remove(player.getUniqueId());
        playerStateService.refreshNow(player);
        invalidateSleepStatus(event.getFrom());
        invalidateSleepStatus(player.getWorld());
        scheduleSleepStateUpdate(event.getFrom());
        scheduleSleepStateUpdate(player.getWorld());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        playerStateService.refreshNow(player);

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!Tag.BEDS.isTagged(event.getClickedBlock().getType())) {
            return;
        }

        World world = player.getWorld();
        if (!isOverworld(world) || SleepTimingRules.isNight(world.getTime()) || player.isSleeping()) {
            return;
        }

        if (canForceSleepDuringThunderstorm(world) && player.sleep(event.getClickedBlock().getLocation(), true)) {
            sleepingPlayers.add(player.getUniqueId());
            playerStateService.refreshNow(player);
            invalidateSleepStatus(world);
            scheduleDelayedWeatherSleepUpdate(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerExit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerExit(event.getPlayer());
    }

    private void handlePlayerExit(Player player) {
        sleepingPlayers.remove(player.getUniqueId());
        invalidateSleepStatus(player.getWorld());
        scheduleSleepStateUpdate(player.getWorld());
    }

    private void scheduleDelayedWeatherSleepUpdate(Player player) {
        PlatformScheduler.runForPlayerDelayed(plugin, player, () -> {
            if (player.isOnline() && player.isSleeping()) {
                playerStateService.refreshNow(player);
                invalidateSleepStatus(player.getWorld());
                scheduleSleepStateUpdate(player.getWorld());
            }
        }, WEATHER_SLEEP_CHECK_DELAY_TICKS);
    }

    private boolean isOverworld(World world) {
        return world.getEnvironment() == World.Environment.NORMAL;
    }

    private boolean canForceSleepDuringThunderstorm(World world) {
        return getSleepTarget(world) == SleepTimingRules.SleepTarget.WEATHER;
    }

    private SleepTimingRules.SleepTarget getSleepTarget(World world) {
        return SleepTimingRules.resolve(world.getTime(), world.hasStorm(), world.isThundering(), getWeatherSleepMode());
    }

    private void scheduleSleepStateUpdate(World world) {
        if (!isOverworld(world)) {
            return;
        }

        cleanupSleepingPlayers();
        invalidateSleepStatus(world);
        PlatformScheduler.runGlobal(plugin, () -> updateSleepState(world));
    }

    private void updateSleepState(World world) {
        if (activeTransitionWorlds.contains(world.getUID())) {
            return;
        }

        SleepTimingRules.SleepTarget sleepTarget = getSleepTarget(world);
        if (sleepTarget == SleepTimingRules.SleepTarget.NONE) {
            return;
        }

        SleepState state = getSleepState(world);
        if (state.sleepingPlayers >= state.requiredPlayers) {
            startSkip(world, state, sleepTarget);
        } else {
            showSleepStatus(world, state, sleepTarget);
        }
    }

    public SleepStatus getSleepStatus(World world) {
        if (world == null || !isOverworld(world)) {
            return new SleepStatus(0, 0, 1);
        }

        CachedSleepStatus cached = cachedStatuses.get(world.getUID());
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAtMs() <= STATUS_CACHE_TTL_MS) {
            return cached.status();
        }

        SleepStatus recalculated = calculateSleepStatus(world);
        cachedStatuses.put(world.getUID(), new CachedSleepStatus(recalculated, now));
        return recalculated;
    }

    private SleepStatus calculateSleepStatus(World world) {
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);
        UUID targetWorldId = world.getUID();
        int activePlayers = 0;
        int sleeping = 0;

        for (PlayerStateSnapshot snapshot : playerStateService.getSnapshots()) {
            if (eligibilityService.shouldCountAsActive(snapshot, targetWorldId, perWorld)) {
                activePlayers++;
            }

            // Sleeping players still count even if AFK, because sleeping is already an explicit opt-in.
            if (sleepingPlayers.contains(snapshot.playerId())
                    && eligibilityService.shouldCountAsSleeping(snapshot, targetWorldId, perWorld)) {
                sleeping++;
            }
        }

        int requiredPlayers = SleepRequirementCalculator.calculate(
                plugin.getConfig().getString("settings.required-type", "percent"),
                plugin.getConfig().getDouble("settings.required-value", 50D),
                activePlayers
        );

        return new SleepStatus(activePlayers, sleeping, requiredPlayers);
    }

    private SleepState getSleepState(World world) {
        List<UUID> recipients = new ArrayList<>();
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);
        UUID targetWorldId = world.getUID();

        for (PlayerStateSnapshot snapshot : playerStateService.getSnapshots()) {
            if (eligibilityService.shouldCountAsActive(snapshot, targetWorldId, perWorld)) {
                recipients.add(snapshot.playerId());
            }
        }

        SleepStatus status = getSleepStatus(world);
        return new SleepState(recipients, status.sleepingPlayers(), status.requiredPlayers());
    }

    private void startSkip(World world, SleepState state, SleepTimingRules.SleepTarget sleepTarget) {
        UUID worldId = world.getUID();
        if (!activeTransitionWorlds.add(worldId)) {
            return;
        }

        if (sleepTarget == SleepTimingRules.SleepTarget.NIGHT) {
            SleepSkip.incrementNightsSkipped();
        }

        String startMessage = sleepTarget == SleepTimingRules.SleepTarget.NIGHT
                ? plugin.tr("messages.nightSkipping", "<green>Skipping the night...")
                : plugin.tr("messages.weatherSkipping", "<green>Sleeping through the thunderstorm...");
        sendConfiguredMessage(world, state.recipients, startMessage);
        runSkip(world, state.recipients, sleepTarget);
    }

    private void runSkip(World world, Collection<UUID> recipients, SleepTimingRules.SleepTarget sleepTarget) {
        if (SleepTimingRules.shouldAdvanceToNextMorning(sleepTarget)) {
            // Prevent vanilla from advancing the world alongside the custom transition.
            runWorldState(world, () -> disableVanillaSleepSkip(world));
        }

        resetRestStatistic(world);
        if (SleepTimingRules.shouldAdvanceToNextMorning(sleepTarget)) {
            startSmoothDayTransition(world);
        }
        scheduleSkipFinish(
                world,
                recipients,
                sleepTarget,
                SleepTimingRules.getCompletionDelayTicks(sleepTarget, getTransitionDurationTicks())
        );
    }

    private void showSleepStatus(World world, SleepState state, SleepTimingRules.SleepTarget sleepTarget) {
        String message = sleepTarget == SleepTimingRules.SleepTarget.NIGHT
                ? plugin.tr(
                "messages.sleepingStatus",
                "<yellow>{sleeping}/{needed} players are sleeping. Need <yellow>{needed} to skip the night!"
        )
                : plugin.tr(
                "messages.weatherSleepingStatus",
                "<yellow>{sleeping}/{needed} players are sleeping. Need <yellow>{needed} to sleep through the thunderstorm!"
        );
        message = message
                .replace("{sleeping}", String.valueOf(state.sleepingPlayers))
                .replace("{needed}", String.valueOf(state.requiredPlayers));
        sendConfiguredMessage(world, state.recipients, message);
    }

    private void resetRestStatistic(World world) {
        for (UUID playerId : List.copyOf(sleepingPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (player.isOnline() && player.getWorld().equals(world)) {
                    player.setStatistic(Statistic.TIME_SINCE_REST, 0);
                }
            });
        }
    }

    private void scheduleSkipFinish(
            World world,
            Collection<UUID> recipients,
            SleepTimingRules.SleepTarget sleepTarget,
            long delayTicks
    ) {
        Runnable finish = () -> {
            clearWeatherIfNeeded(world, sleepTarget);
            wakeSleepingPlayers(world);
            removeSleepingPlayersForWorld(world);
            String completionMessage = sleepTarget == SleepTimingRules.SleepTarget.NIGHT
                    ? plugin.tr("messages.nightSkipped", "<green>Good morning!")
                    : plugin.tr("messages.weatherSkipped", "<green>The thunderstorm has passed.");
            sendConfiguredMessage(world, recipients, completionMessage);
            if (sleepTarget == SleepTimingRules.SleepTarget.NIGHT) {
                restoreVanillaSleepSkip(world);
            }
            invalidateSleepStatus(world);
            activeTransitionWorlds.remove(world.getUID());
        };

        PlatformScheduler.runGlobalDelayed(plugin, finish, delayTicks);
    }

    private void clearWeatherIfNeeded(World world, SleepTimingRules.SleepTarget sleepTarget) {
        if (!SleepTimingRules.shouldClearWeather(
                sleepTarget,
                plugin.getConfig().getBoolean("settings.skip-rain", true)
        )) {
            return;
        }

        runWorldState(world, () -> {
            if (world.hasStorm()) {
                world.setStorm(false);
            }
            if (world.isThundering()) {
                world.setThundering(false);
            }
        });
    }

    private void startSmoothDayTransition(World world) {
        runWorldState(world, () -> {
            long startTime = world.getTime();
            long startFullTime = world.getFullTime();
            long transitionTicks = getTransitionDurationTicks();
            if (startTime < 1000L) {
                world.setFullTime(startFullTime + (24000L - startTime));
                return;
            }

            long targetFullTime = startFullTime + (24000L - startTime);
            if (plugin.isFolia()) {
                final long[] tick = {0L};
                final PlatformScheduler.TaskHandle[] handleRef = new PlatformScheduler.TaskHandle[1];
                handleRef[0] = PlatformScheduler.runGlobalAtFixedRate(plugin, () -> {
                    tick[0]++;
                    world.setFullTime(interpolateFullTime(startFullTime, targetFullTime, tick[0], transitionTicks));
                    if (tick[0] >= transitionTicks) {
                        world.setFullTime(targetFullTime);
                        handleRef[0].cancel();
                    }
                }, 1L, 1L);
                return;
            }

            new BukkitRunnable() {
                private long tick;

                @Override
                public void run() {
                    tick++;
                    world.setFullTime(interpolateFullTime(startFullTime, targetFullTime, tick, transitionTicks));
                    if (tick >= transitionTicks) {
                        world.setFullTime(targetFullTime);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        });
    }

    private long interpolateFullTime(long startFullTime, long targetFullTime, long tick, long durationTicks) {
        double progress = Math.min(1D, (double) tick / durationTicks);
        return startFullTime + Math.round((targetFullTime - startFullTime) * progress);
    }

    private long getTransitionDurationTicks() {
        return Math.max(20L, plugin.getConfig().getLong("settings.transition-duration-ticks", DEFAULT_DAY_TRANSITION_TICKS));
    }

    private void removeSleepingPlayersForWorld(World world) {
        UUID worldId = world.getUID();
        sleepingPlayers.removeIf(uuid -> {
            PlayerStateSnapshot snapshot = playerStateService.getSnapshot(uuid);
            return snapshot == null || snapshot.worldId().equals(worldId);
        });
        invalidateSleepStatus(world);
    }

    private void wakeSleepingPlayers(World world) {
        for (UUID playerId : List.copyOf(sleepingPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (player.isOnline() && player.getWorld().equals(world) && player.isSleeping()) {
                    player.wakeup(false);
                }
            });
        }
    }

    private void disableVanillaSleepSkip(World world) {
        Integer currentPercentage = world.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
        if (currentPercentage == null) {
            return;
        }

        previousSleepingPercentages.putIfAbsent(world.getUID(), currentPercentage);
        world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, DISABLED_SLEEP_PERCENTAGE);
    }

    private void restoreVanillaSleepSkip(World world) {
        runWorldState(world, () -> {
            Integer previousPercentage = previousSleepingPercentages.remove(world.getUID());
            if (previousPercentage != null) {
                world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, previousPercentage);
            }
        });
    }

    private void sendConfiguredMessage(World world, Collection<UUID> recipients, String message) {
        if (recipients.isEmpty()) {
            return;
        }

        String messageKey = getWorldMessageKey(world);
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            ActionBar.send(plugin, messageKey, recipients, message, plugin.getConfig().getInt("settings.actionbar-duration", 5));
            return;
        }

        ActionBar.cancelKey(messageKey);
        for (UUID recipient : recipients) {
            Player player = Bukkit.getPlayer(recipient);
            if (player != null && player.isOnline()) {
                PlatformScheduler.runForPlayer(plugin, player, () -> player.sendRichMessage(message));
            }
        }
    }

    public void invalidateAllCaches() {
        cachedStatuses.clear();
    }

    private void invalidateSleepStatus(World world) {
        if (world != null) {
            cachedStatuses.remove(world.getUID());
        }
    }

    private void cleanupSleepingPlayers() {
        sleepingPlayers.retainAll(playerStateService.getKnownPlayerIds());
    }

    private String getWeatherSleepMode() {
        Object raw = plugin.getConfig().get("settings.weather-sleep-mode");
        if (raw instanceof String configuredMode && !configuredMode.isBlank()) {
            String normalized = configuredMode.toLowerCase();
            return "rain".equals(normalized) ? "thunderstorm" : normalized;
        }
        return "thunderstorm";
    }

    private String getWorldMessageKey(World world) {
        if (!plugin.getConfig().getBoolean("settings.per-world", false)) {
            return "sleep:global";
        }
        return "sleep:world:" + world.getUID();
    }

    private void runWorldState(World world, Runnable runnable) {
        if (plugin.isFolia()) {
            PlatformScheduler.runGlobal(plugin, runnable);
            return;
        }
        runnable.run();
    }

    private record SleepState(Collection<UUID> recipients, int sleepingPlayers, int requiredPlayers) {
    }

    public record SleepStatus(int activePlayers, int sleepingPlayers, int requiredPlayers) {
    }

    private record CachedSleepStatus(SleepStatus status, long cachedAtMs) {
    }
}
