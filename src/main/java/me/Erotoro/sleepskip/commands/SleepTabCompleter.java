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
        if (!sender.hasPermission("sleepskip.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            addIfMatches(completions, "reload", args[0]);
            addIfMatches(completions, "status", args[0]);
            addIfMatches(completions, "broadcaststatus", args[0]);
            addIfMatches(completions, "forceskip", args[0]);
            return completions;
        }

        if ("forceskip".equalsIgnoreCase(args[0])) {
            List<String> completions = new ArrayList<>();
            String input = args[args.length - 1];
            if (args.length >= 2) {
                addIfMatches(completions, "--instant", input);
                Bukkit.getWorlds().forEach(world -> addIfMatches(completions, world.getName(), input));
            }
            return completions;
        }

        if (args.length != 1) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        addIfMatches(completions, "reload", args[0]);
        addIfMatches(completions, "status", args[0]);
        addIfMatches(completions, "broadcaststatus", args[0]);
        return completions;
    }

    private void addIfMatches(List<String> completions, String candidate, String input) {
        if (candidate.startsWith(input.toLowerCase())) {
            completions.add(candidate);
        }
    }
}
