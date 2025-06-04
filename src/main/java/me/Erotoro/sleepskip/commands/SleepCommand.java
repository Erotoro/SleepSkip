package me.Erotoro.sleepskip.commands;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.utils.ActionBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SleepCommand implements CommandExecutor {

    private final SleepSkip plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SleepCommand(SleepSkip plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Проверка прав доступа
        if (!sender.hasPermission("sleepskip.admin")) {
            String noPermissionMsg = plugin.getConfig().getString("messages.no-permission", "<red>У вас нет прав для использования этой команды!");
            sender.sendMessage(miniMessage.deserialize(noPermissionMsg));
            return true;
        }

        // Проверка наличия аргументов
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Обработка подкоманд
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            String reloadMsg = plugin.getConfig().getString("messages.reload-success", "<green>Конфиг перезагружен!");
            sender.sendMessage(miniMessage.deserialize(reloadMsg));
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            String statusMessage = plugin.getConfig().getString("messages.status-message", "<yellow>Плагин SleepSkip работает корректно.");
            Component component = miniMessage.deserialize(statusMessage);
            if (sender instanceof Player) {
                ActionBar.sendToAll(plugin, statusMessage, 5);
            } else {
                // Альтернативный вывод для консоли
                sender.sendMessage(miniMessage.serialize(component));
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    ActionBar.sendToAll(plugin, statusMessage, 5);
                }
            }
            return true;
        }

        // Неизвестная подкоманда
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        String helpTitle = plugin.getConfig().getString("messages.help-title", "<gold>=== SleepSkip Команды ===");
        String helpReload = plugin.getConfig().getString("messages.help-reload", "<yellow>/sleep reload - Перезагрузить конфиг");
        String helpStatus = plugin.getConfig().getString("messages.help-status", "<yellow>/sleep status - Проверить статус плагина");

        sender.sendMessage(miniMessage.deserialize(helpTitle));
        sender.sendMessage(miniMessage.deserialize(helpReload));
        sender.sendMessage(miniMessage.deserialize(helpStatus));
    }
}