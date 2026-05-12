package me.Erotoro.sleepskip.services;

import me.Erotoro.sleepskip.SleepSkip;
import me.Erotoro.sleepskip.util.PlatformScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presentation service for sunrise/day announcements, including optional typewriter animation and sound.
 */
public class MorningAnnouncementService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final SleepSkip plugin;
    private final TitleSessionCoordinator titleSessionCoordinator;
    private final ConcurrentHashMap<UUID, PlatformScheduler.TaskHandle> activeAnimations = new ConcurrentHashMap<>();

    public MorningAnnouncementService(SleepSkip plugin) {
        this.plugin = plugin;
        this.titleSessionCoordinator = plugin != null ? plugin.getTitleSessionCoordinator() : new TitleSessionCoordinator();
    }

    public void announceMorning(World world, Collection<UUID> recipients, int dayCount) {
        if (world == null || recipients == null || recipients.isEmpty()) {
            return;
        }

        String scopeKey = scopeKey(world);
        long token = titleSessionCoordinator.claim(scopeKey);
        cancelWorldAnimation(world.getUID());

        AnnouncementConfig config = readConfig(dayCount);
        if (!config.animationEnabled() || "static".equalsIgnoreCase(config.animationMode())) {
            showStaticAnnouncement(world, recipients, config, scopeKey, token);
            return;
        }

        List<String> frames = buildTypewriterFrames(config.fullTitle());
        if (frames.isEmpty()) {
            showFrame(world, recipients, config.fullTitle(), config.subtitle(), config.fadeInTicks(), config.stayTicks(), config.fadeOutTicks(), scopeKey, token);
            if (config.playSoundOnFinish()) {
                playConfiguredSound(world, recipients, config, scopeKey, token);
            }
            return;
        }

        final int[] frameIndex = {0};
        final PlatformScheduler.TaskHandle[] handleRef = new PlatformScheduler.TaskHandle[1];
        handleRef[0] = PlatformScheduler.runGlobalAtFixedRate(
                plugin,
                () -> {
                    if (!titleSessionCoordinator.isCurrent(scopeKey, token)) {
                        cancelWorldAnimation(world.getUID());
                        return;
                    }
                    if (frameIndex[0] >= frames.size()) {
                        cancelWorldAnimation(world.getUID());
                        showFrame(
                                world,
                                recipients,
                                config.fullTitle(),
                                config.subtitle(),
                                config.fadeInTicks(),
                                Math.max(config.stayTicks(), config.finalHoldTicks()),
                                config.fadeOutTicks(),
                                scopeKey,
                                token
                        );
                        if (config.playSoundOnFinish()) {
                            playConfiguredSound(world, recipients, config, scopeKey, token);
                        }
                        return;
                    }

                    String currentFrame = frames.get(frameIndex[0]);
                    boolean finalTypingFrame = frameIndex[0] == frames.size() - 1;
                    String subtitle = finalTypingFrame ? config.subtitle() : "";
                    showFrame(world, recipients, currentFrame, subtitle, 0, config.stepIntervalTicks() + 2, 0, scopeKey, token);
                    if (config.playSoundOnEachStep()) {
                        playConfiguredSound(world, recipients, config, scopeKey, token);
                    }
                    frameIndex[0]++;
                },
                1L,
                config.stepIntervalTicks()
        );
        activeAnimations.put(world.getUID(), handleRef[0]);
    }

    public void stop() {
        for (UUID worldId : Set.copyOf(activeAnimations.keySet())) {
            cancelWorldAnimation(worldId);
        }
    }

    private void showStaticAnnouncement(World world, Collection<UUID> recipients, AnnouncementConfig config, String scopeKey, long token) {
        showFrame(
                world,
                recipients,
                config.fullTitle(),
                config.subtitle(),
                config.fadeInTicks(),
                Math.max(config.stayTicks(), config.finalHoldTicks()),
                config.fadeOutTicks(),
                scopeKey,
                token
        );
        if (config.playSoundOnFinish()) {
            playConfiguredSound(world, recipients, config, scopeKey, token);
        }
    }

    private void cancelWorldAnimation(UUID worldId) {
        PlatformScheduler.TaskHandle handle = activeAnimations.remove(worldId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private AnnouncementConfig readConfig(int dayCount) {
        String titleTemplate = configuredOrLocalized(
                "day-counter.title-format",
                "messages.day-counter-title",
                "<gold><bold>Day {day}</bold></gold>"
        );
        String subtitleTemplate = configuredOrLocalized(
                "day-counter.subtitle-format",
                "messages.day-counter-subtitle",
                "<gray>Good morning!</gray>"
        );

        return new AnnouncementConfig(
                titleTemplate.replace("{day}", Integer.toString(dayCount)),
                subtitleTemplate.replace("{day}", Integer.toString(dayCount)),
                plugin.getConfig().getBoolean("day-counter.animation.enabled", false),
                plugin.getConfig().getString("day-counter.animation.mode", "typewriter"),
                Math.max(1L, plugin.getConfig().getLong("day-counter.animation.step-interval-ticks", 2L)),
                Math.max(1L, plugin.getConfig().getLong("day-counter.animation.final-hold-ticks", 30L)),
                Math.max(0, plugin.getConfig().getInt("day-counter.fade-in-ticks", 10)),
                Math.max(1, plugin.getConfig().getInt("day-counter.stay-ticks", 50)),
                Math.max(0, plugin.getConfig().getInt("day-counter.fade-out-ticks", 20)),
                plugin.getConfig().getBoolean("day-counter.sound.enabled", false),
                plugin.getConfig().getString("day-counter.sound.type", "BLOCK_TRIPWIRE_CLICK_ON"),
                (float) plugin.getConfig().getDouble("day-counter.sound.volume", 0.7D),
                (float) plugin.getConfig().getDouble("day-counter.sound.pitch", 1.35D),
                plugin.getConfig().getBoolean("day-counter.sound.play-on-each-step", true),
                plugin.getConfig().getBoolean("day-counter.sound.play-on-finish", true)
        );
    }

    private List<String> buildTypewriterFrames(String fullTitle) {
        List<StyledCharacter> styledCharacters = parseStyledCharacters(fullTitle);
        if (styledCharacters.isEmpty()) {
            return List.of();
        }

        List<String> frames = new ArrayList<>();
        List<String> initialTags = styledCharacters.getFirst().activeTags();
        frames.add(renderStyledText(List.of(), initialTags, true));
        for (int visibleLength = 1; visibleLength <= styledCharacters.size(); visibleLength++) {
            frames.add(renderStyledText(styledCharacters.subList(0, visibleLength), initialTags, true));
        }
        frames.add(renderStyledText(styledCharacters, initialTags, false));
        return frames;
    }

    static List<String> buildTypewriterFramesForTests(String fullTitle) {
        return new MorningAnnouncementService(null).buildTypewriterFrames(fullTitle);
    }

    private List<StyledCharacter> parseStyledCharacters(String fullTitle) {
        if (fullTitle == null || fullTitle.isBlank()) {
            return List.of();
        }

        List<StyledCharacter> characters = new ArrayList<>();
        List<String> activeTags = new ArrayList<>();
        int index = 0;
        while (index < fullTitle.length()) {
            char current = fullTitle.charAt(index);
            if (current == '<') {
                int end = fullTitle.indexOf('>', index);
                if (end < 0) {
                    break;
                }

                String tag = fullTitle.substring(index, end + 1);
                applyTag(activeTags, tag);
                index = end + 1;
                continue;
            }

            characters.add(new StyledCharacter(String.valueOf(current), List.copyOf(activeTags)));
            index++;
        }
        return characters;
    }

    private void applyTag(List<String> activeTags, String tag) {
        if (tag == null || tag.length() < 3) {
            return;
        }

        if (tag.startsWith("</")) {
            String tagName = normalizeTagName(tag);
            for (int index = activeTags.size() - 1; index >= 0; index--) {
                if (normalizeTagName(activeTags.get(index)).equals(tagName)) {
                    activeTags.remove(index);
                    break;
                }
            }
            return;
        }

        if (!tag.endsWith("/>")) {
            activeTags.add(tag);
        }
    }

    private String normalizeTagName(String tag) {
        String normalized = tag.substring(1, tag.length() - 1).trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator);
        }
        separator = normalized.indexOf(' ');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator);
        }
        return normalized.toLowerCase();
    }

    private String renderStyledText(List<StyledCharacter> characters, List<String> fallbackTags, boolean appendCursor) {
        StringBuilder rendered = new StringBuilder();
        List<String> currentTags = Collections.emptyList();

        for (StyledCharacter character : characters) {
            rendered.append(transitionTags(currentTags, character.activeTags()));
            rendered.append(character.value());
            currentTags = character.activeTags();
        }

        if (appendCursor) {
            List<String> cursorTags = characters.isEmpty()
                    ? fallbackTags
                    : characters.get(characters.size() - 1).activeTags();
            rendered.append(transitionTags(currentTags, cursorTags));
            rendered.append("_");
            currentTags = cursorTags;
        }

        rendered.append(closeTags(currentTags));
        return rendered.toString();
    }

    private String transitionTags(List<String> currentTags, List<String> targetTags) {
        if (currentTags.equals(targetTags)) {
            return "";
        }

        int sharedPrefixLength = 0;
        int maxShared = Math.min(currentTags.size(), targetTags.size());
        while (sharedPrefixLength < maxShared
                && currentTags.get(sharedPrefixLength).equals(targetTags.get(sharedPrefixLength))) {
            sharedPrefixLength++;
        }

        StringBuilder transition = new StringBuilder();
        transition.append(closeTags(currentTags, sharedPrefixLength));
        for (int index = sharedPrefixLength; index < targetTags.size(); index++) {
            String tag = targetTags.get(index);
            transition.append(tag);
        }
        return transition.toString();
    }

    private String closeTags(List<String> activeTags) {
        return closeTags(activeTags, 0);
    }

    private String closeTags(List<String> activeTags, int keepPrefixLength) {
        if (activeTags.isEmpty()) {
            return "";
        }

        StringBuilder closing = new StringBuilder();
        for (int index = activeTags.size() - 1; index >= keepPrefixLength; index--) {
            closing.append("</").append(normalizeTagName(activeTags.get(index))).append('>');
        }
        return closing.toString();
    }

    private void showFrame(
            World world,
            Collection<UUID> recipients,
            String titleText,
            String subtitleText,
            int fadeInTicks,
            long stayTicks,
            int fadeOutTicks,
            String scopeKey,
            long token
    ) {
        Component title = MINI_MESSAGE.deserialize(titleText == null ? "" : titleText);
        Component subtitle = MINI_MESSAGE.deserialize(subtitleText == null ? "" : subtitleText);
        Title rendered = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(Math.max(0, fadeInTicks) * 50L),
                        Duration.ofMillis(Math.max(1L, stayTicks) * 50L),
                        Duration.ofMillis(Math.max(0, fadeOutTicks) * 50L)
                )
        );

        for (UUID recipient : recipients) {
            Player player = Bukkit.getPlayer(recipient);
            if (player == null || !player.isOnline()) {
                continue;
            }
            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (titleSessionCoordinator.isCurrent(scopeKey, token) && player.isOnline() && player.getWorld().equals(world)) {
                    player.showTitle(rendered);
                }
            });
        }
    }

    private void playConfiguredSound(World world, Collection<UUID> recipients, AnnouncementConfig config, String scopeKey, long token) {
        if (!config.soundEnabled()) {
            return;
        }

        Sound sound = resolveSound(config.soundType());
        if (sound == null) {
            return;
        }

        for (UUID recipient : recipients) {
            Player player = Bukkit.getPlayer(recipient);
            if (player == null || !player.isOnline()) {
                continue;
            }
            PlatformScheduler.runForPlayer(plugin, player, () -> {
                if (titleSessionCoordinator.isCurrent(scopeKey, token) && player.isOnline() && player.getWorld().equals(world)) {
                    player.playSound(player.getLocation(), sound, config.soundVolume(), config.soundPitch());
                }
            });
        }
    }

    private String scopeKey(World world) {
        if (world == null) {
            return "sleep:global";
        }
        boolean perWorld = plugin.getConfig().getBoolean("settings.per-world", false);
        return perWorld ? "sleep:world:" + world.getUID() : "sleep:global";
    }

    private Sound resolveSound(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }

        String normalized = configured.trim().toUpperCase().replace('.', '_').replace(':', '_');
        try {
            return Sound.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown morning announcement sound: " + configured);
            return null;
        }
    }

    private String configuredOrLocalized(String configPath, String localeKey, String fallback) {
        String configured = plugin.getConfig().getString(configPath, "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return plugin.tr(localeKey, fallback);
    }

    private record AnnouncementConfig(
            String fullTitle,
            String subtitle,
            boolean animationEnabled,
            String animationMode,
            long stepIntervalTicks,
            long finalHoldTicks,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks,
            boolean soundEnabled,
            String soundType,
            float soundVolume,
            float soundPitch,
            boolean playSoundOnEachStep,
            boolean playSoundOnFinish
    ) {
    }

    private record StyledCharacter(String value, List<String> activeTags) {
    }
}
