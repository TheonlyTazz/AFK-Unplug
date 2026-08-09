package com.theonlytazz.unpluggedafk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.theonlytazz.unpluggedafk.player.OfflinePlayerManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.SocketAddress;

@Mixin(ServerLoginPacketListenerImpl.class)
abstract class ServerLoginPacketListenerMixin {
    @WrapOperation(
            method = "handleAcceptedLogin",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;canPlayerLogin(Ljava/net/SocketAddress;Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/network/chat/Component;")
    )
    private Component unpluggedAfk$replaceShadowBeforeLogin(PlayerList playerList, SocketAddress address,
                                                            GameProfile profile, Operation<Component> original) {
        OfflinePlayerManager.get().prepareRealLogin(profile.getId());
        return original.call(playerList, address, profile);
    }
}
