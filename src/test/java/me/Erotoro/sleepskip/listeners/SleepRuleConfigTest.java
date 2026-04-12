package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SleepRuleConfigTest {

    @Test
    void unifiedSleepSettingsTakePriorityOverLegacyKeys() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("sleep.start-threshold-percent", 42D);
        config.set("sleep.max-speed-multiplier", 9.5D);
        config.set("sleep.update-interval-ticks", 4L);
        config.set("gradual-acceleration.start-threshold-percent", 66D);
        config.set("gradual-acceleration.max-speed-multiplier", 3D);
        config.set("gradual-acceleration.update-interval-ticks", 20L);

        when(plugin.getConfig()).thenReturn(config);

        SleepRuleConfig ruleConfig = new SleepRuleConfig(plugin);

        assertEquals(0.42D, ruleConfig.getNightAccelerationStartThresholdRatio(), 0.000001D);
        assertEquals(9.5D, ruleConfig.getNightAccelerationMaxSpeedMultiplier(), 0.000001D);
        assertEquals(4L, ruleConfig.getNightAccelerationUpdateIntervalTicks());
    }

    @Test
    void legacyGradualAccelerationKeysStillWorkAsFallback() {
        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("gradual-acceleration.start-threshold-percent", 60D);
        config.set("gradual-acceleration.max-speed-multiplier", 8D);
        config.set("gradual-acceleration.update-interval-ticks", 6L);

        when(plugin.getConfig()).thenReturn(config);

        SleepRuleConfig ruleConfig = new SleepRuleConfig(plugin);

        assertEquals(0.60D, ruleConfig.getNightAccelerationStartThresholdRatio(), 0.000001D);
        assertEquals(8D, ruleConfig.getNightAccelerationMaxSpeedMultiplier(), 0.000001D);
        assertEquals(6L, ruleConfig.getNightAccelerationUpdateIntervalTicks());
    }
}
