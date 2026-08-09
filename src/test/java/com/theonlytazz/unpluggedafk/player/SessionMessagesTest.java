package com.theonlytazz.unpluggedafk.player;

import com.theonlytazz.unpluggedafk.config.UnpluggedConfig;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionMessagesTest {
    @Test
    void usesDefaultReasonAndConfiguredNameDecoration() {
        UnpluggedConfig.Messages messages = new UnpluggedConfig.Messages();
        messages.defaultUnpluggedReason = "maintenance";
        OfflineSession session = new OfflineSession(UUID.randomUUID(), "Alex", 10, 0,
                SessionMessages.reason("", messages), UnpluggedStatus.ACTIVE);
        assertTrue(SessionMessages.started(session, messages).contains("§eAlex§r"));
        assertTrue(SessionMessages.started(session, messages).endsWith("maintenance"));
    }

    @Test
    void formatsDurationForReturnFeedback() {
        UnpluggedConfig.Messages messages = new UnpluggedConfig.Messages();
        messages.displayDuration = true;
        OfflineSession session = new OfflineSession(UUID.randomUUID(), "Alex", 10, 0,
                "farm", UnpluggedStatus.ACTIVE);
        String result = SessionMessages.ended(session, UnpluggedStatus.REPLACED, "returned",
                Instant.ofEpochMilli(125_000), messages);
        assertTrue(result.contains("2m 5s"));
        assertTrue(result.contains("returned"));
    }
}
