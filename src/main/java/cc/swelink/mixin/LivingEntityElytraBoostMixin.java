package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.ElytraBoostModule;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityElytraBoostMixin {
    @ModifyReturnValue(method = "updateFallFlyingMovement", at = @At("RETURN"))
    private Vec3 paradox$elytraBoost(Vec3 original) {
        var client = Minecraft.getInstance();
        if ((Object) this != client.player) return original;
        Vec3 glide = Paradox.MODULES.get(ElytraBoostModule.class).glide(client.player);
        return glide == null ? original : glide;
    }
}
