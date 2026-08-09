package com.theonlytazz.unpluggedafk.mixin;

import com.theonlytazz.unpluggedafk.player.OfflinePlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityAuthorityMixin {
    @Inject(method = "isControlledByLocalInstance", at = @At("HEAD"), cancellable = true)
    private void unpluggedAfk$serverControlsOfflinePlayers(CallbackInfoReturnable<Boolean> callback) {
        Entity self = (Entity) (Object) this;
        if (self instanceof OfflinePlayer || self.getControllingPassenger() instanceof OfflinePlayer) {
            callback.setReturnValue(!self.level().isClientSide());
        }
    }
}
