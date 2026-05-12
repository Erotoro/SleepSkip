package me.Erotoro.sleepskip.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MorningAnnouncementServiceTest {

    @Test
    void typewriterFramesPreserveInlineMiniMessageFormatting() {
        List<String> frames = MorningAnnouncementService.buildTypewriterFramesForTests(
                "<gold>Day <white>12</white></gold>"
        );

        assertEquals("<gold>_</gold>", frames.get(0));
        assertEquals("<gold>D_</gold>", frames.get(1));
        assertEquals("<gold>Day <white>1_</white></gold>", frames.get(5));
        assertEquals("<gold>Day <white>12_</white></gold>", frames.get(6));
        assertEquals("<gold>Day <white>12</white></gold>", frames.get(7));
    }
}
