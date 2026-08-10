package com.theonlytazz.unpluggedafk.player;

import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

public record OfflineSession(UUID uuid, String name, long timeoutMinutes,
                             long startedAtEpochMilli, String reason,
                             UnpluggedStatus status) {
    public OfflineSession {
        if (name == null) name = "";
        if (reason == null) reason = "";
        if (status == null) status = UnpluggedStatus.INACTIVE;
    }

    public static OfflineSession active(UUID uuid, String name, long minutes, String reason) {
        return new OfflineSession(uuid, name, minutes, System.currentTimeMillis(), reason, UnpluggedStatus.ACTIVE);
    }

    public boolean expired(Instant now) {
        return status == UnpluggedStatus.ACTIVE && timeoutMinutes > 0
                && now.toEpochMilli() >= startedAtEpochMilli + timeoutMinutes * 60_000L;
    }

    public Duration elapsed(Instant now) {
        return Duration.ofMillis(Math.max(0L, now.toEpochMilli() - startedAtEpochMilli));
    }

    public Duration remaining(Instant now) {
        if (status != UnpluggedStatus.ACTIVE || timeoutMinutes <= 0) return Duration.ZERO;
        long end = startedAtEpochMilli + timeoutMinutes * 60_000L;
        return Duration.ofMillis(Math.max(0L, end - now.toEpochMilli()));
    }

    public OfflineSession ended(UnpluggedStatus newStatus, String feedback) {
        return new OfflineSession(uuid, name, timeoutMinutes, startedAtEpochMilli, feedback, newStatus);
    }
}
