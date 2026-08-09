package com.theonlytazz.unpluggedafk.config;

import java.util.Objects;

public final class UnpluggedConfig {
    public Main main = new Main();
    public Commands commands = new Commands();
    public OfflinePlayers unplugged = new OfflinePlayers();
    public Messages messages = new Messages();

    public void normalize() {
        main = Objects.requireNonNullElseGet(main, Main::new);
        commands = Objects.requireNonNullElseGet(commands, Commands::new);
        unplugged = Objects.requireNonNullElseGet(unplugged, OfflinePlayers::new);
        messages = Objects.requireNonNullElseGet(messages, Messages::new);
        commands.unplugCommandPermissions = clampPermission(commands.unplugCommandPermissions);
        commands.unpluggedAdminCommandPermissions = clampPermission(commands.unpluggedAdminCommandPermissions);
        commands.afkCommandPermissions = clampPermission(commands.afkCommandPermissions);
        unplugged.defaultUnpluggedTimeout = Math.max(1, unplugged.defaultUnpluggedTimeout);
    }

    private static int clampPermission(int value) {
        return Math.max(0, Math.min(4, value));
    }

    public static final class Main {
        public boolean unpluggedAfkEnabled = true;
        public boolean debugMode;
        public boolean reducedListDebugInfo = true;
        public boolean advancedAdminOptions;
    }

    public static final class Commands {
        public int unplugCommandPermissions;
        public int unpluggedAdminCommandPermissions = 3;
        public int afkCommandPermissions;
        public boolean enableUnplugCommand = true;
        public boolean enableAfkCommand;
    }

    public static final class OfflinePlayers {
        public long defaultUnpluggedTimeout = 129_600;
        public boolean resetHealthUponDeath;
        public boolean unpluggedDisableDamage;
        public boolean unpluggedHidePlayer;
        public boolean unpluggedHideFromOps;
    }

    public static final class Messages {
        public boolean broadcastMessages;
        public boolean hideUnpluggedJoin;
        public boolean displayDuration;
        public boolean displayReturnFeedback;
        public String defaultUnpluggedReason = "";
        public String unpluggedKickMessage = "§6Your player will be AFK§r";
        public String unpluggedExpiredReason = "§eTimeout expired§r";
        public String unpluggedStarted = " §ehas been unplugged§r";
        public String unpluggedReplaced = "§6Replaced by player§r";
        public String unpluggedTerminated = "§cAFK session terminated§r";
    }
}
