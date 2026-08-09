package com.theonlytazz.unpluggedafk.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;

final class SessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type LIST_TYPE = new TypeToken<List<OfflineSession>>() {}.getType();

    private SessionStore() {}

    static List<OfflineSession> load(Path path) {
        if (!Files.isRegularFile(path)) return List.of();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<OfflineSession> sessions = GSON.fromJson(reader, LIST_TYPE);
            return sessions == null ? List.of() : List.copyOf(sessions);
        } catch (IOException | JsonParseException exception) {
            UnpluggedAfk.LOGGER.error("Could not load offline sessions from {}", path, exception);
            return List.of();
        }
    }

    static void save(Path path, Collection<OfflineSession> sessions) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(sessions, LIST_TYPE, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            UnpluggedAfk.LOGGER.error("Could not save offline sessions to {}", path, exception);
        }
    }
}
