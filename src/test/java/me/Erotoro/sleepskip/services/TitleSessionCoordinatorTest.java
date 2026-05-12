package me.Erotoro.sleepskip.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleSessionCoordinatorTest {

    @Test
    void claimingNewTokenInvalidatesPreviousTokenForSameScope() {
        TitleSessionCoordinator coordinator = new TitleSessionCoordinator();

        long first = coordinator.claim("sleep:global");
        long second = coordinator.claim("sleep:global");

        assertFalse(coordinator.isCurrent("sleep:global", first));
        assertTrue(coordinator.isCurrent("sleep:global", second));
    }

    @Test
    void tokensAreTrackedIndependentlyPerScope() {
        TitleSessionCoordinator coordinator = new TitleSessionCoordinator();

        long global = coordinator.claim("sleep:global");
        long world = coordinator.claim("sleep:world:test");

        assertTrue(coordinator.isCurrent("sleep:global", global));
        assertTrue(coordinator.isCurrent("sleep:world:test", world));
        assertFalse(coordinator.isCurrent("sleep:world:test", global));
    }
}
