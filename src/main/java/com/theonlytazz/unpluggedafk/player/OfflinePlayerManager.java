package com.theonlytazz.unpluggedafk.player;

import com.mojang.authlib.GameProfile;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;
import com.theonlytazz.unpluggedafk.api.UnpluggedAfkApi;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OfflinePlayerManager {
    private static final OfflinePlayerManager INSTANCE = new OfflinePlayerManager();
    private final Map<UUID, OfflineSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, OfflinePlayer> players = new ConcurrentHashMap<>();
    private Path storePath;

    private OfflinePlayerManager() {}
    public static OfflinePlayerManager get() { return INSTANCE; }

    public Collection<OfflineSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public Optional<OfflineSession> session(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    public int activeCount() { return players.size(); }
    public boolean isActive(UUID uuid) { return players.containsKey(uuid); }

    public boolean spawn(MinecraftServer server, GameProfile profile, long minutes, String reason) {
        if (profile.getId() == null || players.containsKey(profile.getId()) || server.getPlayerList().getPlayer(profile.getId()) != null) return false;
        OfflineSession session = OfflineSession.active(profile.getId(), profile.getName(), minutes, reason);
        sessions.put(profile.getId(), session);
        restore(server, session);
        if (!players.containsKey(profile.getId())) {
            sessions.remove(profile.getId());
            return false;
        }
        saveSessions();
        return true;
    }

    public int purgeEnded() {
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> entry.getValue().status() != UnpluggedStatus.ACTIVE);
        saveSessions();
        return before - sessions.size();
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
        applyVisibility(server, replacement);
        UnpluggedAfkApi.fireStarted(session);
        saveSessions();
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

    public void start(MinecraftServer server) {
        players.clear();
        sessions.clear();
        storePath = server.getWorldPath(LevelResource.ROOT).resolve("unplugged_afk_sessions.json");
        for (OfflineSession session : SessionStore.load(storePath)) sessions.put(session.uuid(), session);

        for (OfflineSession session : List.copyOf(sessions.values())) {
            if (session.status() != UnpluggedStatus.ACTIVE) continue;
            if (session.expired(Instant.now())) {
                sessions.put(session.uuid(), session.ended(UnpluggedStatus.EXPIRED,
                        ConfigManager.get().messages.unpluggedExpiredReason));
                continue;
            }
            restore(server, session);
        }
        saveSessions();
        UnpluggedAfk.LOGGER.info("Restored {} offline player(s)", players.size());
    }

    private void restore(MinecraftServer server, OfflineSession session) {
        if (server.getPlayerList().getPlayer(session.uuid()) != null) return;
        GameProfile profile = server.getProfileCache().get(session.uuid())
                .orElseGet(() -> new GameProfile(session.uuid(), session.name()));
        var information = net.minecraft.server.level.ClientInformation.createDefault();
        FakeConnection connection = new FakeConnection();
        OfflinePlayer replacement = new OfflinePlayer(server, server.overworld(), profile, information);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, information, true);
        server.getPlayerList().placeNewPlayer(connection, replacement, cookie);
        players.put(session.uuid(), replacement);
        applyVisibility(server, replacement);
        UnpluggedAfkApi.fireStarted(session);
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
        if (old != null) UnpluggedAfkApi.fireEnded(sessions.get(uuid));
        saveSessions();
        return true;
    }

    public void onRealPlayerJoined(ServerPlayer player) {
        if (player instanceof OfflinePlayer) return;
        OfflineSession previous = sessions.remove(player.getUUID());
        players.remove(player.getUUID());
        hideAllFrom(player);
        if (previous != null && ConfigManager.get().messages.displayReturnFeedback && !previous.reason().isBlank()) {
            player.sendSystemMessage(Component.literal(previous.reason()).withStyle(ChatFormatting.GOLD));
        }
        saveSessions();
    }

    public void hideAllFrom(ServerPlayer viewer) {
        for (OfflinePlayer hidden : players.values()) hideFrom(hidden, viewer);
    }

    private void applyVisibility(MinecraftServer server, OfflinePlayer hidden) {
        if (!ConfigManager.get().unplugged.unpluggedHidePlayer) return;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) hideFrom(hidden, viewer);
    }

    private void hideFrom(OfflinePlayer hidden, ServerPlayer viewer) {
        if (!ConfigManager.get().unplugged.unpluggedHidePlayer || viewer == hidden) return;
        boolean viewerIsOp = viewer.getServer() != null && viewer.getServer().getPlayerList().isOp(viewer.getGameProfile());
        if (viewerIsOp && !ConfigManager.get().unplugged.unpluggedHideFromOps) return;
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(hidden.getId()));
    }

    public void stop(MinecraftServer server) {
        for (OfflinePlayer player : List.copyOf(players.values())) server.getPlayerList().save(player);
        saveSessions();
        players.clear();
    }

    public void saveSessions() {
        if (storePath != null) SessionStore.save(storePath, sessions.values());
    }

    private static void broadcast(MinecraftServer server, Component message) {
        if (!ConfigManager.get().messages.broadcastMessages) return;
        server.sendSystemMessage(message);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
