package com.theonlytazz.unpluggedafk.player;

import com.mojang.authlib.GameProfile;
import com.theonlytazz.unpluggedafk.config.ConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class OfflinePlayer extends ServerPlayer {
    private boolean active = true;

    OfflinePlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation information) {
        super(server, level, profile, information);
    }

    public boolean isOfflineReplacement() {
        return active;
    }

    void deactivate() {
        active = false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (ConfigManager.get().unplugged.unpluggedDisableDamage) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (ConfigManager.get().unplugged.resetHealthUponDeath) {
            setHealth(getMaxHealth());
            getFoodData().setFoodLevel(20);
            return;
        }
        OfflinePlayerManager.get().terminate(this, "Player died");
    }
}
