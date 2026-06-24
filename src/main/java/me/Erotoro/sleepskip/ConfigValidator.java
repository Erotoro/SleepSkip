package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

/**
 * Normalizes config into a safe runtime shape.
 */
public final class ConfigValidator {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "ru", "en", "ua", "de", "es", "fr", "pl", "pt", "zh", "it", "cs", "hi", "tr", "id", "fi"
    );
    private static final Set<String> SUPPORTED_REQUIRED_TYPES = Set.of("fixed", "percent");
    private static final Set<String> SUPPORTED_WEATHER_SLEEP_MODES = Set.of("none", "thunderstorm");
    private static final Set<String> SUPPORTED_DAY_COUNTER_ANIMATION_MODES = Set.of("static", "typewriter");
    private static final Set<String> SUPPORTED_OVERLAY_MODES = Set.of("title", "bossbar", "both");
    private static final Set<String> SUPPORTED_BOSSBAR_STYLES = Set.of(
            "progress", "notched_6", "notched_10", "notched_12", "notched_20"
    );
    private static final Set<String> SUPPORTED_BOSSBAR_COLORS = Set.of(
            "pink", "blue", "red", "green", "yellow", "purple", "white"
    );
    private static final String LEGACY_THRESHOLD_SKIP_MODE = "threshold_skip";
    private static final String UNIFIED_START_THRESHOLD_PERCENT_PATH = "sleep.start-threshold-percent";
    private static final String UNIFIED_MAX_SPEED_MULTIPLIER_PATH = "sleep.max-speed-multiplier";
    private static final String UNIFIED_UPDATE_INTERVAL_TICKS_PATH = "sleep.update-interval-ticks";
    private static final String LEGACY_START_THRESHOLD_PERCENT_PATH = "gradual-acceleration.start-threshold-percent";
    private static final String LEGACY_MAX_SPEED_MULTIPLIER_PATH = "gradual-acceleration.max-speed-multiplier";
    private static final String LEGACY_UPDATE_INTERVAL_TICKS_PATH = "gradual-acceleration.update-interval-ticks";
    private static final String LEGACY_ENABLED_PATH = "gradual-acceleration.enabled";

    private ConfigValidator() {
    }

    public static void validate(SleepSkip plugin) {
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;

        String language = normalizedString(config, "settings.language", "en");
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-language",
                    "Unsupported language in config. Falling back to en."
            ));
            config.set("settings.language", "en");
            changed = true;
        }

        String requiredType = normalizedString(config, "settings.required-type", "percent");
        if (!SUPPORTED_REQUIRED_TYPES.contains(requiredType)) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-required-type",
                    "Invalid required-type in config. Falling back to percent."
            ));
            requiredType = "percent";
            config.set("settings.required-type", requiredType);
            changed = true;
        }

        String weatherSleepMode = normalizedString(config, "settings.weather-sleep-mode", "thunderstorm");
        // Keep older configs working while preserving vanilla-like rules.
        if ("rain".equals(weatherSleepMode)) {
            plugin.getLogger().warning("weather-sleep-mode: rain is deprecated and was mapped to thunderstorm for vanilla-like behavior.");
            weatherSleepMode = "thunderstorm";
            config.set("settings.weather-sleep-mode", weatherSleepMode);
            changed = true;
        }
        if (!SUPPORTED_WEATHER_SLEEP_MODES.contains(weatherSleepMode)) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-weather-sleep-mode",
                    "Invalid weather-sleep-mode. Falling back to thunderstorm."
            ));
            config.set("settings.weather-sleep-mode", "thunderstorm");
            changed = true;
        }

        if (migrateLegacyNightBehaviorConfig(plugin, config)) {
            changed = true;
        }

        if (migrateLegacyOverlayEnabled(plugin, config)) {
            changed = true;
        }

        String overlayMode = normalizedString(config, "overlay.mode", "both");
        if (!SUPPORTED_OVERLAY_MODES.contains(overlayMode)) {
            plugin.getLogger().warning("overlay.mode must be one of: title, bossbar, both. Falling back to both.");
            config.set("overlay.mode", "both");
            changed = true;
        }

        String bossBarStyle = normalizedString(config, "overlay.bossbar.style", "progress");
        if (!SUPPORTED_BOSSBAR_STYLES.contains(bossBarStyle)) {
            plugin.getLogger().warning("overlay.bossbar.style is invalid. Falling back to PROGRESS.");
            config.set("overlay.bossbar.style", "PROGRESS");
            changed = true;
        }

        for (String colorPath : new String[]{
                "overlay.bossbar.night-color",
                "overlay.bossbar.weather-color",
                "overlay.bossbar.transition-color",
                "overlay.bossbar.acceleration-color"
        }) {
            if (!config.contains(colorPath)) {
                continue;
            }
            String color = normalizedString(config, colorPath, "blue");
            if (!SUPPORTED_BOSSBAR_COLORS.contains(color)) {
                plugin.getLogger().warning(colorPath + " is invalid. Falling back to BLUE.");
                config.set(colorPath, "BLUE");
                changed = true;
            }
        }

        double startThresholdPercent = config.getDouble(UNIFIED_START_THRESHOLD_PERCENT_PATH, 50D);
        double normalizedStartThresholdPercent = Math.max(0D, Math.min(100D, startThresholdPercent));
        if (Double.compare(startThresholdPercent, normalizedStartThresholdPercent) != 0) {
            plugin.getLogger().warning("sleep.start-threshold-percent must be between 0 and 100.");
            config.set(UNIFIED_START_THRESHOLD_PERCENT_PATH, normalizedStartThresholdPercent);
            changed = true;
        }

        double maxSpeedMultiplier = config.getDouble(UNIFIED_MAX_SPEED_MULTIPLIER_PATH, 12D);
        double normalizedMaxSpeedMultiplier = Math.max(1D, maxSpeedMultiplier);
        if (Double.compare(maxSpeedMultiplier, normalizedMaxSpeedMultiplier) != 0) {
            plugin.getLogger().warning("sleep.max-speed-multiplier must be at least 1.");
            config.set(UNIFIED_MAX_SPEED_MULTIPLIER_PATH, normalizedMaxSpeedMultiplier);
            changed = true;
        }

        long updateIntervalTicks = config.getLong(UNIFIED_UPDATE_INTERVAL_TICKS_PATH, 5L);
        long normalizedUpdateIntervalTicks = Math.max(1L, updateIntervalTicks);
        if (updateIntervalTicks != normalizedUpdateIntervalTicks) {
            plugin.getLogger().warning("sleep.update-interval-ticks must be at least 1.");
            config.set(UNIFIED_UPDATE_INTERVAL_TICKS_PATH, normalizedUpdateIntervalTicks);
            changed = true;
        }

        double requiredValue = config.getDouble("settings.required-value", 50D);
        if ("fixed".equals(requiredType)) {
            int normalized = Math.max(1, (int) Math.round(requiredValue));
            if (requiredValue != normalized) {
                plugin.getLogger().warning(plugin.tr(
                        "logs.invalid-required-fixed",
                        "Fixed required-value must be at least 1."
                ));
                config.set("settings.required-value", normalized);
                changed = true;
            }
        } else {
            double normalized = Math.max(1D, Math.min(100D, requiredValue));
            if (Double.compare(requiredValue, normalized) != 0) {
                plugin.getLogger().warning(plugin.tr(
                        "logs.invalid-required-percent",
                        "Percent required-value must be between 1 and 100."
                ));
                config.set("settings.required-value", normalized);
                changed = true;
            }
        }

        long transitionTicks = config.getLong("settings.transition-duration-ticks", 60L);
        long normalizedTransitionTicks = Math.max(20L, Math.min(400L, transitionTicks));
        if (transitionTicks != normalizedTransitionTicks) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-transition-duration",
                    "transition-duration-ticks must be between 20 and 400."
            ));
            config.set("settings.transition-duration-ticks", normalizedTransitionTicks);
            changed = true;
        }

        int actionBarDuration = config.getInt("settings.actionbar-duration", 5);
        int normalizedActionBarDuration = Math.max(1, Math.min(30, actionBarDuration));
        if (actionBarDuration != normalizedActionBarDuration) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-actionbar-duration",
                    "actionbar-duration must be between 1 and 30."
            ));
            config.set("settings.actionbar-duration", normalizedActionBarDuration);
            changed = true;
        }

        long afkTimeout = config.getLong("settings.afk-timeout", 300L);
        if (afkTimeout < 0L) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-afk-timeout",
                    "afk-timeout must not be negative. Falling back to 300."
            ));
            config.set("settings.afk-timeout", 300L);
            changed = true;
        }

        int daytimeTicks = config.getInt("settings.daytime-ticks", 0);
        int normalizedDaytimeTicks = Math.max(0, Math.min(23999, daytimeTicks));
        if (daytimeTicks != normalizedDaytimeTicks) {
            plugin.getLogger().warning("settings.daytime-ticks must be between 0 and 23999.");
            config.set("settings.daytime-ticks", normalizedDaytimeTicks);
            changed = true;
        }

        long dayCounterStayTicks = config.getLong("day-counter.stay-ticks", 50L);
        if (dayCounterStayTicks < 1L) {
            plugin.getLogger().warning("day-counter.stay-ticks must be at least 1.");
            config.set("day-counter.stay-ticks", 50L);
            changed = true;
        }

        String animationMode = normalizedString(config, "day-counter.animation.mode", "typewriter");
        if (!SUPPORTED_DAY_COUNTER_ANIMATION_MODES.contains(animationMode)) {
            plugin.getLogger().warning("day-counter.animation.mode must be one of: static, typewriter.");
            config.set("day-counter.animation.mode", "typewriter");
            changed = true;
        }

        long stepIntervalTicks = config.getLong("day-counter.animation.step-interval-ticks", 2L);
        if (stepIntervalTicks < 1L) {
            plugin.getLogger().warning("day-counter.animation.step-interval-ticks must be at least 1.");
            config.set("day-counter.animation.step-interval-ticks", 2L);
            changed = true;
        }

        long finalHoldTicks = config.getLong("day-counter.animation.final-hold-ticks", 30L);
        if (finalHoldTicks < 1L) {
            plugin.getLogger().warning("day-counter.animation.final-hold-ticks must be at least 1.");
            config.set("day-counter.animation.final-hold-ticks", 30L);
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
            plugin.reloadConfig();
        }
    }

    private static String normalizedString(FileConfiguration config, String path, String fallback) {
        Object raw = config.get(path);
        if (raw instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.toLowerCase();
            }
        }
        return fallback.toLowerCase();
    }

    private static boolean migrateLegacyOverlayEnabled(SleepSkip plugin, FileConfiguration config) {
        if (!config.contains("settings.overlay-enabled")) {
            return false;
        }

        // Pre-unification builds had a second master switch under settings. Preserve an explicit
        // opt-out, then drop the duplicate so overlay.enabled is the single source of truth.
        boolean legacyEnabled = config.getBoolean("settings.overlay-enabled", true);
        if (!legacyEnabled) {
            config.set("overlay.enabled", false);
        }
        config.set("settings.overlay-enabled", null);
        plugin.getLogger().warning(
                "settings.overlay-enabled is deprecated. Migrated to overlay.enabled (single overlay master switch)."
        );
        return true;
    }

    private static boolean migrateLegacyNightBehaviorConfig(SleepSkip plugin, FileConfiguration config) {
        boolean changed = false;

        boolean hasUnifiedStartThreshold = config.contains(UNIFIED_START_THRESHOLD_PERCENT_PATH);
        boolean hasUnifiedMaxSpeed = config.contains(UNIFIED_MAX_SPEED_MULTIPLIER_PATH);
        boolean hasUnifiedUpdateInterval = config.contains(UNIFIED_UPDATE_INTERVAL_TICKS_PATH);

        String legacyMode = normalizedString(config, "settings.night-behavior", LEGACY_THRESHOLD_SKIP_MODE);
        boolean legacyAccelerationEnabled = config.getBoolean(LEGACY_ENABLED_PATH, true);
        boolean legacyConfigPresent = config.contains("settings.night-behavior")
                || config.contains("gradual-acceleration");

        if (!hasUnifiedStartThreshold) {
            double migratedStartThreshold;
            if (LEGACY_THRESHOLD_SKIP_MODE.equals(legacyMode) || !legacyAccelerationEnabled) {
                migratedStartThreshold = 100D;
            } else {
                migratedStartThreshold = config.getDouble(LEGACY_START_THRESHOLD_PERCENT_PATH, 50D);
            }
            config.set(UNIFIED_START_THRESHOLD_PERCENT_PATH, migratedStartThreshold);
            changed = true;
        }

        if (!hasUnifiedMaxSpeed) {
            config.set(UNIFIED_MAX_SPEED_MULTIPLIER_PATH, config.getDouble(LEGACY_MAX_SPEED_MULTIPLIER_PATH, 12D));
            changed = true;
        }

        if (!hasUnifiedUpdateInterval) {
            config.set(UNIFIED_UPDATE_INTERVAL_TICKS_PATH, config.getLong(LEGACY_UPDATE_INTERVAL_TICKS_PATH, 5L));
            changed = true;
        }

        if (legacyConfigPresent) {
            plugin.getLogger().warning(
                    "settings.night-behavior and gradual-acceleration.* are deprecated. Migrated to sleep.* unified model."
            );
            config.set("settings.night-behavior", null);
            config.set("gradual-acceleration", null);
            changed = true;
        }

        return changed;
    }
}
