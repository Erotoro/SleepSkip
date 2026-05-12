package me.Erotoro.sleepskip;

import me.Erotoro.sleepskip.afk.AFKChecker;
import me.Erotoro.sleepskip.commands.SleepCommand;
import me.Erotoro.sleepskip.commands.SleepTabCompleter;
import me.Erotoro.sleepskip.hooks.ExternalPluginHooks;
import me.Erotoro.sleepskip.listeners.SleepListener;
import me.Erotoro.sleepskip.services.DayCounterService;
import me.Erotoro.sleepskip.services.MorningAnnouncementService;
import me.Erotoro.sleepskip.placeholders.SleepSkipPlaceholderExpansion;
import me.Erotoro.sleepskip.services.SleepOverlayService;
import me.Erotoro.sleepskip.services.PlayerStateService;
import me.Erotoro.sleepskip.services.TitleSessionCoordinator;
import me.Erotoro.sleepskip.utils.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plugin bootstrap and shared service registry.
 */
public class SleepSkip extends JavaPlugin {

    private static final AtomicInteger NIGHTS_SKIPPED_COUNT = new AtomicInteger();

    private AFKChecker afkChecker;
    private LocaleManager localeManager;
    private ExternalPluginHooks externalPluginHooks;
    private PlayerStateService playerStateService;
    private SleepOverlayService sleepOverlayService;
    private MorningAnnouncementService morningAnnouncementService;
    private DayCounterService dayCounterService;
    private TitleSessionCoordinator titleSessionCoordinator;
    private SleepListener sleepListener;
    private boolean folia;
    private Metrics metrics;

    @Override
    public void onEnable() {
        folia = isFoliaServer();

        saveDefaultConfig();
        mergeBundledConfigDefaultsForReload();
        localeManager = new LocaleManager(this);
        ConfigValidator.validate(this);
        localeManager.reload();

        logServerType();
        initMetrics();

        afkChecker = new AFKChecker(this);
        externalPluginHooks = new ExternalPluginHooks(this);
        externalPluginHooks.logDetectedHooks();
        externalPluginHooks.logConflicts();
        playerStateService = new PlayerStateService(this, afkChecker, externalPluginHooks);
        playerStateService.start();
        titleSessionCoordinator = new TitleSessionCoordinator();
        morningAnnouncementService = new MorningAnnouncementService(this);
        dayCounterService = new DayCounterService(this);
        dayCounterService.reload();
        sleepOverlayService = new SleepOverlayService(this);

        registerCommand();
        sleepListener = new SleepListener(this, playerStateService, sleepOverlayService);
        Bukkit.getPluginManager().registerEvents(sleepListener, this);
        registerPlaceholderExpansion();

        getLogger().info(tr("logs.plugin-enabled", "SleepSkip enabled"));
    }

    @Override
    public void onDisable() {
        ActionBar.cancelCurrentTask();
        if (sleepListener != null) {
            sleepListener.shutdownActiveSkipSessions();
        }
        if (sleepOverlayService != null) {
            sleepOverlayService.stopAll();
        }
        if (morningAnnouncementService != null) {
            morningAnnouncementService.stop();
        }
        if (dayCounterService != null) {
            dayCounterService.stop();
        }
        if (playerStateService != null) {
            playerStateService.stop();
        }
        getLogger().info(tr("logs.plugin-disabled", "SleepSkip disabled"));
    }

    private void registerCommand() {
        PluginCommand sleepCommand = getCommand("sleep");
        if (sleepCommand == null) {
            getLogger().severe(tr("logs.command-missing", "Command 'sleep' is not defined in plugin.yml"));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        sleepCommand.setExecutor(new SleepCommand(this));
        sleepCommand.setTabCompleter(new SleepTabCompleter());
        sleepCommand.setDescription(tr("plugin.command-description", "SleepSkip commands"));
        sleepCommand.setUsage(tr("plugin.command-usage", "/sleep <reload|status>"));
    }

    private void logServerType() {
        getLogger().info(folia
                ? tr("logs.detected-folia", "Detected Folia server")
                : tr("logs.detected-paper", "Detected Paper/Spigot server"));
    }

    private void initMetrics() {
        int pluginId = 29936;
        metrics = new Metrics(this, pluginId);

        metrics.addCustomChart(new SimplePie("required_type", () ->
                getConfig().getString("settings.required-type", "percent")));
        metrics.addCustomChart(new SimplePie("ignore_afk", () ->
                getConfig().getBoolean("settings.ignore-afk", true) ? "true" : "false"));
        metrics.addCustomChart(new SimplePie("per_world", () ->
                getConfig().getBoolean("settings.per-world", false) ? "true" : "false"));
        metrics.addCustomChart(new SimplePie("skip_rain", () ->
                getConfig().getBoolean("settings.skip-rain", true) ? "true" : "false"));
        metrics.addCustomChart(new SimplePie("server_type", () ->
                folia ? "Folia" : "Paper/Spigot"));
        metrics.addCustomChart(new SingleLineChart("nights_skipped", NIGHTS_SKIPPED_COUNT::get));
        NIGHTS_SKIPPED_COUNT.set(0);
    }

    public static void incrementNightsSkipped() {
        NIGHTS_SKIPPED_COUNT.incrementAndGet();
    }

    public AFKChecker getAfkChecker() {
        return afkChecker;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public SleepListener getSleepListener() {
        return sleepListener;
    }

    public PlayerStateService getPlayerStateService() {
        return playerStateService;
    }

    public void mergeBundledConfigDefaultsForReload() {
        try (InputStream resource = getResource("config.yml")) {
            if (resource == null) {
                return;
            }

            YamlConfiguration bundledConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );
            FileConfiguration currentConfig = getConfig();

            boolean changed = false;
            for (String key : bundledConfig.getKeys(true)) {
                if (!currentConfig.isSet(key)) {
                    currentConfig.set(key, bundledConfig.get(key));
                    changed = true;
                }
            }

            if (changed) {
                saveConfig();
                reloadConfig();
            }
        } catch (Exception exception) {
            getLogger().warning("Failed to merge bundled config defaults: " + exception.getMessage());
        }
    }

    public DayCounterService getDayCounterService() {
        return dayCounterService;
    }

    public MorningAnnouncementService getMorningAnnouncementService() {
        return morningAnnouncementService;
    }

    public SleepOverlayService getSleepOverlayService() {
        return sleepOverlayService;
    }

    public TitleSessionCoordinator getTitleSessionCoordinator() {
        return titleSessionCoordinator;
    }

    public boolean isFolia() {
        return folia;
    }

    public String tr(String key) {
        return localeManager.tr(key);
    }

    public String tr(String key, String fallback) {
        return localeManager.tr(key, fallback);
    }

    private void registerPlaceholderExpansion() {
        // PlaceholderAPI is optional.
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SleepSkipPlaceholderExpansion(this).register();
            getLogger().info(tr("logs.hook-placeholderapi", "Hooked into PlaceholderAPI."));
        }
    }

    private boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

