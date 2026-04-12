package me.Erotoro.sleepskip.services;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEligibilityServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WORLD_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final PlayerEligibilityService service = new PlayerEligibilityService();

    @Test
    void activePlayersIgnoreAfkWhenIgnoreAfkDisabled() {
        PlayerStateSnapshot snapshot = snapshot(true, false);

        assertTrue(service.shouldCountAsActive(snapshot, WORLD_ID, false, false));
    }

    @Test
    void activePlayersExcludeAfkWhenIgnoreAfkEnabled() {
        PlayerStateSnapshot snapshot = snapshot(true, false);

        assertFalse(service.shouldCountAsActive(snapshot, WORLD_ID, false, true));
    }

    @Test
    void sleepingPlayersIgnoreAfkWhenIgnoreAfkDisabledEvenIfCountAfkSleepersIsFalse() {
        PlayerStateSnapshot snapshot = snapshot(true, true);

        assertTrue(service.shouldCountAsSleeping(snapshot, WORLD_ID, false, false, false));
    }

    @Test
    void sleepingPlayersStillRespectCountAfkSleepersWhenIgnoreAfkEnabled() {
        PlayerStateSnapshot snapshot = snapshot(true, true);

        assertFalse(service.shouldCountAsSleeping(snapshot, WORLD_ID, false, false, true));
        assertTrue(service.shouldCountAsSleeping(snapshot, WORLD_ID, false, true, true));
    }

    private PlayerStateSnapshot snapshot(boolean afk, boolean sleeping) {
        return new PlayerStateSnapshot(
                PLAYER_ID,
                WORLD_ID,
                true,
                false,
                false,
                false,
                false,
                afk,
                sleeping
        );
    }
}
