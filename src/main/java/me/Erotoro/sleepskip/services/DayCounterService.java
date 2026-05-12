package me.Erotoro.sleepskip.services;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks the displayed day number from the world's actual full time and manages sunrise announcements.
 */
public class DayCounterService {
    private static final long NATURAL_DAY_POLL_INTERVAL_TICKS = 20L;

    private final SleepSkip plugin;
    private final MorningAnnouncementService morningAnnouncementService;
    private final File dataFile;
    private YamlConfiguration dataConfig;
    private PlatformScheduler.TaskHandle monitorTask = () -> { };

    public DayCounterService(SleepSkip plugin) {
        this.plugin = plugin;
        this.morningAnnouncementService = plugin.getMorningAnnouncementService();
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public synchronized void reload() {
        stop();

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for day counter storage.");
        }
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("Failed to create data.yml for day counter storage.");
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to create data.yml: " + exception.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        initializeWorldState();
        startNaturalDayMonitor();
    }

    public synchronized void stop() {
        monitorTask.cancel();
        monitorTask = () -> { };
    }

    public synchronized int getDayCount(World world) {
        return world == null ? 1 : displayDay(world);
    }

    public void announceSleepSkipMorning(World world, Collection<UUID> recipientsOverride) {
        if (!isEnabled() || world == null) {
            return;
        }

        long currentDayIndex = currentDayIndex(world);
        synchronized (this) {
            long lastAnnouncedDayIndex = dataConfig.getLong(path(world, "last-announced-day-index"), -1L);
            if (lastAnnouncedDayIndex >= currentDayIndex) {
                return;
            }
            saveWorldState(world, currentDayIndex, true);
        }
        announce(world, recipientsOverride, displayDay(world));
    }

    public void scheduleSleepSkipMorningAnnouncement(World world, Collection<UUID> recipientsOverride) {
        if (!isEnabled() || world == null) {
            return;
        }

        SleepOverlayService overlayService = plugin.getSleepOverlayService();
        if (overlayService == null) {
            PlatformScheduler.runGlobalDelayed(plugin, () -> announceSleepSkipMorning(world, recipientsOverride), 1L);
            return;
        }

        overlayService.runAfterCompletionGrace(
                world,
                () -> announceSleepSkipMorning(world, recipientsOverride)
        );
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("day-counter.enabled", true);
    }

    private void initializeWorldState() {
        for (World world : Bukkit.getWorlds()) {
            if (!isTrackedWorld(world)) {
                continue;
            }
            saveWorldState(world, currentDayIndex(world), false);
        }
        save();
    }

    private void startNaturalDayMonitor() {
        monitorTask = PlatformScheduler.runGlobalAtFixedRate(
                plugin,
                this::pollNaturalDayChanges,
                NATURAL_DAY_POLL_INTERVAL_TICKS,
                NATURAL_DAY_POLL_INTERVAL_TICKS
        );
    }

    private void pollNaturalDayChanges() {
        if (!isEnabled()) {
            return;
        }

        synchronized (this) {
            boolean changed = false;
            for (World world : Bukkit.getWorlds()) {
                if (!isTrackedWorld(world)) {
                    continue;
                }

                long currentDayIndex = currentDayIndex(world);
                long previousDayIndex = dataConfig.getLong(path(world, "last-seen-day-index"), currentDayIndex);
                if (currentDayIndex <= previousDayIndex) {
                    continue;
                }

                saveWorldState(world, currentDayIndex, false);
                changed = true;

                if (shouldAnnounceNaturalSunrise()) {
                    long lastAnnouncedDayIndex = dataConfig.getLong(path(world, "last-announced-day-index"), -1L);
                    if (currentDayIndex > lastAnnouncedDayIndex) {
                        dataConfig.set(path(world, "last-announced-day-index"), currentDayIndex);
                        announce(world, null, displayDay(world));
                    }
                }
            }

            if (changed) {
                save();
            }
        }
    }

    private void announce(World world, Collection<UUID> recipientsOverride, int dayCount) {
        if (world == null || !isEnabled()) {
            return;
        }

        Set<UUID> recipients = resolveRecipients(world, recipientsOverride);
        if (recipients.isEmpty()) {
            return;
        }
        morningAnnouncementService.announceMorning(world, recipients, dayCount);
    }

    private boolean shouldAnnounceNaturalSunrise() {
        return plugin.getConfig().getBoolean("day-counter.announce-natural-sunrise", true);
    }

    private Set<UUID> resolveRecipients(World world, Collection<UUID> recipientsOverride) {
        if (plugin.getConfig().getBoolean("day-counter.show-to-all", true)) {
            Set<UUID> allPlayers = new LinkedHashSet<>();
            for (Player player : world.getPlayers()) {
                allPlayers.add(player.getUniqueId());
            }
            return allPlayers;
        }

        return recipientsOverride == null ? Set.of() : Set.copyOf(recipientsOverride);
    }

    private boolean isTrackedWorld(World world) {
        return world != null && world.getEnvironment() == World.Environment.NORMAL;
    }

    private void saveWorldState(World world, long dayIndex, boolean announced) {
        rememberWorldName(world);
        dataConfig.set(path(world, "last-seen-day-index"), dayIndex);
        if (announced) {
            dataConfig.set(path(world, "last-announced-day-index"), dayIndex);
        } else if (!dataConfig.contains(path(world, "last-announced-day-index"))) {
            dataConfig.set(path(world, "last-announced-day-index"), dayIndex - 1L);
        }
    }

    private void rememberWorldName(World world) {
        if (world != null && plugin.getConfig().getBoolean("settings.per-world", false)) {
            dataConfig.set(path(world, "name"), world.getName());
        }
    }

    private long currentDayIndex(World world) {
        return Math.max(0L, world.getFullTime() / 24000L);
    }

    private int displayDay(World world) {
        return computeDisplayDay(world.getFullTime());
    }

    public static int computeDisplayDay(long fullTime) {
        long dayIndex = Math.max(0L, fullTime / 24000L);
        return (int) Math.min(Integer.MAX_VALUE, dayIndex);
    }

    private String path(World world, String field) {
        if (!plugin.getConfig().getBoolean("settings.per-world", false)) {
            return "day-counter.global." + field;
        }
        return "day-counter.worlds." + world.getUID() + "." + field;
    }

    private void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save data.yml: " + exception.getMessage());
        }
    }
}
