package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.RotationsModule;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderRotationMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void paradox$renderSilentRotations(LivingEntity entity, LivingEntityRenderState state,
                                            float tickDelta, CallbackInfo ci) {
        Paradox.MODULES.get(RotationsModule.class).applyRenderState(entity, state, tickDelta);
    }
}
