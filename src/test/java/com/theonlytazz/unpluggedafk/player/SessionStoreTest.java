package com.theonlytazz.unpluggedafk.player;

import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    @TempDir Path directory;

    @Test
    void roundTripsSessions() {
        Path path = directory.resolve("sessions.json");
        OfflineSession expected = new OfflineSession(UUID.randomUUID(), "Player", 30, 1234L,
                "farm", UnpluggedStatus.ACTIVE);
        SessionStore.save(path, List.of(expected));
        assertEquals(List.of(expected), SessionStore.load(path));
    }

    @Test
    void malformedStoreLoadsAsEmpty() throws Exception {
        Path path = directory.resolve("sessions.json");
        Files.writeString(path, "not json");
        assertEquals(List.of(), SessionStore.load(path));
    }
}
