package me.Erotoro.sleepskip.rewards;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Immutable, validated snapshot of the optional {@code rewards.*} configuration.
 *
 * <p>Parsing happens once (on enable and on reload) so the hot path that grants rewards never has
 * to touch raw config or handle malformed values. Invalid entries are dropped with a warning at
 * parse time rather than failing silently at runtime.
 */
public record RewardSettings(
        boolean enabled,
        boolean onlyNaturalSkips,
        boolean healToFull,
        boolean feed,
        int experience,
        List<PotionReward> potionEffects,
        List<String> commands,
        String message
) {

    /** A single potion effect to apply to a sleeper, with durations already converted to ticks. */
    public record PotionReward(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles) {
    }

    /** An empty, fully disabled configuration used as a safe default. */
    public static RewardSettings disabled() {
        return new RewardSettings(false, false, false, false, 0, List.of(), List.of(), "");
    }

    /** Reads and normalizes the {@code rewards.*} section. Never returns {@code null}. */
    public static RewardSettings from(FileConfiguration config, Logger logger) {
        boolean enabled = config.getBoolean("rewards.enabled", false);
        boolean onlyNaturalSkips = config.getBoolean("rewards.only-natural-skips", false);
        boolean healToFull = config.getBoolean("rewards.heal", false);
        boolean feed = config.getBoolean("rewards.feed", false);
        int experience = Math.max(0, config.getInt("rewards.experience", 0));

        String rawMessage = config.getString("rewards.message", "");
        String message = rawMessage == null ? "" : rawMessage.trim();

        List<PotionReward> potionEffects = parsePotionEffects(config.getMapList("rewards.potion-effects"), logger);
        List<String> commands = parseCommands(config.getStringList("rewards.commands"));

        return new RewardSettings(enabled, onlyNaturalSkips, healToFull, feed, experience, potionEffects, commands, message);
    }

    /** Whether any reward action is actually configured (so the listener can short-circuit). */
    public boolean hasAnyReward() {
        return healToFull
                || feed
                || experience > 0
                || !potionEffects.isEmpty()
                || !commands.isEmpty()
                || !message.isEmpty();
    }

    private static List<PotionReward> parsePotionEffects(List<Map<?, ?>> rawList, Logger logger) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }

        List<PotionReward> result = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            Object typeValue = raw.get("type");
            if (typeValue == null) {
                continue;
            }

            PotionEffectType type = resolvePotionEffectType(typeValue.toString());
            if (type == null) {
                logger.warning("Unknown reward potion effect '" + typeValue + "' was skipped.");
                continue;
            }

            int durationSeconds = Math.max(1, asInt(raw.get("duration-seconds"), 10));
            int amplifier = Math.max(0, asInt(raw.get("amplifier"), 0));
            boolean ambient = asBoolean(raw.get("ambient"), true);
            boolean particles = asBoolean(raw.get("particles"), false);
            result.add(new PotionReward(type, durationSeconds * 20, amplifier, ambient, particles));
        }
        return List.copyOf(result);
    }

    static List<String> parseCommands(List<String> rawCommands) {
        if (rawCommands == null || rawCommands.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String command : rawCommands) {
            if (command == null) {
                continue;
            }
            String trimmed = command.trim();
            if (trimmed.startsWith("/")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Resolves a configured effect name (e.g. {@code REGENERATION}, {@code fire-resistance}) to a
     * {@link PotionEffectType}. Uses the legacy name lookup, which is stable across the plugin's
     * whole supported version range; the registry-based alternative changed shape between versions.
     */
    @SuppressWarnings("deprecation")
    static PotionEffectType resolvePotionEffectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_').replace('.', '_');
        return PotionEffectType.getByName(normalized);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }
}
