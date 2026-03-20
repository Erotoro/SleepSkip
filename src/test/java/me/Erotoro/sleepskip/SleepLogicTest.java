package me.Erotoro.sleepskip;

import me.Erotoro.sleepskip.util.SleepRequirementCalculator;
import me.Erotoro.sleepskip.util.SleepTimingRules;
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
}
