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
    private String currentLanguage = "en";

    public LocaleManager(SleepSkip plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Object configuredLanguage = plugin.getConfig().get("settings.language");
        if (configuredLanguage instanceof String language && !language.isBlank()) {
            currentLanguage = language.toLowerCase();
        } else {
            currentLanguage = "en";
        }

        plugin.saveResource("lang/en.yml", false);
        plugin.saveResource("lang/ru.yml", false);
        plugin.saveResource("lang/ua.yml", false);
        mergeBundledLocaleDefaults(new File(plugin.getDataFolder(), "lang/en.yml"), "lang/en.yml");
        mergeBundledLocaleDefaults(new File(plugin.getDataFolder(), "lang/ru.yml"), "lang/ru.yml");
        mergeBundledLocaleDefaults(new File(plugin.getDataFolder(), "lang/ua.yml"), "lang/ua.yml");

        File localeFile = new File(plugin.getDataFolder(), "lang/" + currentLanguage + ".yml");
        if (!localeFile.exists()) {
            currentLanguage = "en";
            localeFile = new File(plugin.getDataFolder(), "lang/en.yml");
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

        if (!"en".equals(language)) {
            try (InputStream fallbackResource = plugin.getResource("lang/en.yml")) {
                if (fallbackResource != null) {
                    currentLanguage = "en";
                    return YamlConfiguration.loadConfiguration(
                            new InputStreamReader(fallbackResource, StandardCharsets.UTF_8)
                    );
                }
            } catch (Exception fallbackError) {
                plugin.getLogger().log(Level.WARNING, "Failed to load bundled locale lang/en.yml", fallbackError);
            }
        }

        plugin.getLogger().warning("Locale fallback exhausted. Using empty locale map.");
        return new YamlConfiguration();
    }

    private void mergeBundledLocaleDefaults(File localeFile, String resourcePath) {
        if (localeFile == null || resourcePath == null || !localeFile.exists()) {
            return;
        }

        YamlConfiguration currentLocale = new YamlConfiguration();
        try {
            currentLocale.load(localeFile);
        } catch (Exception exception) {
            // Invalid locale files are handled later by loadLocaleWithFallback.
            return;
        }
        YamlConfiguration bundledLocale = loadBundledConfiguration(resourcePath);
        if (bundledLocale == null) {
            return;
        }

        boolean changed = false;
        for (String key : bundledLocale.getKeys(true)) {
            if (!currentLocale.isSet(key)) {
                currentLocale.set(key, bundledLocale.get(key));
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        try {
            currentLocale.save(localeFile);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to merge locale defaults for " + localeFile.getPath(), exception);
        }
    }

    private YamlConfiguration loadBundledConfiguration(String resourcePath) {
        try (InputStream resource = plugin.getResource(resourcePath)) {
            if (resource == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled locale " + resourcePath, exception);
            return null;
        }
    }
}
