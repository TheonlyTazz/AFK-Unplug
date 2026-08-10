package com.theonlytazz.unpluggedafk.permission;

import com.theonlytazz.unpluggedafk.UnpluggedAfk;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class AccessController {
    public static final String USE = "unplugged_afk.use";
    public static final String MAX_DURATION = "unplugged_afk.duration.max";
    public static final String AUTO = "unplugged_afk.auto";
    public static final String BYPASS_LIMITS = "unplugged_afk.bypass_limits";

    private AccessController() {}

    public static boolean mayUse(ServerPlayer player) {
        if (isOperatorBypass(player)) return true;
        Optional<Boolean> rankValue = ftbBoolean(player, USE);
        if (rankValue.isPresent()) return rankValue.get();

        var access = ConfigManager.get().access;
        boolean listed = access.players.stream().anyMatch(entry -> matches(player, entry));
        return switch (access.mode) {
            case "ALLOWLIST" -> listed;
            case "DENYLIST" -> !listed;
            default -> true;
        };
    }

    public static boolean mayAutoUnplug(ServerPlayer player) {
        if (!mayUse(player)) return false;
        return isOperatorBypass(player) || ftbBoolean(player, AUTO).orElse(true);
    }

    public static boolean bypassesLimits(ServerPlayer player) {
        return isOperatorBypass(player) || ftbBoolean(player, BYPASS_LIMITS).orElse(false);
    }

    public static long maximumDuration(ServerPlayer player) {
        long configured = ConfigManager.get().unplugged.maximumUnpluggedTimeout;
        if (bypassesLimits(player)) return Long.MAX_VALUE;
        return Math.max(1L, ftbLong(player, MAX_DURATION).orElse(configured));
    }

    private static boolean matches(ServerPlayer player, String entry) {
        String value = entry == null ? "" : entry.trim();
        return value.equalsIgnoreCase(player.getUUID().toString())
                || value.toLowerCase(Locale.ROOT).equals(player.nameAndId().name().toLowerCase(Locale.ROOT));
    }

    private static boolean isOperatorBypass(ServerPlayer player) {
        return ConfigManager.get().access.operatorsBypass
                && player.level().getServer() != null
                && player.level().getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static Optional<Boolean> ftbBoolean(ServerPlayer player, String node) {
        return permissionValue(player, node).flatMap(value -> invokeOptional(value, "asBoolean", Boolean.class));
    }

    private static Optional<Long> ftbLong(ServerPlayer player, String node) {
        Optional<?> value = permissionValue(player, node);
        Optional<Integer> integer = value.flatMap(v -> invokeOptional(v, "asInteger", Integer.class));
        if (integer.isPresent()) return Optional.of(integer.get().longValue());
        return value.flatMap(v -> invokeOptional(v, "asLong", Long.class));
    }

    private static Optional<?> permissionValue(ServerPlayer player, String node) {
        if (!ConfigManager.get().access.useFtbRanks) return Optional.empty();
        try {
            Class<?> api = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
            Method method = api.getMethod("getPermissionValue", ServerPlayer.class, String.class);
            return Optional.ofNullable(method.invoke(null, player, node));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException exception) {
            UnpluggedAfk.LOGGER.debug("Could not query FTB Ranks permission {}", node, exception);
            return Optional.empty();
        }
    }

    private static <T> Optional<T> invokeOptional(Object target, String methodName, Class<T> type) {
        try {
            Object result = target.getClass().getMethod(methodName).invoke(target);
            if (result instanceof Optional<?> optional && optional.isPresent() && type.isInstance(optional.get())) {
                return Optional.of(type.cast(optional.get()));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Optional.empty();
    }
}
