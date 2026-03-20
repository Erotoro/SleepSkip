package me.Erotoro.sleepskip.services;

import java.util.UUID;

/**
 * Immutable snapshot of the player properties used in sleep calculations.
 */
public record PlayerStateSnapshot(
        UUID playerId,
        UUID worldId,
        boolean overworld,
        boolean bypass,
        boolean spectator,
        boolean npc,
        boolean vanished,
        boolean afk,
        boolean sleeping
) {
}
