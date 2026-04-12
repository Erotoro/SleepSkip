package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.services.PlayerEligibilityService;
import me.Erotoro.sleepskip.services.PlayerStateService;
import me.Erotoro.sleepskip.services.PlayerStateSnapshot;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SleepStatusTrackerTest {

    @Test
    void nonOverworldStateDoesNotBuildRecipientsAndReturnsNeutralStatus() {
        UUID worldId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        SleepSkip plugin = mock(SleepSkip.class);
        PlayerStateService playerStateService = mock(PlayerStateService.class);
        World world = mock(World.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.per-world", false);
        config.set("settings.ignore-afk", true);

        when(plugin.getConfig()).thenReturn(config);
        when(world.getUID()).thenReturn(worldId);

        SleepStatusTracker tracker = new SleepStatusTracker(plugin, playerStateService, new PlayerEligibilityService());

        SleepRuntimeSessions.SleepState state = tracker.getSleepState(world, false, true, 0L);

        assertTrue(state.recipients().isEmpty());
        assertTrue(state.overlayRecipients().isEmpty());
        assertEquals(0, state.sleepingPlayers());
        assertEquals(1, state.requiredPlayers());
        verify(playerStateService, never()).getSnapshots();
    }

    @Test
    void overworldStateBuildsRecipientsFromEligibleSleepingSnapshotsOnly() {
        UUID worldId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        SleepSkip plugin = mock(SleepSkip.class);
        PlayerStateService playerStateService = mock(PlayerStateService.class);
        World world = mock(World.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.per-world", false);
        config.set("settings.ignore-afk", true);
        config.set("settings.required-type", "percent");
        config.set("settings.required-value", 50D);

        when(plugin.getConfig()).thenReturn(config);
        when(world.getUID()).thenReturn(worldId);
        when(playerStateService.getSnapshots()).thenReturn(List.of(new PlayerStateSnapshot(
                playerId,
                worldId,
                true,
                false,
                false,
                false,
                false,
                false,
                false
        )));

        SleepStatusTracker tracker = new SleepStatusTracker(plugin, playerStateService, new PlayerEligibilityService());

        SleepRuntimeSessions.SleepState state = tracker.getSleepState(world, true, true, 0L);

        assertEquals(List.of(playerId), List.copyOf(state.recipients()));
        assertTrue(state.overlayRecipients().isEmpty());
        assertEquals(0, state.sleepingPlayers());
        assertEquals(1, state.requiredPlayers());

        tracker.markSleeping(playerId);
        state = tracker.getSleepState(world, true, true, 0L);

        assertEquals(List.of(playerId), List.copyOf(state.recipients()));
        assertEquals(List.of(playerId), List.copyOf(state.overlayRecipients()));
        assertEquals(1, state.sleepingPlayers());
        assertEquals(1, state.requiredPlayers());
        verify(playerStateService, atLeastOnce()).getSnapshots();
    }

    @Test
    void resetSleepingPlayersFromSnapshotsRestoresSleepCounters() {
        UUID worldId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        SleepSkip plugin = mock(SleepSkip.class);
        PlayerStateService playerStateService = mock(PlayerStateService.class);
        World world = mock(World.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.per-world", false);
        config.set("settings.ignore-afk", true);
        config.set("settings.required-type", "percent");
        config.set("settings.required-value", 50D);

        when(plugin.getConfig()).thenReturn(config);
        when(world.getUID()).thenReturn(worldId);
        when(playerStateService.getSnapshots()).thenReturn(List.of(new PlayerStateSnapshot(
                playerId,
                worldId,
                true,
                false,
                false,
                false,
                false,
                false,
                true
        )));

        SleepStatusTracker tracker = new SleepStatusTracker(plugin, playerStateService, new PlayerEligibilityService());

        SleepRuntimeSessions.SleepState beforeReset = tracker.getSleepState(world, true, true, 0L);
        assertEquals(0, beforeReset.sleepingPlayers());
        assertTrue(beforeReset.overlayRecipients().isEmpty());

        tracker.resetSleepingPlayersFromSnapshots();

        SleepRuntimeSessions.SleepState afterReset = tracker.getSleepState(world, true, true, 0L);
        assertEquals(1, afterReset.sleepingPlayers());
        assertEquals(List.of(playerId), List.copyOf(afterReset.overlayRecipients()));
    }
}
