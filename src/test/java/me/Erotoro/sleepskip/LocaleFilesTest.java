package me.Erotoro.sleepskip;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleFilesTest {

    @Test
    void messagesSectionHasNoDuplicateKeys() throws IOException {
        Path langDir = Path.of("src", "main", "resources", "lang");
        for (String language : List.of("en.yml", "ru.yml", "ua.yml")) {
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
        for (String language : List.of("en.yml", "ru.yml", "ua.yml")) {
            Path file = langDir.resolve(language);
            String content = Files.readString(file);
            List<String> invalidCharacters = findDisallowedControlCharacters(content);
            assertTrue(
                    invalidCharacters.isEmpty(),
                    () -> "Invalid control characters in " + language + ": " + invalidCharacters
            );
        }
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
