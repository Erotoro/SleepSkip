package me.Erotoro.sleepskip;

import me.Erotoro.sleepskip.afk.AFKChecker;
import me.Erotoro.sleepskip.commands.SleepCommand;
import me.Erotoro.sleepskip.listeners.SleepListener;
import org.bukkit.plugin.java.JavaPlugin;

public class SleepSkip extends JavaPlugin {

    private AFKChecker afkChecker;

    @Override
    public void onEnable() {
        // Сохранение и проверка конфигурации
        saveDefaultConfig();
        try {
            getConfig().getString("settings.required-type");
            getConfig().getInt("settings.required-value");
        } catch (Exception e) {
            getLogger().severe("Ошибка загрузки config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация AFKChecker
        afkChecker = new AFKChecker(this);

        // Регистрация команды
        if (getCommand("sleep") == null) {
            getLogger().severe("Команда 'sleep' не определена в plugin.yml! Отключаю плагин...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("sleep").setExecutor(new SleepCommand(this));
        getLogger().info("Команда /sleep успешно зарегистрирована.");

        // Регистрация слушателя событий
        getServer().getPluginManager().registerEvents(new SleepListener(this, afkChecker), this);
        getLogger().info("Слушатель событий успешно зарегистрирован.");

        getLogger().info("SleepSkip включен!");
    }

    @Override
    public void onDisable() {
        if (afkChecker != null) {
            // Остановка любых задач AFKChecker, если они есть (расширить при необходимости)
        }
        getLogger().info("SleepSkip отключен!");
    }

    public AFKChecker getAfkChecker() {
        return afkChecker;
    }
}