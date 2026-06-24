package me.Erotoro.sleepskip.api;

/**
 * API-stable classification of what a sleep skip targets.
 *
 * <p>This is the public counterpart of the plugin's internal sleep-target model and is
 * intentionally decoupled from it so the developer API stays stable across refactors.
 */
public enum SleepSkipType {

    /** Skipping a regular night to the configured morning time. */
    NIGHT,

    /** Sleeping through a daytime thunderstorm to clear the weather. */
    WEATHER
}
