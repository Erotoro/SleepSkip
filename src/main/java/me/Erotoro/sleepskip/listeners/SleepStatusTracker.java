package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.services.PlayerEligibilityService;
import me.Erotoro.sleepskip.services.PlayerStateService;
import me.Erotoro.sleepskip.services.PlayerStateSnapshot;
import me.Erotoro.sleepskip.util.SleepRequirementCalculator;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SleepStatusTracker {

    private static final SleepListener.SleepStatus NEUTRAL_SLEEP_STATUS = new SleepListener.SleepStatus(0, 0, 1);
    private static final SleepRuntimeSessions.SleepState NEUTRAL_SLEEP_STATE =
            new SleepRuntimeSessions.SleepState(List.of(), List.of(), 0, 1);

    private final SleepSkip plugin;
    private final PlayerStateService playerStateService;
    private final PlayerEligibilityService eligibilityService;
    private final Set<UUID> sleepingPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, SleepRuntimeSessions.CachedSleepStatus> cachedStatuses = new ConcurrentHashMap<>();

    SleepStatusTracker(SleepSkip plugin, PlayerStateService playerStateService, PlayerEligibilityService eligibilityService) {
        this.plugin = plugin;
        this.playerStateService = playerStateService;
        this.eligibilityService = eligibilityService;
    }

    void markSleeping(UUID playerId) {
        sleepingPlayers.add(playerId);
    }

    void unmarkSleeping(UUID playerId) {
        sleepingPlayers.remove(playerId);
    }

    List<UUID> sleepingPlayersSnapshot() {
        return List.copyOf(sleepingPlayers);
    }

    void removeSleepingPlayersForWorld(World world) {
        UUID worldId = world.getUID();
        sleepingPlayers.removeIf(uuid -> {
            PlayerStateSnapshot snapshot = playerStateService.getSnapshot(uuid);
            return snapshot == null || snapshot.worldId().equals(worldId);
        });
        invalidate(world);
    }

    void cleanupSleepingPlayers() {
        sleepingPlayers.retainAll(playerStateService.getKnownPlayerIds());
    }

    void resetSleepingPlayersFromSnapshots() {
        sleepingPlayers.clear();
        for (PlayerStateSnapshot snapshot : playerStateService.getSnapshots()) {
            if (snapshot != null && snapshot.sleeping()) {
                sleepingPlayers.add(snapshot.playerId());
            }
        }
    }

    void invalidate(World world) {
        if (world != null) {
            cachedStatuses.remove(world.getUID());
        }
    }

    void invalidateAll() {
        cachedStatuses.clear();
    }

    SleepListener.SleepStatus getSleepStatus(World world, boolean bypassCache, long cacheTtlMs, boolean isOverworld) {
        if (world == null || !isOverworld) {
            return NEUTRAL_SLEEP_STATUS;
        }

        if (!bypassCache) {
            SleepRuntimeSessions.CachedSleepStatus cached = cachedStatuses.get(world.getUID());
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.cachedAtMs() <= cacheTtlMs) {
                return cached.status();
            }
        }

        SleepListener.SleepStatus recalculated = calculateSleepStatus(world);
        cachedStatuses.put(world.getUID(), new SleepRuntimeSessions.CachedSleepStatus(recalculated, System.currentTimeMillis()));
        return recalculated;
    }

    SleepRuntimeSessions.SleepState getSleepState(World world, boolean isOverworld, boolean bypassCache, long cacheTtlMs) {
        if (world == null || !isOverworld) {
            return NEUTRAL_SLEEP_STATE;
        }

        List<UUID> recipients = new ArrayList<>();
        List<UUID> overlayRecipients = new ArrayList<>();
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);
        boolean countAfkSleepers = plugin.getConfig().getBoolean("settings.count-afk-sleepers", true);
        boolean ignoreAfk = plugin.getConfig().getBoolean("settings.ignore-afk", true);
        UUID targetWorldId = world.getUID();

        for (PlayerStateSnapshot snapshot : playerStateService.getSnapshots()) {
            if (eligibilityService.shouldCountAsActive(snapshot, targetWorldId, perWorld, ignoreAfk)) {
                recipients.add(snapshot.playerId());
            }
            if (sleepingPlayers.contains(snapshot.playerId())
                    && eligibilityService.shouldCountAsSleeping(snapshot, targetWorldId, perWorld, countAfkSleepers, ignoreAfk)) {
                overlayRecipients.add(snapshot.playerId());
            }
        }

        SleepListener.SleepStatus status = getSleepStatus(world, bypassCache, cacheTtlMs, isOverworld);
        return new SleepRuntimeSessions.SleepState(recipients, overlayRecipients, status.sleepingPlayers(), status.requiredPlayers());
    }

    private SleepListener.SleepStatus calculateSleepStatus(World world) {
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);
        boolean countAfkSleepers = plugin.getConfig().getBoolean("settings.count-afk-sleepers", true);
        boolean ignoreAfk = plugin.getConfig().getBoolean("settings.ignore-afk", true);
        UUID targetWorldId = world.getUID();
        int activePlayers = 0;
        int sleeping = 0;

        for (PlayerStateSnapshot snapshot : playerStateService.getSnapshots()) {
            if (eligibilityService.shouldCountAsActive(snapshot, targetWorldId, perWorld, ignoreAfk)) {
                activePlayers++;
            }

            if (sleepingPlayers.contains(snapshot.playerId())
                    && eligibilityService.shouldCountAsSleeping(snapshot, targetWorldId, perWorld, countAfkSleepers, ignoreAfk)) {
                sleeping++;
            }
        }

        int requiredPlayers = SleepRequirementCalculator.calculate(
                plugin.getConfig().getString("settings.required-type", "percent"),
                plugin.getConfig().getDouble("settings.required-value", 50D),
                activePlayers
        );

        return new SleepListener.SleepStatus(activePlayers, sleeping, requiredPlayers);
    }
}
