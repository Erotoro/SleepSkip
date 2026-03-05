package me.Erotoro.sleepskip;

import me.Erotoro.sleepskip.afk.AFKChecker;
import me.Erotoro.sleepskip.commands.SleepCommand;
import me.Erotoro.sleepskip.listeners.SleepListener;
import me.Erotoro.sleepskip.utils.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

public class SleepSkip extends JavaPlugin {

    private AFKChecker afkChecker;
    private boolean isFolia;
    private Metrics metrics;

    @Override
    public void onEnable() {
        // Определение типа сервера
        isFolia = isFoliaServer();
        
        if (isFolia) {
            getLogger().info("Обнаружен сервер Folia - включена поддержка многопоточности");
        } else {
            getLogger().info("Обнаружен сервер Paper/Spigot");
        }
        
        // Инициализация bStats метрик
        initMetrics();
        
        // Сохранение и проверка конфигурации
        saveDefaultConfig();
        try {
            getConfig().getString("settings.required-type");
            getConfig().getInt("settings.required-value");
        } catch (Exception e) {
            getLogger().severe("Ошибка загрузки config.yml: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация AFKChecker
        afkChecker = new AFKChecker(this);

        // Регистрация команды
        if (getCommand("sleep") == null) {
            getLogger().severe("Команда 'sleep' не определена в plugin.yml! Отключаю плагин...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("sleep").setExecutor(new SleepCommand(this));
        getLogger().info("Команда /sleep успешно зарегистрирована.");

        // Регистрация слушателя событий
        Bukkit.getPluginManager().registerEvents(new SleepListener(this, afkChecker), this);
        getLogger().info("Слушатель событий успешно зарегистрирован.");

        getLogger().info("SleepSkip версии " + getDescription().getVersion() + " включен!");
    }

    @Override
    public void onDisable() {
        // Очищаем ActionBar при отключении плагина
        ActionBar.cancelCurrentTask(this);
        getLogger().info("SleepSkip отключен!");
    }

    /**
     * Инициализация bStats метрик
     * ID плагина: нужно зарегистрировать на https://bstats.org/plugin/bukkit/SleepSkip
     */
    private void initMetrics() {
        int pluginId = 29936; // Заменить на реальный ID после регистрации на bStats
        metrics = new Metrics(this, pluginId);

        // График: Тип требуемого количества (fixed/percent)
        metrics.addCustomChart(new SimplePie("required_type", () ->
            getConfig().getString("settings.required-type", "percent")));

        // График: Игнорирование AFK
        metrics.addCustomChart(new SimplePie("ignore_afk", () ->
            getConfig().getBoolean("settings.ignore-afk", true) ? "true" : "false"));

        // График: Per-world режим
        metrics.addCustomChart(new SimplePie("per_world", () ->
            getConfig().getBoolean("settings.per-world", false) ? "true" : "false"));

        // График: Пропуск дождя
        metrics.addCustomChart(new SimplePie("skip_rain", () ->
            getConfig().getBoolean("settings.skip-rain", true) ? "true" : "false"));

        // График: Использование Folia
        metrics.addCustomChart(new SimplePie("server_type", () ->
            isFolia ? "Folia" : "Paper/Spigot"));

        // График: Количество ночных пропусков
        metrics.addCustomChart(new SingleLineChart("nights_skipped", () ->
            nightsSkippedCount));
        nightsSkippedCount = 0; // Сброс счётчика
    }
    
    // Счётчик пропущенных ночей (для метрик)
    private static int nightsSkippedCount = 0;
    
    public static void incrementNightsSkipped() {
        nightsSkippedCount++;
    }

    public AFKChecker getAfkChecker() {
        return afkChecker;
    }
    
    public boolean isFolia() {
        return isFolia;
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