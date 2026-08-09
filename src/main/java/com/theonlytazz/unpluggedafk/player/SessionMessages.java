package com.theonlytazz.unpluggedafk.player;

import com.theonlytazz.unpluggedafk.config.UnpluggedConfig;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;

import java.time.Duration;
import java.time.Instant;

final class SessionMessages {
    private SessionMessages() {}

    static String reason(String requested, UnpluggedConfig.Messages messages) {
        return requested == null || requested.isBlank() ? messages.defaultUnpluggedReason : requested;
    }

    static String started(OfflineSession session, UnpluggedConfig.Messages messages) {
        StringBuilder result = new StringBuilder(playerName(session, messages)).append(messages.unpluggedStarted);
        if (messages.displayDuration) result.append(messages.whenUnpluggedDurationPrefix)
                .append(session.timeoutMinutes()).append(messages.whenUnpluggedDurationSuffix);
        appendReason(result, session.reason(), messages.unpluggedPunctuation);
        return result.toString();
    }

    static String ended(OfflineSession session, UnpluggedStatus status, String detail,
                        Instant now, UnpluggedConfig.Messages messages) {
        long elapsed = Math.max(0, now.toEpochMilli() - session.startedAtEpochMilli());
        boolean successful = status == UnpluggedStatus.EXPIRED || status == UnpluggedStatus.REPLACED;
        StringBuilder result = new StringBuilder();
        if (successful) {
            result.append(messages.displayDuration ? messages.unpluggedSuccessfulPrefix : messages.unpluggedSuccessful);
            if (messages.displayDuration) result.append(formatDuration(elapsed)).append(messages.unpluggedSuccessfulSuffix);
            appendReason(result, detail, messages.unpluggedSuccessfulPunctuation);
        } else {
            result.append(messages.unpluggedUnsuccessful);
            if (messages.displayDuration) result.append(messages.unpluggedUnsuccessfulPrefix).append(formatDuration(elapsed));
            appendReason(result, detail, messages.unpluggedUnsuccessfulPunctuation);
        }
        return result.toString();
    }

    static String endedBroadcast(OfflineSession session, UnpluggedStatus status, Instant now,
                                 UnpluggedConfig.Messages messages) {
        String suffix = switch (status) {
            case EXPIRED -> messages.whenUnpluggedExpired;
            case REPLACED -> messages.whenUnpluggedReturned;
            case TERMINATED -> messages.whenUnpluggedTerminated;
            default -> messages.whenUnpluggedInterrupted;
        };
        StringBuilder result = new StringBuilder(playerName(session, messages)).append(suffix);
        if (messages.displayDuration) result.append(messages.whenReturnDurationPrefix)
                .append(formatDuration(Math.max(0, now.toEpochMilli() - session.startedAtEpochMilli())))
                .append(messages.whenReturnDurationSuffix);
        return result.toString();
    }

    private static String playerName(OfflineSession session, UnpluggedConfig.Messages messages) {
        return messages.unpluggedPlayerPrefix + session.name() + messages.unpluggedPlayerSuffix;
    }

    private static void appendReason(StringBuilder target, String reason, String punctuation) {
        if (reason != null && !reason.isBlank()) target.append(punctuation).append(reason);
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).toSeconds();
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
