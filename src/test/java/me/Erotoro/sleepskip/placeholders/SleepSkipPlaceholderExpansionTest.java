package me.Erotoro.sleepskip.placeholders;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.listeners.SleepListener;
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
        when(server.getWorlds()).thenReturn(List.of(nether, overworld));
        when(listener.getSleepStatus(overworld)).thenReturn(new SleepListener.SleepStatus(4, 2, 2));

        SleepSkipPlaceholderExpansion expansion = new SleepSkipPlaceholderExpansion(plugin);

        assertEquals("world", expansion.onRequest(offlinePlayer, "world"));
        assertEquals("2", expansion.onRequest(offlinePlayer, "sleeping"));
    }

    @Test
    void noneOfflineModeReturnsUnavailableForOfflinePlayerWhenPerWorldDisabled() {
        SleepSkip plugin = mock(SleepSkip.class);
        SleepListener listener = mock(SleepListener.class);
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
        when(server.getWorlds()).thenReturn(List.of(overworld));

        SleepSkipPlaceholderExpansion expansion = new SleepSkipPlaceholderExpansion(plugin);

        assertEquals("N/A", expansion.onRequest(offlinePlayer, "world"));
        assertEquals("N/A", expansion.onRequest(offlinePlayer, "sleeping"));
    }
}
