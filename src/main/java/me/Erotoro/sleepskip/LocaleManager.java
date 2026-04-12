package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Resolves localized strings with optional config overrides for backward compatibility.
 */
public class LocaleManager {

    private final SleepSkip plugin;
    private FileConfiguration localeConfig;
    private String currentLanguage = "ru";

    public LocaleManager(SleepSkip plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Object configuredLanguage = plugin.getConfig().get("settings.language");
        if (configuredLanguage instanceof String language && !language.isBlank()) {
            currentLanguage = language.toLowerCase();
        } else {
            currentLanguage = "ru";
        }

        plugin.saveResource("lang/ru.yml", false);
        plugin.saveResource("lang/en.yml", false);
        plugin.saveResource("lang/ua.yml", false);

        File localeFile = new File(plugin.getDataFolder(), "lang/" + currentLanguage + ".yml");
        if (!localeFile.exists()) {
            currentLanguage = "ru";
            localeFile = new File(plugin.getDataFolder(), "lang/ru.yml");
        }

        localeConfig = loadLocaleWithFallback(localeFile, currentLanguage);
    }

    public String tr(String key) {
        return tr(key, key);
    }

    public String tr(String key, String fallback) {
        // Old versions stored messages directly in config.yml.
        String configOverride = plugin.getConfig().getString(key);
        if (configOverride != null && !configOverride.isBlank()) {
            return configOverride;
        }

        if (localeConfig == null) {
            reload();
        }
        return localeConfig.getString(key, fallback);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    private FileConfiguration loadLocaleWithFallback(File localeFile, String language) {
        YamlConfiguration locale = new YamlConfiguration();
        try {
            locale.load(localeFile);
            return locale;
        } catch (Exception fileLoadError) {
            plugin.getLogger().warning(
                    "Failed to parse locale file " + localeFile.getPath() + ". Restoring bundled locale."
            );
            plugin.getLogger().log(Level.FINE, "Locale parse error details", fileLoadError);
        }

        String resourcePath = "lang/" + language + ".yml";
        try {
            plugin.saveResource(resourcePath, true);
            locale.load(localeFile);
            return locale;
        } catch (Exception overwriteError) {
            plugin.getLogger().warning(
                    "Failed to restore locale file " + localeFile.getPath() + " from bundled resource."
            );
            plugin.getLogger().log(Level.FINE, "Locale restore error details", overwriteError);
        }

        try (InputStream resource = plugin.getResource(resourcePath)) {
            if (resource != null) {
                return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            }
        } catch (Exception streamError) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled locale " + resourcePath, streamError);
        }

        if (!"ru".equals(language)) {
            try (InputStream fallbackResource = plugin.getResource("lang/ru.yml")) {
                if (fallbackResource != null) {
                    currentLanguage = "ru";
                    return YamlConfiguration.loadConfiguration(
                            new InputStreamReader(fallbackResource, StandardCharsets.UTF_8)
                    );
                }
            } catch (Exception fallbackError) {
                plugin.getLogger().log(Level.WARNING, "Failed to load bundled locale lang/ru.yml", fallbackError);
            }
        }

        plugin.getLogger().warning("Locale fallback exhausted. Using empty locale map.");
        return new YamlConfiguration();
    }
}
