package com.theonlytazz.unpluggedafk.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

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
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                boolean legacyMessages = root.has("messages")
                        && root.getAsJsonObject("messages").has("unpluggedStarted");
                UnpluggedConfig parsed = GSON.fromJson(root, UnpluggedConfig.class);
                if (legacyMessages && parsed != null) {
                    parsed.messages.broadcastMessages = true;
                    parsed.messages.hideUnpluggedJoin = true;
                }
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

    public static List<String> optionNames() {
        List<String> names = new ArrayList<>();
        for (Field sectionField : UnpluggedConfig.class.getFields()) {
            try {
                Object section = sectionField.get(config);
                if (section == null) continue;
                for (Field option : section.getClass().getFields()) {
                    names.add(sectionField.getName() + "." + option.getName());
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return List.copyOf(names);
    }

    public static synchronized boolean setOption(String path, String rawValue) {
        int separator = path.indexOf('.');
        if (separator < 1 || separator == path.length() - 1) return false;
        try {
            Field sectionField = UnpluggedConfig.class.getField(path.substring(0, separator));
            Object section = sectionField.get(config);
            Field option = section.getClass().getField(path.substring(separator + 1));
            Object value;
            if (option.getType() == boolean.class) {
                if (!rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) return false;
                value = Boolean.parseBoolean(rawValue);
            } else if (option.getType() == int.class) {
                value = Integer.parseInt(rawValue);
            } else if (option.getType() == long.class) {
                value = Long.parseLong(rawValue);
            } else if (option.getType() == String.class) {
                value = rawValue.replace('&', '§');
            } else {
                return false;
            }
            option.set(section, value);
            config.normalize();
            save();
            return true;
        } catch (IllegalAccessException | NoSuchFieldException | NumberFormatException exception) {
            return false;
        }
    }

    private static void requireInitialized() {
        if (path == null) throw new IllegalStateException("ConfigManager has not been initialized");
    }
}
