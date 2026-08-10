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
                .requires(source -> source.permissions() instanceof LevelBasedPermissionSet levels
                        && levels.level().isEqualOrHigherThan(PermissionLevel.byId(ConfigManager.get().commands.unplugCommandPermissions)))
                .executes(ctx -> unplug(ctx.getSource().getPlayerOrException(),
                        ConfigManager.get().unplugged.defaultUnpluggedTimeout, ""))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(ctx -> unplug(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "minutes"), ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> unplug(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "minutes"), StringArgumentType.getString(ctx, "reason")))));
        if (ConfigManager.get().commands.enableUnplugCommand) event.getDispatcher().register(root);
        if (ConfigManager.get().commands.enableAfkCommand) event.getDispatcher().register(Commands.literal("afk").redirect(root.build()));
        AdminCommands.register(event.getDispatcher());
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
    public void serverStopping(ServerStoppingEvent event) {
        OfflinePlayerManager.get().stop(event.getServer());
    }
}
