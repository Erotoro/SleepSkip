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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(plugin.getResource("lang/ru.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bundledBytes));
        when(plugin.getResource("lang/en.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bundledBytes));
        when(plugin.getResource("lang/ua.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bundledBytes));

        LocaleManager localeManager = new LocaleManager(plugin);
        localeManager.reload();

        assertEquals("Доброе утро!", localeManager.tr("messages.nightSkipped"));
        verify(plugin).saveResource("lang/ru.yml", true);
    }

    @Test
    void reloadMergesMissingLocaleKeysWithoutOverwritingExistingValues() throws Exception {
        Path dataFolderPath = tempDir.resolve("plugin-data");
        Path langDir = dataFolderPath.resolve("lang");
        Files.createDirectories(langDir);

        Path ruPath = langDir.resolve("ru.yml");
        Path enPath = langDir.resolve("en.yml");
        Path uaPath = langDir.resolve("ua.yml");

        Files.writeString(ruPath, "messages:\n  nightSkipped: \"Моё утро!\"\n");
        Files.writeString(enPath, "messages:\n  nightSkipped: \"stub\"\n");
        Files.writeString(uaPath, "messages:\n  nightSkipped: \"stub\"\n");

        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.language", "ru");

        String bundledRu = """
                messages:
                  nightSkipped: "Доброе утро!"
                  day-counter-title: "<gold><bold>День {day}</bold></gold>"
                """;
        String bundledStub = "messages:\n  nightSkipped: \"stub\"\n";

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolderPath.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocaleManagerTest"));
        when(plugin.getResource("lang/ru.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bundledRu.getBytes(StandardCharsets.UTF_8)));
        when(plugin.getResource("lang/en.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bundledStub.getBytes(StandardCharsets.UTF_8)));
        when(plugin.getResource("lang/ua.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bundledStub.getBytes(StandardCharsets.UTF_8)));

        LocaleManager localeManager = new LocaleManager(plugin);
        localeManager.reload();

        YamlConfiguration mergedLocale = YamlConfiguration.loadConfiguration(ruPath.toFile());
        assertEquals("Моё утро!", mergedLocale.getString("messages.nightSkipped"));
        assertEquals("<gold><bold>День {day}</bold></gold>", mergedLocale.getString("messages.day-counter-title"));
    }
    @Test
    void reloadDoesNotResaveExistingBundledLocaleFiles() throws Exception {
        Path dataFolderPath = tempDir.resolve("plugin-data");
        Path langDir = dataFolderPath.resolve("lang");
        Files.createDirectories(langDir);

        for (String language : LocaleManager.BUNDLED_LANGUAGES) {
            Files.writeString(langDir.resolve(language + ".yml"), "messages:\n  nightSkipped: \"stub\"\n");
        }

        SleepSkip plugin = mock(SleepSkip.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("settings.language", "en");
        byte[] bundledBytes = "messages:\n  nightSkipped: \"stub\"\n".getBytes(StandardCharsets.UTF_8);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolderPath.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocaleManagerTest"));
        for (String language : LocaleManager.BUNDLED_LANGUAGES) {
            when(plugin.getResource("lang/" + language + ".yml"))
                    .thenAnswer(invocation -> new ByteArrayInputStream(bundledBytes));
        }

        LocaleManager localeManager = new LocaleManager(plugin);
        localeManager.reload();

        verify(plugin, never()).saveResource(anyString(), eq(false));
    }
}
