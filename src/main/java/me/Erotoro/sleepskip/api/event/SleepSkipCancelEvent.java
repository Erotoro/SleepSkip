package me.Erotoro.sleepskip.api.event;

import me.Erotoro.sleepskip.api.SleepSkipType;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an in-progress sleep skip is aborted at runtime — typically because the conditions
 * that started it are no longer met (for example sleepers left their beds before the transition
 * finished).
 *
 * <p>This event is informational and is not fired for shutdown- or reload-driven teardown, which
 * are not gameplay cancellations.
 */
public class SleepSkipCancelEvent extends SleepSkipEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    public SleepSkipCancelEvent(@NotNull World world, @NotNull SleepSkipType type, @NotNull Reason reason) {
        super(world, type);
        this.reason = reason;
    }

    /**
     * Why the skip was cancelled.
     *
     * @return the cancellation reason, never {@code null}
     */
    public @NotNull Reason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** Why an active skip was cancelled. */
    public enum Reason {

        /** The sleep conditions are no longer satisfied (sleepers woke up, target world changed, ...). */
        CONDITIONS_NOT_MET,

        /** The target world became unavailable while the skip was running. */
        WORLD_UNLOADED
    }
}
