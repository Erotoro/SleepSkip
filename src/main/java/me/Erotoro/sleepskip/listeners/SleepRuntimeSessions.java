package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.util.PlatformScheduler;
import me.Erotoro.sleepskip.util.SleepTimingRules;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

final class SleepRuntimeSessions {

    static final PlatformScheduler.TaskHandle NO_OP_TASK = () -> { };

    private SleepRuntimeSessions() {
    }

    record SleepState(
            Collection<UUID> recipients,
            Collection<UUID> overlayRecipients,
            int sleepingPlayers,
            int requiredPlayers
    ) {
    }

    record CachedSleepStatus(SleepListener.SleepStatus status, long cachedAtMs) {
    }

    record NightAccelerationProfile(double speedMultiplier) {
    }

    enum NightBehaviorState {
        IDLE,
        ACCELERATING,
        FULL_SKIP
    }

    static final class ActiveNightAccelerationSession {
        private final PlatformScheduler.TaskHandle taskHandle;
        private final long updateIntervalTicks;
        private double pendingExtraTicks;

        ActiveNightAccelerationSession(PlatformScheduler.TaskHandle taskHandle, long updateIntervalTicks) {
            this.taskHandle = taskHandle != null ? taskHandle : NO_OP_TASK;
            this.updateIntervalTicks = updateIntervalTicks;
        }

        long updateIntervalTicks() {
            return updateIntervalTicks;
        }

        void cancel() {
            taskHandle.cancel();
        }

        synchronized long consumeWholeExtraTicks(double speedMultiplier) {
            double additionalPerTick = Math.max(0D, speedMultiplier - 1D);
            double exactExtraTicks = (additionalPerTick * updateIntervalTicks) + pendingExtraTicks;
            long wholeExtraTicks = (long) Math.floor(exactExtraTicks);
            pendingExtraTicks = Math.max(0D, exactExtraTicks - wholeExtraTicks);
            return Math.max(0L, wholeExtraTicks);
        }
    }

    static final class ActiveSkipSession {
        private final UUID worldId;
        private final SleepTimingRules.SleepTarget sleepTarget;
        private final Integer previousSleepingPercentage;
        private final long transitionDurationTicks;
        private final long completionDelayTicks;
        private final long startedDayIndex;
        private final boolean forced;
        private volatile Set<UUID> recipients;
        private volatile Set<UUID> sleepers;
        private volatile PlatformScheduler.TaskHandle finishTaskHandle = NO_OP_TASK;
        private volatile PlatformScheduler.TaskHandle transitionTaskHandle = NO_OP_TASK;
        private volatile PlatformScheduler.TaskHandle validationTaskHandle = NO_OP_TASK;
        private volatile boolean cancelled;
        private volatile boolean completed;
        private volatile boolean committed;

        ActiveSkipSession(
                UUID worldId,
                SleepTimingRules.SleepTarget sleepTarget,
                Collection<UUID> recipients,
                Collection<UUID> sleepers,
                Integer previousSleepingPercentage,
                long transitionDurationTicks,
                long completionDelayTicks,
                long startedDayIndex,
                boolean forced
        ) {
            this.worldId = worldId;
            this.sleepTarget = sleepTarget;
            this.recipients = Set.copyOf(recipients);
            this.sleepers = Set.copyOf(sleepers);
            this.previousSleepingPercentage = previousSleepingPercentage;
            this.transitionDurationTicks = transitionDurationTicks;
            this.completionDelayTicks = completionDelayTicks;
            this.startedDayIndex = startedDayIndex;
            this.forced = forced;
        }

        UUID worldId() {
            return worldId;
        }

        SleepTimingRules.SleepTarget sleepTarget() {
            return sleepTarget;
        }

        Set<UUID> recipients() {
            return recipients;
        }

        Set<UUID> sleepers() {
            return sleepers;
        }

        void updateSleepers(Collection<UUID> sleepers) {
            this.sleepers = Set.copyOf(sleepers);
        }

        void updateRecipients(Collection<UUID> recipients) {
            this.recipients = Set.copyOf(recipients);
        }

        Integer previousSleepingPercentage() {
            return previousSleepingPercentage;
        }

        long transitionDurationTicks() {
            return transitionDurationTicks;
        }

        long completionDelayTicks() {
            return completionDelayTicks;
        }

        long startedDayIndex() {
            return startedDayIndex;
        }

        boolean forced() {
            return forced;
        }

        void setFinishTaskHandle(PlatformScheduler.TaskHandle finishTaskHandle) {
            this.finishTaskHandle = finishTaskHandle != null ? finishTaskHandle : NO_OP_TASK;
        }

        void setTransitionTaskHandle(PlatformScheduler.TaskHandle transitionTaskHandle) {
            this.transitionTaskHandle = transitionTaskHandle != null ? transitionTaskHandle : NO_OP_TASK;
        }

        void setValidationTaskHandle(PlatformScheduler.TaskHandle validationTaskHandle) {
            this.validationTaskHandle = validationTaskHandle != null ? validationTaskHandle : NO_OP_TASK;
        }

        boolean markCancelled() {
            if (cancelled || completed) {
                return false;
            }
            cancelled = true;
            return true;
        }

        boolean markCompleted() {
            if (cancelled || completed) {
                return false;
            }
            completed = true;
            return true;
        }

        boolean isCancelled() {
            return cancelled;
        }

        boolean isCompleted() {
            return completed;
        }

        boolean markCommitted() {
            if (cancelled || completed || committed) {
                return false;
            }
            committed = true;
            return true;
        }

        boolean isCommitted() {
            return committed;
        }

        void cancelTasks() {
            finishTaskHandle.cancel();
            transitionTaskHandle.cancel();
            validationTaskHandle.cancel();
        }
    }

    static final class DelegatingTaskHandle implements PlatformScheduler.TaskHandle {
        private volatile PlatformScheduler.TaskHandle delegate;
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
            PlatformScheduler.TaskHandle currentDelegate = delegate;
            if (currentDelegate != null) {
                currentDelegate.cancel();
            }
        }

        void setDelegate(PlatformScheduler.TaskHandle delegate) {
            this.delegate = delegate;
            if (cancelled && delegate != null) {
                delegate.cancel();
            }
        }
    }
}
