package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.afk.AFKChecker;
import me.Erotoro.sleepskip.utils.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.Statistic;

import java.util.HashSet;
import java.util.Set;

public class SleepListener implements Listener {

    private final SleepSkip plugin;
    private final AFKChecker afkChecker;
    private final Set<Player> sleepingPlayers = new HashSet<>();

    public SleepListener(SleepSkip plugin, AFKChecker afkChecker) {
        this.plugin = plugin;
        this.afkChecker = afkChecker;
    }

    @EventHandler
    public void onPlayerSleep(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        // Проверка времени суток (только ночь)
        if (world.getTime() < 12541 || world.getTime() > 23458) {
            return;
        }

        // Обновляем активность игрока
        afkChecker.updatePlayerActivity(player);

        sleepingPlayers.add(player);
        checkAndSkipNight(player);
    }

    @EventHandler
    public void onPlayerWakeUp(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        sleepingPlayers.remove(player);
        afkChecker.updatePlayerActivity(player);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Обновляем активность игрока при взаимодействии
        afkChecker.updatePlayerActivity(event.getPlayer());
    }

    private void checkAndSkipNight(Player triggeringPlayer) {
        World world = triggeringPlayer.getWorld();
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);

        // Подсчет активных игроков (только в этом мире, если per-world включен)
        long totalPlayers = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (perWorld && !p.getWorld().equals(world)) continue;
            if (!afkChecker.isPlayerAFK(p)) {
                totalPlayers++;
            }
        }

        // Подсчет спящих игроков в этом мире
        int sleeping = 0;
        for (Player p : sleepingPlayers) {
            if (perWorld && !p.getWorld().equals(world)) continue;
            sleeping++;
        }

        int needed = getRequiredSleepers((int) totalPlayers);

        if (sleeping >= needed) {
            skipNight(world);
        } else {
            // Читаем сообщение из конфига и заменяем плейсхолдеры
            String statusMsg = plugin.getConfig().getString("messages.sleepingStatus",
                    "<yellow>{sleeping}/{needed} игроков спит. Нужно <yellow>{needed} для пропуска ночи!");
            statusMsg = statusMsg.replace("{sleeping}", String.valueOf(sleeping))
                    .replace("{needed}", String.valueOf(needed));

            int actionbarDuration = plugin.getConfig().getInt("settings.actionbar-duration", 5);
            ActionBar.sendToAll(plugin, statusMsg, actionbarDuration);
        }
    }

    private int getRequiredSleepers(int totalPlayers) {
        String requiredType = plugin.getConfig().getString("settings.required-type", "percent");
        if (requiredType.equalsIgnoreCase("fixed")) {
            return plugin.getConfig().getInt("settings.required-value", 1);
        } else {
            double percent = plugin.getConfig().getDouble("settings.required-value", 50);
            return (int) Math.ceil(totalPlayers * (percent / 100.0));
        }
    }

    private void skipNight(World world) {
        // Установка времени на утро
        world.setTime(0);

        // Остановка дождя и грозы, если включено в конфиге
        if (plugin.getConfig().getBoolean("settings.skip-rain", true)) {
            world.setStorm(false);
            world.setThundering(false);
        }

        // Сброс статистики TIME_SINCE_REST для всех спящих игроков (чтобы фантомы не спавнились)
        for (Player sleepingPlayer : sleepingPlayers) {
            sleepingPlayer.setStatistic(Statistic.TIME_SINCE_REST, 0);
        }

        // Читаем сообщение из конфига и отправляем его
        String nightMsg = plugin.getConfig().getString("messages.nightSkipped",
                "<green>Ночь пропущена! Доброе утро!");
        int actionbarDuration = plugin.getConfig().getInt("settings.actionbar-duration", 5);
        ActionBar.sendToAll(plugin, nightMsg, actionbarDuration);

        // Очищаем список спящих игроков
        sleepingPlayers.clear();
    }
}