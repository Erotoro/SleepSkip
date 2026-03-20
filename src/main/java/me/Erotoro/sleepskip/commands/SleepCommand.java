package me.Erotoro.sleepskip.commands;

import me.Erotoro.sleepskip.ConfigValidator;
import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.utils.ActionBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Administrative command surface for reload/status and help output.
 */
public class SleepCommand implements CommandExecutor {

    private final SleepSkip plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SleepCommand(SleepSkip plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sleepskip.admin")) {
            sender.sendMessage(miniMessage.deserialize(plugin.tr(
                    "messages.no-permission",
                    "<red>You do not have permission to use this command!"
            )));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            ConfigValidator.validate(plugin);
            plugin.getLocaleManager().reload();
            plugin.getSleepListener().invalidateAllCaches();
            sender.sendMessage(miniMessage.deserialize(plugin.tr(
                    "messages.reload-success",
                    "<green>Config reloaded!"
            )));
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            String statusMessage = plugin.tr(
                    "messages.status-message",
                    "<yellow>SleepSkip is running normally."
            );
            Component component = miniMessage.deserialize(statusMessage);
            sender.sendMessage(component);
            return true;
        }

        if (args[0].equalsIgnoreCase("broadcaststatus")) {
            String statusMessage = plugin.tr(
                    "messages.status-message",
                    "<yellow>SleepSkip is running normally."
            );
            Component component = miniMessage.deserialize(statusMessage);
            sender.sendMessage(component);
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
                    ActionBar.sendToAll(plugin, "command:status", statusMessage, plugin.getConfig().getInt("settings.actionbar-duration", 5));
                } else {
                    Bukkit.getOnlinePlayers().forEach(player -> player.sendRichMessage(statusMessage));
                }
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-title", "<gold>=== SleepSkip Commands ===")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-reload", "<yellow>/sleep reload - Reload config")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-status", "<yellow>/sleep status - Check plugin status")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-broadcaststatus", "<yellow>/sleep broadcaststatus - Broadcast plugin status")));
    }
}
