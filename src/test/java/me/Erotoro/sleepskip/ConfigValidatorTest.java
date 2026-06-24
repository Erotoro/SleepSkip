package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigValidatorTest {

    @Test
    void validateKeepsExpandedSupportedLanguageCodes() {
        List<String> languages = List.of("it", "cs", "hi", "tr", "id", "fi");

        for (String language : languages) {
            SleepSkip plugin = mock(SleepSkip.class);
            FileConfiguration config = new YamlConfiguration();
            config.set("settings.language", language);

            when(plugin.getConfig()).thenReturn(config);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("test-config-validator"));
            when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

            ConfigValidator.validate(plugin);

            assertEquals(language, config.getString("settings.language"));
        }
    }

    @Test
    void validateMigratesThresholdSkipModeToUnifiedSleepConfig() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.night-behavior", "threshold_skip");

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-config-validator"));
        when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        ConfigValidator.validate(plugin);

        assertEquals(100D, config.getDouble("sleep.start-threshold-percent"));
        assertEquals(12D, config.getDouble("sleep.max-speed-multiplier"));
        assertEquals(5L, config.getLong("sleep.update-interval-ticks"));
        assertNull(config.get("settings.night-behavior"));
        assertNull(config.get("gradual-acceleration"));
        verify(plugin, times(1)).saveConfig();
        verify(plugin, times(1)).reloadConfig();
    }

    @Test
    void validateMigratesGradualAccelerationToUnifiedSleepConfig() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.night-behavior", "gradual_acceleration");
        config.set("gradual-acceleration.enabled", true);
        config.set("gradual-acceleration.start-threshold-percent", 35D);
        config.set("gradual-acceleration.max-speed-multiplier", 7.5D);
        config.set("gradual-acceleration.update-interval-ticks", 3L);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-config-validator"));
        when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        ConfigValidator.validate(plugin);

        assertEquals(35D, config.getDouble("sleep.start-threshold-percent"));
        assertEquals(7.5D, config.getDouble("sleep.max-speed-multiplier"));
        assertEquals(3L, config.getLong("sleep.update-interval-ticks"));
        assertNull(config.get("settings.night-behavior"));
        assertNull(config.get("gradual-acceleration"));
        verify(plugin, times(1)).saveConfig();
        verify(plugin, times(1)).reloadConfig();
    }

    @Test
    void validateMigratesLegacyOverlayEnabledFalseIntoOverlayEnabled() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.overlay-enabled", false);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-config-validator"));
        when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        ConfigValidator.validate(plugin);

        assertNull(config.get("settings.overlay-enabled"));
        assertFalse(config.getBoolean("overlay.enabled"));
    }

    @Test
    void validateDropsLegacyOverlayEnabledTrueWithoutForcingOverlayOff() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.overlay-enabled", true);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-config-validator"));
        when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        ConfigValidator.validate(plugin);

        assertNull(config.get("settings.overlay-enabled"));
        assertTrue(config.getBoolean("overlay.enabled", true));
    }

    @Test
    void validateNormalizesInvalidOverlayModeAndBossBarStyle() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("overlay.mode", "hologram");
        config.set("overlay.bossbar.style", "rainbow");
        config.set("overlay.bossbar.night-color", "turquoise");

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-config-validator"));
        when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        ConfigValidator.validate(plugin);

        assertEquals("both", config.getString("overlay.mode"));
        assertEquals("PROGRESS", config.getString("overlay.bossbar.style"));
        assertEquals("BLUE", config.getString("overlay.bossbar.night-color"));
    }
}
