package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.player.OptimizerModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.world.entity.AbstractInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SteppedInterpolationHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SteppedInterpolationHandler.class)
public abstract class SteppedInterpolationHandlerMixin extends AbstractInterpolationHandler {
    protected SteppedInterpolationHandlerMixin(Entity entity, int steps) {
        super(entity, steps);
    }

    @ModifyExpressionValue(method = {"startInterpolating", "doInterpolate"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/SteppedInterpolationHandler;interpolationSteps:I"))
    private int paradox$fastPlayers(int steps) {
        return Paradox.MODULES.get(OptimizerModule.class).interpolationSteps(entity, steps);
    }
}
