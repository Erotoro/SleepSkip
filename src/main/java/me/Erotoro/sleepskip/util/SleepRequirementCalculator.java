package me.Erotoro.sleepskip.util;

/**
 * Converts config threshold settings into the concrete number of sleepers required right now.
 */
public final class SleepRequirementCalculator {

    private SleepRequirementCalculator() {
    }

    public static int calculate(String requiredType, double requiredValue, int activePlayers) {
        if ("fixed".equalsIgnoreCase(requiredType)) {
            return Math.max(1, (int) Math.round(requiredValue));
        }

        if (activePlayers <= 0) {
            return 1;
        }

        return Math.max(1, (int) Math.ceil(activePlayers * (requiredValue / 100D)));
    }
}
