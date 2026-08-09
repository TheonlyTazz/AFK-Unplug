package com.theonlytazz.unpluggedafk;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import com.theonlytazz.unpluggedafk.state.UnpluggedStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
                .then(Commands.literal("enabled").then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("disableDamage").then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setDisableDamage(ctx.getSource(), BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("defaultTimeout").then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(ctx -> setTimeout(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")))));

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
        source.sendSuccess(() -> Component.literal("Unplugged AFK NeoForge: "
                + OfflinePlayerManager.get().activeCount() + " active, "
                + OfflinePlayerManager.get().sessions().size() + " tracked session(s)"), false);
        return 1;
    }

    private static int playerInfo(CommandSourceStack source, Collection<GameProfile> profiles) {
        for (GameProfile profile : profiles) {
            source.sendSuccess(() -> Component.literal(profile.getName() + ": "
                    + OfflinePlayerManager.get().session(profile.getId()).map(Object::toString).orElse("not tracked")), false);
        }
        return profiles.size();
    }

    private static int list(CommandSourceStack source) {
        var sessions = OfflinePlayerManager.get().sessions();
        if (sessions.isEmpty()) source.sendSuccess(() -> Component.literal("No tracked unplugged players."), false);
        sessions.forEach(session -> source.sendSuccess(() -> Component.literal(session.name() + " — " + session.status()
                + " (" + session.timeoutMinutes() + " min)"), false));
        return sessions.size();
    }

    private static int save(CommandSourceStack source) {
        ConfigManager.save();
        OfflinePlayerManager.get().saveSessions();
        source.sendSuccess(() -> Component.literal("Unplugged AFK state saved."), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ConfigManager.reload();
        source.sendSuccess(() -> Component.literal("Unplugged AFK configuration reloaded."), true);
        return 1;
    }

    private static int purge(CommandSourceStack source) {
        int removed = OfflinePlayerManager.get().purgeEnded();
        source.sendSuccess(() -> Component.literal("Purged " + removed + " ended session(s)."), true);
        return removed;
    }

    private static int kick(CommandSourceStack source, Collection<GameProfile> profiles) {
        int removed = 0;
        for (GameProfile profile : profiles) {
            if (OfflinePlayerManager.get().remove(source.getServer(), profile.getId(), UnpluggedStatus.TERMINATED,
                    ConfigManager.get().messages.unpluggedTerminated)) removed++;
        }
        int count = removed;
        source.sendSuccess(() -> Component.literal("Removed " + count + " offline player(s)."), true);
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

    private static int setEnabled(CommandSourceStack source, boolean value) {
        ConfigManager.get().main.unpluggedAfkEnabled = value;
        return saveChanged(source, "enabled", value);
    }

    private static int setDisableDamage(CommandSourceStack source, boolean value) {
        ConfigManager.get().unplugged.unpluggedDisableDamage = value;
        return saveChanged(source, "disableDamage", value);
    }

    private static int setTimeout(CommandSourceStack source, int value) {
        ConfigManager.get().unplugged.defaultUnpluggedTimeout = value;
        return saveChanged(source, "defaultTimeout", value);
    }

    private static int saveChanged(CommandSourceStack source, String key, Object value) {
        ConfigManager.save();
        source.sendSuccess(() -> Component.literal("Set " + key + " to " + value), true);
        return 1;
    }
}
