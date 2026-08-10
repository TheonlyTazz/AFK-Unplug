package com.theonlytazz.unpluggedafk.player;

import com.mojang.authlib.GameProfile;
import com.theonlytazz.unpluggedafk.UnpluggedAfk;
import com.theonlytazz.unpluggedafk.Translations;
import com.theonlytazz.unpluggedafk.api.UnpluggedAfkApi;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import com.theonlytazz.unpluggedafk.permission.AccessController;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OfflinePlayerManager {
    private static final OfflinePlayerManager INSTANCE = new OfflinePlayerManager();
    private final Map<UUID, OfflineSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, OfflinePlayer> players = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingPlayerInfoRefreshes = new ConcurrentHashMap<>();
    private final Set<String> suppressedJoinNames = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> pendingStatusSuppressions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextLogouts = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAutomatic> pendingAutomatic = new ConcurrentHashMap<>();
    private final Set<UUID> manualDisconnects = ConcurrentHashMap.newKeySet();
    private Path storePath;
    private boolean stopping;

    private OfflinePlayerManager() {}
    public static OfflinePlayerManager get() { return INSTANCE; }

    public Collection<OfflineSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public Optional<OfflineSession> session(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    public Optional<Component> sessionDetails(UUID uuid) {
        OfflineSession session = sessions.get(uuid);
        if (session == null) return Optional.empty();
        OfflinePlayer player = players.get(uuid);
        String location = player == null ? "-" : String.format(Locale.ROOT, "%s @ %.1f, %.1f, %.1f",
                player.level().dimension().location(), player.getX(), player.getY(), player.getZ());
        long remainingSeconds = session.remaining(Instant.now()).toSeconds();
        return Optional.of(Translations.component("command.unplugged_afk.admin.player_details",
                session.status(), remainingSeconds, location,
                session.reason().isBlank() ? "-" : session.reason()));
    }

    public int activeCount() { return players.size(); }
    public boolean isActive(UUID uuid) { return players.containsKey(uuid); }

    public boolean spawn(MinecraftServer server, GameProfile profile, long minutes, String reason) {
        if (profile.getId() == null || players.containsKey(profile.getId()) || server.getPlayerList().getPlayer(profile.getId()) != null) return false;
        reason = SessionMessages.reason(reason);
        OfflineSession session = OfflineSession.active(profile.getId(), profile.getName(), minutes, reason);
        sessions.put(profile.getId(), session);
        restore(server, session);
        if (!players.containsKey(profile.getId())) {
            sessions.remove(profile.getId());
            return false;
        }
        saveSessions();
        broadcast(server, SessionMessages.started(session, ConfigManager.get().messages));
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
        if (!AccessController.mayUse(original)) return false;
        if (!AccessController.bypassesLimits(original)
                && players.size() >= ConfigManager.get().unplugged.maximumSimultaneousPlayers) {
            original.sendSystemMessage(Translations.component("command.unplugged_afk.server_limit"));
            return false;
        }

        minutes = minutes > 0 ? minutes : ConfigManager.get().unplugged.defaultUnpluggedTimeout;
        long maximum = AccessController.maximumDuration(original);
        if (minutes > maximum) return false;
        server.getPlayerList().save(original);
        GameProfile profile = original.getGameProfile();
        var level = original.serverLevel();
        var info = original.clientInformation();
        double x = original.getX(), y = original.getY(), z = original.getZ();
        float yaw = original.getYRot(), pitch = original.getXRot();
        var gameMode = original.gameMode.getGameModeForPlayer();

        suppressedJoinNames.add(profile.getName().toLowerCase(Locale.ROOT));
        manualDisconnects.add(profile.getId());
        server.getPlayerList().remove(original);
        original.connection.disconnect(Translations.component("disconnect.unplugged_afk.unplugged"));

        FakeConnection connection = new FakeConnection();
        OfflinePlayer replacement = new OfflinePlayer(server, level, profile, info);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, info, true);
        placeReplacement(server, connection, replacement, cookie);
        replacement.connection.teleport(x, y, z, yaw, pitch);
        replacement.gameMode.changeGameModeForPlayer(gameMode);

        reason = SessionMessages.reason(reason);
        OfflineSession session = OfflineSession.active(profile.getId(), profile.getName(), minutes, reason);
        sessions.put(profile.getId(), session);
        players.put(profile.getId(), replacement);
        suppressedJoinNames.remove(profile.getName().toLowerCase(Locale.ROOT));
        applyPresentation(server, replacement);
        pendingPlayerInfoRefreshes.put(profile.getId(), 2);
        UnpluggedAfkApi.fireStarted(session);
        saveSessions();
        broadcast(server, SessionMessages.started(session, ConfigManager.get().messages));
        UnpluggedAfk.LOGGER.info("{} is now represented by an offline player for {} minute(s)", profile.getName(), minutes);
        return true;
    }

    public void tick(MinecraftServer server) {
        for (var entry : List.copyOf(pendingAutomatic.entrySet())) {
            PendingAutomatic pending = entry.getValue();
            if (pending.ticksRemaining() > 0) {
                pendingAutomatic.put(entry.getKey(), pending.tick());
            } else {
                pendingAutomatic.remove(entry.getKey());
                if (!stopping && server.getPlayerList().getPlayer(entry.getKey()) == null
                        && players.size() < ConfigManager.get().unplugged.maximumSimultaneousPlayers) {
                    spawn(server, pending.profile(), pending.minutes(), "message.unplugged_afk.reason.automatic");
                }
            }
        }
        for (var entry : List.copyOf(pendingStatusSuppressions.entrySet())) {
            if (entry.getValue() > 1) {
                pendingStatusSuppressions.put(entry.getKey(), entry.getValue() - 1);
            } else {
                pendingStatusSuppressions.remove(entry.getKey());
                suppressedJoinNames.remove(entry.getKey());
            }
        }

        for (var entry : List.copyOf(pendingPlayerInfoRefreshes.entrySet())) {
            if (entry.getValue() > 1) {
                pendingPlayerInfoRefreshes.put(entry.getKey(), entry.getValue() - 1);
                continue;
            }
            pendingPlayerInfoRefreshes.remove(entry.getKey());
            OfflinePlayer player = players.get(entry.getKey());
            if (player != null) refreshPlayerInfo(server, player);
        }

        Instant now = Instant.now();
        for (OfflineSession session : List.copyOf(sessions.values())) {
            if (session.expired(now)) remove(server, session.uuid(), UnpluggedStatus.EXPIRED,
                    "message.unplugged_afk.reason.timeout");
        }
    }

    public void start(MinecraftServer server) {
        stopping = false;
        players.clear();
        pendingPlayerInfoRefreshes.clear();
        suppressedJoinNames.clear();
        pendingStatusSuppressions.clear();
        nextLogouts.clear();
        pendingAutomatic.clear();
        manualDisconnects.clear();
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
        GameProfile profile = server.getProfileCache().get(session.uuid())
                .orElseGet(() -> new GameProfile(session.uuid(), session.name()));
        var information = net.minecraft.server.level.ClientInformation.createDefault();
        FakeConnection connection = new FakeConnection();
        OfflinePlayer replacement = new OfflinePlayer(server, server.overworld(), profile, information);
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        replacement.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, information, true);
        placeReplacement(server, connection, replacement, cookie);
        if (replacement.blockPosition().equals(BlockPos.ZERO)) {
            replacement.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    replacement.getYRot(), replacement.getXRot());
        }
        players.put(session.uuid(), replacement);
        applyPresentation(server, replacement);
        UnpluggedAfkApi.fireStarted(session);
    }

    public void terminate(OfflinePlayer player, String reason) {
        MinecraftServer server = player.getServer();
        if (server != null) remove(server, player.getUUID(), UnpluggedStatus.TERMINATED, reason);
    }

    public boolean remove(MinecraftServer server, UUID uuid, UnpluggedStatus status, String reason) {
        OfflinePlayer player = players.get(uuid);
        pendingPlayerInfoRefreshes.remove(uuid);
        OfflineSession old = sessions.get(uuid);
        Instant now = Instant.now();
        if (old != null) sessions.put(uuid, old.ended(status, reason));
        if (player == null) return false;
        player.deactivate();
        removeAfkNameplate(server, player);
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
        manualDisconnects.remove(player.getUUID());
        pendingAutomatic.remove(player.getUUID());
        OfflineSession previous = sessions.remove(player.getUUID());
        players.remove(player.getUUID());
        hideAllFrom(player);
        if (previous != null && ConfigManager.get().messages.displayReturnFeedback) {
            player.sendSystemMessage(SessionMessages.feedback(previous, Instant.now(), ConfigManager.get().messages));
        }
        saveSessions();
    }

    public void onRealPlayerLoggedOut(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (stopping || manualDisconnects.remove(uuid)) return;
        var automatic = ConfigManager.get().automatic;
        Long minutes = nextLogouts.remove(uuid);
        if (minutes == null) minutes = automatic.players.get(uuid.toString());
        if (minutes == null && automatic.mode.equals("EVERYONE")) minutes = automatic.defaultDurationMinutes;
        if (minutes == null || automatic.mode.equals("DISABLED") || !AccessController.mayAutoUnplug(player)) return;
        minutes = Math.min(minutes, AccessController.maximumDuration(player));
        player.level().getServer().getPlayerList().save(player);
        pendingAutomatic.put(uuid, new PendingAutomatic(player.getGameProfile(), minutes,
                automatic.delaySeconds * 20));
    }

    public void setAutomatic(ServerPlayer player, long minutes) {
        ConfigManager.get().automatic.players.put(player.getUUID().toString(), minutes);
        ConfigManager.save();
    }

    public void disableAutomatic(UUID uuid) {
        ConfigManager.get().automatic.players.remove(uuid.toString());
        nextLogouts.remove(uuid);
        pendingAutomatic.remove(uuid);
        ConfigManager.save();
    }

    public void armNextLogout(ServerPlayer player, long minutes) {
        nextLogouts.put(player.getUUID(), minutes);
    }

    public boolean cancelAutomatic(UUID uuid) {
        boolean changed = nextLogouts.remove(uuid) != null;
        changed |= pendingAutomatic.remove(uuid) != null;
        return changed;
    }

    public Component automaticStatus(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (nextLogouts.containsKey(uuid)) {
            return Translations.component("command.unplugged_afk.status.next", nextLogouts.get(uuid));
        }
        Long duration = ConfigManager.get().automatic.players.get(uuid.toString());
        if (duration != null) return Translations.component("command.unplugged_afk.status.auto", duration);
        return Translations.component("command.unplugged_afk.status.off");
    }

    public void prepareRealLogin(UUID uuid) {
        OfflinePlayer shadow = players.get(uuid);
        if (shadow == null) return;
        String name = shadow.getGameProfile().getName().toLowerCase(Locale.ROOT);
        suppressedJoinNames.add(name);
        pendingStatusSuppressions.put(name, 2);
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
        String name = replacement.getGameProfile().getName().toLowerCase(Locale.ROOT);
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
                .map(player -> player.getGameProfile().getName().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private void applyPresentation(MinecraftServer server, OfflinePlayer player) {
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) applyVisibility(player, viewer);
    }

    private void refreshPlayerInfo(MinecraftServer server, OfflinePlayer player) {
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player && (shouldHideFrom(player, viewer)
                    || !ConfigManager.get().unplugged.showAfkInTabList)) {
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            }
        }
    }

    private void applyVisibility(OfflinePlayer hidden, ServerPlayer viewer) {
        if (viewer == hidden) return;
        if (shouldHideFrom(hidden, viewer)) {
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
            viewer.connection.send(new ClientboundRemoveEntitiesPacket(hidden.getId()));
            return;
        }
        if (ConfigManager.get().unplugged.showAfkNameplate) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(afkTeam(hidden), true));
        }
        if (!ConfigManager.get().unplugged.showAfkInTabList) {
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
        }
    }

    private static boolean shouldHideFrom(OfflinePlayer hidden, ServerPlayer viewer) {
        if (!ConfigManager.get().unplugged.unpluggedHidePlayer || viewer == hidden) return false;
        MinecraftServer viewerServer = viewer.getServer();
        boolean viewerIsOp = viewerServer != null && viewerServer.getPlayerList().isOp(viewer.getGameProfile());
        return !viewerIsOp || ConfigManager.get().unplugged.unpluggedHideFromOps;
    }

    private static PlayerTeam afkTeam(OfflinePlayer player) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), "uafk" + player.getUUID().toString().replace("-", "").substring(0, 12));
        team.setPlayerSuffix(Component.literal(" ").append(Translations.component("label.unplugged_afk.afk")));
        team.getPlayers().add(player.getGameProfile().getName());
        return team;
    }

    private static void removeAfkNameplate(MinecraftServer server, OfflinePlayer player) {
        if (!ConfigManager.get().unplugged.showAfkNameplate) return;
        var packet = ClientboundSetPlayerTeamPacket.createRemovePacket(afkTeam(player));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player) viewer.connection.send(packet);
        }
    }

    public void stop(MinecraftServer server) {
        stopping = true;
        pendingAutomatic.clear();
        for (OfflinePlayer player : List.copyOf(players.values())) {
            server.getPlayerList().save(player);
        }
        saveSessions();
        players.clear();
        pendingPlayerInfoRefreshes.clear();
        suppressedJoinNames.clear();
        pendingStatusSuppressions.clear();
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

    private record PendingAutomatic(GameProfile profile, long minutes, int ticksRemaining) {
        PendingAutomatic tick() { return new PendingAutomatic(profile, minutes, ticksRemaining - 1); }
    }
}
