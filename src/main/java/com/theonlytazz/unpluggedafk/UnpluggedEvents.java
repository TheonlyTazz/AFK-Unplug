package com.theonlytazz.unpluggedafk;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.player.OfflinePlayer;
import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import com.theonlytazz.unpluggedafk.permission.AccessController;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class UnpluggedEvents {
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("unplug")
                .requires(source -> (source.permissions() instanceof LevelBasedPermissionSet levels
                        && levels.level().isEqualOrHigherThan(PermissionLevel.byId(ConfigManager.get().commands.unplugCommandPermissions)))
                        || (source.getPlayer() != null && AccessController.hasUseGrant(source.getPlayer())))
                .executes(ctx -> unplug(ctx.getSource().getPlayerOrException(),
                        ConfigManager.get().unplugged.defaultUnpluggedTimeout, ""))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(ctx -> unplug(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "minutes"), ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> unplug(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "minutes"), StringArgumentType.getString(ctx, "reason")))));
        root.then(Commands.literal("status")
                .executes(ctx -> status(ctx.getSource().getPlayerOrException())));
        root.then(Commands.literal("cancel")
                .executes(ctx -> cancel(ctx.getSource().getPlayerOrException())));
        root.then(Commands.literal("auto")
                .then(Commands.literal("on")
                        .executes(ctx -> auto(ctx.getSource().getPlayerOrException(),
                                ConfigManager.get().automatic.defaultDurationMinutes))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(ctx -> auto(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(Commands.literal("off")
                        .executes(ctx -> autoOff(ctx.getSource().getPlayerOrException()))));
        root.then(Commands.literal("next")
                .executes(ctx -> next(ctx.getSource().getPlayerOrException(),
                        ConfigManager.get().automatic.defaultDurationMinutes))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(ctx -> next(ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "minutes")))));
        if (ConfigManager.get().commands.enableUnplugCommand) event.getDispatcher().register(root);
        if (ConfigManager.get().commands.enableAfkCommand) {
            if (event.getDispatcher().getRoot().getChild("afk") == null) {
                event.getDispatcher().register(Commands.literal("afk").redirect(root.build()));
            } else {
                UnpluggedAfk.LOGGER.warn("The optional /afk alias was not registered because another mod owns it; use /unplug");
            }
        }
        AdminCommands.register(event.getDispatcher());
    }

    private static int status(ServerPlayer player) {
        player.sendSystemMessage(OfflinePlayerManager.get().automaticStatus(player));
        return 1;
    }

    private static int cancel(ServerPlayer player) {
        boolean cancelled = OfflinePlayerManager.get().cancelAutomatic(player.getUUID());
        player.sendSystemMessage(Translations.component(cancelled
                ? "command.unplugged_afk.cancelled" : "command.unplugged_afk.nothing_to_cancel"));
        return cancelled ? 1 : 0;
    }

    private static int auto(ServerPlayer player, long minutes) {
        if (!AccessController.mayAutoUnplug(player) || minutes > AccessController.maximumDuration(player)) {
            player.sendSystemMessage(Translations.component("command.unplugged_afk.denied"));
            return 0;
        }
        OfflinePlayerManager.get().setAutomatic(player, minutes);
        player.sendSystemMessage(Translations.component("command.unplugged_afk.auto.enabled", minutes));
        return 1;
    }

    private static int autoOff(ServerPlayer player) {
        OfflinePlayerManager.get().disableAutomatic(player.getUUID());
        player.sendSystemMessage(Translations.component("command.unplugged_afk.auto.disabled"));
        return 1;
    }

    private static int next(ServerPlayer player, long minutes) {
        if (!AccessController.mayAutoUnplug(player) || minutes > AccessController.maximumDuration(player)) {
            player.sendSystemMessage(Translations.component("command.unplugged_afk.denied"));
            return 0;
        }
        OfflinePlayerManager.get().armNextLogout(player, minutes);
        player.sendSystemMessage(Translations.component("command.unplugged_afk.next.armed", minutes));
        return 1;
    }

    private static int unplug(ServerPlayer player, long minutes, String reason) {
        var server = player.level().getServer();
        if (server != null && server.isSingleplayerOwner(player.nameAndId())) {
            player.sendSystemMessage(Translations.component("command.unplugged_afk.singleplayer_owner"));
            return 0;
        }
        if (!AccessController.mayUse(player)) {
            player.sendSystemMessage(Translations.component("command.unplugged_afk.denied"));
            return 0;
        }
        long maximum = AccessController.maximumDuration(player);
        if (minutes > maximum) {
            player.sendSystemMessage(Translations.component("command.unplugged_afk.duration_too_long", maximum));
            return 0;
        }
        return OfflinePlayerManager.get().unplug(player, minutes, reason) ? 1 : 0;
    }

    @SubscribeEvent
    public void serverTick(ServerTickEvent.Post event) {
        OfflinePlayerManager.get().tick(event.getServer());
    }

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) {
        OfflinePlayerManager.get().start(event.getServer());
    }

    @SubscribeEvent
    public void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof OfflinePlayer)) {
            OfflinePlayerManager.get().onRealPlayerJoined(player);
        }
    }

    @SubscribeEvent
    public void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof OfflinePlayer)) {
            OfflinePlayerManager.get().onRealPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        OfflinePlayerManager.get().stop(event.getServer());
    }
}
