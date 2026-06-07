package me.Erotoro.sleepskip.placeholders;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.listeners.SleepListener;
import me.Erotoro.sleepskip.services.DayCounterService;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SleepSkipPlaceholderExpansionTest {

    @Test
    void globalOfflineModeResolvesGlobalWorldEvenWhenPerWorldEnabled() {
        SleepSkip plugin = mock(SleepSkip.class);
        SleepListener listener = mock(SleepListener.class);
        DayCounterService dayCounterService = mock(DayCounterService.class);
        Server server = mock(Server.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);

        FileConfiguration config = new YamlConfiguration();
        config.set("settings.per-world", true);
        config.set("placeholders.offline-mode", "global");

        World overworld = mock(World.class);
        World nether = mock(World.class);
        when(overworld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(overworld.getName()).thenReturn("world");
        when(overworld.getUID()).thenReturn(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(nether.getName()).thenReturn("world_nether");
        when(nether.getUID()).thenReturn(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSleepListener()).thenReturn(listener);
        when(plugin.getDayCounterService()).thenReturn(dayCounterService);
        when(server.getWorlds()).thenReturn(List.of(nether, overworld));
        when(listener.getSleepStatus(overworld)).thenReturn(new SleepListener.SleepStatus(4, 2, 2));
        when(dayCounterService.getDayCount(overworld)).thenReturn(47);

        SleepSkipPlaceholderExpansion expansion = new SleepSkipPlaceholderExpansion(plugin);

        assertEquals("world", expansion.onRequest(offlinePlayer, "world"));
        assertEquals("2", expansion.onRequest(offlinePlayer, "sleeping"));
        assertEquals("47", expansion.onRequest(offlinePlayer, "day_count"));
    }

    @Test
    void resolvesExtendedPlaceholdersForOnlineWorldContext() {
        SleepSkip plugin = mock(SleepSkip.class);
        SleepListener listener = mock(SleepListener.class);
        DayCounterService dayCounterService = mock(DayCounterService.class);
        Server server = mock(Server.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);

        FileConfiguration config = new YamlConfiguration();
        config.set("settings.per-world", false);
        config.set("placeholders.offline-mode", "global");

        World overworld = mock(World.class);
        when(overworld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(overworld.getName()).thenReturn("world");
        when(overworld.getUID()).thenReturn(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        when(overworld.getTime()).thenReturn(13000L); // night

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSleepListener()).thenReturn(listener);
        when(plugin.getDayCounterService()).thenReturn(dayCounterService);
        when(server.getWorlds()).thenReturn(List.of(overworld));
        when(listener.getSleepStatus(overworld)).thenReturn(new SleepListener.SleepStatus(5, 2, 4));
        when(listener.getBehaviorStateName(overworld)).thenReturn("ACCELERATING");
        when(listener.getCurrentSpeedMultiplier(overworld)).thenReturn(6.0D);

        SleepSkipPlaceholderExpansion expansion = new SleepSkipPlaceholderExpansion(plugin);

        assertEquals("2", expansion.onRequest(offlinePlayer, "remaining"));
        assertEquals("50", expansion.onRequest(offlinePlayer, "percent"));
        assertEquals("ACCELERATING", expansion.onRequest(offlinePlayer, "state"));
        assertEquals("6.0", expansion.onRequest(offlinePlayer, "speed"));
        assertEquals("true", expansion.onRequest(offlinePlayer, "is_night"));
        assertEquals("5", expansion.onRequest(offlinePlayer, "active_players"));
    }

    @Test
    void noneOfflineModeReturnsUnavailableForOfflinePlayerWhenPerWorldDisabled() {
        SleepSkip plugin = mock(SleepSkip.class);
        SleepListener listener = mock(SleepListener.class);
        DayCounterService dayCounterService = mock(DayCounterService.class);
        Server server = mock(Server.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);

        FileConfiguration config = new YamlConfiguration();
        config.set("settings.per-world", false);
        config.set("placeholders.offline-mode", "none");

        World overworld = mock(World.class);
        when(overworld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(overworld.getName()).thenReturn("world");
        when(overworld.getUID()).thenReturn(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSleepListener()).thenReturn(listener);
        when(plugin.getDayCounterService()).thenReturn(dayCounterService);
        when(server.getWorlds()).thenReturn(List.of(overworld));

        SleepSkipPlaceholderExpansion expansion = new SleepSkipPlaceholderExpansion(plugin);

        assertEquals("N/A", expansion.onRequest(offlinePlayer, "world"));
        assertEquals("N/A", expansion.onRequest(offlinePlayer, "sleeping"));
        assertEquals("N/A", expansion.onRequest(offlinePlayer, "day_count"));
    }
}
