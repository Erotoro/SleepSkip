package me.Erotoro.sleepskip.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
        if (args.length != 1 || !sender.hasPermission("sleepskip.admin")) {
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
