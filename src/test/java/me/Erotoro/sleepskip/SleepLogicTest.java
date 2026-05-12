package me.Erotoro.sleepskip;

import me.Erotoro.sleepskip.services.DayCounterService;
import me.Erotoro.sleepskip.util.SleepRequirementCalculator;
import me.Erotoro.sleepskip.util.SleepTimingRules;
import me.Erotoro.sleepskip.listeners.SleepListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Small regression suite for the pure calculation/rule helpers that drive sleep behavior.
 */
class SleepLogicTest {

    @Test
    void percentCalculationRoundsUp() {
        assertEquals(2, SleepRequirementCalculator.calculate("percent", 50D, 3));
    }

    @Test
    void fixedCalculationKeepsAtLeastOne() {
        assertEquals(1, SleepRequirementCalculator.calculate("fixed", 0D, 5));
    }

    @Test
    void hundredPercentRequiresAllActivePlayers() {
        assertEquals(4, SleepRequirementCalculator.calculate("percent", 100D, 4));
    }

    @Test
    void nightAlwaysUsesNightSleepTarget() {
        assertEquals(
                SleepTimingRules.SleepTarget.NIGHT,
                SleepTimingRules.resolve(13000L, true, true, "thunderstorm")
        );
    }

    @Test
    void daytimeThunderstormAllowsWeatherSleep() {
        assertEquals(
                SleepTimingRules.SleepTarget.WEATHER,
                SleepTimingRules.resolve(6000L, true, true, "thunderstorm")
        );
    }

    @Test
    void daytimeRainDoesNotAllowSleep() {
        assertEquals(
                SleepTimingRules.SleepTarget.NONE,
                SleepTimingRules.resolve(6000L, true, false, "thunderstorm")
        );
    }

    @Test
    void weatherSleepModeNoneDisablesDaytimeStormSleep() {
        assertEquals(
                SleepTimingRules.SleepTarget.NONE,
                SleepTimingRules.resolve(6000L, true, true, "none")
        );
    }

    @Test
    void onlyNightSleepAdvancesToNextMorning() {
        assertEquals(true, SleepTimingRules.shouldAdvanceToNextMorning(SleepTimingRules.SleepTarget.NIGHT));
        assertEquals(false, SleepTimingRules.shouldAdvanceToNextMorning(SleepTimingRules.SleepTarget.WEATHER));
    }

    @Test
    void weatherSleepAlwaysClearsWeather() {
        assertEquals(true, SleepTimingRules.shouldClearWeather(SleepTimingRules.SleepTarget.WEATHER, false));
    }

    @Test
    void nightSleepRespectsSkipRainSetting() {
        assertEquals(true, SleepTimingRules.shouldClearWeather(SleepTimingRules.SleepTarget.NIGHT, true));
        assertEquals(false, SleepTimingRules.shouldClearWeather(SleepTimingRules.SleepTarget.NIGHT, false));
    }

    @Test
    void displayDayUsesActualFullTimeCycles() {
        assertEquals(0, DayCounterService.computeDisplayDay(0L));
        assertEquals(0, DayCounterService.computeDisplayDay(23999L));
        assertEquals(1, DayCounterService.computeDisplayDay(24000L));
        assertEquals(2, DayCounterService.computeDisplayDay(48000L));
    }

    @Test
    void nextMorningFullTimeUsesConfiguredDaytimeTicksForStandardNightSkip() {
        assertEquals(
                30000L,
                SleepListener.resolveNextMorningFullTime(20000L, 20000L % 24000L, 6000L)
        );
    }

    @Test
    void nextMorningFullTimeRollsIntoNextDayWhenConfiguredMorningTimeIsEarlierThanCurrentTime() {
        assertEquals(
                24000L,
                SleepListener.resolveNextMorningFullTime(23000L, 23000L % 24000L, 0L)
        );
    }

    @Test
    void configuredMorningTimeAfterSmoothSkipStaysInCurrentReachedDay() {
        assertEquals(
                30000L,
                SleepListener.resolveConfiguredMorningFullTimeAfterSkip(24000L, 0L, 6000L)
        );
    }

    @Test
    void configuredMorningTimeAfterSkipDoesNotAddExtraDayWhenAlreadyAtDawn() {
        assertEquals(
                24000L,
                SleepListener.resolveConfiguredMorningFullTimeAfterSkip(24000L, 0L, 0L)
        );
    }

    @Test
    void smoothSkipDoesNotAdvanceAnotherDayIfTransitionStartsAfterNightAlreadyEnded() {
        assertEquals(
                24050L,
                SleepListener.resolveSmoothSkipTargetFullTime(24050L, 50L)
        );
    }

    @Test
    void vanillaSleepSkipShouldStaySuppressedWhileNightSleepIsActive() {
        assertEquals(
                true,
                SleepListener.shouldKeepVanillaSleepSkipSuppressedForTests(
                        SleepTimingRules.SleepTarget.NIGHT,
                        false,
                        1
                )
        );
    }

    @Test
    void vanillaSleepSkipShouldBeReleasedWhenNightSleepEndsWithoutActiveSkip() {
        assertEquals(
                false,
                SleepListener.shouldKeepVanillaSleepSkipSuppressedForTests(
                        SleepTimingRules.SleepTarget.NONE,
                        false,
                        0
                )
        );
    }

    @Test
    void pluginShouldNotAdvanceTimeAgainIfDayAlreadyAdvancedSinceSkipStarted() {
        assertEquals(
                true,
                SleepListener.hasDayAdvancedSinceSkipStartedForTests(10L, 11L)
        );
    }

    @Test
    void pluginMayAdvanceTimeWhenStillInSameStartedDay() {
        assertEquals(
                false,
                SleepListener.hasDayAdvancedSinceSkipStartedForTests(10L, 10L)
        );
    }

    @Test
    void forcedWeatherSkipBypassesSleepingValidation() {
        assertEquals(
                true,
                SleepListener.isSkipSessionStillValidForTests(
                        SleepTimingRules.SleepTarget.WEATHER,
                        true,
                        true,
                        0,
                        1,
                        SleepTimingRules.SleepTarget.WEATHER
                )
        );
    }

    @Test
    void nonForcedWeatherSkipStillRequiresEnoughSleepers() {
        assertEquals(
                false,
                SleepListener.isSkipSessionStillValidForTests(
                        SleepTimingRules.SleepTarget.WEATHER,
                        false,
                        true,
                        0,
                        1,
                        SleepTimingRules.SleepTarget.WEATHER
                )
        );
    }
}
