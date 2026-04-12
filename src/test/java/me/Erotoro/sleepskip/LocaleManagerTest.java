package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocaleManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void reloadRestoresBundledLocaleWhenLocaleFileIsInvalid() throws Exception {
        Path dataFolderPath = tempDir.resolve("plugin-data");
        Path langDir = dataFolderPath.resolve("lang");
        Files.createDirectories(langDir);
        Path localePath = langDir.resolve("ru.yml");

        byte[] brokenYaml = new byte[]{'m', 'e', 's', 's', 'a', 'g', 'e', 's', ':', '\n', 1};
        Files.write(localePath, brokenYaml);

        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.language", "ru");

        String bundledLocale = "messages:\n  nightSkipped: \"Доброе утро!\"\n";
        byte[] bundledBytes = bundledLocale.getBytes(StandardCharsets.UTF_8);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolderPath.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocaleManagerTest"));
        when(plugin.getResource("lang/ru.yml")).thenReturn(new ByteArrayInputStream(bundledBytes));
        when(plugin.getResource("lang/en.yml")).thenReturn(new ByteArrayInputStream(bundledBytes));
        when(plugin.getResource("lang/ua.yml")).thenReturn(new ByteArrayInputStream(bundledBytes));

        LocaleManager localeManager = new LocaleManager(plugin);
        localeManager.reload();

        assertEquals("Доброе утро!", localeManager.tr("messages.nightSkipped"));
        verify(plugin).saveResource("lang/ru.yml", true);
    }
}

