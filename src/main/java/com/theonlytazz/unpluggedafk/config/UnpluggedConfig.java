package com.theonlytazz.unpluggedafk.config;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public final class UnpluggedConfig {
    public Main main = new Main();
    public Commands commands = new Commands();
    public OfflinePlayers unplugged = new OfflinePlayers();
    public Access access = new Access();
    public Messages messages = new Messages();

    public void normalize() {
        main = Objects.requireNonNullElseGet(main, Main::new);
        commands = Objects.requireNonNullElseGet(commands, Commands::new);
        unplugged = Objects.requireNonNullElseGet(unplugged, OfflinePlayers::new);
        access = Objects.requireNonNullElseGet(access, Access::new);
        messages = Objects.requireNonNullElseGet(messages, Messages::new);
        commands.unplugCommandPermissions = clampPermission(commands.unplugCommandPermissions);
        commands.unpluggedAdminCommandPermissions = clampPermission(commands.unpluggedAdminCommandPermissions);
        commands.afkCommandPermissions = clampPermission(commands.afkCommandPermissions);
        unplugged.defaultUnpluggedTimeout = Math.max(1, unplugged.defaultUnpluggedTimeout);
        unplugged.maximumUnpluggedTimeout = Math.max(1, unplugged.maximumUnpluggedTimeout);
        unplugged.maximumSimultaneousPlayers = Math.max(1, unplugged.maximumSimultaneousPlayers);
        access.mode = Objects.requireNonNullElse(access.mode, "EVERYONE").toUpperCase();
        if (!access.mode.equals("EVERYONE") && !access.mode.equals("ALLOWLIST") && !access.mode.equals("DENYLIST")) {
            access.mode = "EVERYONE";
        }
        access.players = Objects.requireNonNullElseGet(access.players, ArrayList::new);
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
        public long maximumUnpluggedTimeout = 129_600;
        public int maximumSimultaneousPlayers = 20;
        public boolean resetHealthUponDeath;
        public boolean unpluggedDisableDamage;
        public boolean unpluggedHidePlayer;
        public boolean unpluggedHideFromOps;
        public boolean showAfkNameplate = true;
        public boolean showAfkInTabList = true;
    }

    public static final class Access {
        public String mode = "EVERYONE";
        public List<String> players = new ArrayList<>();
        public boolean operatorsBypass = true;
        public boolean useFtbRanks = true;
    }

    public static final class Messages {
        public boolean broadcastMessages = true;
        public boolean hideUnpluggedJoin = true;
        public boolean displayDuration;
        public boolean displayReturnFeedback;
        public String durationFormat = "PRETTY";
        public String timeDateFormat = "RFC1123";
    }
}
