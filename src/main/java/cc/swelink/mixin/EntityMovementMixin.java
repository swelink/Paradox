package cc.swelink.mixin;

import cc.swelink.Paradox;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @ModifyArg(
            method = "moveRelative",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"),
            index = 2
    )
    private float paradox$movementYaw(float yaw) {
        if ((Object) this != Minecraft.getInstance().player) {
            return yaw;
        }
        return Paradox.ROTATIONS.movementYaw(yaw);
    }
}
