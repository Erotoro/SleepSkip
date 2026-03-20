package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

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

        localeConfig = YamlConfiguration.loadConfiguration(localeFile);
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
}
