package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.utils.rotation.MoveFix;
import cc.swelink.utils.rotation.Rotation;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementRotationMixin {
    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 paradox$fixJumpVelocity(Vec3 original) {
        Rotation rotation = paradox$movementRotation();
        if (rotation == null) {
            return original;
        }

        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.2F, 0.0, Mth.cos(yaw) * 0.2F);
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F")
    )
    private float paradox$fixFallFlyingPitch(float original) {
        Rotation rotation = paradox$movementRotation();
        return rotation == null ? original : rotation.pitch();
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 paradox$fixFallFlyingLook(Vec3 original) {
        Rotation rotation = paradox$movementRotation();
        return rotation == null ? original : rotation.direction();
    }

    @Unique
    private Rotation paradox$movementRotation() {
        if ((Object) this != Minecraft.getInstance().player) {
            return null;
        }
        if (Paradox.ROTATIONS.movementCorrection() == MoveFix.OFF) {
            return null;
        }
        return Paradox.ROTATIONS.packetRotation();
    }
}
