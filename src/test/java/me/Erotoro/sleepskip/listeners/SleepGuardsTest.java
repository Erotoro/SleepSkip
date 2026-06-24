package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SleepGuardsTest {

    private static SleepSkip pluginWith(FileConfiguration config) {
        SleepSkip plugin = mock(SleepSkip.class);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    private static World worldWithId(UUID id) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        return world;
    }

    @Test
    void defaultsBlockNothing() {
        SleepGuards guards = new SleepGuards(pluginWith(new YamlConfiguration()));
        World world = worldWithId(UUID.randomUUID());

        assertFalse(guards.isSkipBlocked(world, 1));
        assertFalse(guards.isOnCooldown(world));
    }

    @Test
    void minimumPlayersOnlineBlocksBelowThreshold() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.min-players-online", 3);
        SleepGuards guards = new SleepGuards(pluginWith(config));
        World world = worldWithId(UUID.randomUUID());

        assertTrue(guards.isSkipBlocked(world, 2));
        assertFalse(guards.isSkipBlocked(world, 3));
        assertFalse(guards.isSkipBlocked(world, 4));
    }

    @Test
    void cooldownBlocksUntilWindowElapses() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.skip-cooldown-seconds", 60);
        SleepGuards guards = new SleepGuards(pluginWith(config));
        World world = worldWithId(UUID.randomUUID());

        assertFalse(guards.isOnCooldown(world));
        guards.markSkipped(world);
        assertTrue(guards.isOnCooldown(world));
        assertTrue(guards.isSkipBlocked(world, 100));
    }

    @Test
    void cooldownDisabledNeverBlocks() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.skip-cooldown-seconds", 0);
        SleepGuards guards = new SleepGuards(pluginWith(config));
        World world = worldWithId(UUID.randomUUID());

        guards.markSkipped(world);
        assertFalse(guards.isOnCooldown(world));
    }

    @Test
    void cooldownIsTrackedPerWorld() {
        FileConfiguration config = new YamlConfiguration();
        config.set("limits.skip-cooldown-seconds", 60);
        SleepGuards guards = new SleepGuards(pluginWith(config));
        World skipped = worldWithId(UUID.randomUUID());
        World other = worldWithId(UUID.randomUUID());

        guards.markSkipped(skipped);

        assertTrue(guards.isOnCooldown(skipped));
        assertFalse(guards.isOnCooldown(other));
    }
}
