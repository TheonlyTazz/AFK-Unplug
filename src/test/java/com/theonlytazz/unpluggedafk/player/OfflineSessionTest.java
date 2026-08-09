package com.theonlytazz.unpluggedafk.player;

import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfflineSessionTest {
    @Test
    void activeSessionExpiresAtDeadline() {
        OfflineSession session = new OfflineSession(UUID.randomUUID(), "Player", 2,
                1_000L, "farm", UnpluggedStatus.ACTIVE);
        assertFalse(session.expired(Instant.ofEpochMilli(120_999L)));
        assertTrue(session.expired(Instant.ofEpochMilli(121_000L)));
    }

    @Test
    void endedSessionKeepsIdentityAndTiming() {
        UUID id = UUID.randomUUID();
        OfflineSession active = new OfflineSession(id, "Player", 10, 42L, "farm", UnpluggedStatus.ACTIVE);
        OfflineSession ended = active.ended(UnpluggedStatus.REPLACED, "returned");
        assertEquals(id, ended.uuid());
        assertEquals(42L, ended.startedAtEpochMilli());
        assertEquals(UnpluggedStatus.REPLACED, ended.status());
        assertEquals("returned", ended.reason());
    }
}
