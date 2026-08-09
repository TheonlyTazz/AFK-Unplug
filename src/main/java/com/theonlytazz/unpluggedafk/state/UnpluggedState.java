package com.theonlytazz.unpluggedafk.state;

import java.time.Duration;
import java.time.Instant;

public record UnpluggedState(UnpluggedStatus status, long timeoutMinutes,
                             Instant startedAt, String reason) {
    public static UnpluggedState inactive() {
        return new UnpluggedState(UnpluggedStatus.INACTIVE, -1, Instant.EPOCH, "");
    }

    public UnpluggedState {
        if (status == null) status = UnpluggedStatus.INACTIVE;
        if (startedAt == null) startedAt = Instant.EPOCH;
        if (reason == null) reason = "";
    }

    public boolean isActive() {
        return status == UnpluggedStatus.ACTIVE;
    }

    public boolean isExpired(Instant now) {
        return isActive() && timeoutMinutes > 0
                && !now.isBefore(startedAt.plus(Duration.ofMinutes(timeoutMinutes)));
    }
}
