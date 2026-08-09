package com.theonlytazz.unpluggedafk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public final class Translations {
    private static final String RESOURCE = "/assets/unplugged_afk/lang/en_us.json";
    private static final Map<String, String> ENGLISH = loadEnglish();

    private Translations() {}

    public static MutableComponent component(String key, Object... arguments) {
        String template = ENGLISH.getOrDefault(key, key);
        Object[] resolved = Arrays.stream(arguments)
                .map(argument -> argument instanceof Component component ? component.getString() : argument)
                .toArray();
        try {
            return Component.literal(String.format(Locale.ROOT, template, resolved));
        } catch (RuntimeException invalidTemplate) {
            UnpluggedAfk.LOGGER.error("Invalid translation template for {}", key, invalidTemplate);
            return Component.literal(template);
        }
    }

    private static Map<String, String> loadEnglish() {
        try (var stream = Translations.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> translations = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), type);
            return Map.copyOf(translations);
        } catch (Exception exception) {
            UnpluggedAfk.LOGGER.error("Could not load bundled English translations", exception);
            return Map.of();
        }
    }
}
