package me.Erotoro.sleepskip.commands;

import me.Erotoro.sleepskip.ConfigValidator;
import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.listeners.SleepListener;
import me.Erotoro.sleepskip.utils.ActionBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
            plugin.getSleepListener().resetRuntimeState();
            plugin.reloadConfig();
            plugin.mergeBundledConfigDefaultsForReload();
            ConfigValidator.validate(plugin);
            plugin.getLocaleManager().reload();
            plugin.getDayCounterService().reload();
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

        if (args[0].equalsIgnoreCase("forceskip")) {
            return handleForceSkip(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleForceSkip(CommandSender sender, String[] args) {
        boolean instant = false;
        String requestedWorld = null;

        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if ("--instant".equalsIgnoreCase(argument)) {
                instant = true;
                continue;
            }
            if (requestedWorld == null) {
                requestedWorld = argument;
                continue;
            }

            sendForceSkipUsage(sender);
            return true;
        }

        World world = resolveTargetWorld(sender, requestedWorld);
        if (world == null) {
            return true;
        }

        SleepListener.ForceSkipResult result = plugin.getSleepListener().requestForceSkip(sender, world, instant);
        switch (result) {
            case STARTED_SMOOTH -> sender.sendMessage(miniMessage.deserialize(plugin.tr(
                    "messages.force-skip-started-smooth",
                    "<green>Started smooth skip in world <yellow>{world}</yellow>."
            ).replace("{world}", world.getName())));
            case STARTED_INSTANT -> sender.sendMessage(miniMessage.deserialize(plugin.tr(
                    "messages.force-skip-started-instant",
                    "<green>Skipped to morning instantly in world <yellow>{world}</yellow>."
            ).replace("{world}", world.getName())));
            case ALREADY_RUNNING -> sender.sendMessage(miniMessage.deserialize(plugin.tr(
                    "messages.force-skip-already-running",
                    "<yellow>Sleep skip is already in progress."
            )));
            case UNAVAILABLE -> sender.sendMessage(miniMessage.deserialize(plugin.tr(
                    "messages.force-skip-unavailable",
                    "<red>It's not night in this world."
            )));
        }

        return true;
    }

    private World resolveTargetWorld(CommandSender sender, String requestedWorld) {
        if (requestedWorld != null && !requestedWorld.isBlank()) {
            World world = Bukkit.getWorld(requestedWorld);
            if (world == null) {
                sender.sendMessage(miniMessage.deserialize(plugin.tr(
                        "messages.force-skip-world-not-found",
                        "<red>World <yellow>{world}</yellow> was not found."
                ).replace("{world}", requestedWorld)));
            }
            return world;
        }

        if (sender instanceof Player player) {
            return player.getWorld();
        }

        sender.sendMessage(miniMessage.deserialize(plugin.tr(
                "messages.force-skip-world-required",
                "<red>Console must specify a world: <yellow>/sleep forceskip [world] [--instant]</yellow>"
        )));
        return null;
    }

    private void sendForceSkipUsage(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(plugin.tr(
                "messages.force-skip-usage",
                "<yellow>Usage: /sleep forceskip [world] [--instant]</yellow>"
        )));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-title", "<gold>=== SleepSkipUltra Commands ===")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-reload", "<yellow>/sleep reload - Reload config")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-status", "<yellow>/sleep status - Check plugin status")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-broadcaststatus", "<yellow>/sleep broadcaststatus - Broadcast plugin status")));
        sender.sendMessage(miniMessage.deserialize(plugin.tr("messages.help-forceskip", "<yellow>/sleep forceskip [world] [--instant] - Force a sleep skip")));
    }
}
