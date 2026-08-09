package com.theonlytazz.unpluggedafk.api;

import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import com.theonlytazz.unpluggedafk.player.OfflineSession;

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
