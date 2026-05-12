package me.Erotoro.sleepskip.services;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import me.Erotoro.sleepskip.util.SleepTimingRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SleepOverlayService {

    private static final String MODE_TITLE = "title";
    private static final int PROGRESS_STEP_PERCENT = 5;
    private static final long TICK_NANOS = 50_000_000L;
    private static final long MIN_COMPLETION_HOLD_TICKS = 4L;
    private static final long MAX_COMPLETION_HOLD_TICKS = 20L;
    private static final int MAX_ACTIVE_FADE_IN_TICKS = 3;
    private static final int MIN_ACTIVE_STAY_TICKS = 36_000; // 30 minutes, effectively persistent while session is active.
    private static final String LOCALE_RU = "ru";
    private static final String LOCALE_UA = "ua";
    private static final String KEY_OVERLAY_NIGHT_TITLE = "messages.overlay-night-title";
    private static final String KEY_OVERLAY_NIGHT_SUBTITLE = "messages.overlay-night-subtitle";
    private static final String KEY_OVERLAY_WEATHER_TITLE = "messages.overlay-weather-title";
    private static final String KEY_OVERLAY_WEATHER_SUBTITLE = "messages.overlay-weather-subtitle";
    private static final String KEY_OVERLAY_TRANSITION_NIGHT_TITLE = "messages.overlay-transition-night-title";
    private static final String KEY_OVERLAY_TRANSITION_NIGHT_SUBTITLE = "messages.overlay-transition-night-subtitle";
    private static final String KEY_OVERLAY_TRANSITION_WEATHER_TITLE = "messages.overlay-transition-weather-title";
    private static final String KEY_OVERLAY_TRANSITION_WEATHER_SUBTITLE = "messages.overlay-transition-weather-subtitle";
    private static final String KEY_OVERLAY_ACCELERATION_NIGHT_TITLE = "messages.overlay-acceleration-night-title";
    private static final String KEY_OVERLAY_ACCELERATION_NIGHT_SUBTITLE = "messages.overlay-acceleration-night-subtitle";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final SleepSkip plugin;
    private final TitleSessionCoordinator titleSessionCoordinator;
    private final ConcurrentHashMap<String, OverlaySession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletionGraceState> completionGraceStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<GuardedPostCompletionTask>> postCompletionTasks = new ConcurrentHashMap<>();

    public SleepOverlayService(SleepSkip plugin) {
        this.plugin = plugin;
        this.titleSessionCoordinator = plugin.getTitleSessionCoordinator();
    }

    public void showStatus(
            World world,
            SleepTimingRules.SleepTarget sleepTarget,
            Collection<UUID> recipients,
            int sleeping,
            int needed
    ) {
        if (!isOverlayEnabled() || !plugin.getConfig().getBoolean("overlay.show-status-before-skip", true)) {
            stopAll();
            return;
        }
        if (sleeping <= 0 || recipients.isEmpty() || sleepTarget == SleepTimingRules.SleepTarget.NONE) {
            stop(world);
            return;
        }

        startSession(
                new OverlayDescriptor(
                        scope(world),
                        Set.copyOf(recipients),
                        OverlayPhase.STATUS,
                        templateFor(sleepTarget, OverlayPhase.STATUS),
                        sleeping,
                        needed,
                        Math.max(0, needed - sleeping),
                        0L,
                        "x1"
                )
        );
    }

    public void startTransition(
            World world,
            SleepTimingRules.SleepTarget sleepTarget,
            Collection<UUID> recipients,
            int sleeping,
            int needed,
            long transitionDurationTicks
    ) {
        if (!isOverlayEnabled() || !plugin.getConfig().getBoolean("overlay.show-progress-during-transition", true)) {
            stopAll();
            return;
        }
        if (recipients.isEmpty() || sleepTarget == SleepTimingRules.SleepTarget.NONE) {
            stop(world);
            return;
        }
        logTransitionLifecycle(
                "start_transition_request",
                scope(world),
                "target=" + sleepTarget
                        + ",sleeping=" + sleeping
                        + ",needed=" + needed
                        + ",transitionDurationTicks=" + Math.max(1L, transitionDurationTicks)
                        + ",recipients=" + recipients.size()
        );

        startSession(
                new OverlayDescriptor(
                        scope(world),
                        Set.copyOf(recipients),
                        OverlayPhase.TRANSITION,
                        templateFor(sleepTarget, OverlayPhase.TRANSITION),
                        sleeping,
                        needed,
                        0,
                        Math.max(1L, transitionDurationTicks),
                        "x1"
                )
        );
    }

    public void showAcceleration(
            World world,
            Collection<UUID> recipients,
            int sleeping,
            int needed,
            double speedMultiplier
    ) {
        if (!isOverlayEnabled() || !plugin.getConfig().getBoolean("overlay.show-status-before-skip", true)) {
            stopAll();
            return;
        }
        if (sleeping <= 0 || recipients.isEmpty()) {
            stop(world);
            return;
        }

        startSession(
                new OverlayDescriptor(
                        scope(world),
                        Set.copyOf(recipients),
                        OverlayPhase.ACCELERATION,
                        templateFor(SleepTimingRules.SleepTarget.NIGHT, OverlayPhase.ACCELERATION),
                        sleeping,
                        needed,
                        Math.max(0, needed - sleeping),
                        0L,
                        formatSpeedMultiplier(speedMultiplier)
                )
        );
    }

    public void completeTransition(World world) {
        completeTransition(scope(world).key());
    }

    public void runAfterCompletionGrace(World world, Runnable task) {
        if (world == null || task == null) {
            return;
        }

        String key = scope(world).key();
        synchronized (this) {
            CompletionGraceState completionGraceState = completionGraceStates.get(key);
            if (completionGraceState == null) {
                long expectedToken = titleSessionCoordinator.currentToken(key);
                PlatformScheduler.runGlobalDelayed(plugin, () -> runGuardedTask(key, expectedToken, task), 1L);
                return;
            }
            postCompletionTasks.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new GuardedPostCompletionTask(completionGraceState.token(), task));
        }
    }

    public void refreshRecipients(World world, Collection<UUID> recipients) {
        if (world == null) {
            return;
        }
        Set<UUID> normalizedRecipients = recipients == null ? Set.of() : Set.copyOf(recipients);
        refreshRecipients(scope(world).key(), normalizedRecipients);
    }

    public void stop(World world) {
        stop(scope(world).key());
    }

    public void stopByWorldId(UUID worldId) {
        if (worldId == null) {
            return;
        }

        for (var entry : Set.copyOf(sessions.entrySet())) {
            OverlaySession session = entry.getValue();
            if (session == null) {
                continue;
            }
            if (worldId.equals(session.descriptor().scope().worldId())) {
                forceStop(entry.getKey());
            }
        }

        for (var entry : Set.copyOf(completionGraceStates.entrySet())) {
            CompletionGraceState state = entry.getValue();
            if (state == null || state.scope() == null) {
                continue;
            }
            if (worldId.equals(state.scope().worldId())) {
                forceStop(entry.getKey());
            }
        }
    }

    public void stopAll() {
        Set<String> allKeys = new HashSet<>(sessions.keySet());
        allKeys.addAll(completionGraceStates.keySet());
        for (String key : allKeys) {
            forceStop(key);
        }
    }

    public boolean hasActiveOverlay(World world) {
        if (!isOverlayEnabled()) {
            if (!sessions.isEmpty()) {
                stopAll();
            }
            return false;
        }
        return world != null && sessions.containsKey(scope(world).key());
    }

    private synchronized void startSession(OverlayDescriptor descriptor) {
        CompletionGraceState completionHold = completionGraceStates.get(descriptor.scope().key());
        if (completionHold != null) {
            logTransitionLifecycle(
                    "session_start_blocked_by_completion_hold",
                    descriptor.scope(),
                    "incomingPhase=" + descriptor.phase() + ",holdExpiresAtNanos=" + completionHold.expiresAtNanos()
            );
            return;
        }
        OverlaySession existing = sessions.get(descriptor.scope().key());
        if (shouldIgnoreSessionStart(existing, descriptor)) {
            logTransitionLifecycle(
                    "session_start_ignored",
                    descriptor.scope(),
                    "activePhase=" + existing.descriptor().phase() + ",incomingPhase=" + descriptor.phase()
            );
            return;
        }
        if (existing != null && existing.descriptor().equals(descriptor)) {
            return;
        }
        long token = titleSessionCoordinator.claim(descriptor.scope().key());
        if (existing != null) {
            clearRecipientsNoLongerTargeted(existing.descriptor().recipients(), descriptor.recipients(), descriptor.scope(), token);
            stop(descriptor.scope().key(), false);
        }

        int intervalTicks = getUpdateIntervalTicks();
        OverlaySession session = new OverlaySession(descriptor, token, null, createTransitionProgressState(descriptor));

        PlatformScheduler.TaskHandle handle = PlatformScheduler.runGlobalAtFixedRate(plugin, () -> {
            OverlaySession current = sessions.get(descriptor.scope().key());
            if (current != session) {
                return;
            }
            OverlayDescriptor currentDescriptor = current.descriptor();

            int progress = currentDescriptor.phase() == OverlayPhase.TRANSITION
                    ? calculateTransitionProgress(current)
                    : 0;
            OverlayFrame frame = renderFrame(
                    currentDescriptor,
                    progress,
                    intervalTicks,
                    true
            );
            if (!session.shouldSend(frame)) {
                return;
            }

            if (currentDescriptor.phase() == OverlayPhase.TRANSITION) {
                logTransitionLifecycle(
                        "progress_frame",
                        currentDescriptor.scope(),
                        "progress=" + frame.progress()
                                + ",elapsedTicks=" + session.currentElapsedTicks()
                                + ",transitionDurationTicks=" + currentDescriptor.transitionDurationTicks()
                );
            }
            sendTitle(currentDescriptor.scope(), session.token(), frame);
        }, 1L, intervalTicks);

        session.setHandle(handle);
        sessions.put(descriptor.scope().key(), session);
        pushFrameIfChanged(session, descriptor, intervalTicks, true);
    }

    private synchronized void stop(String key) {
        stop(key, true);
    }

    private synchronized void forceStop(String key) {
        stop(key, true, true);
    }

    private synchronized void refreshRecipients(String key, Set<UUID> recipients) {
        OverlaySession session = sessions.get(key);
        if (session == null) {
            return;
        }

        OverlayDescriptor currentDescriptor = session.descriptor();
        if (currentDescriptor.recipients().equals(recipients)) {
            return;
        }

        clearRecipientsNoLongerTargeted(currentDescriptor.recipients(), recipients, currentDescriptor.scope(), session.token());
        if (recipients.isEmpty()) {
            stop(key, false);
            return;
        }

        session.updateDescriptor(currentDescriptor.withRecipients(recipients));
    }

    private synchronized void completeTransition(String key) {
        OverlaySession session = sessions.remove(key);
        if (session == null) {
            logTransitionLifecycle("complete_transition_no_session", scopeByKey(key), "");
            return;
        }

        session.cancel();
        OverlayDescriptor descriptor = session.descriptor();
        if (descriptor.phase() != OverlayPhase.TRANSITION) {
            logTransitionLifecycle(
                    "complete_transition_non_transition_phase",
                    descriptor.scope(),
                    "phase=" + descriptor.phase()
            );
            clearTitles(session);
            return;
        }

        OverlayFrame finalFrame = renderFrame(descriptor, 100, getUpdateIntervalTicks(), false);
        // Must bypass dedupe here: final completion frame is semantically required and should always be reasserted.
        sendTitle(descriptor.scope(), session.token(), finalFrame);
        long completionHoldTicks = resolveCompletionHoldTicks(getUpdateIntervalTicks());
        armCompletionGrace(
                descriptor.scope().key(),
                descriptor.scope(),
                session.token(),
                finalFrame.recipients(),
                completionHoldTicks
        );
        logTransitionLifecycle(
                "complete_transition",
                descriptor.scope(),
                "finalFrameSent=true,progress=100"
                        + ",holdTicks=" + completionHoldTicks
                        + ",recipients=" + finalFrame.recipients().size()
        );
    }

    private synchronized void stop(String key, boolean clearTitles) {
        stop(key, clearTitles, false);
    }

    private synchronized void stop(String key, boolean clearTitles, boolean ignoreCompletionGrace) {
        OverlaySession session = sessions.remove(key);
        if (session != null) {
            session.cancel();
        }
        if (!clearTitles) {
            return;
        }

        CompletionGraceState completionGraceState = completionGraceStates.get(key);
        if (!ignoreCompletionGrace && completionGraceState != null) {
            logTransitionLifecycle(
                    "stop_deferred_for_completion_grace",
                    completionGraceState.scope(),
                    "expiresAtNanos=" + completionGraceState.expiresAtNanos()
            );
            return;
        }

        if (completionGraceState != null) {
            completionGraceStates.remove(key, completionGraceState);
            clearRecipients(completionGraceState.scope(), completionGraceState.token(), completionGraceState.recipients());
            runPostCompletionTasks(key);
            logTransitionLifecycle("force_stop_cleared_completion_grace", completionGraceState.scope(), "");
            return;
        }

        if (session != null) {
            clearTitles(session);
            return;
        }

        logTransitionLifecycle("stop_without_session", scopeByKey(key), "");
    }

    private void armCompletionGrace(String key, OverlayScope scope, long token, Set<UUID> recipients, long holdTicks) {
        if (holdTicks <= 0L || recipients == null || recipients.isEmpty()) {
            completionGraceStates.remove(key);
            return;
        }

        long startedAtNanos = System.nanoTime();
        long holdNanos = holdTicks * TICK_NANOS;
        CompletionGraceState state = new CompletionGraceState(scope, token, Set.copyOf(recipients), startedAtNanos + holdNanos, startedAtNanos);
        completionGraceStates.put(key, state);

        PlatformScheduler.runGlobalDelayed(plugin, () -> clearAfterCompletionGrace(key, state.issuedAtNanos()), holdTicks);
    }

    private void clearAfterCompletionGrace(String key, long issuedAtNanos) {
        CompletionGraceState state;
        synchronized (this) {
            CompletionGraceState current = completionGraceStates.get(key);
            if (current == null || current.issuedAtNanos() != issuedAtNanos) {
                return;
            }
            completionGraceStates.remove(key);
            if (sessions.containsKey(key)) {
                return;
            }
            state = current;
        }

        logTransitionLifecycle(
                "completion_grace_elapsed_stop",
                state.scope(),
                "recipients=" + state.recipients().size()
        );
        boolean hasQueuedPostTasks = hasQueuedPostCompletionTasks(key);
        if (shouldClearRecipientsBeforePostCompletionTasks(hasQueuedPostTasks)) {
            clearRecipients(state.scope(), state.token(), state.recipients());
        }
        runPostCompletionTasks(key);
    }

    private void runPostCompletionTasks(String key) {
        List<GuardedPostCompletionTask> tasks = postCompletionTasks.remove(key);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        for (GuardedPostCompletionTask task : tasks) {
            PlatformScheduler.runGlobalDelayed(plugin, () -> runGuardedTask(key, task.expectedToken(), task.task()), 1L);
        }
    }

    private void runGuardedTask(String scopeKey, long expectedToken, Runnable task) {
        if (task == null) {
            return;
        }
        if (expectedToken > 0L && !titleSessionCoordinator.isCurrent(scopeKey, expectedToken)) {
            return;
        }
        task.run();
    }

    private boolean hasQueuedPostCompletionTasks(String key) {
        List<GuardedPostCompletionTask> tasks = postCompletionTasks.get(key);
        return tasks != null && !tasks.isEmpty();
    }

    static boolean shouldClearRecipientsBeforePostCompletionTasks(boolean hasQueuedPostTasks) {
        return !hasQueuedPostTasks;
    }

    private void clearTitles(OverlaySession session) {
        clearRecipients(session.descriptor().scope(), session.token(), session.descriptor().recipients());
    }

    private OverlayFrame renderFrame(OverlayDescriptor descriptor, int progress, int intervalTicks, boolean activelyMaintained) {
        String title = replacePlaceholders(
                plugin.tr(descriptor.template().titleKey(), descriptor.template().fallbackTitle()),
                descriptor.sleeping(),
                descriptor.needed(),
                descriptor.remaining(),
                progress,
                descriptor.speedMultiplier()
        );
        String subtitle = replacePlaceholders(
                plugin.tr(descriptor.template().subtitleKey(), descriptor.template().fallbackSubtitle()),
                descriptor.sleeping(),
                descriptor.needed(),
                descriptor.remaining(),
                progress,
                descriptor.speedMultiplier()
        );
        OverlayTimings timings = resolveTimings(intervalTicks, activelyMaintained);
        return new OverlayFrame(title, subtitle, descriptor.recipients(), progress, timings.fadeInTicks(), timings.stayTicks(), timings.fadeOutTicks());
    }

    private boolean shouldIgnoreSessionStart(OverlaySession existing, OverlayDescriptor incoming) {
        if (existing == null) {
            return false;
        }

        OverlayPhase activePhase = existing.descriptor().phase();
        OverlayPhase incomingPhase = incoming.phase();
        return activePhase == OverlayPhase.TRANSITION && incomingPhase != OverlayPhase.TRANSITION;
    }

    private void pushFrameIfChanged(
            OverlaySession session,
            OverlayDescriptor descriptor,
            int intervalTicks,
            boolean activelyMaintained
    ) {
        if (session == null || descriptor == null) {
            return;
        }

        int progress = descriptor.phase() == OverlayPhase.TRANSITION
                ? calculateTransitionProgress(session)
                : 0;
        OverlayFrame frame = renderFrame(descriptor, progress, intervalTicks, activelyMaintained);
        if (!session.shouldSend(frame)) {
            return;
        }
        sendTitle(descriptor.scope(), session.token(), frame);
    }

    private void sendTitle(OverlayScope scope, long token, OverlayFrame frame) {
        Component title = MINI_MESSAGE.deserialize(frame.title());
        Component subtitle = MINI_MESSAGE.deserialize(frame.subtitle());
        Title renderedTitle = Title.title(title, subtitle, Title.Times.times(
                ticksToDuration(frame.fadeInTicks()),
                ticksToDuration(frame.stayTicks()),
                ticksToDuration(frame.fadeOutTicks())
        ));

        for (UUID recipient : frame.recipients()) {
            Player player = Bukkit.getPlayer(recipient);
            if (player == null || !player.isOnline()) {
                continue;
            }
            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (titleSessionCoordinator.isCurrent(scope.key(), token) && isValidRecipient(player, scope)) {
                    player.showTitle(renderedTitle);
                }
            });
        }
    }

    private boolean isValidRecipient(Player player, OverlayScope scope) {
        return player.isOnline() && (!scope.perWorld() || player.getWorld().getUID().equals(scope.worldId()));
    }

    private void clearRecipientsNoLongerTargeted(Set<UUID> previousRecipients, Set<UUID> nextRecipients, OverlayScope scope, long token) {
        if (previousRecipients.isEmpty()) {
            return;
        }

        Set<UUID> staleRecipients = new HashSet<>(previousRecipients);
        staleRecipients.removeAll(nextRecipients);
        clearRecipients(scope, token, staleRecipients);
    }

    private void clearRecipients(OverlayScope scope, long token, Collection<UUID> recipients) {
        for (UUID recipient : recipients) {
            Player player = Bukkit.getPlayer(recipient);
            if (player == null || !player.isOnline()) {
                continue;
            }
            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (titleSessionCoordinator.isCurrent(scope.key(), token) && isValidRecipient(player, scope)) {
                    player.clearTitle();
                }
            });
        }
    }

    private String replacePlaceholders(String message, int sleeping, int needed, int remaining, int progress, String speedMultiplier) {
        return message
                .replace("{sleeping}", String.valueOf(sleeping))
                .replace("{needed}", String.valueOf(needed))
                .replace("{remaining}", String.valueOf(Math.max(0, remaining)))
                .replace("{progress}", String.valueOf(Math.max(0, Math.min(100, progress))))
                .replace("{speed}", speedMultiplier != null ? speedMultiplier : "x1");
    }

    private int calculateTransitionProgress(OverlaySession session) {
        TransitionProgressState transitionProgress = session.transitionProgressState();
        if (transitionProgress == null) {
            return 0;
        }

        long elapsedNanos = Math.max(0L, System.nanoTime() - transitionProgress.startedAtNanos());
        long elapsedTicks = elapsedNanos / TICK_NANOS;
        return calculateVisibleTransitionProgressPercent(elapsedTicks, transitionProgress.transitionDurationTicks());
    }

    static int calculateVisibleTransitionProgressPercent(long elapsedTransitionTicks, long plannedTransitionTicks) {
        return calculateTransitionProgressPercent(elapsedTransitionTicks, plannedTransitionTicks);
    }

    static int calculateTransitionProgressPercent(long elapsedTransitionTicks, long plannedTransitionTicks) {
        if (plannedTransitionTicks <= 0L) {
            return 100;
        }

        double ratio = Math.max(0D, Math.min(1D, elapsedTransitionTicks / (double) plannedTransitionTicks));
        int rawProgress = (int) Math.round(ratio * 100D);
        return normalizeProgressPercent(rawProgress);
    }

    static int normalizeProgressPercent(int rawProgress) {
        int clampedProgress = Math.max(0, Math.min(100, rawProgress));
        int roundedProgress = (int) (Math.round(clampedProgress / (double) PROGRESS_STEP_PERCENT) * PROGRESS_STEP_PERCENT);
        return Math.max(0, Math.min(100, roundedProgress));
    }

    static long resolveCompletionHoldTicks(int updateIntervalTicks) {
        int normalizedInterval = Math.max(1, updateIntervalTicks);
        long computed = normalizedInterval + 2L;
        return Math.max(MIN_COMPLETION_HOLD_TICKS, Math.min(MAX_COMPLETION_HOLD_TICKS, computed));
    }

    private TransitionProgressState createTransitionProgressState(OverlayDescriptor descriptor) {
        if (descriptor.phase() != OverlayPhase.TRANSITION) {
            return null;
        }
        return new TransitionProgressState(System.nanoTime(), Math.max(1L, descriptor.transitionDurationTicks()));
    }

    private boolean isOverlayEnabled() {
        return plugin.getConfig().getBoolean("settings.overlay-enabled", true)
                && plugin.getConfig().getBoolean("overlay.enabled", true)
                && MODE_TITLE.equalsIgnoreCase(plugin.getConfig().getString("overlay.mode", MODE_TITLE));
    }

    private int getUpdateIntervalTicks() {
        return Math.max(5, Math.min(10, plugin.getConfig().getInt("overlay.update-interval-ticks", 8)));
    }

    private OverlayTimings resolveTimings(int updateIntervalTicks, boolean activelyMaintained) {
        int fadeInTicks = (int) Math.max(0L, plugin.getConfig().getLong("overlay.fade-in-ticks", 2L));
        int fadeOutTicks = (int) Math.max(0L, plugin.getConfig().getLong("overlay.fade-out-ticks", 4L));
        int configuredStayTicks = (int) Math.max(1L, plugin.getConfig().getLong("overlay.stay-ticks", 14L));
        if (!activelyMaintained) {
            int minimumStayTicks = updateIntervalTicks + fadeOutTicks + 1;
            int stayTicks = Math.max(configuredStayTicks, minimumStayTicks);
            return new OverlayTimings(fadeInTicks, stayTicks, fadeOutTicks);
        }

        // Continuous profile for active overlay sessions:
        // - short fade in for smooth text changes
        // - no fade out between frames to prevent blinking
        // - long stay to keep frame visible even when no meaningful update occurs
        int activeFadeInTicks = Math.min(fadeInTicks, MAX_ACTIVE_FADE_IN_TICKS);
        int activeFadeOutTicks = 0;
        int activeMinimumStayTicks = Math.max(MIN_ACTIVE_STAY_TICKS, updateIntervalTicks + 2);
        int activeStayTicks = Math.max(configuredStayTicks, activeMinimumStayTicks);
        return new OverlayTimings(activeFadeInTicks, activeStayTicks, activeFadeOutTicks);
    }

    private Duration ticksToDuration(long ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }

    private OverlayScope scope(World world) {
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);
        String key = perWorld ? "sleep:world:" + world.getUID() : "sleep:global";
        return new OverlayScope(key, world.getUID(), perWorld);
    }

    private OverlayScope scopeByKey(String key) {
        if (key == null) {
            return new OverlayScope("unknown", null, false);
        }

        OverlaySession session = sessions.get(key);
        if (session != null) {
            return session.descriptor().scope();
        }

        CompletionGraceState completionGraceState = completionGraceStates.get(key);
        if (completionGraceState != null) {
            return completionGraceState.scope();
        }

        if ("sleep:global".equals(key)) {
            return new OverlayScope(key, null, false);
        }
        return new OverlayScope(key, null, true);
    }

    private void logTransitionLifecycle(String event, OverlayScope scope, String details) {
        if (!plugin.getConfig().getBoolean("overlay.debug-transition-lifecycle", false)) {
            return;
        }

        String worldId = scope != null && scope.worldId() != null ? scope.worldId().toString() : "n/a";
        String worldName = resolveWorldName(scope);
        String suffix = (details == null || details.isBlank()) ? "" : " " + details;
        Logger logger = plugin.getLogger();
        logger.info("[SleepOverlay] event=" + event
                + " worldId=" + worldId
                + " world=" + worldName
                + " scope=" + (scope != null ? scope.key() : "n/a")
                + suffix);
    }

    private String resolveWorldName(OverlayScope scope) {
        if (scope == null || scope.worldId() == null) {
            return "n/a";
        }
        World world = Bukkit.getWorld(scope.worldId());
        return world != null ? world.getName() : "unloaded";
    }

    private OverlayTemplate templateFor(SleepTimingRules.SleepTarget sleepTarget, OverlayPhase phase) {
        if (phase == OverlayPhase.STATUS) {
            return sleepTarget == SleepTimingRules.SleepTarget.NIGHT
                    ? new OverlayTemplate(
                    KEY_OVERLAY_NIGHT_TITLE,
                    KEY_OVERLAY_NIGHT_SUBTITLE,
                    localizedFallback("Night", "\u041d\u043e\u0447\u044c", "\u041d\u0456\u0447"),
                    localizedFallback(
                            "{sleeping}/{needed} asleep \u00b7 {remaining} more needed",
                            "{sleeping}/{needed} \u0441\u043f\u044f\u0442 \u00b7 \u043d\u0443\u0436\u043d\u043e \u0435\u0449\u0451 {remaining}",
                            "{sleeping}/{needed} \u0441\u043f\u043b\u044f\u0442\u044c \u00b7 \u043f\u043e\u0442\u0440\u0456\u0431\u043d\u043e \u0449\u0435 {remaining}"
                    )
            )
                    : new OverlayTemplate(
                    KEY_OVERLAY_WEATHER_TITLE,
                    KEY_OVERLAY_WEATHER_SUBTITLE,
                    localizedFallback("Storm", "\u0413\u0440\u043e\u0437\u0430", "\u0413\u0440\u043e\u0437\u0430"),
                    localizedFallback(
                            "{sleeping}/{needed} resting \u00b7 {remaining} more needed",
                            "{sleeping}/{needed} \u043e\u0442\u0434\u044b\u0445\u0430\u044e\u0442 \u00b7 \u043d\u0443\u0436\u043d\u043e \u0435\u0449\u0451 {remaining}",
                            "{sleeping}/{needed} \u0432\u0456\u0434\u043f\u043e\u0447\u0438\u0432\u0430\u044e\u0442\u044c \u00b7 \u043f\u043e\u0442\u0440\u0456\u0431\u043d\u043e \u0449\u0435 {remaining}"
                    )
            );
        }

        if (phase == OverlayPhase.ACCELERATION) {
            return new OverlayTemplate(
                    KEY_OVERLAY_ACCELERATION_NIGHT_TITLE,
                    KEY_OVERLAY_ACCELERATION_NIGHT_SUBTITLE,
                    localizedFallback("Night accelerating", "\u0423\u0441\u043a\u043e\u0440\u0435\u043d\u0438\u0435 \u043d\u043e\u0447\u0438", "\u041f\u0440\u0438\u0441\u043a\u043e\u0440\u0435\u043d\u043d\u044f \u043d\u043e\u0447\u0456"),
                    localizedFallback(
                            "{sleeping}/{needed} asleep \u00b7 speed {speed}",
                            "{sleeping}/{needed} \u0441\u043f\u044f\u0442 \u00b7 \u0441\u043a\u043e\u0440\u043e\u0441\u0442\u044c {speed}",
                            "{sleeping}/{needed} \u0441\u043f\u043b\u044f\u0442\u044c \u00b7 \u0448\u0432\u0438\u0434\u043a\u0456\u0441\u0442\u044c {speed}"
                    )
            );
        }

        return sleepTarget == SleepTimingRules.SleepTarget.NIGHT
                ? new OverlayTemplate(
                KEY_OVERLAY_TRANSITION_NIGHT_TITLE,
                KEY_OVERLAY_TRANSITION_NIGHT_SUBTITLE,
                localizedFallback("Dawn", "\u0420\u0430\u0441\u0441\u0432\u0435\u0442", "\u0421\u0432\u0456\u0442\u0430\u043d\u043e\u043a"),
                localizedFallback(
                        "Night passing \u00b7 {progress}%",
                        "\u041d\u043e\u0447\u044c \u043f\u0440\u043e\u0445\u043e\u0434\u0438\u0442 \u00b7 {progress}%",
                        "\u041d\u0456\u0447 \u043c\u0438\u043d\u0430\u0454 \u00b7 {progress}%"
                )
        )
                : new OverlayTemplate(
                KEY_OVERLAY_TRANSITION_WEATHER_TITLE,
                KEY_OVERLAY_TRANSITION_WEATHER_SUBTITLE,
                localizedFallback("Clear skies", "\u042f\u0441\u043d\u043e\u0435 \u043d\u0435\u0431\u043e", "\u042f\u0441\u043d\u0435 \u043d\u0435\u0431\u043e"),
                localizedFallback(
                        "Storm passing \u00b7 {progress}%",
                        "\u0413\u0440\u043e\u0437\u0430 \u0441\u0442\u0438\u0445\u0430\u0435\u0442 \u00b7 {progress}%",
                        "\u0413\u0440\u043e\u0437\u0430 \u0441\u0442\u0438\u0445\u0430\u0454 \u00b7 {progress}%"
                )
        );
    }

    private String localizedFallback(String english, String russian, String ukrainian) {
        String language = plugin.getLocaleManager().getCurrentLanguage();
        if (LOCALE_RU.equalsIgnoreCase(language)) {
            return russian;
        }
        if (LOCALE_UA.equalsIgnoreCase(language)) {
            return ukrainian;
        }
        return english;
    }

    private String formatSpeedMultiplier(double speedMultiplier) {
        double normalized = Math.max(1D, speedMultiplier);
        double rounded = Math.round(normalized * 2D) / 2D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.00001D) {
            return "x" + (int) Math.rint(rounded);
        }
        return "x" + rounded;
    }

    private record OverlayDescriptor(
            OverlayScope scope,
            Set<UUID> recipients,
            OverlayPhase phase,
            OverlayTemplate template,
            int sleeping,
            int needed,
            int remaining,
            long transitionDurationTicks,
            String speedMultiplier
    ) {
        private OverlayDescriptor withRecipients(Set<UUID> recipients) {
            return new OverlayDescriptor(scope, recipients, phase, template, sleeping, needed, remaining, transitionDurationTicks, speedMultiplier);
        }
    }

    private record OverlayFrame(
            String title,
            String subtitle,
            Set<UUID> recipients,
            int progress,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks
    ) {
    }

    private record OverlayTimings(int fadeInTicks, int stayTicks, int fadeOutTicks) {
    }

    private record OverlayScope(String key, UUID worldId, boolean perWorld) {
    }

    private record OverlayTemplate(String titleKey, String subtitleKey, String fallbackTitle, String fallbackSubtitle) {
    }

    private record TransitionProgressState(long startedAtNanos, long transitionDurationTicks) {
    }

    private record CompletionGraceState(OverlayScope scope, long token, Set<UUID> recipients, long expiresAtNanos, long issuedAtNanos) {
    }

    private record GuardedPostCompletionTask(long expectedToken, Runnable task) {
    }

    private enum OverlayPhase {
        STATUS,
        TRANSITION,
        ACCELERATION
    }

    private static final class OverlaySession {
        private volatile OverlayDescriptor descriptor;
        private final long token;
        private final TransitionProgressState transitionProgressState;
        private PlatformScheduler.TaskHandle handle;
        private OverlayFrame lastFrame;

        private OverlaySession(
                OverlayDescriptor descriptor,
                long token,
                PlatformScheduler.TaskHandle handle,
                TransitionProgressState transitionProgressState
        ) {
            this.descriptor = descriptor;
            this.token = token;
            this.handle = handle;
            this.transitionProgressState = transitionProgressState;
        }

        private OverlayDescriptor descriptor() {
            return descriptor;
        }

        private void updateDescriptor(OverlayDescriptor descriptor) {
            this.descriptor = descriptor;
            this.lastFrame = null;
        }

        private TransitionProgressState transitionProgressState() {
            return transitionProgressState;
        }

        private long token() {
            return token;
        }

        private void setHandle(PlatformScheduler.TaskHandle handle) {
            this.handle = handle;
        }

        private long currentElapsedTicks() {
            if (transitionProgressState == null) {
                return 0L;
            }
            long elapsedNanos = Math.max(0L, System.nanoTime() - transitionProgressState.startedAtNanos());
            return elapsedNanos / TICK_NANOS;
        }

        private boolean shouldSend(OverlayFrame frame) {
            if (frame.equals(lastFrame)) {
                return false;
            }
            lastFrame = frame;
            return true;
        }

        private void cancel() {
            if (handle != null) {
                handle.cancel();
            }
        }
    }
}
