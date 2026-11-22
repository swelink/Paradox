package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.ElytraBoostModule;
import cc.swelink.utils.rotation.MoveFix;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketRotationMixin {
    @Shadow
    private LivingEntity attachedToEntity;

    @ModifyConstant(method = "tick", constant = {@Constant(doubleValue = 1.5), @Constant(doubleValue = 0.1)})
    private double paradox$boostSpeed(double original) {
        if (attachedToEntity == null || attachedToEntity != Minecraft.getInstance().player) return original;
        return original * Paradox.MODULES.get(ElytraBoostModule.class).rocketScale(Minecraft.getInstance().player);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 paradox$boostDirection(Vec3 original) {
        if (attachedToEntity == null || attachedToEntity != Minecraft.getInstance().player) return original;
        var glided = Paradox.MODULES.get(ElytraBoostModule.class).boostRotation(Minecraft.getInstance().player);
        if (glided != null) return glided.direction();
        if (Paradox.ROTATIONS.movementCorrection() == MoveFix.OFF) return original;
        var rotation = Paradox.ROTATIONS.packetRotation();
        return rotation == null ? original : rotation.direction();
    }
}
