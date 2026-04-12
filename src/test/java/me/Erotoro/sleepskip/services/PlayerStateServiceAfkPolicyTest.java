package me.Erotoro.sleepskip.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateServiceAfkPolicyTest {

    @Test
    void afkIsAlwaysFalseWhenIgnoreAfkDisabled() {
        assertFalse(PlayerStateService.resolveAfkFlag(false, false, false));
        assertFalse(PlayerStateService.resolveAfkFlag(false, true, false));
        assertFalse(PlayerStateService.resolveAfkFlag(false, false, true));
        assertFalse(PlayerStateService.resolveAfkFlag(false, true, true));
    }

    @Test
    void afkIsUnionOfProvidersWhenIgnoreAfkEnabled() {
        assertFalse(PlayerStateService.resolveAfkFlag(true, false, false));
        assertTrue(PlayerStateService.resolveAfkFlag(true, true, false));
        assertTrue(PlayerStateService.resolveAfkFlag(true, false, true));
        assertTrue(PlayerStateService.resolveAfkFlag(true, true, true));
    }
}
