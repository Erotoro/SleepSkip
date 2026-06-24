package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.api.SleepSkipType;
import me.Erotoro.sleepskip.api.event.SleepSkipCancelEvent;
import me.Erotoro.sleepskip.api.event.SleepSkipCompleteEvent;
import me.Erotoro.sleepskip.api.event.SleepSkipStartEvent;
import me.Erotoro.sleepskip.services.DayCounterService;
import me.Erotoro.sleepskip.services.PlayerEligibilityService;
import me.Erotoro.sleepskip.services.PlayerStateService;
import me.Erotoro.sleepskip.services.SleepOverlayService;
import me.Erotoro.sleepskip.listeners.SleepRuntimeSessions.ActiveNightAccelerationSession;
import me.Erotoro.sleepskip.listeners.SleepRuntimeSessions.ActiveSkipSession;
import me.Erotoro.sleepskip.listeners.SleepRuntimeSessions.DelegatingTaskHandle;
import me.Erotoro.sleepskip.listeners.SleepRuntimeSessions.NightBehaviorState;
import me.Erotoro.sleepskip.listeners.SleepRuntimeSessions.NightAccelerationProfile;
import me.Erotoro.sleepskip.listeners.SleepRuntimeSessions.SleepState;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import me.Erotoro.sleepskip.util.SleepTimingRules;
import me.Erotoro.sleepskip.utils.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Core gameplay coordinator for sleep counting, transitions and weather-specific sleep rules.
 */
public class SleepListener implements Listener {

    private static final int DISABLED_SLEEP_PERCENTAGE = 101;
    private static final long WEATHER_SLEEP_CHECK_DELAY_TICKS = 20L;
    private static final long ACTIVE_SKIP_VALIDATION_INTERVAL_TICKS = 1L;
    private static final long STATUS_CACHE_TTL_MS = 1000L;

    private final SleepSkip plugin;
    private final PlayerStateService playerStateService;
    private final SleepOverlayService sleepOverlayService;
    private final DayCounterService dayCounterService;
    private final SleepRuleConfig ruleConfig;
    private final SleepStatusTracker statusTracker;
    private final SleepGuards sleepGuards;
    private final ConcurrentHashMap<UUID, ActiveSkipSession> activeSkipSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ActiveNightAccelerationSession> activeNightAccelerationSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NightBehaviorState> nightBehaviorStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> suppressedVanillaSleepPercentages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> currentSpeedMultipliers = new ConcurrentHashMap<>();

    public SleepListener(SleepSkip plugin, PlayerStateService playerStateService, SleepOverlayService sleepOverlayService) {
        this.plugin = plugin;
        this.playerStateService = playerStateService;
        this.sleepOverlayService = sleepOverlayService;
        this.dayCounterService = plugin.getDayCounterService();
        this.ruleConfig = new SleepRuleConfig(plugin);
        this.statusTracker = new SleepStatusTracker(plugin, playerStateService, new PlayerEligibilityService());
        this.sleepGuards = new SleepGuards(plugin);
    }

    @EventHandler
    public void onPlayerSleep(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        SleepTimingRules.SleepTarget sleepTarget = getSleepTarget(world);
        if (!ruleConfig.isOverworld(world) || sleepTarget == SleepTimingRules.SleepTarget.NONE) {
            return;
        }

        playerStateService.refreshNow(player);
        statusTracker.markSleeping(player.getUniqueId());
        statusTracker.invalidate(world);

        if (sleepTarget == SleepTimingRules.SleepTarget.WEATHER) {
            scheduleDelayedWeatherSleepUpdate(player);
            return;
        }

        ensureVanillaSleepSkipSuppressed(world);
        scheduleSleepStateUpdate(world);
    }

    @EventHandler
    public void onPlayerWakeUp(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        statusTracker.unmarkSleeping(player.getUniqueId());
        playerStateService.refreshNow(player);
        statusTracker.invalidate(player.getWorld());
        scheduleSleepStateUpdate(player.getWorld());
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        statusTracker.unmarkSleeping(player.getUniqueId());
        playerStateService.refreshNow(player);
        statusTracker.invalidate(event.getFrom());
        statusTracker.invalidate(player.getWorld());
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
        if (!ruleConfig.isOverworld(world) || SleepTimingRules.isNight(world.getTime()) || player.isSleeping()) {
            return;
        }

        if (ruleConfig.canForceSleepDuringThunderstorm(world) && player.sleep(event.getClickedBlock().getLocation(), true)) {
            statusTracker.markSleeping(player.getUniqueId());
            playerStateService.refreshNow(player);
            statusTracker.invalidate(world);
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
        statusTracker.unmarkSleeping(player.getUniqueId());
        statusTracker.invalidate(player.getWorld());
        scheduleSleepStateUpdate(player.getWorld());
    }

    private void scheduleDelayedWeatherSleepUpdate(Player player) {
        PlatformScheduler.runForPlayerDelayed(plugin, player, () -> {
            if (player.isOnline() && player.isSleeping()) {
                playerStateService.refreshNow(player);
                statusTracker.invalidate(player.getWorld());
                scheduleSleepStateUpdate(player.getWorld());
            }
        }, WEATHER_SLEEP_CHECK_DELAY_TICKS);
    }

    private SleepTimingRules.SleepTarget getSleepTarget(World world) {
        return ruleConfig.getSleepTarget(world);
    }

    private void scheduleSleepStateUpdate(World world) {
        if (!ruleConfig.isOverworld(world)) {
            clearNightBehaviorState(world.getUID());
            cancelActiveSkip(world, null, null, false);
            stopNightAcceleration(world, true);
            sleepOverlayService.stop(world);
            return;
        }

        statusTracker.cleanupSleepingPlayers();
        statusTracker.invalidate(world);
        PlatformScheduler.runGlobal(plugin, () -> updateSleepState(world));
    }

    private void updateSleepState(World world) {
        ActiveSkipSession activeSession = activeSkipSessions.get(world.getUID());
        SleepTimingRules.SleepTarget currentSleepTarget = getSleepTarget(world);

        if (activeSession != null) {
            transitionNightBehaviorState(world, NightBehaviorState.FULL_SKIP);
            validateActiveSkip(world, activeSession, currentSleepTarget);
            return;
        }

        if (currentSleepTarget == SleepTimingRules.SleepTarget.NONE) {
            releaseVanillaSleepSkipSuppression(world);
            transitionNightBehaviorState(world, NightBehaviorState.IDLE);
            sleepOverlayService.stop(world);
            return;
        }

        SleepState state = getSleepState(world);
        if (currentSleepTarget == SleepTimingRules.SleepTarget.WEATHER) {
            releaseVanillaSleepSkipSuppression(world);
            transitionNightBehaviorState(world, NightBehaviorState.IDLE);
            handleWeatherSleepState(world, state);
            return;
        }

        if (currentSleepTarget != SleepTimingRules.SleepTarget.NIGHT) {
            releaseVanillaSleepSkipSuppression(world);
            transitionNightBehaviorState(world, NightBehaviorState.IDLE);
            sleepOverlayService.stop(world);
            return;
        }

        handleNightSleepState(world, state);
    }

    private void handleWeatherSleepState(World world, SleepState state) {
        if (state.sleepingPlayers() >= state.requiredPlayers()
                && !sleepGuards.isSkipBlocked(world, state.recipients().size())) {
            startSkip(world, state, SleepTimingRules.SleepTarget.WEATHER);
            return;
        }

        if (state.sleepingPlayers() <= 0) {
            sleepOverlayService.stop(world);
            return;
        }

        showSleepStatus(world, state, SleepTimingRules.SleepTarget.WEATHER);
    }

    private void handleNightSleepState(World world, SleepState state) {
        NightBehaviorPlan plan = buildNightBehaviorPlan(world, state);
        handleNightSleepState(world, state, plan);
    }

    private void handleNightSleepState(World world, SleepState state, NightBehaviorPlan plan) {
        transitionNightBehaviorState(world, plan.behaviorState());

        if (plan.behaviorState() == NightBehaviorState.FULL_SKIP) {
            startSkip(world, state, SleepTimingRules.SleepTarget.NIGHT);
            return;
        }

        if (plan.behaviorState() == NightBehaviorState.ACCELERATING) {
            startOrUpdateNightAcceleration(world, state, plan.accelerationProfile());
            return;
        }

        if (state.sleepingPlayers() <= 0) {
            releaseVanillaSleepSkipSuppression(world);
            sleepOverlayService.stop(world);
            return;
        }

        showSleepStatus(world, state, SleepTimingRules.SleepTarget.NIGHT);
    }

    private NightBehaviorPlan buildNightBehaviorPlan(World world, SleepState state) {
        if (state.sleepingPlayers() <= 0 || state.requiredPlayers() <= 0) {
            return new NightBehaviorPlan(NightBehaviorState.IDLE, null);
        }

        // Anti-troll gates block natural skips/acceleration while still showing sleep status.
        if (sleepGuards.isSkipBlocked(world, state.recipients().size())) {
            return new NightBehaviorPlan(NightBehaviorState.IDLE, null);
        }

        if (state.sleepingPlayers() >= state.requiredPlayers()) {
            return new NightBehaviorPlan(NightBehaviorState.FULL_SKIP, null);
        }

        NightAccelerationProfile profile = buildNightAccelerationProfile(state);
        if (profile == null) {
            return new NightBehaviorPlan(NightBehaviorState.IDLE, null);
        }

        return new NightBehaviorPlan(NightBehaviorState.ACCELERATING, profile);
    }

    private void validateActiveSkip(World world, ActiveSkipSession session, SleepTimingRules.SleepTarget currentSleepTarget) {
        if (session.isCancelled() || session.isCompleted()) {
            activeSkipSessions.remove(world.getUID(), session);
            return;
        }

        SleepState state = getSleepState(world);
        session.updateRecipients(state.recipients());
        // Transition UI recipients should not collapse when players wake up near finish.
        sleepOverlayService.refreshRecipients(world, state.recipients());

        if (!isActiveSessionStillValid(world, session, state, currentSleepTarget)) {
            cancelActiveSkip(world, session, state.recipients(), true);
        }
    }

    private boolean isActiveSessionStillValid(
            World world,
            ActiveSkipSession session,
            SleepState state,
            SleepTimingRules.SleepTarget currentSleepTarget
    ) {
        return isSkipSessionStillValid(
                session.sleepTarget(),
                session.isCommitted(),
                ruleConfig.isOverworld(world),
                state.sleepingPlayers(),
                state.requiredPlayers(),
                currentSleepTarget
        );
    }

    private static boolean isSkipSessionStillValid(
            SleepTimingRules.SleepTarget sessionSleepTarget,
            boolean committed,
            boolean overworld,
            int sleepingPlayers,
            int requiredPlayers,
            SleepTimingRules.SleepTarget currentSleepTarget
    ) {
        if (sessionSleepTarget == SleepTimingRules.SleepTarget.WEATHER) {
            if (currentSleepTarget != SleepTimingRules.SleepTarget.WEATHER) {
                return false;
            }
            if (committed) {
                return true;
            }
            return sleepingPlayers >= requiredPlayers;
        }

        if (!overworld) {
            return false;
        }

        if (committed) {
            return true;
        }

        return sleepingPlayers >= requiredPlayers;
    }

    public static boolean isSkipSessionStillValidForTests(
            SleepTimingRules.SleepTarget sessionSleepTarget,
            boolean committed,
            boolean overworld,
            int sleepingPlayers,
            int requiredPlayers,
            SleepTimingRules.SleepTarget currentSleepTarget
    ) {
        return isSkipSessionStillValid(
                sessionSleepTarget,
                committed,
                overworld,
                sleepingPlayers,
                requiredPlayers,
                currentSleepTarget
        );
    }

    public SleepStatus getSleepStatus(World world) {
        return statusTracker.getSleepStatus(
                world,
                world != null && shouldBypassStatusCache(world),
                ruleConfig.getStatusCacheTtlMs(STATUS_CACHE_TTL_MS),
                world != null && ruleConfig.isOverworld(world)
        );
    }

    private SleepState getSleepState(World world) {
        return statusTracker.getSleepState(
                world,
                ruleConfig.isOverworld(world),
                shouldBypassStatusCache(world),
                ruleConfig.getStatusCacheTtlMs(STATUS_CACHE_TTL_MS)
        );
    }

    private void startSkip(World world, SleepState state, SleepTimingRules.SleepTarget sleepTarget) {
        startSkip(world, sleepTarget, state.recipients(), state.sleepingPlayers(), state.requiredPlayers(), false);
    }

    private void startSkip(
            World world,
            SleepTimingRules.SleepTarget sleepTarget,
            Collection<UUID> recipients,
            int sleepingPlayers,
            int requiredPlayers,
            boolean forced
    ) {
        UUID worldId = world.getUID();
        if (sleepTarget == SleepTimingRules.SleepTarget.NIGHT) {
            transitionNightBehaviorState(world, NightBehaviorState.FULL_SKIP);
        }
        long transitionDurationTicks = resolveTransitionDurationTicks(sleepTarget);
        long completionDelayTicks = SleepTimingRules.getCompletionDelayTicks(sleepTarget, transitionDurationTicks);
        Integer previousSleepingPercentage = SleepTimingRules.shouldAdvanceToNextMorning(sleepTarget)
                ? takeOrDisableVanillaSleepSkip(world)
                : null;

        ActiveSkipSession session = new ActiveSkipSession(
                worldId,
                sleepTarget,
                Set.copyOf(recipients),
                collectWorldSleepers(world),
                previousSleepingPercentage,
                transitionDurationTicks,
                completionDelayTicks,
                currentDayIndex(world),
                forced
        );
        ActiveSkipSession existing = activeSkipSessions.putIfAbsent(worldId, session);
        if (existing != null) {
            restoreVanillaSleepSkip(world, previousSleepingPercentage);
            return;
        }

        SleepSkipStartEvent startEvent = new SleepSkipStartEvent(
                world, toApiType(sleepTarget), sleepingPlayers, Math.max(1, requiredPlayers), Set.copyOf(recipients), forced);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            // Unwind the just-created session so a vetoed attempt leaves no trace; the skip can be
            // re-attempted (and re-fire this event) the next time the conditions are met.
            activeSkipSessions.remove(worldId, session);
            restoreVanillaSleepSkip(world, previousSleepingPercentage);
            transitionNightBehaviorState(world, NightBehaviorState.IDLE);
            logOverlayLifecycle(world, "skip_cancelled_by_api", "target=" + sleepTarget);
            return;
        }

        if (forced) {
            session.markCommitted();
        }
        logOverlayLifecycle(
                world,
                "skip_start",
                "target=" + sleepTarget
                        + ",sleeping=" + sleepingPlayers
                        + ",needed=" + requiredPlayers
                        + ",transitionTicks=" + transitionDurationTicks
                        + ",completionDelayTicks=" + completionDelayTicks
                        + ",recipients=" + recipients.size()
        );

        String startMessage = sleepTarget == SleepTimingRules.SleepTarget.NIGHT
                ? plugin.tr("messages.nightSkipping", "<green>Skipping the night...")
                : plugin.tr("messages.weatherSkipping", "<green>Sleeping through the thunderstorm...");
        sendConfiguredMessage(world, recipients, startMessage);
        sleepOverlayService.startTransition(
                world,
                sleepTarget,
                Set.copyOf(recipients),
                sleepingPlayers,
                requiredPlayers,
                transitionDurationTicks
        );
        runSkip(world, session);
    }

    private void runSkip(World world, ActiveSkipSession session) {
        session.setValidationTaskHandle(startActiveSkipValidationTask(world.getUID(), session));

        if (SleepTimingRules.shouldAdvanceToNextMorning(session.sleepTarget())) {
            session.setTransitionTaskHandle(startSmoothDayTransition(world, session));
        }
        session.setFinishTaskHandle(scheduleSkipFinish(world, session));
    }

    private PlatformScheduler.TaskHandle startActiveSkipValidationTask(UUID worldId, ActiveSkipSession session) {
        return PlatformScheduler.runGlobalAtFixedRate(
                plugin,
                () -> tickActiveSkipValidation(worldId, session),
                1L,
                ACTIVE_SKIP_VALIDATION_INTERVAL_TICKS
        );
    }

    private void tickActiveSkipValidation(UUID worldId, ActiveSkipSession session) {
        ActiveSkipSession current = activeSkipSessions.get(worldId);
        if (current != session || session.isCancelled() || session.isCompleted()) {
            session.cancelTasks();
            return;
        }

        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            if (!activeSkipSessions.remove(worldId, session)) {
                return;
            }
            if (!session.markCancelled()) {
                return;
            }
            session.cancelTasks();
            clearNightBehaviorState(worldId);
            sleepOverlayService.stopByWorldId(worldId);
            return;
        }
        transitionNightBehaviorState(world, NightBehaviorState.FULL_SKIP);

        SleepState state = getSleepState(world);
        session.updateRecipients(state.recipients());
        sleepOverlayService.refreshRecipients(world, state.recipients());

        SleepTimingRules.SleepTarget currentSleepTarget = getSleepTarget(world);
        if (!isActiveSessionStillValid(world, session, state, currentSleepTarget)) {
            cancelActiveSkip(world, session, state.recipients(), true);
        }
    }

    private void startOrUpdateNightAcceleration(World world, SleepState state, NightAccelerationProfile profile) {
        UUID worldId = world.getUID();
        if (activeNightAccelerationSessions.get(worldId) == null) {
            long intervalTicks = ruleConfig.getNightAccelerationUpdateIntervalTicks();
            PlatformScheduler.TaskHandle taskHandle = startNightAccelerationTask(worldId, intervalTicks);
            ActiveNightAccelerationSession created = new ActiveNightAccelerationSession(taskHandle, intervalTicks);
            ActiveNightAccelerationSession raced = activeNightAccelerationSessions.putIfAbsent(worldId, created);
            if (raced != null) {
                taskHandle.cancel();
            }
        }

        currentSpeedMultipliers.put(worldId, profile.speedMultiplier());
        sleepOverlayService.showAcceleration(
                world,
                state.overlayRecipients(),
                state.sleepingPlayers(),
                state.requiredPlayers(),
                profile.speedMultiplier()
        );
    }

    private PlatformScheduler.TaskHandle startNightAccelerationTask(UUID worldId, long intervalTicks) {
        return PlatformScheduler.runGlobalAtFixedRate(plugin, () -> tickNightAcceleration(worldId), 1L, intervalTicks);
    }

    private void tickNightAcceleration(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            clearNightBehaviorState(worldId);
            stopNightAcceleration(worldId, null, true);
            sleepOverlayService.stopByWorldId(worldId);
            return;
        }

        if (!ruleConfig.isOverworld(world)) {
            transitionNightBehaviorState(world, NightBehaviorState.IDLE);
            return;
        }

        if (activeSkipSessions.containsKey(worldId)) {
            transitionNightBehaviorState(world, NightBehaviorState.FULL_SKIP);
            return;
        }

        if (getSleepTarget(world) != SleepTimingRules.SleepTarget.NIGHT) {
            transitionNightBehaviorState(world, NightBehaviorState.IDLE);
            return;
        }

        SleepState state = getSleepState(world);
        NightBehaviorPlan plan = buildNightBehaviorPlan(world, state);
        handleNightSleepState(world, state, plan);
        if (plan.behaviorState() != NightBehaviorState.ACCELERATING || plan.accelerationProfile() == null) {
            return;
        }

        ActiveNightAccelerationSession session = activeNightAccelerationSessions.get(worldId);
        if (session == null) {
            return;
        }

        applyNightAcceleration(world, session, plan.accelerationProfile().speedMultiplier());
    }

    private void applyNightAcceleration(World world, ActiveNightAccelerationSession session, double speedMultiplier) {
        if (!SleepTimingRules.isNight(world.getTime())) {
            return;
        }

        long extraTicks = computeExtraNightTicks(session, speedMultiplier);
        if (extraTicks <= 0L) {
            return;
        }

        runWorldState(world, () -> world.setFullTime(world.getFullTime() + extraTicks));
    }

    private long computeExtraNightTicks(ActiveNightAccelerationSession session, double speedMultiplier) {
        if (session == null) {
            return 0L;
        }
        return session.consumeWholeExtraTicks(speedMultiplier);
    }

    private NightAccelerationProfile buildNightAccelerationProfile(SleepState state) {
        if (state.requiredPlayers() <= 0) {
            return null;
        }

        double contribution = state.sleepingPlayers() / (double) state.requiredPlayers();
        double startRatio = ruleConfig.getNightAccelerationStartThresholdRatio();
        if (contribution < startRatio) {
            return null;
        }

        double maxSpeed = ruleConfig.getNightAccelerationMaxSpeedMultiplier();
        if (maxSpeed <= 1D) {
            return null;
        }

        double normalizedProgress = (contribution - startRatio) / Math.max(0.00001D, 1D - startRatio);
        normalizedProgress = Math.max(0D, Math.min(1D, normalizedProgress));
        double speedMultiplier = 1D + normalizedProgress * (maxSpeed - 1D);
        return new NightAccelerationProfile(Math.max(1D, Math.min(maxSpeed, speedMultiplier)));
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
                .replace("{sleeping}", String.valueOf(state.sleepingPlayers()))
                .replace("{needed}", String.valueOf(state.requiredPlayers()));
        sleepOverlayService.showStatus(world, sleepTarget, state.overlayRecipients(), state.sleepingPlayers(), state.requiredPlayers());
        sendConfiguredMessage(world, state.recipients(), message);
    }

    private void resetPhantomTimerForWorld(World world) {
        for (Player player : world.getPlayers()) {
            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (player.isOnline() && player.getWorld().equals(world)) {
                    player.setStatistic(Statistic.TIME_SINCE_REST, 0);
                }
            });
        }
    }

    private PlatformScheduler.TaskHandle scheduleSkipFinish(World world, ActiveSkipSession session) {
        Runnable finish = () -> {
            ActiveSkipSession current = activeSkipSessions.get(world.getUID());
            if (!isFinishSessionStillActive(world, session, current)) {
                return;
            }

            SleepState state = getSleepState(world);
            SleepTimingRules.SleepTarget currentSleepTarget = getSleepTarget(world);
            if (!isActiveSessionStillValid(world, session, state, currentSleepTarget)) {
                cancelActiveSkip(world, session, state.recipients(), true);
                return;
            }

            session.updateRecipients(state.recipients());
            if (!session.markCompleted()) {
                return;
            }
            session.cancelTasks();

            // Preserve start-of-skip sleepers and include any players still sleeping at completion.
            Set<UUID> sleepers = new LinkedHashSet<>(session.sleepers());
            sleepers.addAll(collectWorldSleepers(world));
            session.updateSleepers(sleepers);

            if (session.sleepTarget() == SleepTimingRules.SleepTarget.NIGHT) {
                if (!hasDayAdvancedSinceSkipStarted(session.startedDayIndex(), currentDayIndex(world))) {
                    applyConfiguredMorningTime(world);
                }
                resetPhantomTimerForWorld(world);
                SleepSkip.incrementNightsSkipped();
            }

            // Signal transition completion before wake/cleanup side-effects can trigger overlay recipient shrink.
            sleepOverlayService.completeTransition(world);

            if (session.sleepTarget() == SleepTimingRules.SleepTarget.NIGHT && dayCounterService.isEnabled()) {
                dayCounterService.scheduleSleepSkipMorningAnnouncement(world, state.overlayRecipients());
            }

            clearWeatherIfNeeded(world, session.sleepTarget());
            wakeSleepingPlayers(world);
            removeSleepingPlayersForWorld(world);
            String completionMessage = session.sleepTarget() == SleepTimingRules.SleepTarget.NIGHT
                    ? plugin.tr("messages.nightSkipped", "<green>Good morning!")
                    : plugin.tr("messages.weatherSkipped", "<green>The thunderstorm has passed.");
            sendConfiguredMessage(world, session.recipients(), completionMessage);
            restoreVanillaSleepSkip(world, session.previousSleepingPercentage());
            statusTracker.invalidate(world);
            logOverlayLifecycle(
                    world,
                    "skip_finish_before_complete_transition",
                    "target=" + session.sleepTarget()
                            + ",sleepingNow=" + state.sleepingPlayers()
                            + ",requiredNow=" + state.requiredPlayers()
                            + ",recipients=" + state.recipients().size()
            );
            logOverlayLifecycle(world, "skip_finish_complete_transition_called", "target=" + session.sleepTarget());
            sleepGuards.markSkipped(world);
            fireSkipComplete(world, session.sleepTarget(), sleepers, session.recipients(), false, session.forced());
            activeSkipSessions.remove(world.getUID(), session);
        };

        return PlatformScheduler.runGlobalDelayed(plugin, finish, session.completionDelayTicks());
    }

    private boolean isFinishSessionStillActive(World world, ActiveSkipSession expectedSession, ActiveSkipSession currentSession) {
        if (world == null || currentSession == null) {
            return false;
        }

        return currentSession == expectedSession
                && !expectedSession.isCancelled()
                && !expectedSession.isCompleted()
                && expectedSession.worldId().equals(world.getUID());
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

    private PlatformScheduler.TaskHandle startSmoothDayTransition(World world, ActiveSkipSession session) {
        DelegatingTaskHandle delegatingHandle = new DelegatingTaskHandle();

        runWorldState(world, () -> {
            long startTime = world.getTime();
            long startFullTime = world.getFullTime();
            long transitionTicks = Math.max(1L, session.transitionDurationTicks());
            long targetFullTime = resolveSmoothSkipTargetFullTime(startFullTime, startTime);
            if (targetFullTime <= startFullTime) {
                if (!session.markCommitted()) {
                    delegatingHandle.setDelegate(SleepRuntimeSessions.NO_OP_TASK);
                    return;
                }
                world.setFullTime(startFullTime);
                delegatingHandle.setDelegate(SleepRuntimeSessions.NO_OP_TASK);
                return;
            }
            final long[] tick = {0L};
            final PlatformScheduler.TaskHandle[] handleRef = new PlatformScheduler.TaskHandle[1];
            handleRef[0] = PlatformScheduler.runGlobalAtFixedRate(plugin, () -> {
                ActiveSkipSession current = activeSkipSessions.get(world.getUID());
                if (current != session || session.isCancelled() || session.isCompleted()) {
                    handleRef[0].cancel();
                    return;
                }

                if (hasDayAdvancedSinceSkipStarted(session.startedDayIndex(), currentDayIndex(world))) {
                    handleRef[0].cancel();
                    return;
                }

                if (!session.isCommitted() && !session.markCommitted()) {
                    handleRef[0].cancel();
                    return;
                }

                tick[0]++;
                world.setFullTime(interpolateFullTime(startFullTime, targetFullTime, tick[0], transitionTicks));
                if (tick[0] >= transitionTicks) {
                    world.setFullTime(targetFullTime);
                    handleRef[0].cancel();
                }
            }, 1L, 1L);
            delegatingHandle.setDelegate(handleRef[0]);
        });

        return delegatingHandle;
    }

    public static long resolveNextMorningFullTime(long startFullTime, long currentTimeOfDay, long daytimeTicks) {
        long normalizedTimeOfDay = Math.floorMod(currentTimeOfDay, 24000L);
        long normalizedDaytimeTicks = Math.max(0L, Math.min(23999L, daytimeTicks));
        long currentDayBaseFullTime = startFullTime - normalizedTimeOfDay;
        return currentDayBaseFullTime + 24000L + normalizedDaytimeTicks;
    }

    public static long resolveSmoothSkipTargetFullTime(long startFullTime, long currentTimeOfDay) {
        if (!SleepTimingRules.isNight(currentTimeOfDay)) {
            return startFullTime;
        }
        return resolveNextDawnFullTime(startFullTime, currentTimeOfDay);
    }

    private static long resolveNextDawnFullTime(long startFullTime, long currentTimeOfDay) {
        long normalizedTimeOfDay = Math.floorMod(currentTimeOfDay, 24000L);
        long currentDayBaseFullTime = startFullTime - normalizedTimeOfDay;
        return currentDayBaseFullTime + 24000L;
    }

    private long currentDayIndex(World world) {
        if (world == null) {
            return 0L;
        }
        return Math.max(0L, world.getFullTime() / 24000L);
    }

    private static boolean hasDayAdvancedSinceSkipStarted(long startedDayIndex, long currentDayIndex) {
        return currentDayIndex > startedDayIndex;
    }

    public static boolean hasDayAdvancedSinceSkipStartedForTests(long startedDayIndex, long currentDayIndex) {
        return hasDayAdvancedSinceSkipStarted(startedDayIndex, currentDayIndex);
    }

    private void applyConfiguredMorningTime(World world) {
        if (world == null) {
            return;
        }

        long configuredDaytimeTicks = Math.max(0L, Math.min(23999L, plugin.getConfig().getLong("settings.daytime-ticks", 0L)));
        if (configuredDaytimeTicks == world.getTime()) {
            return;
        }

        world.setFullTime(resolveConfiguredMorningFullTimeAfterSkip(world.getFullTime(), world.getTime(), configuredDaytimeTicks));
    }

    public static long resolveConfiguredMorningFullTimeAfterSkip(long currentFullTime, long currentTimeOfDay, long daytimeTicks) {
        long normalizedTimeOfDay = Math.floorMod(currentTimeOfDay, 24000L);
        long normalizedDaytimeTicks = Math.max(0L, Math.min(23999L, daytimeTicks));
        long currentDayBaseFullTime = currentFullTime - normalizedTimeOfDay;
        if (normalizedTimeOfDay <= normalizedDaytimeTicks) {
            return currentDayBaseFullTime + normalizedDaytimeTicks;
        }
        return currentDayBaseFullTime + 24000L + normalizedDaytimeTicks;
    }

    private long resolveTransitionDurationTicks(SleepTimingRules.SleepTarget sleepTarget) {
        if (SleepTimingRules.shouldAdvanceToNextMorning(sleepTarget)) {
            return Math.max(1L, ruleConfig.getTransitionDurationTicks());
        }
        return 1L;
    }

    private long interpolateFullTime(long startFullTime, long targetFullTime, long tick, long durationTicks) {
        double progress = Math.min(1D, (double) tick / durationTicks);
        return startFullTime + Math.round((targetFullTime - startFullTime) * progress);
    }

    private void removeSleepingPlayersForWorld(World world) {
        statusTracker.removeSleepingPlayersForWorld(world);
    }

    private void wakeSleepingPlayers(World world) {
        for (UUID playerId : statusTracker.sleepingPlayersSnapshot()) {
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

    private Integer disableVanillaSleepSkip(World world) {
        Integer currentPercentage = world.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
        if (currentPercentage == null) {
            return null;
        }

        runWorldState(world, () -> world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, DISABLED_SLEEP_PERCENTAGE));
        return currentPercentage;
    }

    private Integer takeOrDisableVanillaSleepSkip(World world) {
        Integer existing = suppressedVanillaSleepPercentages.remove(world.getUID());
        if (existing != null) {
            return existing;
        }
        return disableVanillaSleepSkip(world);
    }

    private void ensureVanillaSleepSkipSuppressed(World world) {
        if (world == null) {
            return;
        }
        suppressedVanillaSleepPercentages.computeIfAbsent(world.getUID(), ignored -> disableVanillaSleepSkip(world));
    }

    private void releaseVanillaSleepSkipSuppression(World world) {
        if (world == null) {
            return;
        }
        Integer previousPercentage = suppressedVanillaSleepPercentages.remove(world.getUID());
        if (previousPercentage != null) {
            restoreVanillaSleepSkip(world, previousPercentage);
        }
    }

    private static boolean shouldKeepVanillaSleepSkipSuppressed(
            SleepTimingRules.SleepTarget sleepTarget,
            boolean hasActiveSkip,
            int sleepingPlayers
    ) {
        return hasActiveSkip || (sleepTarget == SleepTimingRules.SleepTarget.NIGHT && sleepingPlayers > 0);
    }

    public static boolean shouldKeepVanillaSleepSkipSuppressedForTests(
            SleepTimingRules.SleepTarget sleepTarget,
            boolean hasActiveSkip,
            int sleepingPlayers
    ) {
        return shouldKeepVanillaSleepSkipSuppressed(sleepTarget, hasActiveSkip, sleepingPlayers);
    }

    private void restoreVanillaSleepSkip(World world, Integer previousPercentage) {
        if (previousPercentage == null) {
            return;
        }

        runWorldState(world, () -> world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, previousPercentage));
    }

    private void cancelActiveSkip(
            World world,
            ActiveSkipSession expectedSession,
            Collection<UUID> recipientsOverride,
            boolean notifyCancellation
    ) {
        ActiveSkipSession session = expectedSession != null
                ? expectedSession
                : activeSkipSessions.get(world.getUID());
        if (session == null) {
            return;
        }

        if (!activeSkipSessions.remove(world.getUID(), session)) {
            return;
        }
        if (!session.markCancelled()) {
            return;
        }

        session.cancelTasks();
        restoreVanillaSleepSkip(world, session.previousSleepingPercentage());
        logOverlayLifecycle(world, "skip_cancelled", "target=" + session.sleepTarget() + ",notify=" + notifyCancellation);
        sleepOverlayService.stop(world);
        statusTracker.invalidate(world);
        transitionNightBehaviorState(world, NightBehaviorState.IDLE);
        fireSkipCancel(world, session.sleepTarget(), SleepSkipCancelEvent.Reason.CONDITIONS_NOT_MET);

        if (!notifyCancellation) {
            return;
        }

        Collection<UUID> recipients = recipientsOverride != null && !recipientsOverride.isEmpty()
                ? recipientsOverride
                : session.recipients();
        if (recipients.isEmpty()) {
            return;
        }

        String cancellationMessage = session.sleepTarget() == SleepTimingRules.SleepTarget.NIGHT
                ? plugin.tr("messages.nightSkippingCancelled", "<yellow>Night skip cancelled.")
                : plugin.tr("messages.weatherSkippingCancelled", "<yellow>Thunderstorm skip cancelled.");
        sendConfiguredMessage(world, recipients, cancellationMessage);
    }

    private void sendConfiguredMessage(World world, Collection<UUID> recipients, String message) {
        if (recipients.isEmpty()) {
            clearUiState(world);
            return;
        }

        String messageKey = ruleConfig.getWorldMessageKey(world);
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

    private void clearUiState(World world) {
        ActionBar.cancelKey(ruleConfig.getWorldMessageKey(world));
        sleepOverlayService.stop(world);
    }

    public void shutdownActiveSkipSessions() {
        for (var entry : List.copyOf(activeSkipSessions.entrySet())) {
            cancelAndRemoveSession(entry.getKey(), entry.getValue(), true);
        }
        stopAllNightAccelerationSessions(true);
        restoreAllVanillaSleepSkipSuppressions();
        nightBehaviorStates.clear();
        currentSpeedMultipliers.clear();
    }

    public void resetRuntimeState() {
        ActionBar.cancelCurrentTask();
        for (var entry : List.copyOf(activeSkipSessions.entrySet())) {
            cancelAndRemoveSession(entry.getKey(), entry.getValue(), false);
        }
        stopAllNightAccelerationSessions(false);
        sleepOverlayService.stopAll();
        statusTracker.resetSleepingPlayersFromSnapshots();
        statusTracker.invalidateAll();
        restoreAllVanillaSleepSkipSuppressions();
        nightBehaviorStates.clear();
        currentSpeedMultipliers.clear();
    }

    public void invalidateAllCaches() {
        statusTracker.invalidateAll();
    }

    private boolean shouldBypassStatusCache(World world) {
        UUID worldId = world.getUID();
        return activeSkipSessions.containsKey(worldId)
                || activeNightAccelerationSessions.containsKey(worldId)
                || sleepOverlayService.hasActiveOverlay(world);
    }

    private void cancelAndRemoveSession(UUID worldId, ActiveSkipSession session, boolean stopWorldOverlay) {
        if (session == null || !activeSkipSessions.remove(worldId, session)) {
            return;
        }
        if (!session.markCancelled()) {
            return;
        }

        session.cancelTasks();

        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            clearNightBehaviorState(worldId);
            sleepOverlayService.stopByWorldId(worldId);
            return;
        }

        restoreVanillaSleepSkip(world, session.previousSleepingPercentage());
        statusTracker.invalidate(world);
        transitionNightBehaviorState(world, NightBehaviorState.IDLE);
        if (stopWorldOverlay) {
            sleepOverlayService.stop(world);
        }
    }

    private void stopNightAcceleration(World world, boolean stopOverlay) {
        if (world != null) {
            stopNightAcceleration(world.getUID(), world, stopOverlay);
        }
    }

    private void stopAllNightAccelerationSessions(boolean stopOverlay) {
        for (var entry : List.copyOf(activeNightAccelerationSessions.entrySet())) {
            World world = Bukkit.getWorld(entry.getKey());
            stopNightAcceleration(entry.getKey(), world, stopOverlay);
        }
    }

    private void stopNightAcceleration(UUID worldId, World world, boolean stopOverlay) {
        currentSpeedMultipliers.remove(worldId);
        ActiveNightAccelerationSession session = activeNightAccelerationSessions.remove(worldId);
        if (session == null) {
            if (stopOverlay && world != null) {
                sleepOverlayService.stop(world);
            }
            return;
        }

        session.cancel();
        if (stopOverlay && world != null) {
            sleepOverlayService.stop(world);
        }
    }

    private void restoreAllVanillaSleepSkipSuppressions() {
        for (var entry : List.copyOf(suppressedVanillaSleepPercentages.entrySet())) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world != null) {
                restoreVanillaSleepSkip(world, entry.getValue());
            }
        }
        suppressedVanillaSleepPercentages.clear();
    }

    private void clearNightBehaviorState(UUID worldId) {
        if (worldId != null) {
            nightBehaviorStates.remove(worldId);
            currentSpeedMultipliers.remove(worldId);
        }
    }

    /**
     * Current night-skip behaviour state for placeholders: IDLE, ACCELERATING or FULL_SKIP.
     */
    public String getBehaviorStateName(World world) {
        if (world == null) {
            return NightBehaviorState.IDLE.name();
        }
        return nightBehaviorStates.getOrDefault(world.getUID(), NightBehaviorState.IDLE).name();
    }

    /**
     * Current night acceleration multiplier (1.0 when not accelerating).
     */
    public double getCurrentSpeedMultiplier(World world) {
        if (world == null) {
            return 1.0D;
        }
        return currentSpeedMultipliers.getOrDefault(world.getUID(), 1.0D);
    }

    private void transitionNightBehaviorState(World world, NightBehaviorState targetState) {
        if (world == null || targetState == null) {
            return;
        }

        UUID worldId = world.getUID();
        NightBehaviorState previousState = nightBehaviorStates.put(worldId, targetState);
        if (previousState == targetState) {
            return;
        }

        if (targetState == NightBehaviorState.ACCELERATING) {
            cleanupStaleFullSkipSession(world);
            return;
        }

        if (targetState == NightBehaviorState.FULL_SKIP) {
            stopNightAcceleration(worldId, world, false);
            return;
        }

        stopNightAcceleration(worldId, world, true);
    }

    private void cleanupStaleFullSkipSession(World world) {
        if (world == null) {
            return;
        }

        UUID worldId = world.getUID();
        ActiveSkipSession session = activeSkipSessions.get(worldId);
        if (session == null) {
            return;
        }

        if (!session.isCancelled() && !session.isCompleted()) {
            return;
        }

        if (!activeSkipSessions.remove(worldId, session)) {
            return;
        }

        session.cancelTasks();
        restoreVanillaSleepSkip(world, session.previousSleepingPercentage());
        statusTracker.invalidate(world);
        sleepOverlayService.stop(world);
    }

    private void runWorldState(World world, Runnable runnable) {
        if (plugin.isFolia()) {
            PlatformScheduler.runGlobal(plugin, runnable);
            return;
        }
        runnable.run();
    }

    private void logOverlayLifecycle(World world, String event, String details) {
        if (!plugin.getConfig().getBoolean("overlay.debug-transition-lifecycle", false)) {
            return;
        }

        String worldId = world != null ? world.getUID().toString() : "n/a";
        String worldName = world != null ? world.getName() : "n/a";
        String suffix = (details == null || details.isBlank()) ? "" : " " + details;
        Logger logger = plugin.getLogger();
        logger.info("[SleepListener] event=" + event + " worldId=" + worldId + " world=" + worldName + suffix);
    }

    public ForceSkipResult requestForceSkip(CommandSender sender, World world, boolean instant) {
        if (world == null || !ruleConfig.isOverworld(world)) {
            return ForceSkipResult.UNAVAILABLE;
        }

        SleepTimingRules.SleepTarget sleepTarget = getSleepTarget(world);
        if (sleepTarget == SleepTimingRules.SleepTarget.NONE) {
            return ForceSkipResult.UNAVAILABLE;
        }

        if (activeSkipSessions.containsKey(world.getUID())) {
            return ForceSkipResult.ALREADY_RUNNING;
        }

        statusTracker.cleanupSleepingPlayers();
        statusTracker.invalidate(world);

        if (instant) {
            runWorldState(world, () -> executeInstantSkip(world, sleepTarget));
            logForceSkip(sender, world, true);
            return ForceSkipResult.STARTED_INSTANT;
        }

        SleepState state = getSleepState(world);
        startSkip(
                world,
                sleepTarget,
                collectWorldRecipients(world),
                state.sleepingPlayers(),
                Math.max(1, state.requiredPlayers()),
                true
        );
        logForceSkip(sender, world, false);
        return ForceSkipResult.STARTED_SMOOTH;
    }

    private void executeInstantSkip(World world, SleepTimingRules.SleepTarget sleepTarget) {
        SleepState state = getSleepState(world);

        Set<UUID> recipients = collectWorldRecipients(world);
        SleepSkipStartEvent startEvent = new SleepSkipStartEvent(
                world, toApiType(sleepTarget), state.sleepingPlayers(), Math.max(1, state.requiredPlayers()), recipients, true);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            return;
        }
        Set<UUID> sleepers = collectWorldSleepers(world);

        if (sleepTarget == SleepTimingRules.SleepTarget.NIGHT) {
            long targetTime = Math.max(0L, Math.min(23999L, plugin.getConfig().getLong("settings.daytime-ticks", 0L)));
            world.setFullTime(resolveNextMorningFullTime(world.getFullTime(), world.getTime(), targetTime));
            resetPhantomTimerForWorld(world);
            SleepSkip.incrementNightsSkipped();
            if (dayCounterService.isEnabled()) {
                dayCounterService.scheduleSleepSkipMorningAnnouncement(world, state.overlayRecipients());
            }
        }

        clearWeatherIfNeeded(world, sleepTarget);
        wakeSleepingPlayers(world);
        removeSleepingPlayersForWorld(world);
        statusTracker.invalidate(world);
        sleepOverlayService.stop(world);
        sleepGuards.markSkipped(world);
        fireSkipComplete(world, sleepTarget, sleepers, recipients, true, true);
    }

    private Set<UUID> collectWorldRecipients(World world) {
        Set<UUID> recipients = new LinkedHashSet<>();
        for (Player player : world.getPlayers()) {
            recipients.add(player.getUniqueId());
        }
        return recipients;
    }

    /** Players currently tracked as sleeping in the given world (the natural reward targets). */
    private Set<UUID> collectWorldSleepers(World world) {
        Set<UUID> sleepers = new LinkedHashSet<>();
        for (UUID playerId : statusTracker.sleepingPlayersSnapshot()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && player.getWorld().equals(world)) {
                sleepers.add(playerId);
            }
        }
        return sleepers;
    }

    private static SleepSkipType toApiType(SleepTimingRules.SleepTarget sleepTarget) {
        return sleepTarget == SleepTimingRules.SleepTarget.WEATHER ? SleepSkipType.WEATHER : SleepSkipType.NIGHT;
    }

    private void fireSkipComplete(World world, SleepTimingRules.SleepTarget sleepTarget, Collection<UUID> sleepers, Collection<UUID> recipients, boolean instant, boolean forced) {
        Bukkit.getPluginManager().callEvent(new SleepSkipCompleteEvent(
                world, toApiType(sleepTarget), Set.copyOf(sleepers), Set.copyOf(recipients), instant, forced));
    }

    private void fireSkipCancel(World world, SleepTimingRules.SleepTarget sleepTarget, SleepSkipCancelEvent.Reason reason) {
        Bukkit.getPluginManager().callEvent(new SleepSkipCancelEvent(world, toApiType(sleepTarget), reason));
    }

    private void logForceSkip(CommandSender sender, World world, boolean instant) {
        String initiator = sender == null ? "UNKNOWN" : sender.getName();
        plugin.getLogger().info("[SleepSkipUltra] Force skip triggered by "
                + initiator
                + " in world '"
                + world.getName()
                + "' ("
                + (instant ? "instant" : "smooth")
                + ")");
    }

    public record SleepStatus(int activePlayers, int sleepingPlayers, int requiredPlayers) {
    }

    private record NightBehaviorPlan(NightBehaviorState behaviorState, NightAccelerationProfile accelerationProfile) {
    }

    public enum ForceSkipResult {
        STARTED_SMOOTH,
        STARTED_INSTANT,
        ALREADY_RUNNING,
        UNAVAILABLE
    }
}





