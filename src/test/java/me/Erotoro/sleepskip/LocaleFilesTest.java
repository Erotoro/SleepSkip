package me.Erotoro.sleepskip;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleFilesTest {

    private static final List<String> ALL_LOCALE_FILES = LocaleManager.BUNDLED_LANGUAGES.stream()
            .map(language -> language + ".yml")
            .toList();

    @Test
    void messagesSectionHasNoDuplicateKeys() throws IOException {
        Path langDir = Path.of("src", "main", "resources", "lang");
        for (String language : ALL_LOCALE_FILES) {
            Path file = langDir.resolve(language);
            List<String> duplicates = findDuplicateMessageKeys(file);
            assertTrue(
                    duplicates.isEmpty(),
                    () -> "Duplicate message keys in " + language + ": " + duplicates
            );
        }
    }

    @Test
    void localeFilesContainNoDisallowedControlCharacters() throws IOException {
        Path langDir = Path.of("src", "main", "resources", "lang");
        for (String language : ALL_LOCALE_FILES) {
            Path file = langDir.resolve(language);
            String content = Files.readString(file);
            List<String> invalidCharacters = findDisallowedControlCharacters(content);
            assertTrue(
                    invalidCharacters.isEmpty(),
                    () -> "Invalid control characters in " + language + ": " + invalidCharacters
            );
        }
    }

    @Test
    void everyLocaleHasTheSameKeysAsEnglish() {
        Path langDir = Path.of("src", "main", "resources", "lang");
        Set<String> englishKeys = loadKeys(langDir.resolve("en.yml"));
        assertTrue(!englishKeys.isEmpty(), () -> "en.yml has no keys");

        for (String language : ALL_LOCALE_FILES) {
            if ("en.yml".equals(language)) {
                continue;
            }
            Set<String> localeKeys = loadKeys(langDir.resolve(language));

            Set<String> missing = new TreeSet<>(englishKeys);
            missing.removeAll(localeKeys);
            Set<String> extra = new TreeSet<>(localeKeys);
            extra.removeAll(englishKeys);

            assertTrue(missing.isEmpty(), () -> "Missing keys in " + language + ": " + missing);
            assertTrue(extra.isEmpty(), () -> "Unexpected extra keys in " + language + ": " + extra);
        }
    }

    private Set<String> loadKeys(Path file) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
        return new HashSet<>(configuration.getKeys(true));
    }

    private List<String> findDuplicateMessageKeys(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        boolean inMessages = false;

        for (String line : lines) {
            if (!inMessages) {
                if ("messages:".equals(line.trim())) {
                    inMessages = true;
                }
                continue;
            }

            if (!line.startsWith("  ")) {
                break;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int keyEnd = trimmed.indexOf(':');
            if (keyEnd <= 0) {
                continue;
            }

            String key = trimmed.substring(0, keyEnd);
            if (!seen.add(key) && !duplicates.contains(key)) {
                duplicates.add(key);
            }
        }

        return duplicates;
    }

    private List<String> findDisallowedControlCharacters(String content) {
        List<String> invalid = new ArrayList<>();
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (!Character.isISOControl(character)) {
                continue;
            }
            if (character == '\n' || character == '\r' || character == '\t') {
                continue;
            }
            invalid.add("index=" + index + ",code=U+" + String.format("%04X", (int) character));
        }
        return invalid;
    }
}
