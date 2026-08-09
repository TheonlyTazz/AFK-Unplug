package com.theonlytazz.unpluggedafk.state;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UnpluggedStateTest {
    @Test
    void expiresAtConfiguredDeadline() {
        Instant start = Instant.parse("2026-08-09T12:00:00Z");
        UnpluggedState state = new UnpluggedState(UnpluggedStatus.ACTIVE, 15, start, "farm");
        assertFalse(state.isExpired(start.plusSeconds(899)));
        assertTrue(state.isExpired(start.plusSeconds(900)));
    }

    @Test
    void inactiveStateNeverExpires() {
        assertFalse(UnpluggedState.inactive().isExpired(Instant.MAX));
    }
}
