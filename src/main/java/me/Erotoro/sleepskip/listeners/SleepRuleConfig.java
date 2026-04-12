package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.util.SleepTimingRules;
import org.bukkit.World;

final class SleepRuleConfig {

    private static final long DEFAULT_DAY_TRANSITION_TICKS = 60L;
    private static final String SLEEP_START_THRESHOLD_PERCENT_PATH = "sleep.start-threshold-percent";
    private static final String SLEEP_MAX_SPEED_MULTIPLIER_PATH = "sleep.max-speed-multiplier";
    private static final String SLEEP_UPDATE_INTERVAL_TICKS_PATH = "sleep.update-interval-ticks";
    private static final String LEGACY_START_THRESHOLD_PERCENT_PATH = "gradual-acceleration.start-threshold-percent";
    private static final String LEGACY_MAX_SPEED_MULTIPLIER_PATH = "gradual-acceleration.max-speed-multiplier";
    private static final String LEGACY_UPDATE_INTERVAL_TICKS_PATH = "gradual-acceleration.update-interval-ticks";

    private final SleepSkip plugin;

    SleepRuleConfig(SleepSkip plugin) {
        this.plugin = plugin;
    }

    boolean isOverworld(World world) {
        return world.getEnvironment() == World.Environment.NORMAL;
    }

    SleepTimingRules.SleepTarget getSleepTarget(World world) {
        return SleepTimingRules.resolve(world.getTime(), world.hasStorm(), world.isThundering(), getWeatherSleepMode());
    }

    boolean canForceSleepDuringThunderstorm(World world) {
        return getSleepTarget(world) == SleepTimingRules.SleepTarget.WEATHER;
    }

    long getTransitionDurationTicks() {
        return Math.max(20L, plugin.getConfig().getLong("settings.transition-duration-ticks", DEFAULT_DAY_TRANSITION_TICKS));
    }

    double getNightAccelerationStartThresholdRatio() {
        double percent = getDoubleFromUnifiedOrLegacy(
                SLEEP_START_THRESHOLD_PERCENT_PATH,
                LEGACY_START_THRESHOLD_PERCENT_PATH,
                50D
        );
        double clampedPercent = Math.max(0D, Math.min(100D, percent));
        return clampedPercent / 100D;
    }

    double getNightAccelerationMaxSpeedMultiplier() {
        return Math.max(1D, getDoubleFromUnifiedOrLegacy(
                SLEEP_MAX_SPEED_MULTIPLIER_PATH,
                LEGACY_MAX_SPEED_MULTIPLIER_PATH,
                12D
        ));
    }

    long getNightAccelerationUpdateIntervalTicks() {
        return Math.max(1L, getLongFromUnifiedOrLegacy(
                SLEEP_UPDATE_INTERVAL_TICKS_PATH,
                LEGACY_UPDATE_INTERVAL_TICKS_PATH,
                5L
        ));
    }

    long getStatusCacheTtlMs(long defaultTtlMs) {
        return Math.max(100L, plugin.getConfig().getLong("settings.status-cache-ttl-ms", defaultTtlMs));
    }

    String getWorldMessageKey(World world) {
        if (!plugin.getConfig().getBoolean("settings.per-world", false)) {
            return "sleep:global";
        }
        return "sleep:world:" + world.getUID();
    }

    private String getWeatherSleepMode() {
        Object raw = plugin.getConfig().get("settings.weather-sleep-mode");
        if (raw instanceof String configuredMode && !configuredMode.isBlank()) {
            String normalized = configuredMode.toLowerCase();
            return "rain".equals(normalized) ? "thunderstorm" : normalized;
        }
        return "thunderstorm";
    }

    private double getDoubleFromUnifiedOrLegacy(String primaryPath, String legacyPath, double fallback) {
        if (plugin.getConfig().contains(primaryPath)) {
            return plugin.getConfig().getDouble(primaryPath, fallback);
        }
        return plugin.getConfig().getDouble(legacyPath, fallback);
    }

    private long getLongFromUnifiedOrLegacy(String primaryPath, String legacyPath, long fallback) {
        if (plugin.getConfig().contains(primaryPath)) {
            return plugin.getConfig().getLong(primaryPath, fallback);
        }
        return plugin.getConfig().getLong(legacyPath, fallback);
    }
}
