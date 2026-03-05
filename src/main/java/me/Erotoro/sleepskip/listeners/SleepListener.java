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

        // Проверка типа мира (только обычный мир)
        if (!isOverworld(world)) {
            return;
        }

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
        
        // Если игрок проснулся до завершения перехода, обновляем статус
        updateSleepStatus(player.getWorld());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Обновляем активность игрока при взаимодействии
        afkChecker.updatePlayerActivity(event.getPlayer());
    }

    /**
     * Проверяет, является ли мир обычным (Overworld)
     * Nether и End миры исключаются из подсчёта
     */
    private boolean isOverworld(World world) {
        return world.getEnvironment() == World.Environment.NORMAL;
    }

    private synchronized void checkAndSkipNight(Player triggeringPlayer) {
        World world = triggeringPlayer.getWorld();
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);

        // Подсчет активных игроков в обычном мире (Nether и End исключаются)
        long totalPlayers = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Исключаем игроков в Незере и Энде
            if (!isOverworld(p.getWorld())) continue;
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
            // Достаточно спящих — запускаем переход
            skipNight(world, sleeping, needed);
        } else {
            // Показываем статус сна
            showSleepStatus(world, sleeping, needed);
        }
    }

    /**
     * Обновляет статус сна для мира (вызывается при пробуждении игрока)
     */
    private void updateSleepStatus(World world) {
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);

        long totalPlayers = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Исключаем игроков в Незере и Энде
            if (!isOverworld(p.getWorld())) continue;
            if (perWorld && !p.getWorld().equals(world)) continue;
            if (!afkChecker.isPlayerAFK(p)) {
                totalPlayers++;
            }
        }

        int sleeping = 0;
        for (Player p : sleepingPlayers) {
            if (perWorld && !p.getWorld().equals(world)) continue;
            sleeping++;
        }

        int needed = getRequiredSleepers((int) totalPlayers);

        if (sleeping < needed) {
            showSleepStatus(world, sleeping, needed);
        }
    }

    /**
     * Показывает статус сна в ActionBar
     */
    private void showSleepStatus(World world, int sleeping, int needed) {
        String statusMsg = plugin.getConfig().getString("messages.sleepingStatus",
                "<yellow>{sleeping}/{needed} игроков спит. Нужно <yellow>{needed} для пропуска ночи!");
        statusMsg = statusMsg.replace("{sleeping}", String.valueOf(sleeping))
                .replace("{needed}", String.valueOf(needed));

        int actionbarDuration = plugin.getConfig().getInt("settings.actionbar-duration", 5);
        ActionBar.sendToAll(plugin, statusMsg, actionbarDuration);
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

    private void skipNight(World world, int sleeping, int needed) {
        // Увеличиваем счётчик пропущенных ночей для метрик
        SleepSkip.incrementNightsSkipped();
        
        // Показываем сообщение "Ночь пропускается..."
        String skippingMsg = plugin.getConfig().getString("messages.nightSkipping",
                "<green>Ночь пропускается...");
        int actionbarDuration = plugin.getConfig().getInt("settings.actionbar-duration", 5);
        ActionBar.sendToAll(plugin, skippingMsg, actionbarDuration);

        // Запуск плавного перехода от ночи к дню (5 секунд = 100 тиков)
        startSmoothDayTransition(world);

        // Остановка дождя и грозы, если включено в конфиге
        if (plugin.getConfig().getBoolean("settings.skip-rain", true)) {
            world.setStorm(false);
            world.setThundering(false);
        }

        // Сброс статистики TIME_SINCE_REST для всех спящих игроков (чтобы фантомы не спавнились)
        for (Player sleepingPlayer : sleepingPlayers) {
            sleepingPlayer.setStatistic(Statistic.TIME_SINCE_REST, 0);
        }

        // После завершения перехода показываем "Доброе утро!"
        showGoodMorning(world);

        // Очищаем список спящих игроков
        sleepingPlayers.clear();
    }

    /**
     * Показывает сообщение "Доброе утро!" после завершения перехода
     */
    private void showGoodMorning(World world) {
        // Планируем задачу на момент после завершения перехода (5 секунд + небольшой буфер)
        if (plugin.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                String morningMsg = plugin.getConfig().getString("messages.nightSkipped",
                        "<green>Доброе утро!");
                ActionBar.sendToAll(plugin, morningMsg, 3);
            }, 110L); // 5.5 секунд с запасом
        } else {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    String morningMsg = plugin.getConfig().getString("messages.nightSkipped",
                            "<green>Доброе утро!");
                    ActionBar.sendToAll(plugin, morningMsg, 3);
                }
            }.runTaskLater(plugin, 110L);
        }
    }

    private void startSmoothDayTransition(World world) {
        long startTime = world.getTime();

        // Если время уже близко к утру, просто устанавливаем 0
        if (startTime < 1000L) {
            world.setTime(0);
            return;
        }

        // Плавный переход в течение 5 секунд (100 тиков)
        if (plugin.isFolia()) {
            // Folia: используем региональный планировщик
            final long[] tickCounter = {0L};
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                tickCounter[0]++;
                float progress = (float) tickCounter[0] / 100L; // 0.0 to 1.0

                // Интерполяция времени от текущего до 0 (через рассвет)
                long currentTime = (long) (startTime + (24000L - startTime) * progress);
                if (currentTime >= 24000L) {
                    currentTime = 0L;
                }
                world.setTime(currentTime);

                if (tickCounter[0] >= 100L) {
                    world.setTime(0); // Фиксируем утро
                    task.cancel();
                }
            }, 1L, 1L);
        } else {
            // Paper/Spigot: используем стандартный BukkitRunnable
            new org.bukkit.scheduler.BukkitRunnable() {
                long tick = 0L;
                @Override
                public void run() {
                    tick++;
                    float progress = (float) tick / 100L; // 0.0 to 1.0

                    // Интерполяция времени от текущего до 0 (через рассвет)
                    long currentTime = (long) (startTime + (24000L - startTime) * progress);
                    if (currentTime >= 24000L) {
                        currentTime = 0L;
                    }
                    world.setTime(currentTime);

                    if (tick >= 100L) {
                        world.setTime(0); // Фиксируем утро
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }
}