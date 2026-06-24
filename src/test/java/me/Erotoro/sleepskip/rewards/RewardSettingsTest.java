package me.Erotoro.sleepskip.rewards;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardSettingsTest {

    private static final Logger LOGGER = Logger.getLogger(RewardSettingsTest.class.getName());

    @Test
    void defaultsAreDisabledAndEmpty() {
        RewardSettings settings = RewardSettings.from(new YamlConfiguration(), LOGGER);

        assertFalse(settings.enabled());
        assertFalse(settings.hasAnyReward());
        assertEquals(0, settings.experience());
        assertTrue(settings.potionEffects().isEmpty());
        assertTrue(settings.commands().isEmpty());
        assertEquals("", settings.message());
    }

    @Test
    void disabledFactoryHasNoRewards() {
        RewardSettings settings = RewardSettings.disabled();

        assertFalse(settings.enabled());
        assertFalse(settings.hasAnyReward());
    }

    @Test
    void readsScalarRewardOptions() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.enabled", true);
        config.set("rewards.only-natural-skips", true);
        config.set("rewards.heal", true);
        config.set("rewards.feed", true);
        config.set("rewards.experience", 25);
        config.set("rewards.message", "  <green>Well rested!  ");

        RewardSettings settings = RewardSettings.from(config, LOGGER);

        assertTrue(settings.enabled());
        assertTrue(settings.onlyNaturalSkips());
        assertTrue(settings.healToFull());
        assertTrue(settings.feed());
        assertEquals(25, settings.experience());
        assertEquals("<green>Well rested!", settings.message());
        assertTrue(settings.hasAnyReward());
    }

    @Test
    void negativeExperienceIsClampedToZero() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.experience", -10);

        assertEquals(0, RewardSettings.from(config, LOGGER).experience());
    }

    @Test
    void commandsAreTrimmedSlashStrippedAndCompacted() {
        List<String> parsed = RewardSettings.parseCommands(List.of(
                "/give {player} minecraft:bread 4",
                "   ",
                "say good morning {player}",
                "  /xp add {player} 10  "
        ));

        assertEquals(List.of(
                "give {player} minecraft:bread 4",
                "say good morning {player}",
                "xp add {player} 10"
        ), parsed);
    }

    @Test
    void hasAnyRewardIsTrueWhenOnlyCommandsConfigured() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.commands", List.of("give {player} minecraft:bread 1"));

        RewardSettings settings = RewardSettings.from(config, LOGGER);

        assertTrue(settings.hasAnyReward());
        assertEquals(1, settings.commands().size());
    }
}
