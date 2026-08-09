package com.theonlytazz.unpluggedafk.mixin;

import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void unpluggedAfk$suppressSyntheticJoin(Component message, boolean overlay, CallbackInfo callback) {
        if (OfflinePlayerManager.get().shouldSuppressJoin(message)) callback.cancel();
    }
}
