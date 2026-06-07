package me.Erotoro.sleepskip.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Small admin-only completion set to keep the command UX predictable.
 */
public class SleepTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        boolean admin = sender.hasPermission("sleepskip.admin");
        boolean canToggleBossBar = admin || sender.hasPermission("sleepskip.bossbar.toggle");
        if (!admin && !canToggleBossBar) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (admin) {
                addIfMatches(completions, "reload", args[0]);
                addIfMatches(completions, "status", args[0]);
                addIfMatches(completions, "broadcaststatus", args[0]);
                addIfMatches(completions, "forceskip", args[0]);
            }
            if (canToggleBossBar) {
                addIfMatches(completions, "bossbar", args[0]);
            }
            return completions;
        }

        if ("bossbar".equalsIgnoreCase(args[0]) && canToggleBossBar) {
            if (args.length == 2) {
                List<String> completions = new ArrayList<>();
                addIfMatches(completions, "on", args[1]);
                addIfMatches(completions, "off", args[1]);
                addIfMatches(completions, "toggle", args[1]);
                addIfMatches(completions, "status", args[1]);
                return completions;
            }
            return List.of();
        }

        if ("forceskip".equalsIgnoreCase(args[0]) && admin) {
            List<String> completions = new ArrayList<>();
            String input = args[args.length - 1];
            if (args.length >= 2) {
                addIfMatches(completions, "--instant", input);
                Bukkit.getWorlds().forEach(world -> addIfMatches(completions, world.getName(), input));
            }
            return completions;
        }

        return List.of();
    }

    private void addIfMatches(List<String> completions, String candidate, String input) {
        if (candidate.startsWith(input.toLowerCase())) {
            completions.add(candidate);
        }
    }
}
