package com.theonlytazz.unpluggedafk.api;

import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import com.theonlytazz.unpluggedafk.player.OfflineSession;
import com.theonlytazz.unpluggedafk.player.OfflinePlayer;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class UnpluggedAfkApi {
    private static final CopyOnWriteArrayList<SessionListener> LISTENERS = new CopyOnWriteArrayList<>();

    private UnpluggedAfkApi() {}

    public static Optional<OfflineSession> getSession(UUID playerId) {
        return OfflinePlayerManager.get().session(playerId);
    }

    public static boolean isUnplugged(UUID playerId) {
        return OfflinePlayerManager.get().isActive(playerId);
    }

    public static boolean isUnplugged(ServerPlayer player) {
        return player instanceof OfflinePlayer && OfflinePlayerManager.get().isActive(player.getUUID());
    }

    public static Optional<Duration> getRemainingTime(UUID playerId) {
        return getSession(playerId).filter(session -> session.status() == UnpluggedStatus.ACTIVE)
                .map(session -> session.remaining(Instant.now()));
    }

    public static AutoCloseable addListener(SessionListener listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static void fireStarted(OfflineSession session) {
        LISTENERS.forEach(listener -> listener.onStarted(session));
    }

    public static void fireEnded(OfflineSession session) {
        LISTENERS.forEach(listener -> listener.onEnded(session));
    }

    public interface SessionListener {
        default void onStarted(OfflineSession session) {}
        default void onEnded(OfflineSession session) {}
    }
}
