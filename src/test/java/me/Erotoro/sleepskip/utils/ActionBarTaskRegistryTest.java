package me.Erotoro.sleepskip.utils;

import me.Erotoro.sleepskip.util.PlatformScheduler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBarTaskRegistryTest {

    @Test
    void finishingOldTaskMustNotCancelNewTaskForSameKey() {
        ActionBarTaskRegistry registry = new ActionBarTaskRegistry();
        String key = "ab-race-old-does-not-kill-new";

        CountingHandle oldHandle = new CountingHandle();
        Object oldIdentity = new Object();
        registry.replaceTask(key, oldIdentity, oldHandle);

        CountingHandle newHandle = new CountingHandle();
        Object newIdentity = new Object();
        registry.replaceTask(key, newIdentity, newHandle);

        assertEquals(1, oldHandle.cancelCount);
        assertFalse(registry.isTaskActive(key, oldIdentity));
        assertTrue(registry.isTaskActive(key, newIdentity));
        assertEquals(0, newHandle.cancelCount);

        registry.completeIfCurrent(key, oldIdentity);

        assertTrue(registry.isTaskActive(key, newIdentity));
        assertEquals(0, newHandle.cancelCount);

        registry.cancelKey(key);
    }

    @Test
    void finishingCurrentTaskMustRemoveAndCancelIt() {
        ActionBarTaskRegistry registry = new ActionBarTaskRegistry();
        String key = "ab-current-finishes";

        CountingHandle currentHandle = new CountingHandle();
        Object currentIdentity = new Object();
        registry.replaceTask(key, currentIdentity, currentHandle);

        registry.completeIfCurrent(key, currentIdentity);

        assertFalse(registry.isTaskActive(key, currentIdentity));
        assertEquals(1, currentHandle.cancelCount);
    }

    private static final class CountingHandle implements PlatformScheduler.TaskHandle {
        private int cancelCount;

        @Override
        public void cancel() {
            cancelCount++;
        }
    }
}
