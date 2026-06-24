package me.Erotoro.sleepskip.api.event;

import me.Erotoro.sleepskip.api.SleepSkipType;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.UUID;

/**
 * Fired right before a sleep skip begins, once the plugin has committed to a skip session
 * for a world but before any time, weather, or visual transition is applied.
 *
 * <p>This event is {@link Cancellable}. Cancelling it aborts the skip cleanly: no time change,
 * no weather change, and the vanilla sleeping gamerule is restored to its previous value. The
 * event is re-fired the next time the skip conditions are met (for example after another player
 * gets into bed), so a handler can veto an individual attempt without permanently disabling the
 * feature.
 *
 * <p>{@link #isForced()} distinguishes natural sleeping from an admin {@code /sleep forceskip};
 * handlers that only want to gate natural skips should check it.
 */
public class SleepSkipStartEvent extends SleepSkipEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int sleepingPlayers;
    private final int requiredPlayers;
    private final Set<UUID> recipients;
    private final boolean forced;
    private boolean cancelled;

    public SleepSkipStartEvent(
            @NotNull World world,
            @NotNull SleepSkipType type,
            int sleepingPlayers,
            int requiredPlayers,
            @NotNull Set<UUID> recipients,
            boolean forced
    ) {
        super(world, type);
        this.sleepingPlayers = sleepingPlayers;
        this.requiredPlayers = requiredPlayers;
        this.recipients = Set.copyOf(recipients);
        this.forced = forced;
    }

    /**
     * Number of players counted as sleeping when the skip started.
     *
     * @return the sleeping count
     */
    public int getSleepingPlayers() {
        return sleepingPlayers;
    }

    /**
     * Number of players that were required to trigger the skip.
     *
     * @return the required count (always at least 1)
     */
    public int getRequiredPlayers() {
        return requiredPlayers;
    }

    /**
     * Unique ids of the players who will receive skip feedback (titles, bossbar, messages).
     *
     * @return an immutable snapshot of the recipient ids
     */
    public @NotNull @Unmodifiable Set<UUID> getRecipients() {
        return recipients;
    }

    /**
     * Whether this skip was triggered by an admin {@code /sleep forceskip} rather than by
     * players naturally reaching the sleep threshold.
     *
     * @return {@code true} if forced by a command
     */
    public boolean isForced() {
        return forced;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
