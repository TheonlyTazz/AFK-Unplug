package com.theonlytazz.unpluggedafk.player;

import com.mojang.authlib.GameProfile;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;
import com.theonlytazz.unpluggedafk.api.UnpluggedAfkApi;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OfflinePlayerManager {
    private static final OfflinePlayerManager INSTANCE = new OfflinePlayerManager();
    private final Map<UUID, OfflineSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, OfflinePlayer> players = new ConcurrentHashMap<>();
    private final Set<String> suppressedJoinNames = ConcurrentHashMap.newKeySet();
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

    public boolean spawn(MinecraftServer server, NameAndId profile, long minutes, String reason) {
        if (players.containsKey(profile.id()) || server.getPlayerList().getPlayer(profile.id()) != null) return false;
        reason = SessionMessages.reason(reason);
        OfflineSession session = OfflineSession.active(profile.id(), profile.name(), minutes, reason);
        sessions.put(profile.id(), session);
        restore(server, session);
        if (!players.containsKey(profile.id())) {
            sessions.remove(profile.id());
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
        MinecraftServer server = original.level().getServer();
        if (server == null || original instanceof OfflinePlayer || players.containsKey(original.getUUID())) return false;
        if (!ConfigManager.get().main.unpluggedAfkEnabled) return false;

        minutes = minutes > 0 ? minutes : ConfigManager.get().unplugged.defaultUnpluggedTimeout;
        server.getPlayerList().save(original);
        GameProfile profile = original.getGameProfile();
        var level = original.level();
        var info = original.clientInformation();
        double x = original.getX(), y = original.getY(), z = original.getZ();
        float yaw = original.getYRot(), pitch = original.getXRot();
        var gameMode = original.gameMode.getGameModeForPlayer();

        suppressedJoinNames.add(profile.name().toLowerCase(Locale.ROOT));
        server.getPlayerList().remove(original);
        original.connection.disconnect(Component.translatable("disconnect.unplugged_afk.unplugged"));

        FakeConnection connection = new FakeConnection();
        OfflinePlayer replacement = new OfflinePlayer(server, level, profile, info);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, info, true);
        placeReplacement(server, connection, replacement, cookie);
        replacement.connection.teleport(x, y, z, yaw, pitch);
        replacement.gameMode.changeGameModeForPlayer(gameMode);

        reason = SessionMessages.reason(reason);
        OfflineSession session = OfflineSession.active(profile.id(), profile.name(), minutes, reason);
        sessions.put(profile.id(), session);
        players.put(profile.id(), replacement);
        suppressedJoinNames.remove(profile.name().toLowerCase(Locale.ROOT));
        applyPresentation(server, replacement);
        UnpluggedAfkApi.fireStarted(session);
        saveSessions();
        broadcast(server, SessionMessages.started(session, ConfigManager.get().messages));
        UnpluggedAfk.LOGGER.info("{} is now represented by an offline player for {} minute(s)", profile.name(), minutes);
        return true;
    }

    public void tick(MinecraftServer server) {
        Instant now = Instant.now();
        for (OfflineSession session : List.copyOf(sessions.values())) {
            if (session.expired(now)) remove(server, session.uuid(), UnpluggedStatus.EXPIRED,
                    "message.unplugged_afk.reason.timeout");
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
                        "message.unplugged_afk.reason.timeout"));
                continue;
            }
            restore(server, session);
        }
        saveSessions();
        UnpluggedAfk.LOGGER.info("Restored {} offline player(s)", players.size());
    }

    private void restore(MinecraftServer server, OfflineSession session) {
        if (server.getPlayerList().getPlayer(session.uuid()) != null) return;
        GameProfile profile = server.services().profileResolver().fetchById(session.uuid())
                .orElseGet(() -> new GameProfile(session.uuid(), session.name()));
        var information = net.minecraft.server.level.ClientInformation.createDefault();
        FakeConnection connection = new FakeConnection();
        OfflinePlayer replacement = new OfflinePlayer(server, server.overworld(), profile, information);
        BlockPos spawn = server.overworld().getRespawnData().pos();
        replacement.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, information, true);
        placeReplacement(server, connection, replacement, cookie);
        if (replacement.blockPosition().equals(BlockPos.ZERO)) {
            replacement.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    replacement.getYRot(), replacement.getXRot());
        }
        players.put(session.uuid(), replacement);
        applyPresentation(server, replacement);
        UnpluggedAfkApi.fireStarted(session);
    }

    public void terminate(OfflinePlayer player, String reason) {
        MinecraftServer server = player.level().getServer();
        if (server != null) remove(server, player.getUUID(), UnpluggedStatus.TERMINATED, reason);
    }

    public boolean remove(MinecraftServer server, UUID uuid, UnpluggedStatus status, String reason) {
        OfflinePlayer player = players.get(uuid);
        OfflineSession old = sessions.get(uuid);
        Instant now = Instant.now();
        if (old != null) sessions.put(uuid, old.ended(status, reason));
        if (player == null) return false;
        player.deactivate();
        server.getPlayerList().save(player);
        server.getPlayerList().remove(player);
        players.remove(uuid);
        broadcastFakeLeave(server, player);
        player.discard();
        if (old != null) {
            UnpluggedAfkApi.fireEnded(sessions.get(uuid));
            broadcast(server, SessionMessages.endedBroadcast(sessions.get(uuid), now, ConfigManager.get().messages));
        }
        saveSessions();
        return true;
    }

    public void onRealPlayerJoined(ServerPlayer player) {
        if (player instanceof OfflinePlayer) return;
        OfflineSession previous = sessions.remove(player.getUUID());
        players.remove(player.getUUID());
        hideAllFrom(player);
        if (previous != null && ConfigManager.get().messages.displayReturnFeedback) {
            player.sendSystemMessage(SessionMessages.feedback(previous, Instant.now(), ConfigManager.get().messages));
        }
        saveSessions();
    }

    public void prepareRealLogin(UUID uuid) {
        OfflinePlayer shadow = players.get(uuid);
        if (shadow == null) return;
        MinecraftServer server = shadow.level().getServer();
        if (server != null) {
            remove(server, uuid, UnpluggedStatus.REPLACED, "");
        }
    }

    public void hideAllFrom(ServerPlayer viewer) {
        for (OfflinePlayer hidden : players.values()) applyVisibility(hidden, viewer);
    }

    private void placeReplacement(MinecraftServer server, FakeConnection connection,
                                  OfflinePlayer replacement, CommonListenerCookie cookie) {
        String name = replacement.nameAndId().name().toLowerCase(Locale.ROOT);
        if (ConfigManager.get().messages.hideUnpluggedJoin) suppressedJoinNames.add(name);
        try {
            server.getPlayerList().placeNewPlayer(connection, replacement, cookie);
        } finally {
            suppressedJoinNames.remove(name);
        }
    }

    public boolean shouldSuppressJoin(Component message) {
        if (!ConfigManager.get().messages.hideUnpluggedJoin) return false;
        if (!(message.getContents() instanceof TranslatableContents translated)
                || (!translated.getKey().equals("multiplayer.player.joined")
                && !translated.getKey().equals("multiplayer.player.left"))) return false;
        String normalized = message.getString().toLowerCase(Locale.ROOT);
        return suppressedJoinNames.stream().anyMatch(normalized::contains)
                || players.values().stream()
                .map(player -> player.nameAndId().name().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private void applyPresentation(MinecraftServer server, OfflinePlayer player) {
        player.applyAfkPresentation();
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) applyVisibility(player, viewer);
    }

    private void applyVisibility(OfflinePlayer hidden, ServerPlayer viewer) {
        if (viewer == hidden) return;
        if (!ConfigManager.get().unplugged.unpluggedHidePlayer) {
            if (!ConfigManager.get().unplugged.showAfkInTabList) {
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
            }
            return;
        }
        MinecraftServer viewerServer = viewer.level().getServer();
        boolean viewerIsOp = viewerServer != null && viewerServer.getPlayerList().isOp(viewer.nameAndId());
        if (viewerIsOp && !ConfigManager.get().unplugged.unpluggedHideFromOps) return;
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(hidden.getId()));
    }

    public void stop(MinecraftServer server) {
        for (OfflinePlayer player : List.copyOf(players.values())) {
            player.clearAfkPresentation();
            server.getPlayerList().save(player);
        }
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

    private static void broadcastFakeLeave(MinecraftServer server, OfflinePlayer player) {
        if (ConfigManager.get().messages.hideUnpluggedJoin) return;
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("multiplayer.player.left", player.getDisplayName())
                        .withStyle(ChatFormatting.YELLOW), false);
    }
}
