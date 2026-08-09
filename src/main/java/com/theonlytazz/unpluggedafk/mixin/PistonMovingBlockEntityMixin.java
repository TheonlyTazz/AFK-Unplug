package com.theonlytazz.unpluggedafk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.theonlytazz.unpluggedafk.player.OfflinePlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PistonMovingBlockEntity.class)
abstract class PistonMovingBlockEntityMixin {
    @WrapOperation(method = "moveCollidedEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getPistonPushReaction()Lnet/minecraft/world/level/material/PushReaction;"))
    private static PushReaction unpluggedAfk$applySlimeVelocity(Entity entity, Operation<PushReaction> original,
                                                                @Local(argsOnly = true) PistonMovingBlockEntity piston) {
        if (entity instanceof OfflinePlayer && piston.getMovedState().is(Blocks.SLIME_BLOCK)) {
            Vec3 velocity = entity.getDeltaMovement();
            double x = velocity.x(), y = velocity.y(), z = velocity.z();
            Direction direction = piston.getMovementDirection();
            switch (direction.getAxis()) {
                case X -> x = direction.getStepX();
                case Y -> y = direction.getStepY();
                case Z -> z = direction.getStepZ();
            }
            entity.setDeltaMovement(x, y, z);
        }
        return original.call(entity);
    }
}
