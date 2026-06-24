package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SleepRuleConfigWorldFilterTest {

    private static SleepRuleConfig ruleConfigWith(FileConfiguration config) {
        SleepSkip plugin = mock(SleepSkip.class);
        when(plugin.getConfig()).thenReturn(config);
        return new SleepRuleConfig(plugin);
    }

    private static World named(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    @Test
    void allWorldsManagedByDefault() {
        SleepRuleConfig rules = ruleConfigWith(new YamlConfiguration());
        assertTrue(rules.isWorldManaged(named("world")));
    }

    @Test
    void blacklistExcludesWorldCaseInsensitively() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.world-blacklist", List.of("World_Nether"));
        SleepRuleConfig rules = ruleConfigWith(config);

        assertFalse(rules.isWorldManaged(named("world_nether")));
        assertTrue(rules.isWorldManaged(named("world")));
    }

    @Test
    void whitelistLimitsToListedWorlds() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.world-whitelist", List.of("survival"));
        SleepRuleConfig rules = ruleConfigWith(config);

        assertTrue(rules.isWorldManaged(named("Survival")));
        assertFalse(rules.isWorldManaged(named("creative")));
    }

    @Test
    void blacklistWinsOverWhitelist() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.world-whitelist", List.of("survival"));
        config.set("limits.world-blacklist", List.of("survival"));
        SleepRuleConfig rules = ruleConfigWith(config);

        assertFalse(rules.isWorldManaged(named("survival")));
    }
}
