package me.Erotoro.sleepskip.rewards;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.api.event.SleepSkipCompleteEvent;
import me.Erotoro.sleepskip.rewards.RewardSettings.PotionReward;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Optional reward system: grants configured effects, healing, XP, a message, and/or console
 * commands to the players who slept when a skip completes.
 *
 * <p>Disabled by default — it does nothing until a server owner opts in via {@code rewards.enabled}.
 * The listener is always registered so {@code /sleep reload} can toggle the feature without a
 * restart; the configuration snapshot is swapped atomically on reload.
 */
public final class SleepRewardService implements Listener {

    private final SleepSkip plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile RewardSettings settings;

    public SleepRewardService(SleepSkip plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads the {@code rewards.*} configuration. Safe to call at runtime. */
    public void reload() {
        this.settings = RewardSettings.from(plugin.getConfig(), plugin.getLogger());
    }

    @EventHandler
    public void onSleepSkipComplete(SleepSkipCompleteEvent event) {
        RewardSettings current = settings;
        if (!current.enabled() || !current.hasAnyReward()) {
            return;
        }
        if (current.onlyNaturalSkips() && event.isForced()) {
            return;
        }

        Set<UUID> sleepers = event.getSleepers();
        for (UUID sleeperId : sleepers) {
            Player player = Bukkit.getPlayer(sleeperId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            // Mutating a player must happen on its own region thread on Folia.
            PlatformScheduler.runForPlayer(plugin, player, () -> applyPlayerRewards(player, current));
        }

        dispatchCommands(current, sleepers);
    }

    /** Runs on the recipient's region thread. */
    private void applyPlayerRewards(Player player, RewardSettings current) {
        if (!player.isOnline()) {
            return;
        }

        for (PotionReward potion : current.potionEffects()) {
            player.addPotionEffect(new PotionEffect(
                    potion.type(), potion.durationTicks(), potion.amplifier(), potion.ambient(), potion.particles()));
        }

        if (current.healToFull()) {
            healToFull(player);
        }
        if (current.feed()) {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
        if (current.experience() > 0) {
            player.giveExp(current.experience());
        }
        if (!current.message().isEmpty()) {
            player.sendMessage(miniMessage.deserialize(current.message()));
        }
    }

    // getMaxHealth() is deprecated but present across the whole supported range; the Attribute enum
    // constants were renamed between 1.20 and newer builds, so this stays the most compatible option.
    @SuppressWarnings("deprecation")
    private void healToFull(Player player) {
        player.setHealth(player.getMaxHealth());
    }

    private void dispatchCommands(RewardSettings current, Set<UUID> sleepers) {
        if (current.commands().isEmpty()) {
            return;
        }

        List<String> resolvedCommands = new ArrayList<>();
        for (UUID sleeperId : sleepers) {
            Player player = Bukkit.getPlayer(sleeperId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            String playerName = player.getName();
            for (String command : current.commands()) {
                resolvedCommands.add(command.replace("{player}", playerName));
            }
        }
        if (resolvedCommands.isEmpty()) {
            return;
        }

        // Console command dispatch must run on the global region thread on Folia.
        PlatformScheduler.runGlobal(plugin, () -> {
            for (String command : resolvedCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        });
    }
}
