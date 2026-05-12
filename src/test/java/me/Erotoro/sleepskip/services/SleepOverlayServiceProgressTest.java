package me.Erotoro.sleepskip.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepOverlayServiceProgressTest {

    @Test
    void transitionProgressUsesLocalElapsedTicksRatio() {
        assertEquals(75, SleepOverlayService.calculateTransitionProgressPercent(15L, 20L));
    }

    @Test
    void transitionProgressClampsToHundredWhenElapsedExceedsPlan() {
        assertEquals(100, SleepOverlayService.calculateTransitionProgressPercent(200L, 20L));
    }

    @Test
    void transitionProgressClampsToZeroForNegativeElapsed() {
        assertEquals(0, SleepOverlayService.calculateTransitionProgressPercent(-5L, 20L));
    }

    @Test
    void transitionProgressTreatsInvalidPlanAsComplete() {
        assertEquals(100, SleepOverlayService.calculateTransitionProgressPercent(0L, 0L));
    }

    @Test
    void normalizeProgressRoundsToConfiguredStep() {
        assertEquals(75, SleepOverlayService.normalizeProgressPercent(73));
        assertEquals(80, SleepOverlayService.normalizeProgressPercent(78));
    }

    @Test
    void completionHoldTicksUseIntervalWithSafetyBounds() {
        assertEquals(4L, SleepOverlayService.resolveCompletionHoldTicks(1));
        assertEquals(10L, SleepOverlayService.resolveCompletionHoldTicks(8));
        assertEquals(20L, SleepOverlayService.resolveCompletionHoldTicks(50));
    }

    @Test
    void visibleTransitionProgressCanReachHundredBeforeCompletionSignal() {
        assertEquals(100, SleepOverlayService.calculateVisibleTransitionProgressPercent(60L, 60L));
    }

    @Test
    void completionGraceKeepsCurrentTitleWhenMorningAnnouncementIsQueued() {
        assertFalse(SleepOverlayService.shouldClearRecipientsBeforePostCompletionTasks(true));
    }

    @Test
    void completionGraceClearsRecipientsWhenNoMorningAnnouncementIsQueued() {
        assertTrue(SleepOverlayService.shouldClearRecipientsBeforePostCompletionTasks(false));
    }
}
