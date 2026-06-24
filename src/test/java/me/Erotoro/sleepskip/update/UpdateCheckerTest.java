package me.Erotoro.sleepskip.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void normalizeVersionExtractsDottedNumbers() {
        assertEquals("1.9.0", UpdateChecker.normalizeVersion("v1.9.0"));
        assertEquals("1.8.0", UpdateChecker.normalizeVersion("release-1.8.0"));
        assertEquals("1.9.0", UpdateChecker.normalizeVersion("sleepskipultra-1.9.0"));
        assertEquals("1.10", UpdateChecker.normalizeVersion("1.10"));
        assertEquals("2", UpdateChecker.normalizeVersion("v2"));
    }

    @Test
    void normalizeVersionHandlesMissingNumbers() {
        assertEquals("", UpdateChecker.normalizeVersion(null));
        assertEquals("", UpdateChecker.normalizeVersion(""));
        assertEquals("", UpdateChecker.normalizeVersion("latest"));
    }

    @Test
    void isNewerComparesNumericallyNotLexically() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("1.9.1", "1.9.0"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("v1.9.1", "1.9.0"));
    }

    @Test
    void isNewerIsFalseForEqualOrOlder() {
        assertFalse(UpdateChecker.isNewer("1.9.0", "1.9.0"));
        assertFalse(UpdateChecker.isNewer("1.8.0", "1.9.0"));
        assertFalse(UpdateChecker.isNewer("1.9.0", "1.10.0"));
    }

    @Test
    void isNewerTreatsMissingTrailingPartsAsZero() {
        assertFalse(UpdateChecker.isNewer("1.9", "1.9.0"));
        assertFalse(UpdateChecker.isNewer("1.9.0", "1.9"));
        assertTrue(UpdateChecker.isNewer("1.9.1", "1.9"));
    }

    @Test
    void extractTagNameReadsGithubReleasePayload() {
        assertEquals("v1.9.0", UpdateChecker.extractTagName("{\"tag_name\":\"v1.9.0\",\"name\":\"Release\"}"));
        assertEquals("1.0", UpdateChecker.extractTagName("{ \"tag_name\" : \"1.0\" }"));
    }

    @Test
    void extractTagNameReturnsNullWhenAbsent() {
        assertNull(UpdateChecker.extractTagName("{\"name\":\"Release\"}"));
        assertNull(UpdateChecker.extractTagName(null));
    }
}
