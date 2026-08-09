package com.theonlytazz.unpluggedafk;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

import java.util.Collection;

final class AdminCommands {
    private AdminCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var spawn = Commands.literal("spawn")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> spawn(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"),
                                ConfigManager.get().unplugged.defaultUnpluggedTimeout, ""))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(ctx -> spawn(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "minutes"), ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> spawn(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "minutes"), StringArgumentType.getString(ctx, "reason"))))));

        var settings = Commands.literal("set")
                .requires(source -> ConfigManager.get().main.advancedAdminOptions)
                .then(Commands.argument("config", StringArgumentType.string())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ConfigManager.optionNames(), builder))
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> setOption(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "config"),
                                        StringArgumentType.getString(ctx, "value")))));

        dispatcher.register(Commands.literal("unplugged-admin")
                .requires(source -> source.hasPermission(ConfigManager.get().commands.unpluggedAdminCommandPermissions))
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource()))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> playerInfo(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("save").executes(ctx -> save(ctx.getSource())))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("purge").executes(ctx -> purge(ctx.getSource())))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> kick(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                .then(spawn)
                .then(settings));
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.info",
                OfflinePlayerManager.get().activeCount(), OfflinePlayerManager.get().sessions().size()), false);
        return 1;
    }

    private static int playerInfo(CommandSourceStack source, Collection<GameProfile> profiles) {
        for (GameProfile profile : profiles) {
            source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.player_info", profile.getName(),
                    OfflinePlayerManager.get().session(profile.getId()).map(Object::toString)
                            .orElseGet(() -> Translations.component("command.unplugged_afk.admin.not_tracked").getString())), false);
        }
        return profiles.size();
    }

    private static int list(CommandSourceStack source) {
        var sessions = OfflinePlayerManager.get().sessions();
        if (sessions.isEmpty()) source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.list.empty"), false);
        sessions.forEach(session -> source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.list.entry",
                session.name(), session.status(), session.timeoutMinutes()), false));
        return sessions.size();
    }

    private static int save(CommandSourceStack source) {
        ConfigManager.save();
        OfflinePlayerManager.get().saveSessions();
        source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.saved"), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ConfigManager.reload();
        source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.reloaded"), true);
        return 1;
    }

    private static int purge(CommandSourceStack source) {
        int removed = OfflinePlayerManager.get().purgeEnded();
        source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.purged", removed), true);
        return removed;
    }

    private static int kick(CommandSourceStack source, Collection<GameProfile> profiles) {
        int removed = 0;
        for (GameProfile profile : profiles) {
            if (OfflinePlayerManager.get().remove(source.getServer(), profile.getId(), UnpluggedStatus.TERMINATED,
                    "message.unplugged_afk.reason.admin")) removed++;
        }
        int count = removed;
        source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.removed", count), true);
        return removed;
    }

    private static int spawn(CommandSourceStack source, Collection<GameProfile> profiles, long minutes, String reason) {
        int spawned = 0;
        for (GameProfile profile : profiles) {
            var online = source.getServer().getPlayerList().getPlayer(profile.getId());
            boolean result = online != null
                    ? OfflinePlayerManager.get().unplug(online, minutes, reason)
                    : OfflinePlayerManager.get().spawn(source.getServer(), profile, minutes, reason);
            if (result) spawned++;
        }
        return spawned;
    }

    private static int setOption(CommandSourceStack source, String key, String value) {
        if (!ConfigManager.setOption(key, value)) {
            source.sendFailure(Translations.component("command.unplugged_afk.admin.set.invalid", key));
            return 0;
        }
        source.sendSuccess(() -> Translations.component("command.unplugged_afk.admin.set.success", key, value), true);
        return 1;
    }
}
