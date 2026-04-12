package me.Erotoro.sleepskip.utils;

import me.Erotoro.sleepskip.util.PlatformScheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ActionBarTaskRegistry {
    private final ConcurrentHashMap<String, ActiveTask> activeTasks = new ConcurrentHashMap<>();

    void replaceTask(String key, Object taskIdentity, PlatformScheduler.TaskHandle handle) {
        ActiveTask previous = activeTasks.put(key, new ActiveTask(taskIdentity, handle));
        if (previous != null) {
            previous.handle().cancel();
        }
    }

    void completeIfCurrent(String key, Object taskIdentity) {
        activeTasks.computeIfPresent(key, (ignored, task) -> {
            if (task.identity() != taskIdentity) {
                return task;
            }
            task.handle().cancel();
            return null;
        });
    }

    void cancelKey(String key) {
        ActiveTask task = activeTasks.remove(key);
        if (task != null) {
            task.handle().cancel();
        }
    }

    void cancelAll() {
        for (String key : Set.copyOf(activeTasks.keySet())) {
            cancelKey(key);
        }
    }

    boolean isTaskActive(String key, Object taskIdentity) {
        ActiveTask task = activeTasks.get(key);
        return task != null && task.identity() == taskIdentity;
    }

    private record ActiveTask(Object identity, PlatformScheduler.TaskHandle handle) {
    }
}
