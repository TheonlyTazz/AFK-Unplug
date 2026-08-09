package com.theonlytazz.unpluggedafk.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile UnpluggedConfig config = new UnpluggedConfig();
    private static Path path;

    private ConfigManager() {}

    public static synchronized void initialize(Path configPath) {
        path = configPath;
        reload();
    }

    public static UnpluggedConfig get() {
        return config;
    }

    public static synchronized void reload() {
        requireInitialized();
        UnpluggedConfig loaded = new UnpluggedConfig();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                UnpluggedConfig parsed = GSON.fromJson(reader, UnpluggedConfig.class);
                if (parsed != null) loaded = parsed;
            } catch (IOException | JsonParseException exception) {
                UnpluggedAfk.LOGGER.error("Could not read {}; retaining safe defaults", path, exception);
            }
        }
        loaded.normalize();
        config = loaded;
        save();
    }

    public static synchronized void save() {
        requireInitialized();
        Path parent = path.toAbsolutePath().getParent();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            UnpluggedAfk.LOGGER.error("Could not save {}", path, exception);
        }
    }

    private static void requireInitialized() {
        if (path == null) throw new IllegalStateException("ConfigManager has not been initialized");
    }
}
