package me.Erotoro.sleepskip.commands;

import me.Erotoro.sleepskip.LocaleManager;
import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.listeners.SleepListener;
import me.Erotoro.sleepskip.services.DayCounterService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SleepCommandTest {

    @Test
    void reloadCommandMergesBundledDefaultsBeforeReloadingServices() {
        SleepSkip plugin = mock(SleepSkip.class);
        SleepListener listener = mock(SleepListener.class);
        LocaleManager localeManager = mock(LocaleManager.class);
        DayCounterService dayCounterService = mock(DayCounterService.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        FileConfiguration config = new YamlConfiguration();

        when(sender.hasPermission("sleepskip.admin")).thenReturn(true);
        when(plugin.getSleepListener()).thenReturn(listener);
        when(plugin.getLocaleManager()).thenReturn(localeManager);
        when(plugin.getDayCounterService()).thenReturn(dayCounterService);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SleepCommandTest"));
        when(plugin.tr(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

        SleepCommand sleepCommand = new SleepCommand(plugin);
        sleepCommand.onCommand(sender, command, "sleep", new String[]{"reload"});

        verify(plugin, times(1)).mergeBundledConfigDefaultsForReload();
        verify(plugin, atLeastOnce()).reloadConfig();
        verify(localeManager, times(1)).reload();
        verify(dayCounterService, times(1)).reload();
    }
}
