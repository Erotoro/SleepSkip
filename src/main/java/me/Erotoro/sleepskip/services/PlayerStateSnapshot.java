package me.Erotoro.sleepskip.services;

import java.util.UUID;

/**
 * Immutable snapshot of the player properties used in sleep calculations.
 *
 * <p>{@code sleepWeight} is how many sleepers this player counts as (driven by the
 * {@code sleepskip.weight.<n>} permission when weighted sleep is enabled); it is always at least 1.
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
        boolean sleeping,
        int sleepWeight
) {

    /** Backwards-compatible constructor that defaults {@code sleepWeight} to 1 (no weighting). */
    public PlayerStateSnapshot(
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
        this(playerId, worldId, overworld, bypass, spectator, npc, vanished, afk, sleeping, 1);
    }
}
