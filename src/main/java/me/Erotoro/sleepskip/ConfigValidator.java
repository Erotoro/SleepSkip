package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

/**
 * Normalizes config into a safe runtime shape.
 */
public final class ConfigValidator {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ru", "en", "ua");
    private static final Set<String> SUPPORTED_REQUIRED_TYPES = Set.of("fixed", "percent");
    private static final Set<String> SUPPORTED_WEATHER_SLEEP_MODES = Set.of("none", "thunderstorm");

    private ConfigValidator() {
    }

    public static void validate(SleepSkip plugin) {
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;

        String language = normalizedString(config, "settings.language", "ru");
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            plugin.getLogger().warning(plugin.tr(
                    "logs.invalid-language",
                    "Unsupported language in config. Falling back to ru."
            ));
            config.set("settings.language", "ru");
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
}
