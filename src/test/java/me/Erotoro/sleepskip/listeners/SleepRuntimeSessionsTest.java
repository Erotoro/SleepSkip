package me.Erotoro.sleepskip.listeners;

import me.Erotoro.sleepskip.util.SleepTimingRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SleepRuntimeSessionsTest {

    @Test
    void activeSkipSessionKeepsImmutableSleeperSnapshotForCompletionRewards() {
        UUID worldId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID recipientId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID sleeperId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        List<UUID> sleepers = new ArrayList<>(List.of(sleeperId));

        SleepRuntimeSessions.ActiveSkipSession session = new SleepRuntimeSessions.ActiveSkipSession(
                worldId,
                SleepTimingRules.SleepTarget.NIGHT,
                List.of(recipientId),
                sleepers,
                null,
                100,
                100,
                10,
                false
        );

        sleepers.clear();

        assertEquals(Set.of(sleeperId), session.sleepers());
        assertThrows(UnsupportedOperationException.class, () -> session.sleepers().clear());
    }
}
