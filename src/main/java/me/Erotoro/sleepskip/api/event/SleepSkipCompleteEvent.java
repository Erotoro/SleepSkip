package me.Erotoro.sleepskip.api.event;

import me.Erotoro.sleepskip.api.SleepSkipType;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.UUID;

/**
 * Fired after a sleep skip has fully completed: the world has reached morning (or the storm has
 * cleared) and sleeping players are about to be woken.
 *
 * <p>This is the natural hook for "sleep reward" behaviour. {@link #getSleepers()} contains the
 * players who were actually asleep at completion, which is usually who you want to reward, while
 * {@link #getRecipients()} contains everyone who received skip feedback.
 *
 * <p>The event is not cancellable — the skip has already happened.
 */
public class SleepSkipCompleteEvent extends SleepSkipEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Set<UUID> sleepers;
    private final Set<UUID> recipients;
    private final boolean instant;
    private final boolean forced;

    public SleepSkipCompleteEvent(
            @NotNull World world,
            @NotNull SleepSkipType type,
            @NotNull Set<UUID> sleepers,
            @NotNull Set<UUID> recipients,
            boolean instant,
            boolean forced
    ) {
        super(world, type);
        this.sleepers = Set.copyOf(sleepers);
        this.recipients = Set.copyOf(recipients);
        this.instant = instant;
        this.forced = forced;
    }

    /**
     * Unique ids of the players who were asleep in this world when the skip completed.
     *
     * @return an immutable snapshot of the sleeper ids
     */
    public @NotNull @Unmodifiable Set<UUID> getSleepers() {
        return sleepers;
    }

    /**
     * Unique ids of the players who received skip feedback (titles, bossbar, messages).
     *
     * @return an immutable snapshot of the recipient ids
     */
    public @NotNull @Unmodifiable Set<UUID> getRecipients() {
        return recipients;
    }

    /**
     * Whether the skip completed instantly (via {@code /sleep forceskip --instant}) rather than
     * through the smooth sunrise transition.
     *
     * @return {@code true} for an instant skip
     */
    public boolean isInstant() {
        return instant;
    }

    /**
     * Whether the skip was triggered by an admin {@code /sleep forceskip} rather than by players
     * naturally reaching the sleep threshold.
     *
     * @return {@code true} if forced by a command
     */
    public boolean isForced() {
        return forced;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
