package com.theonlytazz.unpluggedafk.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir Path directory;

    @Test
    void createsDefaultsAndRoundTripsChanges() throws Exception {
        Path path = directory.resolve("unplugged_afk.json");
        ConfigManager.initialize(path);
        assertTrue(Files.isRegularFile(path));
        assertTrue(ConfigManager.get().main.unpluggedAfkEnabled);

        ConfigManager.get().messages.displayReturnFeedback = true;
        ConfigManager.get().unplugged.defaultUnpluggedTimeout = 42;
        ConfigManager.save();
        ConfigManager.reload();

        assertTrue(ConfigManager.get().messages.displayReturnFeedback);
        assertEquals(42, ConfigManager.get().unplugged.defaultUnpluggedTimeout);
    }

    @Test
    void malformedInputFallsBackToNormalizedDefaults() throws Exception {
        Path path = directory.resolve("unplugged_afk.json");
        Files.writeString(path, "{ definitely not json");
        ConfigManager.initialize(path);

        assertTrue(ConfigManager.get().main.unpluggedAfkEnabled);
        assertEquals(129_600, ConfigManager.get().unplugged.defaultUnpluggedTimeout);
        assertDoesNotThrow(() -> ConfigManager.reload());
    }

    @Test
    void migratesLegacyMessageConfigToVisibleStatusDefaults() throws Exception {
        Path path = directory.resolve("unplugged_afk.json");
        Files.writeString(path, """
                {"messages":{"broadcastMessages":false,"hideUnpluggedJoin":false,"unpluggedStarted":" legacy"}}
                """);

        ConfigManager.initialize(path);

        assertTrue(ConfigManager.get().messages.broadcastMessages);
        assertTrue(ConfigManager.get().messages.hideUnpluggedJoin);
        assertFalse(Files.readString(path).contains("unpluggedStarted"));
    }

    @Test
    void normalizationClampsUnsafeValues() {
        UnpluggedConfig config = new UnpluggedConfig();
        config.commands.unplugCommandPermissions = 99;
        config.commands.afkCommandPermissions = -4;
        config.unplugged.defaultUnpluggedTimeout = 0;
        config.unplugged.maximumUnpluggedTimeout = 0;
        config.unplugged.maximumSimultaneousPlayers = 0;
        config.access.mode = "invalid";
        config.automatic.mode = "invalid";
        config.automatic.defaultDurationMinutes = 0;
        config.automatic.delaySeconds = 999;
        config.normalize();

        assertEquals(4, config.commands.unplugCommandPermissions);
        assertEquals(0, config.commands.afkCommandPermissions);
        assertEquals(1, config.unplugged.defaultUnpluggedTimeout);
        assertEquals(1, config.unplugged.maximumUnpluggedTimeout);
        assertEquals(1, config.unplugged.maximumSimultaneousPlayers);
        assertEquals("EVERYONE", config.access.mode);
        assertEquals("OPT_IN", config.automatic.mode);
        assertEquals(1, config.automatic.defaultDurationMinutes);
        assertEquals(60, config.automatic.delaySeconds);
    }

    @Test
    void advancedOptionSetterSupportsEveryPrimitiveSectionField() {
        ConfigManager.initialize(directory.resolve("unplugged_afk.json"));
        assertTrue(ConfigManager.optionNames().contains("messages.displayReturnFeedback"));
        assertTrue(ConfigManager.setOption("main.debugMode", "true"));
        assertTrue(ConfigManager.setOption("unplugged.defaultUnpluggedTimeout", "15"));
        assertTrue(ConfigManager.setOption("messages.displayDuration", "true"));
        assertFalse(ConfigManager.setOption("main.debugMode", "perhaps"));
        assertFalse(ConfigManager.setOption("missing.option", "true"));
        assertTrue(ConfigManager.get().main.debugMode);
        assertEquals(15, ConfigManager.get().unplugged.defaultUnpluggedTimeout);
        assertTrue(ConfigManager.get().messages.displayDuration);
    }
}
