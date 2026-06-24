package me.Erotoro.sleepskip.api.event;

import me.Erotoro.sleepskip.api.SleepSkipType;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all SleepSkipUltra lifecycle events.
 *
 * <p>Every event is fired synchronously on the thread that owns the affected world state
 * (the main thread on Paper/Spigot, the owning region or global thread on Folia), so handlers
 * may safely touch that world's state directly. Each concrete subclass declares its own
 * {@code HandlerList}, as required by Bukkit.
 */
public abstract class SleepSkipEvent extends Event {

    private final World world;
    private final SleepSkipType type;

    protected SleepSkipEvent(@NotNull World world, @NotNull SleepSkipType type) {
        this.world = world;
        this.type = type;
    }

    /**
     * The world the skip applies to.
     *
     * @return the affected world, never {@code null}
     */
    public @NotNull World getWorld() {
        return world;
    }

    /**
     * What the skip targets (a night or a thunderstorm).
     *
     * @return the skip type, never {@code null}
     */
    public @NotNull SleepSkipType getType() {
        return type;
    }
}
