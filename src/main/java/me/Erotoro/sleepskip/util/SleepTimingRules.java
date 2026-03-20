package me.Erotoro.sleepskip.util;

/**
 * Encapsulates vanilla-leaning decisions about when sleep should count as night skip or weather skip.
 */
public final class SleepTimingRules {

    private static final long WEATHER_WAKE_DELAY_TICKS = 20L;

    private SleepTimingRules() {
    }

    public static SleepTarget resolve(long worldTime, boolean hasStorm, boolean thundering, String weatherSleepMode) {
        if (isNight(worldTime)) {
            return SleepTarget.NIGHT;
        }

        if ("thunderstorm".equalsIgnoreCase(weatherSleepMode) && hasStorm && thundering) {
            return SleepTarget.WEATHER;
        }

        return SleepTarget.NONE;
    }

    public static boolean isNight(long worldTime) {
        return worldTime >= 12541L && worldTime <= 23458L;
    }

    public static boolean shouldAdvanceToNextMorning(SleepTarget sleepTarget) {
        return sleepTarget == SleepTarget.NIGHT;
    }

    public static boolean shouldClearWeather(SleepTarget sleepTarget, boolean skipRainEnabled) {
        return sleepTarget == SleepTarget.WEATHER || skipRainEnabled;
    }

    public static long getCompletionDelayTicks(SleepTarget sleepTarget, long nightTransitionTicks) {
        if (sleepTarget == SleepTarget.NIGHT) {
            return nightTransitionTicks + 10L;
        }
        return WEATHER_WAKE_DELAY_TICKS;
    }

    public enum SleepTarget {
        NONE,
        NIGHT,
        WEATHER
    }
}
