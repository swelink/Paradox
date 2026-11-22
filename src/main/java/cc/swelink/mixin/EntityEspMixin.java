package cc.swelink.mixin;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.esp.EspModule;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityEspMixin {
    @Inject(method = "affectedByCulling", at = @At("HEAD"), cancellable = true)
    private void paradox$keepEspModel(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Paradox.MODULES.get(EspModule.class).shaderActive() && Paradox.MODULES.get(EspModule.class).shouldRender(entity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void paradox$espColor(Entity entity, EntityRenderState state, float tick, CallbackInfo ci) {
        if (Paradox.MODULES.get(EspModule.class).shaderActive() && Paradox.MODULES.get(EspModule.class).shouldRender(entity)) {
            state.outlineColor = Paradox.MODULES.get(EspModule.class).color();
        }
    }
}
