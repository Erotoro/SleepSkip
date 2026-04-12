package me.Erotoro.sleepskip.services;

import java.util.UUID;

/**
 * Centralizes all rules that decide whether a player participates in sleep calculations.
 */
public class PlayerEligibilityService {

    public boolean shouldCountAsActive(
            PlayerStateSnapshot snapshot,
            UUID targetWorldId,
            boolean perWorld,
            boolean ignoreAfk
    ) {
        if (!passesCommonChecks(snapshot, targetWorldId, perWorld)) {
            return false;
        }

        return !ignoreAfk || !snapshot.afk();
    }

    public boolean shouldCountAsSleeping(
            PlayerStateSnapshot snapshot,
            UUID targetWorldId,
            boolean perWorld,
            boolean countAfkSleepers,
            boolean ignoreAfk
    ) {
        if (!passesCommonChecks(snapshot, targetWorldId, perWorld)) {
            return false;
        }

        if (!ignoreAfk) {
            return true;
        }
        return countAfkSleepers || !snapshot.afk();
    }

    private boolean passesCommonChecks(PlayerStateSnapshot snapshot, UUID targetWorldId, boolean perWorld) {
        if (snapshot == null) {
            return false;
        }
        if (!snapshot.overworld()) {
            return false;
        }
        if (perWorld && !snapshot.worldId().equals(targetWorldId)) {
            return false;
        }
        if (snapshot.bypass() || snapshot.spectator() || snapshot.npc() || snapshot.vanished()) {
            return false;
        }
        return true;
    }
}
