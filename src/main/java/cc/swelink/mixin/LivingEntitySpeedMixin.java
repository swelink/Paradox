package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.movement.speed.SpeedModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySpeedMixin {
    @WrapOperation(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void paradox$customSpeed(LivingEntity entity, MoverType type, Vec3 movement, Operation<Void> original,
                                   @Local(argsOnly = true) Vec3 input) {
        var client = Minecraft.getInstance();
        Vec3 adjusted = entity == client.player
                ? Paradox.MODULES.get(SpeedModule.class).movement(client, input, movement) : movement;
        if (adjusted != movement) entity.setDeltaMovement(Paradox.MODULES.get(SpeedModule.class).velocity(adjusted));
        original.call(entity, type, adjusted);
    }
}
