package com.theonlytazz.unpluggedafk.player;

import com.mojang.authlib.GameProfile;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OfflinePlayerManager {
    private static final OfflinePlayerManager INSTANCE = new OfflinePlayerManager();
    private final Map<UUID, OfflineSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, OfflinePlayer> players = new ConcurrentHashMap<>();

    private OfflinePlayerManager() {}
    public static OfflinePlayerManager get() { return INSTANCE; }

    public Collection<OfflineSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public Optional<OfflineSession> session(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    public boolean unplug(ServerPlayer original, long minutes, String reason) {
        MinecraftServer server = original.getServer();
        if (server == null || original instanceof OfflinePlayer || players.containsKey(original.getUUID())) return false;
        if (!ConfigManager.get().main.unpluggedAfkEnabled) return false;

        minutes = minutes > 0 ? minutes : ConfigManager.get().unplugged.defaultUnpluggedTimeout;
        server.getPlayerList().save(original);
        GameProfile profile = original.getGameProfile();
        var level = original.serverLevel();
        var info = original.clientInformation();
        double x = original.getX(), y = original.getY(), z = original.getZ();
        float yaw = original.getYRot(), pitch = original.getXRot();
        var gameMode = original.gameMode.getGameModeForPlayer();

        server.getPlayerList().remove(original);
        original.connection.disconnect(Component.literal(ConfigManager.get().messages.unpluggedKickMessage));

        FakeConnection connection = new FakeConnection();
        OfflinePlayer replacement = new OfflinePlayer(server, level, profile, info);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, info, true);
        server.getPlayerList().placeNewPlayer(connection, replacement, cookie);
        replacement.connection.teleport(x, y, z, yaw, pitch);
        replacement.gameMode.changeGameModeForPlayer(gameMode);

        OfflineSession session = OfflineSession.active(profile.getId(), profile.getName(), minutes, reason);
        sessions.put(profile.getId(), session);
        players.put(profile.getId(), replacement);
        broadcast(server, Component.literal(profile.getName() + ConfigManager.get().messages.unpluggedStarted));
        UnpluggedAfk.LOGGER.info("{} is now represented by an offline player for {} minute(s)", profile.getName(), minutes);
        return true;
    }

    public void tick(MinecraftServer server) {
        Instant now = Instant.now();
        for (OfflineSession session : List.copyOf(sessions.values())) {
            if (session.expired(now)) remove(server, session.uuid(), UnpluggedStatus.EXPIRED,
                    ConfigManager.get().messages.unpluggedExpiredReason);
        }
    }

    public void terminate(OfflinePlayer player, String reason) {
        MinecraftServer server = player.getServer();
        if (server != null) remove(server, player.getUUID(), UnpluggedStatus.TERMINATED, reason);
    }

    public boolean remove(MinecraftServer server, UUID uuid, UnpluggedStatus status, String reason) {
        OfflinePlayer player = players.remove(uuid);
        OfflineSession old = sessions.get(uuid);
        if (old != null) sessions.put(uuid, old.ended(status, reason));
        if (player == null) return false;
        player.deactivate();
        server.getPlayerList().save(player);
        server.getPlayerList().remove(player);
        player.discard();
        return true;
    }

    public void onRealPlayerJoined(ServerPlayer player) {
        if (player instanceof OfflinePlayer) return;
        OfflineSession previous = sessions.remove(player.getUUID());
        players.remove(player.getUUID());
        if (previous != null && ConfigManager.get().messages.displayReturnFeedback && !previous.reason().isBlank()) {
            player.sendSystemMessage(Component.literal(previous.reason()).withStyle(ChatFormatting.GOLD));
        }
    }

    public void stop(MinecraftServer server) {
        for (OfflinePlayer player : List.copyOf(players.values())) server.getPlayerList().save(player);
        players.clear();
    }

    private static void broadcast(MinecraftServer server, Component message) {
        if (!ConfigManager.get().messages.broadcastMessages) return;
        server.sendSystemMessage(message);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
