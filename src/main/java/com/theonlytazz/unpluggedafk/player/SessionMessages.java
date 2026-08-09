package com.theonlytazz.unpluggedafk.player;

import com.theonlytazz.unpluggedafk.config.UnpluggedConfig;
import com.theonlytazz.unpluggedafk.Translations;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Duration;
import java.time.Instant;

final class SessionMessages {
    private SessionMessages() {}

    static String reason(String requested) {
        return requested == null ? "" : requested.trim();
    }

    static Component started(OfflineSession session, UnpluggedConfig.Messages options) {
        MutableComponent result = Translations.component("message.unplugged_afk.started", session.name());
        if (options.displayDuration) {
            result.append(Translations.component("message.unplugged_afk.started.duration", session.timeoutMinutes()));
        }
        appendDetail(result, session.reason());
        return result;
    }

    static Component feedback(OfflineSession session, Instant now, UnpluggedConfig.Messages options) {
        boolean successful = session.status() == UnpluggedStatus.EXPIRED || session.status() == UnpluggedStatus.REPLACED;
        MutableComponent result = Translations.component(successful
                ? "message.unplugged_afk.feedback.success"
                : "message.unplugged_afk.feedback.interrupted");
        if (options.displayDuration) {
            long elapsed = Math.max(0, now.toEpochMilli() - session.startedAtEpochMilli());
            result.append(Translations.component("message.unplugged_afk.feedback.duration", formatDuration(elapsed)));
        }
        appendDetail(result, session.reason());
        return result;
    }

    static Component endedBroadcast(OfflineSession session, Instant now, UnpluggedConfig.Messages options) {
        String key = switch (session.status()) {
            case EXPIRED -> "message.unplugged_afk.expired";
            case REPLACED -> "message.unplugged_afk.returned";
            case TERMINATED -> "message.unplugged_afk.terminated";
            default -> "message.unplugged_afk.interrupted";
        };
        MutableComponent result = Translations.component(key, session.name());
        if (options.displayDuration) {
            long elapsed = Math.max(0, now.toEpochMilli() - session.startedAtEpochMilli());
            result.append(Translations.component("message.unplugged_afk.returned.duration", formatDuration(elapsed)));
        }
        return result;
    }

    private static void appendDetail(MutableComponent target, String detail) {
        if (detail == null || detail.isBlank()) return;
        Component value = detail.startsWith("message.unplugged_afk.")
                ? Translations.component(detail)
                : Component.literal(detail);
        target.append(Translations.component("message.unplugged_afk.detail", value));
    }

    private static Component formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).toSeconds();
        if (hours > 0) return Translations.component("duration.unplugged_afk.hours_minutes", hours, minutes);
        if (minutes > 0) return Translations.component("duration.unplugged_afk.minutes_seconds", minutes, seconds);
        return Translations.component("duration.unplugged_afk.seconds", seconds);
    }
}
